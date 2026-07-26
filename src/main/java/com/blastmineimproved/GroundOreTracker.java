package com.blastmineimproved;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;

/**
 * Tracks blasted ore sitting on the ground (picked up on the next pass, or when dynamite is gone).
 */
@Singleton
public class GroundOreTracker
{
	@Getter
	private final Map<WorldPoint, Integer> oreByTile = new HashMap<>();

	public void reset()
	{
		oreByTile.clear();
	}

	public void onItemSpawned(WorldPoint point, TileItem item)
	{
		if (item == null || item.getId() != ItemID.LOVAKENGJ_BLASTED_ORE)
		{
			return;
		}
		oreByTile.merge(point, Math.max(1, item.getQuantity()), Integer::sum);
	}

	public void onItemDespawned(WorldPoint point, TileItem item)
	{
		if (item == null || item.getId() != ItemID.LOVAKENGJ_BLASTED_ORE)
		{
			return;
		}
		int qty = Math.max(1, item.getQuantity());
		Integer remaining = oreByTile.get(point);
		if (remaining == null)
		{
			return;
		}
		if (remaining <= qty)
		{
			oreByTile.remove(point);
		}
		else
		{
			oreByTile.put(point, remaining - qty);
		}
	}

	public void onItemQuantityChanged(WorldPoint point, TileItem item, int oldQuantity)
	{
		if (item == null || item.getId() != ItemID.LOVAKENGJ_BLASTED_ORE)
		{
			return;
		}
		int delta = item.getQuantity() - oldQuantity;
		if (delta > 0)
		{
			oreByTile.merge(point, delta, Integer::sum);
		}
		else if (delta < 0)
		{
			Integer remaining = oreByTile.get(point);
			if (remaining == null)
			{
				return;
			}
			int next = remaining + delta;
			if (next <= 0)
			{
				oreByTile.remove(point);
			}
			else
			{
				oreByTile.put(point, next);
			}
		}
	}

	/**
	 * Assign each ore pile to the single nearest pair site so adjacent pairs
	 * (e.g. 1-2 and 3-4) do not share highlights.
	 */
	public NortheastSite ownerSite(WorldPoint oreTile)
	{
		NortheastSite best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (NortheastSite site : NortheastSite.values())
		{
			int distance = distanceToSite(oreTile, site);
			if (distance < bestDistance)
			{
				bestDistance = distance;
				best = site;
			}
		}
		// Ore further than 1 tile from marker/walls likely isn't from that blast
		return bestDistance <= 1 ? best : null;
	}

	private static int distanceToSite(WorldPoint oreTile, NortheastSite site)
	{
		return Math.min(
			oreTile.distanceTo(site.getTile()),
			Math.min(oreTile.distanceTo(site.getWallA()), oreTile.distanceTo(site.getWallB())));
	}

	public boolean hasOreAtSite(NortheastSite site)
	{
		return !oreTilesForSite(site).isEmpty();
	}

	public boolean hasAnyOre()
	{
		return !oreByTile.isEmpty();
	}

	public List<WorldPoint> allOreTiles()
	{
		return new ArrayList<>(oreByTile.keySet());
	}

	public List<WorldPoint> oreTilesForSite(NortheastSite site)
	{
		List<WorldPoint> tiles = new ArrayList<>();
		for (WorldPoint oreTile : oreByTile.keySet())
		{
			if (ownerSite(oreTile) == site)
			{
				tiles.add(oreTile);
			}
		}
		return tiles;
	}

	/** Resync from a tile's ground items (optional safety). */
	public void syncTile(Tile tile)
	{
		if (tile == null)
		{
			return;
		}
		WorldPoint point = tile.getWorldLocation();
		int count = 0;
		List<TileItem> items = tile.getGroundItems();
		if (items != null)
		{
			for (TileItem item : items)
			{
				if (item.getId() == ItemID.LOVAKENGJ_BLASTED_ORE)
				{
					count += Math.max(1, item.getQuantity());
				}
			}
		}
		if (count > 0)
		{
			oreByTile.put(point, count);
		}
		else
		{
			oreByTile.remove(point);
		}
	}
}
