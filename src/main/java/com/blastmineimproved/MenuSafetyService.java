package com.blastmineimproved;

import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;

@Singleton
public class MenuSafetyService
{
	private static final String EXCAVATE = "Excavate";
	private static final String LIGHT = "Light";
	private static final String PLACE = "Place";

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
		boolean outOfDynamite = !hasUnnotedDynamite();

		if (config.deprioritizeWithoutDynamite()
			&& EXCAVATE.equals(option)
			&& isHardRock(event.getIdentifier())
			&& outOfDynamite)
		{
			entry.setDeprioritized(true);
		}

		if (config.enableHelper() && config.hideOffPathMenus() && helperService.isFocusingBlastingStep())
		{
			if (EXCAVATE.equals(option) || PLACE.equals(option) || LIGHT.equals(option))
			{
				// Loaded pots must stay firable when dynamite is gone (odd last pot).
				if (LIGHT.equals(option) && HelperPolicy.neverRemoveLight(outOfDynamite ? 0 : 1))
				{
					// keep Light
				}
				else
				{
					WorldPoint point = worldPointForMenuTarget(entry);
					if (point != null && !helperService.isFocusTile(point))
					{
						client.getMenu().removeMenuEntry(entry);
						return;
					}
				}
			}
		}

		if (config.pairLightSafety()
			&& LIGHT.equals(option)
			&& isLoadedPot(event.getIdentifier())
			&& !HelperPolicy.neverRemoveLight(outOfDynamite ? 0 : 1))
		{
			WorldPoint point = worldPointForMenuTarget(entry);
			if (point != null && !bothPotsLoaded(point, rocks))
			{
				client.getMenu().removeMenuEntry(entry);
				return;
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

		if (!action.getKind().matchesPrefer(option))
		{
			return;
		}

		WorldPoint point = worldPointForMenuTarget(entry);
		if (point != null && !action.getHighlightTiles().isEmpty() && !helperService.isFocusTile(point))
		{
			return;
		}

		entry.setForceLeftClick(true);
	}

	/**
	 * Allow Light when both cavities are ready: each is LOADED or already LIT.
	 * Hides Light until the partner pot is placed; keeps Light available on the
	 * second pot after the first has been lit.
	 */
	private boolean bothPotsLoaded(WorldPoint point, Map<WorldPoint, BlastMineRock> rocks)
	{
		NortheastSite site = NortheastSite.forWallTile(point);
		if (site == null)
		{
			site = NortheastSite.nearestTo(point);
		}
		if (site == null)
		{
			return true;
		}

		return isLoadedOrLitAt(site.getWallA(), rocks) && isLoadedOrLitAt(site.getWallB(), rocks);
	}

	private boolean isLoadedOrLitAt(WorldPoint wall, Map<WorldPoint, BlastMineRock> rocks)
	{
		BlastMineRock tracked = rocks.get(wall);
		if (tracked != null
			&& (tracked.getType() == BlastMineRockType.LOADED || tracked.getType() == BlastMineRockType.LIT))
		{
			return true;
		}

		for (Map.Entry<WorldPoint, BlastMineRock> entry : rocks.entrySet())
		{
			BlastMineRockType type = entry.getValue().getType();
			if (type != BlastMineRockType.LOADED && type != BlastMineRockType.LIT)
			{
				continue;
			}
			WorldPoint loc = entry.getKey();
			if (wall.equals(loc) || wall.distanceTo(loc) == 0)
			{
				return true;
			}
		}

		return sceneHasLoadedOrLitPot(wall);
	}

	private boolean sceneHasLoadedOrLitPot(WorldPoint wall)
	{
		LocalPoint local = LocalPoint.fromWorld(client, wall);
		if (local == null)
		{
			return false;
		}

		Tile[][][] tiles = client.getScene().getTiles();
		int plane = client.getPlane();
		int x = local.getSceneX();
		int y = local.getSceneY();
		if (x < 0 || y < 0 || plane < 0 || plane >= tiles.length
			|| x >= tiles[plane].length || y >= tiles[plane][x].length)
		{
			return false;
		}

		Tile tile = tiles[plane][x][y];
		if (tile == null)
		{
			return false;
		}

		GameObject[] objects = tile.getGameObjects();
		if (objects != null)
		{
			for (GameObject go : objects)
			{
				if (go != null && (isLoadedPot(go.getId()) || isLitPot(go.getId())))
				{
					return true;
				}
			}
		}

		return false;
	}

	private WorldPoint worldPointForMenuTarget(MenuEntry entry)
	{
		return WorldPoint.fromScene(client, entry.getParam0(), entry.getParam1(), client.getPlane());
	}

	private boolean hasUnnotedDynamite()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
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

	private static boolean isLitPot(int id)
	{
		return id == ObjectID.BLAST_MINING_WALL_BURNING_01 || id == ObjectID.BLAST_MINING_WALL_BURNING_02;
	}
}
