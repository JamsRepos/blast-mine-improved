package com.blastmineimproved;

import java.util.Collections;
import java.util.List;

/**
 * How a trip of {@code n} dynamite maps onto NE pairs: full 1-2→7-8 laps, then a short
 * finale of leftover even pairs, then an optional odd last pot.
 *
 * <p>20 dynamite is 2 laps + pairs 1-2 and 3-4. 21 is the same plus one leftover pot.
 * 16 is exactly 2 laps with no finale.
 */
final class TripPlan
{
	private static final int PAIRS_PER_LAP = 8;
	private static final int INVENTORY_SIZE = 28;
	/** Chisel, tinderbox, and a noted dynamite stack — the kit the chest fill is sized around. */
	private static final int TOOL_SLOTS = 3;

	private final int dynamitePerTrip;
	private final int fullLaps;
	private final int finalePairCount;
	private final boolean oddLeftover;

	private TripPlan(int dynamitePerTrip, int fullLaps, int finalePairCount, boolean oddLeftover)
	{
		this.dynamitePerTrip = dynamitePerTrip;
		this.fullLaps = fullLaps;
		this.finalePairCount = finalePairCount;
		this.oddLeftover = oddLeftover;
	}

	static TripPlan of(int dynamitePerTrip)
	{
		int n = Math.max(0, dynamitePerTrip);
		int fullLaps = n / PAIRS_PER_LAP;
		int remainder = n % PAIRS_PER_LAP;
		return new TripPlan(n, fullLaps, remainder / 2, remainder % 2 == 1);
	}

	int getDynamitePerTrip()
	{
		return dynamitePerTrip;
	}

	/**
	 * Filler slots so using noted dynamite on the chest yields exactly {@link #getDynamitePerTrip()}.
	 * 20 dynamite → 5 placeholders; 21 → 4.
	 */
	int placeholderSlots()
	{
		return Math.max(0, INVENTORY_SIZE - dynamitePerTrip - TOOL_SLOTS);
	}

	int getFullLaps()
	{
		return fullLaps;
	}

	int getFinalePairCount()
	{
		return finalePairCount;
	}

	boolean hasOddLeftover()
	{
		return oddLeftover;
	}

	List<NortheastSite> finaleSites()
	{
		int count = Math.min(finalePairCount, NortheastSite.ORDER.size());
		if (count <= 0)
		{
			return Collections.emptyList();
		}
		return NortheastSite.ORDER.subList(0, count);
	}

	/**
	 * After the even finale pairs, the next site in order holds the leftover single pot
	 * (21 dynamite → pair 5-6 after 1-2 and 3-4).
	 */
	NortheastSite leftoverSite()
	{
		if (!oddLeftover)
		{
			return null;
		}
		int index = Math.min(finalePairCount, NortheastSite.ORDER.size() - 1);
		return NortheastSite.ORDER.get(index);
	}
}
