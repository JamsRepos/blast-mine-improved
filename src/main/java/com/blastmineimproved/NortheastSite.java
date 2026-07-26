package com.blastmineimproved;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

/**
 * North-east Blast Mine sites from ground markers.
 * Marker tile is the standing tile; wallA/wallB are the two cavities to work as a pair.
 */
public enum NortheastSite
{
	// Marker 34,33 — cavities south + east
	PAIR_1_2(1, "1-2",
		WorldPoint.fromRegion(5948, 34, 33, 0),
		WorldPoint.fromRegion(5948, 34, 32, 0),
		WorldPoint.fromRegion(5948, 35, 33, 0)),
	// Marker 34,35 — cavities north + east
	PAIR_3_4(2, "3-4",
		WorldPoint.fromRegion(5948, 34, 35, 0),
		WorldPoint.fromRegion(5948, 34, 36, 0),
		WorldPoint.fromRegion(5948, 35, 35, 0)),
	// Marker 32,37 — cavities north + east
	PAIR_5_6(3, "5-6",
		WorldPoint.fromRegion(5948, 32, 37, 0),
		WorldPoint.fromRegion(5948, 32, 38, 0),
		WorldPoint.fromRegion(5948, 33, 37, 0)),
	// Marker 29,39 — cavities north + east
	PAIR_7_8(4, "7-8",
		WorldPoint.fromRegion(5948, 29, 39, 0),
		WorldPoint.fromRegion(5948, 29, 40, 0),
		WorldPoint.fromRegion(5948, 30, 39, 0));

	public static final WorldPoint SACK = WorldPoint.fromRegion(5948, 25, 31, 0);
	public static final WorldPoint OPERATOR = WorldPoint.fromRegion(5948, 24, 24, 0);
	public static final WorldPoint BANK_CHEST = WorldPoint.fromRegion(5948, 29, 30, 0);
	public static final List<NortheastSite> ORDER = Collections.unmodifiableList(Arrays.asList(values()));

	@Getter
	private final int order;

	@Getter
	private final String label;

	@Getter
	private final WorldPoint tile;

	@Getter
	private final WorldPoint wallA;

	@Getter
	private final WorldPoint wallB;

	NortheastSite(int order, String label, WorldPoint tile, WorldPoint wallA, WorldPoint wallB)
	{
		this.order = order;
		this.label = label;
		this.tile = tile;
		this.wallA = wallA;
		this.wallB = wallB;
	}

	public List<WorldPoint> getWallTiles()
	{
		return List.of(wallA, wallB);
	}

	public boolean isWallTile(WorldPoint point)
	{
		return wallA.equals(point) || wallB.equals(point);
	}

	public static NortheastSite forWallTile(WorldPoint point)
	{
		for (NortheastSite site : values())
		{
			if (site.isWallTile(point))
			{
				return site;
			}
		}
		return null;
	}

	public static NortheastSite nearestTo(WorldPoint point)
	{
		NortheastSite byWall = forWallTile(point);
		if (byWall != null)
		{
			return byWall;
		}

		NortheastSite best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (NortheastSite site : values())
		{
			int distance = Math.min(
				site.tile.distanceTo(point),
				Math.min(site.wallA.distanceTo(point), site.wallB.distanceTo(point)));
			if (distance < bestDistance)
			{
				bestDistance = distance;
				best = site;
			}
		}
		// Only accept exact marker / wall adjacency (0), never pull in nearby unrelated walls
		return bestDistance == 0 ? best : null;
	}
}
