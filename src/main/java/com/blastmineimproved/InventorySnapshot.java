package com.blastmineimproved;

import lombok.Value;

/**
 * One inventory scan for the helper. Tools and dynamite use gameval {@code ItemID}s only;
 * noted dynamite is the linked note of fused dynamite, not a hardcoded id.
 */
@Value
public class InventorySnapshot
{
	boolean chisel;
	boolean tinderbox;
	boolean notedDynamite;
	int unnotedDynamite;
	int emptySlots;
	int placeholderItems;

	int oreCycleCapacity()
	{
		return emptySlots + unnotedDynamite;
	}
}
