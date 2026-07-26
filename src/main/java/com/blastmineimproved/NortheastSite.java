package com.blastmineimproved;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

/**
 * North-east Blast Mine sites derived from ground markers.
 * Each site is a pair of cavities that should be progressed/lit together.
 */
public enum NortheastSite
{
	PAIR_1_2(1, "1-2", WorldPoint.fromRegion(5948, 34, 33, 0)),
	PAIR_3_4(2, "3-4", WorldPoint.fromRegion(5948, 34, 35, 0)),
	PAIR_5_6(3, "5-6", WorldPoint.fromRegion(5948, 32, 37, 0)),
	PAIR_7_8(4, "7-8", WorldPoint.fromRegion(5948, 29, 39, 0));

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

	NortheastSite(int order, String label, WorldPoint tile)
	{
		this.order = order;
		this.label = label;
		this.tile = tile;
	}

	public static NortheastSite nearestTo(WorldPoint point)
	{
		NortheastSite best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (NortheastSite site : values())
		{
			int distance = site.tile.distanceTo(point);
			if (distance < bestDistance)
			{
				bestDistance = distance;
				best = site;
			}
		}
		return bestDistance <= 2 ? best : null;
	}
}
