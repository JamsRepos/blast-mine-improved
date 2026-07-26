package com.blastmineimproved;

import java.awt.Color;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

@Singleton
public class HelperService
{
	private static final int SACK_FULL_THRESHOLD = 900;
	private static final Color NEXT_COLOR = new Color(0, 200, 255, 180);
	private static final Color WARN_COLOR = new Color(255, 170, 0, 180);
	private static final Color DEPOSIT_COLOR = new Color(10, 255, 0, 180);

	private final Client client;
	private final BlastMineImprovedConfig config;
	private final BlastedOreTracker oreTracker;

	@Getter
	private HelperAction currentAction = HelperAction.idle();

	@Inject
	HelperService(Client client, BlastMineImprovedConfig config, BlastedOreTracker oreTracker)
	{
		this.client = client;
		this.config = config;
		this.oreTracker = oreTracker;
	}

	public void update(Map<WorldPoint, BlastMineRock> rocks)
	{
		if (!config.enableHelper() || !BlastMineArea.isInBlastMine(client))
		{
			currentAction = HelperAction.idle();
			return;
		}

		if (isSackFull())
		{
			currentAction = new HelperAction(
				HelperAction.Kind.WEAR_PROSPECTORS,
				"Sack full — wear prospectors, then collect from the operator",
				List.of(NortheastSite.OPERATOR),
				WARN_COLOR);
			return;
		}

		Duration oldest = oreTracker.oldestRemaining();
		boolean oreUrgent = oldest != null && oldest.compareTo(Duration.ofSeconds(45)) < 0;
		boolean shouldDeposit = oreTracker.getOreCount() > 0
			&& (oreUrgent || oreTracker.getOreCount() >= 20 || inventoryAlmostFull());

		if (shouldDeposit)
		{
			currentAction = new HelperAction(
				HelperAction.Kind.DEPOSIT_SACK,
				"Deposit blasted ore into the sack (" + oreTracker.getOreCount() + ")",
				List.of(NortheastSite.SACK),
				DEPOSIT_COLOR);
			return;
		}

		int dynamite = countUnnotedDynamite();
		if (dynamite <= config.lowDynamiteThreshold() && oreTracker.getOreCount() == 0)
		{
			currentAction = new HelperAction(
				HelperAction.Kind.BANK_DYNAMITE,
				"Low dynamite (" + dynamite + ") — use dynamite on the bank chest",
				List.of(NortheastSite.BANK_CHEST),
				WARN_COLOR);
			return;
		}

		Map<NortheastSite, List<BlastMineRock>> bySite = groupBySite(rocks);

		for (NortheastSite site : NortheastSite.ORDER)
		{
			List<BlastMineRock> siteRocks = bySite.getOrDefault(site, Collections.emptyList());
			HelperAction action = actionForSite(site, siteRocks);
			if (action != null)
			{
				currentAction = action;
				return;
			}
		}

		if (oreTracker.getOreCount() > 0)
		{
			currentAction = new HelperAction(
				HelperAction.Kind.DEPOSIT_SACK,
				"Deposit remaining blasted ore",
				List.of(NortheastSite.SACK),
				DEPOSIT_COLOR);
			return;
		}

		currentAction = new HelperAction(
			HelperAction.Kind.IDLE,
			"North-east rotation ready — start excavating pair 1-2",
			List.of(NortheastSite.PAIR_1_2.getTile()),
			NEXT_COLOR);
	}

