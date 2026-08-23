package com.blastmineimproved;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HelperPolicyTest
{
	@Test
	public void dynamiteZeroAndOneLoadedPotIsLight()
	{
		assertTrue(HelperPolicy.canLightLoadedPots(0, 1, 0));
		assertTrue(HelperPolicy.canLightLoadedPots(0, 2, 0));
		assertTrue(HelperPolicy.canLightLoadedPots(0, 1, 1));
	}

	@Test
	public void dynamiteLeftDoesNotLightUnpairedPot()
	{
		assertFalse(HelperPolicy.canLightLoadedPots(1, 1, 0));
		assertTrue(HelperPolicy.canLightLoadedPots(1, 2, 0));
		assertTrue(HelperPolicy.canLightLoadedPots(1, 1, 1));
	}

	@Test
	public void guidePickupOffNeverEmitsCollectAtPair()
	{
		assertFalse(HelperPolicy.emitCollectAtPair(RotationMethod.LOOT_AS_YOU_GO, false));
		assertFalse(HelperPolicy.emitCollectAtPair(RotationMethod.BLAST_THEN_LOOT, false));
		assertFalse(HelperPolicy.emitCollectAtPair(RotationMethod.BLAST_THEN_LOOT, true));
	}

	@Test
	public void lootAsYouGoGuidesPickupWhenEnabled()
	{
		assertTrue(HelperPolicy.emitCollectAtPair(RotationMethod.LOOT_AS_YOU_GO, true));
	}

	@Test
	public void blastThenLootDoesNotWaitOnGroundOreToReset()
	{
		assertFalse(HelperPolicy.tripResetRequiresClearGround(false));
		assertTrue(HelperPolicy.tripResetRequiresClearGround(true));
	}

	@Test
	public void dynamiteZeroNeverRemovesLight()
	{
		assertTrue(HelperPolicy.neverRemoveLight(0));
		assertFalse(HelperPolicy.neverRemoveLight(1));
	}

	@Test
	public void collectOreHidesIconsButDoesNotStripRockMenus()
	{
		HelperAction.Kind collect = HelperAction.Kind.COLLECT_ORE;
		assertTrue(collect.hidesOffPathIcons());
		assertFalse(collect.iconsOnFocusOnly());
		assertFalse(collect.stripsOffPathRockMenus());
		assertTrue(collect.matchesPrefer("Take"));
		assertTrue(collect.matchesPrefer("Pick-up"));
	}

	@Test
	public void blastingStepsStripOffPathMenus()
	{
		assertTrue(HelperAction.Kind.EXCAVATE.stripsOffPathRockMenus());
		assertTrue(HelperAction.Kind.PLACE_DYNAMITE.stripsOffPathRockMenus());
		assertTrue(HelperAction.Kind.LIGHT.stripsOffPathRockMenus());
		assertFalse(HelperAction.Kind.DEPOSIT_SACK.stripsOffPathRockMenus());
		assertFalse(HelperAction.Kind.BANK_DYNAMITE.stripsOffPathRockMenus());
		assertFalse(HelperAction.Kind.PREP_INVENTORY.stripsOffPathRockMenus());
	}
}
