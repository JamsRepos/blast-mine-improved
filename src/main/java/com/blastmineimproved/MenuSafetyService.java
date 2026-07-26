package com.blastmineimproved;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;

@Singleton
public class MenuSafetyService
{
	private static final String EXCAVATE = "Excavate";
	private static final String LIGHT = "Light";
	private static final String PLACE = "Place";
	private static final String DEPOSIT = "Deposit";

	private final Client client;
	private final BlastMineImprovedConfig config;
	private final HelperService helperService;

	@Inject
	MenuSafetyService(Client client, BlastMineImprovedConfig config, HelperService helperService)
	{
		this.client = client;
		this.config = config;
		this.helperService = helperService;
	}

	public void onMenuEntryAdded(MenuEntryAdded event, Map<WorldPoint, BlastMineRock> rocks)
	{
		if (!BlastMineArea.isInBlastMine(client))
		{
			return;
		}

		String option = event.getOption();
		MenuEntry entry = event.getMenuEntry();

		if (config.deprioritizeWithoutDynamite()
			&& EXCAVATE.equals(option)
			&& isHardRock(event.getIdentifier())
			&& !hasUnnotedDynamite())
		{
			entry.setDeprioritized(true);
		}

		if (config.pairLightSafety() && LIGHT.equals(option) && isLoadedPot(event.getIdentifier()))
		{
			WorldPoint point = worldPointForMenuTarget(entry);
			if (point != null && !partnerAlsoLoaded(point, rocks))
			{
				client.getMenu().removeMenuEntry(entry);
			}
		}

		if (config.prioritizeHelperOptions())
		{
			preferHelperOption(entry, option);
		}
	}

	private void preferHelperOption(MenuEntry entry, String option)
	{
		HelperAction action = helperService.getCurrentAction();
		if (action == null || action.getKind() == HelperAction.Kind.IDLE)
		{
			return;
		}

		boolean match = false;
		switch (action.getKind())
		{
			case EXCAVATE:
				match = EXCAVATE.equals(option);
				break;
			case PLACE_DYNAMITE:
				match = PLACE.equals(option) || "Use".equals(option);
				break;
			case LIGHT:
				match = LIGHT.equals(option);
				break;
			case DEPOSIT_SACK:
				match = DEPOSIT.equals(option) || option.contains("Deposit");
				break;
			case BANK_DYNAMITE:
				match = "Use".equals(option);
				break;
			case COLLECT_OPERATOR:
			case WEAR_PROSPECTORS:
				match = "Talk-to".equals(option) || "Collect".equals(option);
				break;
			default:
				break;
		}

		if (match)
		{
			entry.setForceLeftClick(true);
		}
	}

	private boolean partnerAlsoLoaded(WorldPoint point, Map<WorldPoint, BlastMineRock> rocks)
	{
		NortheastSite site = NortheastSite.nearestTo(point);
		if (site == null)
		{
			return true;
		}

		List<BlastMineRock> partners = new ArrayList<>();
		for (Map.Entry<WorldPoint, BlastMineRock> e : rocks.entrySet())
		{
			if (NortheastSite.nearestTo(e.getKey()) == site)
			{
				partners.add(e.getValue());
			}
		}

		if (partners.size() < 2)
		{
			// Single tracked rock at site — allow light if this one is loaded
			return partners.stream().anyMatch(r -> r.getType() == BlastMineRockType.LOADED);
		}

		long loaded = partners.stream().filter(r -> r.getType() == BlastMineRockType.LOADED).count();
		return loaded >= 2;
	}

	private WorldPoint worldPointForMenuTarget(MenuEntry entry)
	{
		return WorldPoint.fromScene(client, entry.getParam0(), entry.getParam1(), client.getPlane());
	}

	private boolean hasUnnotedDynamite()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		return inventory != null && inventory.contains(ItemID.LOVAKENGJ_DYNAMITE_FUSED);
	}

	private static boolean isHardRock(int id)
	{
		return id == ObjectID.BLAST_MINING_WALL_01 || id == ObjectID.BLAST_MINING_WALL_02;
	}

	private static boolean isLoadedPot(int id)
	{
		return id == ObjectID.BLAST_MINING_WALL_POT_01 || id == ObjectID.BLAST_MINING_WALL_POT_02;
	}
}