	private HelperAction actionForSite(NortheastSite site, List<BlastMineRock> siteRocks)
	{
		List<BlastMineRock> normal = filter(siteRocks, BlastMineRockType.NORMAL);
		List<BlastMineRock> chiseled = filter(siteRocks, BlastMineRockType.CHISELED);
		List<BlastMineRock> loaded = filter(siteRocks, BlastMineRockType.LOADED);
		List<BlastMineRock> lit = filter(siteRocks, BlastMineRockType.LIT);
		List<BlastMineRock> exploded = filter(siteRocks, BlastMineRockType.EXPLODED);

		if (!normal.isEmpty())
		{
			return new HelperAction(
				HelperAction.Kind.EXCAVATE,
				"Excavate pair " + site.getLabel(),
				tilesOf(normal, site),
				NEXT_COLOR);
		}

		if (!chiseled.isEmpty())
		{
			return new HelperAction(
				HelperAction.Kind.PLACE_DYNAMITE,
				"Place dynamite on pair " + site.getLabel(),
				tilesOf(chiseled, site),
				NEXT_COLOR);
		}

		if (loaded.size() >= 2 || (loaded.size() == 1 && siteRocks.size() == 1))
		{
			return new HelperAction(
				HelperAction.Kind.LIGHT,
				"Light pair " + site.getLabel() + " together",
				tilesOf(loaded, site),
				NEXT_COLOR);
		}

		if (loaded.size() == 1)
		{
			return new HelperAction(
				HelperAction.Kind.PLACE_DYNAMITE,
				"Finish loading pair " + site.getLabel() + " before lighting",
				List.of(site.getTile()),
				WARN_COLOR);
		}

		if (!lit.isEmpty())
		{
			return new HelperAction(
				HelperAction.Kind.IDLE,
				"Wait for pair " + site.getLabel() + " to explode — stay clear",
				tilesOf(lit, site),
				WARN_COLOR);
		}

		if (!exploded.isEmpty())
		{
			return new HelperAction(
				HelperAction.Kind.COLLECT_ORE,
				"Pick up blasted ore from pair " + site.getLabel(),
				tilesOf(exploded, site),
				DEPOSIT_COLOR);
		}

		return null;
	}

	private Map<NortheastSite, List<BlastMineRock>> groupBySite(Map<WorldPoint, BlastMineRock> rocks)
	{
		Map<NortheastSite, List<BlastMineRock>> map = new EnumMap<>(NortheastSite.class);
		for (NortheastSite site : NortheastSite.values())
		{
			map.put(site, new ArrayList<>());
		}

		for (BlastMineRock rock : rocks.values())
		{
			NortheastSite site = NortheastSite.nearestTo(rock.getGameObject().getWorldLocation());
			if (site != null)
			{
				map.get(site).add(rock);
			}
		}
		return map;
	}

	private static List<BlastMineRock> filter(List<BlastMineRock> rocks, BlastMineRockType type)
	{
		List<BlastMineRock> out = new ArrayList<>();
		for (BlastMineRock rock : rocks)
		{
			if (rock.getType() == type)
			{
				out.add(rock);
			}
		}
		return out;
	}

	private static List<WorldPoint> tilesOf(List<BlastMineRock> rocks, NortheastSite fallback)
	{
		if (rocks.isEmpty())
		{
			return List.of(fallback.getTile());
		}
		List<WorldPoint> tiles = new ArrayList<>();
		for (BlastMineRock rock : rocks)
		{
			tiles.add(rock.getGameObject().getWorldLocation());
		}
		return tiles;
	}

	public boolean isSackFull()
	{
		return client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_COAL_BIGGER) >= SACK_FULL_THRESHOLD
			|| client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_GOLD_BIGGER) >= SACK_FULL_THRESHOLD
			|| client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_MITHRIL_BIGGER) >= SACK_FULL_THRESHOLD
			|| client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_ADAMANTITE_BIGGER) >= SACK_FULL_THRESHOLD
			|| client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_RUNITE_BIGGER) >= SACK_FULL_THRESHOLD;
	}

	public int estimateSackXp()
	{
		int coal = client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_COAL_BIGGER);
		int gold = client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_GOLD_BIGGER);
		int mith = client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_MITHRIL_BIGGER);
		int addy = client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_ADAMANTITE_BIGGER);
		int rune = client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_RUNITE_BIGGER);

		double xp = coal * 33.0
			+ gold * 66.0
			+ mith * 120.0
			+ addy * 190.0
			+ rune * 260.0;

		if (config.assumeProspectors())
		{
			xp *= 1.025;
		}
		return (int) Math.round(xp);
	}

	public int totalSackOres()
	{
		return client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_COAL_BIGGER)
			+ client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_GOLD_BIGGER)
			+ client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_MITHRIL_BIGGER)
			+ client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_ADAMANTITE_BIGGER)
			+ client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_RUNITE_BIGGER);
	}

	private int countUnnotedDynamite()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory == null)
		{
			return 0;
		}
		return inventory.count(ItemID.LOVAKENGJ_DYNAMITE_FUSED);
	}

	private boolean inventoryAlmostFull()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory == null)
		{
			return false;
		}
		int used = 0;
		for (net.runelite.api.Item item : inventory.getItems())
		{
			if (item != null && item.getId() > -1)
			{
				used++;
			}
		}
		return used >= 26;
	}
}
