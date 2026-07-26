package com.blastmineimproved;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;

@Singleton
public class BlastedOreTracker
{
	public static final Duration ORE_LIFETIME = Duration.ofMinutes(3);

	@Getter
	public static final class OreTimer
	{
		private final Instant acquiredAt;
		private final int slot;

		OreTimer(Instant acquiredAt, int slot)
		{
			this.acquiredAt = acquiredAt;
			this.slot = slot;
		}

		public Duration remaining()
		{
			Duration elapsed = Duration.between(acquiredAt, Instant.now());
			Duration left = ORE_LIFETIME.minus(elapsed);
			return left.isNegative() ? Duration.ZERO : left;
		}

		public double remainingRelative()
		{
			return remaining().toMillis() / (double) ORE_LIFETIME.toMillis();
		}
	}

	private final Client client;
	private final Deque<Instant> acquisitionTimes = new ArrayDeque<>();
	private int lastOreCount;

	@Inject
	BlastedOreTracker(Client client)
	{
		this.client = client;
	}

	public void reset()
	{
		acquisitionTimes.clear();
		lastOreCount = 0;
	}

	public void syncFromInventory()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		int count = countBlastedOre(inventory);

		if (count > lastOreCount)
		{
			int gained = count - lastOreCount;
			Instant now = Instant.now();
			for (int i = 0; i < gained; i++)
			{
				acquisitionTimes.addLast(now);
			}
		}
		else if (count < lastOreCount)
		{
			int lost = lastOreCount - count;
			for (int i = 0; i < lost && !acquisitionTimes.isEmpty(); i++)
			{
				acquisitionTimes.removeFirst();
			}
		}

		lastOreCount = count;
		while (acquisitionTimes.size() > count)
		{
			acquisitionTimes.removeFirst();
		}
	}

	public Deque<OreTimer> timersForInventorySlots()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		Deque<OreTimer> timers = new ArrayDeque<>();
		if (inventory == null)
		{
			return timers;
		}

		Iterator<Instant> times = acquisitionTimes.iterator();
		Item[] items = inventory.getItems();
		for (int slot = 0; slot < items.length; slot++)
		{
			Item item = items[slot];
			if (item == null || item.getId() != ItemID.LOVAKENGJ_BLASTED_ORE)
			{
				continue;
			}
			if (!times.hasNext())
			{
				break;
			}
			timers.add(new OreTimer(times.next(), slot));
		}
		return timers;
	}

	public Duration oldestRemaining()
	{
		if (acquisitionTimes.isEmpty())
		{
			return null;
		}
		Duration elapsed = Duration.between(acquisitionTimes.peekFirst(), Instant.now());
		Duration left = ORE_LIFETIME.minus(elapsed);
		return left.isNegative() ? Duration.ZERO : left;
	}

	public int getOreCount()
	{
		return lastOreCount;
	}

	private static int countBlastedOre(ItemContainer inventory)
	{
		if (inventory == null)
		{
			return 0;
		}
		int count = 0;
		for (Item item : inventory.getItems())
		{
			if (item != null && item.getId() == ItemID.LOVAKENGJ_BLASTED_ORE)
			{
				count += Math.max(1, item.getQuantity());
			}
		}
		return count;
	}
}
