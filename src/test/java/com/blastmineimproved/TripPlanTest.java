package com.blastmineimproved;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TripPlanTest
{
	@Test
	public void twentyIsTwoLapsAndTwoFinalePairs()
	{
		TripPlan plan = TripPlan.of(20);
		assertEquals(2, plan.getFullLaps());
		assertEquals(2, plan.getFinalePairCount());
		assertFalse(plan.hasOddLeftover());
		assertEquals(2, plan.finaleSites().size());
		assertEquals(NortheastSite.PAIR_1_2, plan.finaleSites().get(0));
		assertEquals(NortheastSite.PAIR_3_4, plan.finaleSites().get(1));
		assertNull(plan.leftoverSite());
		assertEquals(5, plan.placeholderSlots());
	}

	@Test
	public void twentyOneAddsOddLeftover()
	{
		TripPlan plan = TripPlan.of(21);
		assertEquals(2, plan.getFullLaps());
		assertEquals(2, plan.getFinalePairCount());
		assertTrue(plan.hasOddLeftover());
		assertEquals(NortheastSite.PAIR_5_6, plan.leftoverSite());
		assertEquals(4, plan.placeholderSlots());
	}

	@Test
	public void northeastTilesShareBlastMineRegion()
	{
		assertEquals(BlastMineArea.REGION_ID, NortheastSite.PAIR_1_2.getTile().getRegionID());
		assertEquals(BlastMineArea.REGION_ID, NortheastSite.SACK.getRegionID());
		assertEquals(BlastMineArea.REGION_ID, NortheastSite.OPERATOR.getRegionID());
		assertEquals(BlastMineArea.REGION_ID, NortheastSite.BANK_CHEST.getRegionID());
	}

	@Test
	public void sixteenIsTwoLapsNoFinale()
	{
		TripPlan plan = TripPlan.of(16);
		assertEquals(2, plan.getFullLaps());
		assertEquals(0, plan.getFinalePairCount());
		assertFalse(plan.hasOddLeftover());
		assertTrue(plan.finaleSites().isEmpty());
		assertNull(plan.leftoverSite());
		assertEquals(9, plan.placeholderSlots());
	}
}
