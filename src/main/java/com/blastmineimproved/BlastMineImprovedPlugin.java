package com.blastmineimproved;

import com.blastmineimproved.overlay.InventoryOreTimerOverlay;
import com.blastmineimproved.overlay.NextClickOverlay;
import com.blastmineimproved.overlay.RockOverlay;
import com.blastmineimproved.overlay.StatusOverlay;
import com.google.inject.Provides;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Jam's Blast Mine",
	description = "Blast Mine helper with NE rotation guidance, menu safety, ore timers, and sack XP estimates",
	tags = {"blast", "mine", "mining", "dynamite", "lovakengj", "minigame", "skilling"},
	conflicts = {"Blast Mine", "Blast Mine Dynamite Restriction"}
)
public class BlastMineImprovedPlugin extends Plugin
{
	private static final String NO_DYNAMITE_MSG = "That was the last of your dynamite! You can no longer load cavities.";
	private static final String REPLENISH_DYNAMITE_MSG = "You have dynamite and can load chiseled cavities once more.";
	/** ~3s at 600ms/tick — avoids a daemon Timer so login inventory sync cannot leak a thread. */
	private static final int LOGIN_GRACE_TICKS = 5;

	@Getter
	private final Map<WorldPoint, BlastMineRock> rocks = new HashMap<>();

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private BlastMineImprovedConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RockOverlay rockOverlay;

	@Inject
	private StatusOverlay statusOverlay;

	@Inject
	private NextClickOverlay nextClickOverlay;

	@Inject
	private InventoryOreTimerOverlay inventoryOreTimerOverlay;

	@Inject
	private HelperService helperService;

	@Inject
	private MenuSafetyService menuSafetyService;

	@Inject
	private BlastedOreTracker oreTracker;

	@Inject
	private GroundOreTracker groundOreTracker;

	private boolean properLogged;
	private int loginGraceTicks;
	private boolean hadDynamite;
	private ItemContainer previousInventory;

	@Override
	protected void startUp()
	{
		helperService.resetRotation();
		overlayManager.add(rockOverlay);
		overlayManager.add(statusOverlay);
		overlayManager.add(nextClickOverlay);
		overlayManager.add(inventoryOreTimerOverlay);
		log.debug("Jam's Blast Mine started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(rockOverlay);
		overlayManager.remove(statusOverlay);
		overlayManager.remove(nextClickOverlay);
		overlayManager.remove(inventoryOreTimerOverlay);
		rocks.clear();
		oreTracker.reset();
		groundOreTracker.reset();
		helperService.resetRotation();
		loginGraceTicks = 0;
		properLogged = false;

		final Widget blastMineWidget = client.getWidget(InterfaceID.LovakengjBlastMiningHud.DATA);
		if (blastMineWidget != null)
		{
			blastMineWidget.setHidden(false);
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		final GameObject gameObject = event.getGameObject();
		BlastMineRockType type = BlastMineRockType.getRockType(gameObject.getId());
		if (type == null)
		{
			return;
		}

		final BlastMineRock newRock = new BlastMineRock(gameObject, type);
		final BlastMineRock oldRock = rocks.get(gameObject.getWorldLocation());
		if (oldRock == null || oldRock.getType() != newRock.getType())
		{
			rocks.put(gameObject.getWorldLocation(), newRock);
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		final GameObject gameObject = event.getGameObject();
		if (BlastMineRockType.getRockType(gameObject.getId()) == null)
		{
			return;
		}

		WorldPoint loc = gameObject.getWorldLocation();
		BlastMineRock stored = rocks.get(loc);
		// Same-tick type-change spawn must not be wiped by the outgoing object's despawn.
		if (stored != null && stored.getGameObject() == gameObject)
		{
			rocks.remove(loc);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			rocks.clear();
			oreTracker.reset();
			groundOreTracker.reset();
			helperService.resetRotation();
		}

		if (event.getGameState() == GameState.LOGGED_IN)
		{
			properLogged = false;
			loginGraceTicks = LOGIN_GRACE_TICKS;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!BlastMineImprovedConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		String key = event.getKey();
		if ("rotationMethod".equals(key)
			|| "dynamitePerTrip".equals(key)
			|| "guideOrePickup".equals(key)
			|| "enableHelper".equals(key))
		{
			helperService.resetRotation();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (loginGraceTicks > 0)
		{
			loginGraceTicks--;
			if (loginGraceTicks == 0)
			{
				properLogged = true;
			}
		}

		if (rocks.isEmpty() && !BlastMineArea.isInBlastMine(client))
		{
			helperService.update(rocks);
			return;
		}

		rocks.values().removeIf(rock ->
			(rock.getRemainingTimeRelative() == 1 && rock.getType() != BlastMineRockType.NORMAL)
				|| (rock.getRemainingFuseTimeRelative() == 1 && rock.getType() == BlastMineRockType.LIT));

		oreTracker.syncFromInventory();
		helperService.update(rocks);
		checkDynamiteTransitions();
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		groundOreTracker.onItemSpawned(event.getTile().getWorldLocation(), event.getItem());
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		groundOreTracker.onItemDespawned(event.getTile().getWorldLocation(), event.getItem());
	}

	@Subscribe
	public void onItemQuantityChanged(ItemQuantityChanged event)
	{
		groundOreTracker.onItemQuantityChanged(
			event.getTile().getWorldLocation(),
			event.getItem(),
			event.getOldQuantity());
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null && event.getItemContainer() == inventory)
		{
			previousInventory = inventory;
			// Ore-timer sync runs on the game tick, after ground ItemDespawned events have queued
			// their spawn times — syncing here could race ahead of them and lose the floor time.
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		menuSafetyService.onMenuEntryAdded(event, rocks);
	}

	private void checkDynamiteTransitions()
	{
		if (!BlastMineArea.isInBlastMine(client) || !properLogged)
		{
			return;
		}

		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return;
		}

		boolean hasDynamite = inventory.contains(ItemID.LOVAKENGJ_DYNAMITE_FUSED);
		if (hadDynamite && !hasDynamite)
		{
			if (hasOutOfDynamiteMessages())
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", NO_DYNAMITE_MSG, null);
			}
			if (config.soundFx())
			{
				clientThread.invoke(() -> client.playSoundEffect(config.soundId(), config.soundVolume()));
			}
		}
		else if (!hadDynamite && hasDynamite && previousInventory != null)
		{
			if (hasReplenishedMessages())
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", REPLENISH_DYNAMITE_MSG, null);
			}
		}
		hadDynamite = hasDynamite;
	}

	private boolean hasOutOfDynamiteMessages()
	{
		BlastMineImprovedConfig.MessagesEnabledType type = config.chatMessages();
		return type == BlastMineImprovedConfig.MessagesEnabledType.ALL_MESSAGES
			|| type == BlastMineImprovedConfig.MessagesEnabledType.OUT_OF_DYNAMITE;
	}

	private boolean hasReplenishedMessages()
	{
		BlastMineImprovedConfig.MessagesEnabledType type = config.chatMessages();
		return type == BlastMineImprovedConfig.MessagesEnabledType.ALL_MESSAGES
			|| type == BlastMineImprovedConfig.MessagesEnabledType.REPLENISHED_DYNAMITE;
	}

	@Provides
	BlastMineImprovedConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BlastMineImprovedConfig.class);
	}
}
