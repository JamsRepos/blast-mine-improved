package com.blastmineimproved;

/**
 * How the NE helper sequences blasting and looting. Defaults to loot-as-you-go so existing
 * users keep the 20-ore interleaved trip.
 */
public enum RotationMethod
{
	LOOT_AS_YOU_GO("Loot as you go"),
	BLAST_THEN_LOOT("Blast then loot");

	private final String label;

	RotationMethod(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
