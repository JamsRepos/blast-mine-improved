package com.blastmineimproved;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;

/**
 * Tracks the 3-minute disintegration timer of blasted ore held in the inventory.
 *
 * <p>The lifetime starts when the ore spawns on the ground, so pickup times are seeded from
 * {@link GroundOreTracker} rather than the moment the ore enters the inventory. Timers are keyed
 * by inventory slot and reassigned when ore is moved between slots.
 */
@Singleton
public class BlastedOreTracker
{
	public static final Duration ORE_LIFETIME = Duration.ofMinutes(3);
	private static final int INVENTORY_SIZE = 28;

	public static final class OreTimer
	{
		private final Instant spawnedAt;

		OreTimer(Instant spawnedAt)
		{
			this.spawnedAt = spawnedAt;
		}

		public Duration remaining()
		{
			Duration left = ORE_LIFETIME.minus(Duration.between(spawnedAt, Instant.now()));
			return left.isNegative() ? Duration.ZERO : left;
		}

		public double remainingRelative()
		{
			return remaining().toMillis() / (double) ORE_LIFETIME.toMillis();
		}
	}

	private final Client client;

	/** Inventory slot index -> ground spawn time of the ore currently in that slot. */
	private final Map<Integer, Instant> timersBySlot = new HashMap<>();

	/** Ground spawn times of ore that left the ground this tick (pickup candidates). */
	private final Deque<Instant> pendingPickups = new ArrayDeque<>();

	@Inject
	BlastedOreTracker(Client client)
	{
		this.client = client;
	}

	public void reset()
	{
		timersBySlot.clear();
		pendingPickups.clear();
	}

	/**
	 * Record that blasted ore left the ground this tick, carrying its original spawn time so the
	 * timer that follows it into the inventory reflects floor time already elapsed.
	 */
	public void onGroundOrePickedUp(Instant spawnedAt, int quantity)
	{
		Instant time = spawnedAt != null ? spawnedAt : Instant.now();
		for (int i = 0; i < quantity; i++)
		{
			pendingPickups.addLast(time);
		}
	}

	public void syncFromInventory()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			timersBySlot.clear();
			pendingPickups.clear();
			return;
		}

		final Item[] items = inventory.getItems();
		final int limit = Math.min(items.length, INVENTORY_SIZE);

		final boolean[] isOreSlot = new boolean[INVENTORY_SIZE];
		for (int slot = 0; slot < limit; slot++)
		{
			Item item = items[slot];
			isOreSlot[slot] = item != null && item.getId() == ItemID.LOVAKENGJ_BLASTED_ORE;
		}

		// Free timers from slots that no longer hold ore (deposited, disintegrated, or moved out).
		final List<Instant> freedTimers = new ArrayList<>();
		for (Iterator<Map.Entry<Integer, Instant>> it = timersBySlot.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<Integer, Instant> entry = it.next();
			int slot = entry.getKey();
			if (slot >= INVENTORY_SIZE || !isOreSlot[slot])
			{
				freedTimers.add(entry.getValue());
				it.remove();
			}
		}

		// Slots that now hold ore but have no timer yet (picked up or moved in).
		final List<Integer> gainedSlots = new ArrayList<>();
		for (int slot = 0; slot < limit; slot++)
		{
			if (isOreSlot[slot] && !timersBySlot.containsKey(slot))
			{
				gainedSlots.add(slot);
			}
		}

		if (!gainedSlots.isEmpty())
		{
			// Assign moved-ore times first (oldest first), then fresh ground pickups from this tick.
			freedTimers.sort(Comparator.naturalOrder());
			final Deque<Instant> available = new ArrayDeque<>(freedTimers);
			available.addAll(pendingPickups);

			for (int slot : gainedSlots)
			{
				Instant time = available.pollFirst();
				timersBySlot.put(slot, time != null ? time : Instant.now());
			}
		}

		// Pending pickups are only valid for the tick in which the ground ore despawned.
		pendingPickups.clear();
	}

	public OreTimer timerForSlot(int slot)
	{
		Instant spawnedAt = timersBySlot.get(slot);
		return spawnedAt != null ? new OreTimer(spawnedAt) : null;
	}

	public int getOreCount()
	{
		return timersBySlot.size();
	}
}
