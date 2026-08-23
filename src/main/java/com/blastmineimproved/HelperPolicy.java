package com.blastmineimproved;

/**
 * Decision helpers shared by {@link HelperService}, {@link MenuSafetyService}, and unit tests.
 * Kept free of {@code Client} so the 20-vs-21 and pickup-off cases stay testable.
 */
final class HelperPolicy
{
	private HelperPolicy()
	{
	}

	/**
	 * Light when both pots are loaded, the partner is already lit, or dynamite is gone
	 * (odd last pot — do not wait for a partner that cannot be loaded).
	 */
	static boolean canLightLoadedPots(int dynamite, int loaded, int lit)
	{
		if (loaded <= 0)
		{
			return false;
		}
		if (loaded >= 2 || lit > 0)
		{
			return true;
		}
		return dynamite == 0;
	}

	/** Mid-lap pickup is loot-as-you-go only, and only when the user wants the helper to loot. */
	static boolean emitCollectAtPair(RotationMethod method, boolean guideOrePickup)
	{
		return guideOrePickup && method == RotationMethod.LOOT_AS_YOU_GO;
	}

	static boolean tripResetRequiresClearGround(boolean guideOrePickup)
	{
		return guideOrePickup;
	}

	/** A loaded pot is always meant to be fired; never strip Light when dynamite is 0. */
	static boolean neverRemoveLight(int dynamite)
	{
		return dynamite == 0;
	}
}
