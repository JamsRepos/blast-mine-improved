package com.blastmineimproved;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;

/**
 * Tracks blasted ore sitting on the ground (picked up on the next pass, or when dynamite is gone).
 *
 * <p>Also records when each pile first spawned so the inventory disintegration timer can start from
 * ground-spawn time rather than pickup time; on pickup the spawn time is handed to
 * {@link BlastedOreTracker}.
 */
@Singleton
public class GroundOreTracker
{
	@Getter
	private final Map<WorldPoint, Integer> oreByTile = new HashMap<>();

	/** Oldest ground-spawn time still present on each tile. */
	private final Map<WorldPoint, Instant> spawnedAt = new HashMap<>();

	private final BlastedOreTracker blastedOreTracker;

	@Inject
	GroundOreTracker(BlastedOreTracker blastedOreTracker)
	{
		this.blastedOreTracker = blastedOreTracker;
	}

	public void reset()
	{
		oreByTile.clear();
		spawnedAt.clear();
	}

	public void onItemSpawned(WorldPoint point, TileItem item)
	{
		if (item == null || item.getId() != ItemID.LOVAKENGJ_BLASTED_ORE)
		{
			return;
		}
		oreByTile.merge(point, Math.max(1, item.getQuantity()), Integer::sum);
		spawnedAt.putIfAbsent(point, Instant.now());
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
		int removed = Math.min(qty, remaining);
		blastedOreTracker.onGroundOrePickedUp(spawnedAt.get(point), removed);
		if (remaining <= qty)
		{
			oreByTile.remove(point);
			spawnedAt.remove(point);
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
			spawnedAt.putIfAbsent(point, Instant.now());
		}
		else if (delta < 0)
		{
			Integer remaining = oreByTile.get(point);
			if (remaining == null)
			{
				return;
			}
			int removed = Math.min(-delta, remaining);
			blastedOreTracker.onGroundOrePickedUp(spawnedAt.get(point), removed);
			int next = remaining + delta;
			if (next <= 0)
			{
				oreByTile.remove(point);
				spawnedAt.remove(point);
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
}
