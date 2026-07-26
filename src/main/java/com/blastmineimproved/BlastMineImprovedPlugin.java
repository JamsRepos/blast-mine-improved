package com.blastmineimproved;

import com.blastmineimproved.overlay.HelperPanelOverlay;
import com.blastmineimproved.overlay.InventoryOreTimerOverlay;
import com.blastmineimproved.overlay.NextClickOverlay;
import com.blastmineimproved.overlay.OreHudOverlay;
import com.blastmineimproved.overlay.RockOverlay;
import com.google.inject.Provides;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Blast Mine Improved",
	description = "Blast Mine helper with NE rotation guidance, menu safety, ore timers, and sack XP estimates",
	tags = {"blast", "mine", "mining", "dynamite", "lovakengj", "minigame", "skilling"},
	conflicts = {"Blast Mine", "Blast Mine Dynamite Restriction"}
)
public class BlastMineImprovedPlugin extends Plugin
{
	private static final String NO_DYNAMITE_MSG = "That was the last of your dynamite! You can no longer load cavities.";
	private static final String REPLENISH_DYNAMITE_MSG = "You have dynamite and can load chiseled cavities once more.";

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
	private OreHudOverlay oreHudOverlay;

	@Inject
	private HelperPanelOverlay helperPanelOverlay;

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

	private boolean properLogged;
	private boolean hadDynamite;
	private ItemContainer previousInventory;

	@Override
	protected void startUp()
	{
		overlayManager.add(rockOverlay);
		overlayManager.add(oreHudOverlay);
		overlayManager.add(helperPanelOverlay);
		overlayManager.add(nextClickOverlay);
		overlayManager.add(inventoryOreTimerOverlay);
		log.debug("Blast Mine Improved started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(rockOverlay);
		overlayManager.remove(oreHudOverlay);
		overlayManager.remove(helperPanelOverlay);
		overlayManager.remove(nextClickOverlay);
		overlayManager.remove(inventoryOreTimerOverlay);
		rocks.clear();
		oreTracker.reset();

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
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			rocks.clear();
			oreTracker.reset();
		}

		if (event.getGameState() == GameState.LOGGED_IN)
		{
			properLogged = false;
			Timer logTimer = new Timer("bmi-login-grace", true);
			logTimer.schedule(new TimerTask()
			{
				@Override
				public void run()
				{
					properLogged = true;
					logTimer.cancel();
				}
			}, 3000);
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
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
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory != null && event.getItemContainer() == inventory)
		{
			previousInventory = inventory;
			oreTracker.syncFromInventory();
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

		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
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
