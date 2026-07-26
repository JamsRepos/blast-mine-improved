package com.blastmineimproved;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

@Singleton
public class HelperService
{
	private static final int SACK_FULL_THRESHOLD = 900;
	private static final int DEPOSIT_ORE_COUNT = 20;
	/** After this many full 1-2→7-8 passes, the next pass is the short 1-2 + 3-4 finale. */
	private static final int FULL_PASSES_BEFORE_FINALE = 2;
	/** Player is "at" the NE rotation when within this distance of any NE marker. */
	private static final int NE_AREA_RADIUS = 10;
	/** Filler items so using noted dynamite on the chest yields exactly 20 unnoted. */
	private static final int PLACEHOLDER_SLOTS = 5;
	/** One deposit cycle is 20 blasted ore. */
	private static final int ORE_CYCLE_SLOTS = 20;
	private static final int INVENTORY_SIZE = 28;
	private static final Color NEXT_COLOR = new Color(0, 200, 255, 180);
	private static final Color WARN_COLOR = new Color(255, 170, 0, 180);
	private static final Color DEPOSIT_COLOR = new Color(10, 255, 0, 180);
	private static final Color GUIDE_COLOR = new Color(255, 220, 0, 200);

	private static final List<NortheastSite> SHORT_FINALE_SITES = List.of(
		NortheastSite.PAIR_1_2,
		NortheastSite.PAIR_3_4);

	private final Client client;
	private final BlastMineImprovedConfig config;
	private final BlastedOreTracker oreTracker;
	private final GroundOreTracker groundOreTracker;

	@Getter
	private HelperAction currentAction = HelperAction.idle();

	/** Cached highlight tiles for O(1) focus checks in overlays (updated only when action changes). */
	private Set<WorldPoint> focusTiles = Collections.emptySet();

	/** Helper only guides pairs 1-2 and 3-4 for blasting until you deposit. */
	private boolean shortFinale;

	private int fullPassesCompleted;

	/** Sites blasted at least once this trip (eligible for pickup on a later pass). */
	private final Set<NortheastSite> blastedThisTrip = EnumSet.noneOf(NortheastSite.class);

	/** Sites already fired this pass — keep moving; don't rework or wait on them. */
	private final Set<NortheastSite> completedThisPass = EnumSet.noneOf(NortheastSite.class);

	/**
	 * Sites that needed excavate/place/load work this pass. Required before LIT can count as
	 * completed — otherwise leftover fuses from the previous lap auto-skip 7-8.
	 */
	private final Set<NortheastSite> workedThisPass = EnumSet.noneOf(NortheastSite.class);

	@Inject
	HelperService(
		Client client,
		BlastMineImprovedConfig config,
		BlastedOreTracker oreTracker,
		GroundOreTracker groundOreTracker)
	{
		this.client = client;
		this.config = config;
		this.oreTracker = oreTracker;
		this.groundOreTracker = groundOreTracker;
	}

	public void resetRotation()
	{
		shortFinale = false;
		fullPassesCompleted = 0;
		blastedThisTrip.clear();
		completedThisPass.clear();
		workedThisPass.clear();
		setCurrentAction(HelperAction.idle());
	}

	public boolean isNearNortheast()
	{
		if (client.getLocalPlayer() == null)
		{
			return false;
		}
		WorldPoint player = client.getLocalPlayer().getWorldLocation();
		for (NortheastSite site : NortheastSite.ORDER)
		{
			if (player.distanceTo(site.getTile()) <= NE_AREA_RADIUS)
			{
				return true;
			}
		}
		return false;
	}

	/** True when this tile is a current helper target. */
	public boolean isFocusTile(WorldPoint point)
	{
		return point != null && focusTiles.contains(point);
	}

	/**
	 * Hide chisel/dynamite/tinderbox icons that are not on the current helper path
	 * (includes deposit/bank/prep — those steps hide all rock action icons).
	 */
	public boolean shouldHideOffPathRockIcons()
	{
		if (currentAction == null)
		{
			return false;
		}
		switch (currentAction.getKind())
		{
			case EXCAVATE:
			case PLACE_DYNAMITE:
			case LIGHT:
			case COLLECT_ORE:
			case DEPOSIT_SACK:
			case BANK_DYNAMITE:
			case PREP_INVENTORY:
			case WEAR_PROSPECTORS:
			case COLLECT_OPERATOR:
				return true;
			default:
				return false;
		}
	}

	/**
	 * When true with {@link #shouldHideOffPathRockIcons()}, only draw rock action icons on focus
	 * tiles. When false, suppress all rock action icons for this step (deposit/bank/etc.).
	 */
	public boolean restrictRockIconsToFocusTiles()
	{
		if (currentAction == null)
		{
			return false;
		}
		switch (currentAction.getKind())
		{
			case EXCAVATE:
			case PLACE_DYNAMITE:
			case LIGHT:
				return true;
			default:
				return false;
		}
	}

	/**
	 * True when off-path excavate/place/light menus should be removed.
	 */
	public boolean isFocusingBlastingStep()
	{
		if (currentAction == null)
		{
			return false;
		}
		switch (currentAction.getKind())
		{
			case EXCAVATE:
			case PLACE_DYNAMITE:
			case LIGHT:
			case COLLECT_ORE:
			case DEPOSIT_SACK:
			case BANK_DYNAMITE:
			case PREP_INVENTORY:
				return true;
			default:
				return false;
		}
	}

	public void update(Map<WorldPoint, BlastMineRock> rocks)
	{
		if (!config.enableHelper() || !BlastMineArea.isInBlastMine(client))
		{
			setCurrentAction(HelperAction.idle());
			return;
		}

		InventorySnapshot inv = scanInventory();
		int oreCount = oreTracker.getOreCount();
		int dynamite = inv.unnotedDynamite;

		// After the short finale, start a fresh 1-2→7-8 trip once inventory and ground are clear.
		if (shortFinale && oreCount == 0 && dynamite > 0 && !groundOreTracker.hasAnyOre())
		{
			resetRotation();
		}

		if (!isNearNortheast())
		{
			setCurrentAction(new HelperAction(
				HelperAction.Kind.IDLE,
				"Go to the north-east rotation (pairs 1-2)",
				List.of(NortheastSite.PAIR_1_2.getTile()),
				GUIDE_COLOR));
			return;
		}

		List<NortheastSite> sites = shortFinale ? SHORT_FINALE_SITES : NortheastSite.ORDER;
		Map<NortheastSite, List<BlastMineRock>> bySite = groupBySite(rocks);
		updatePassProgress(sites, bySite);

		if (isSackFull())
		{
			setCurrentAction(new HelperAction(
				HelperAction.Kind.WEAR_PROSPECTORS,
				"Sack full — wear prospectors, then collect from the operator",
				List.of(NortheastSite.OPERATOR),
				WARN_COLOR));
			return;
		}

		if (oreCount >= DEPOSIT_ORE_COUNT)
		{
			setCurrentAction(new HelperAction(
				HelperAction.Kind.DEPOSIT_SACK,
				"Deposit 20 blasted ore into the sack",
				List.of(NortheastSite.SACK),
				DEPOSIT_COLOR));
			return;
		}

		if (dynamite == 0)
		{
			HelperAction pendingLight = pendingLightAction(bySite);
			if (pendingLight != null)
			{
				setCurrentAction(pendingLight);
				return;
			}

			if (groundOreTracker.hasAnyOre())
			{
				setCurrentAction(new HelperAction(
					HelperAction.Kind.COLLECT_ORE,
					"Out of dynamite — pick up remaining blasted ore",
					groundOreTracker.allOreTiles(),
					DEPOSIT_COLOR));
				return;
			}
			if (oreCount > 0)
			{
				setCurrentAction(new HelperAction(
					HelperAction.Kind.DEPOSIT_SACK,
					"Deposit blasted ore into the sack (" + oreCount + ")",
					List.of(NortheastSite.SACK),
					DEPOSIT_COLOR));
				return;
			}
			setCurrentAction(bankDynamiteAction(inv, "Out of dynamite — use noted dynamite on the bank chest"));
			return;
		}

		// Missing tools always block; full kit check before starting a trip or when banking.
		HelperAction prep = prepInventoryAction(inv, oreCount == 0 && !groundOreTracker.hasAnyOre());
		if (prep != null)
		{
			setCurrentAction(prep);
			return;
		}

		if (dynamite <= config.lowDynamiteThreshold() && oreCount == 0 && !groundOreTracker.hasAnyOre())
		{
			setCurrentAction(bankDynamiteAction(inv, "Low dynamite (" + dynamite + ") — use noted dynamite on the bank chest"));
			return;
		}

		boolean allowPickup = shortFinale
			? blastedThisTrip.containsAll(SHORT_FINALE_SITES)
			: blastedThisTrip.containsAll(NortheastSite.ORDER);

		for (NortheastSite site : sites)
		{
			if (completedThisPass.contains(site))
			{
				continue;
			}

			List<BlastMineRock> siteRocks = bySite.getOrDefault(site, Collections.emptyList());
			HelperAction action = actionForSite(site, siteRocks, shortFinale, allowPickup);
			if (action != null)
			{
				setCurrentAction(action);
				return;
			}
		}

		NortheastSite waiting = firstIncompleteSite(sites);
		if (waiting != null)
		{
			setCurrentAction(new HelperAction(
				HelperAction.Kind.IDLE,
				"Waiting for pair " + waiting.getLabel() + " to respawn",
				waiting.getWallTiles(),
				NEXT_COLOR));
			return;
		}

		setCurrentAction(new HelperAction(
			HelperAction.Kind.IDLE,
			shortFinale
				? "Finale clear — sweep ore when out of dynamite, deposit at 20"
				: "North-east rotation ready — start excavating pair 1-2",
			shortFinale
				? List.of(NortheastSite.PAIR_1_2.getTile(), NortheastSite.PAIR_3_4.getTile())
				: NortheastSite.PAIR_1_2.getWallTiles(),
			NEXT_COLOR));
	}

	private void setCurrentAction(HelperAction action)
	{
		HelperAction next = action != null ? action : HelperAction.idle();
		if (next.equals(currentAction))
		{
			return;
		}
		currentAction = next;
		List<WorldPoint> tiles = next.getHighlightTiles();
		if (tiles == null || tiles.isEmpty())
		{
			focusTiles = Collections.emptySet();
		}
		else
		{
			focusTiles = Collections.unmodifiableSet(new HashSet<>(tiles));
		}
	}

	private HelperAction bankDynamiteAction(InventorySnapshot inv, String detail)
	{
		if (!inv.hasNotedDynamite)
		{
			detail = "Need a noted dynamite stack — then use it on the bank chest";
		}
		return new HelperAction(
			HelperAction.Kind.BANK_DYNAMITE,
			detail,
			List.of(NortheastSite.BANK_CHEST),
			WARN_COLOR);
	}

	/**
	 * Require chisel, tinderbox, noted dynamite, 5 placeholder items, and 20 ore-cycle slots
	 * (empty and/or unnoted dynamite) before starting — matches the common NE setup.
	 */
	private HelperAction prepInventoryAction(InventorySnapshot inv, boolean fullKitRequired)
	{
		List<String> missing = new ArrayList<>();
		if (!inv.hasChisel)
		{
			missing.add("chisel");
		}
		if (!inv.hasTinderbox)
		{
			missing.add("tinderbox");
		}

		if (!fullKitRequired && missing.isEmpty())
		{
			return null;
		}

		if (fullKitRequired)
		{
			if (!inv.hasNotedDynamite)
			{
				missing.add("noted dynamite");
			}
			if (inv.placeholderItems < PLACEHOLDER_SLOTS)
			{
				missing.add((PLACEHOLDER_SLOTS - inv.placeholderItems) + " more placeholder item(s)");
			}
			if (inv.oreCycleCapacity() < ORE_CYCLE_SLOTS)
			{
				missing.add("20 empty spaces or 20 unnoted dynamite");
			}
		}

		if (missing.isEmpty())
		{
			return null;
		}

		WorldPoint highlight = (!inv.hasNotedDynamite || inv.oreCycleCapacity() < ORE_CYCLE_SLOTS)
			? NortheastSite.BANK_CHEST
			: NortheastSite.PAIR_1_2.getTile();

		return new HelperAction(
			HelperAction.Kind.PREP_INVENTORY,
			"Need " + String.join(", ", missing),
			List.of(highlight),
			WARN_COLOR);
	}

	private InventorySnapshot scanInventory()
	{
		InventorySnapshot snap = new InventorySnapshot();
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory == null)
		{
			snap.emptySlots = INVENTORY_SIZE;
			return snap;
		}

		Item[] items = inventory.getItems();
		for (int i = 0; i < INVENTORY_SIZE; i++)
		{
			Item item = i < items.length ? items[i] : null;
			if (item == null || item.getId() < 0)
			{
				snap.emptySlots++;
				continue;
			}

			int id = item.getId();
			if (id == ItemID.CHISEL)
			{
				snap.hasChisel = true;
				continue;
			}
			if (id == ItemID.TINDERBOX)
			{
				snap.hasTinderbox = true;
				continue;
			}
			if (id == ItemID.LOVAKENGJ_DYNAMITE_FUSED)
			{
				continue;
			}

			ItemComposition def = client.getItemDefinition(id);
			if (def.getNote() != -1 && def.getLinkedNoteId() == ItemID.LOVAKENGJ_DYNAMITE_FUSED)
			{
				snap.hasNotedDynamite = true;
				continue;
			}

			// Any other occupied slot is a placeholder / filler (staminas, teleports, etc.)
			snap.placeholderItems++;
		}

		snap.unnotedDynamite = inventory.count(ItemID.LOVAKENGJ_DYNAMITE_FUSED);
		return snap;
	}

	private NortheastSite firstIncompleteSite(List<NortheastSite> sites)
	{
		for (NortheastSite site : sites)
		{
			if (!completedThisPass.contains(site))
			{
				return site;
			}
		}
		return null;
	}

	private HelperAction pendingLightAction(Map<NortheastSite, List<BlastMineRock>> bySite)
	{
		for (NortheastSite site : NortheastSite.ORDER)
		{
			List<BlastMineRock> siteRocks = bySite.getOrDefault(site, Collections.emptyList());
			List<BlastMineRock> loaded = filter(siteRocks, BlastMineRockType.LOADED);
			List<BlastMineRock> lit = filter(siteRocks, BlastMineRockType.LIT);

			if (loaded.size() >= 2)
			{
				return new HelperAction(
					HelperAction.Kind.LIGHT,
					"Out of dynamite — light pair " + site.getLabel() + " together",
					tilesOf(loaded, site),
					WARN_COLOR);
			}

			if (loaded.size() == 1 && !lit.isEmpty())
			{
				return new HelperAction(
					HelperAction.Kind.LIGHT,
					"Out of dynamite — light the remaining pot on pair " + site.getLabel(),
					tilesOf(loaded, site),
					WARN_COLOR);
			}
		}
		return null;
	}

	/**
	 * Mark pairs done for this pass once fired. When every active site is done,
	 * start the next pass — and after two full passes, switch to the short finale.
	 */
	private void updatePassProgress(List<NortheastSite> sites, Map<NortheastSite, List<BlastMineRock>> bySite)
	{
		for (NortheastSite site : sites)
		{
			List<BlastMineRock> siteRocks = bySite.getOrDefault(site, Collections.emptyList());
			if (needsBlastingWork(siteRocks))
			{
				workedThisPass.add(site);
			}
			if (workedThisPass.contains(site)
				&& hasBeenFired(siteRocks)
				&& !needsBlastingWork(siteRocks))
			{
				completedThisPass.add(site);
				blastedThisTrip.add(site);
			}
		}

		boolean allDone = true;
		for (NortheastSite site : sites)
		{
			if (!completedThisPass.contains(site))
			{
				allDone = false;
				break;
			}
		}
		if (allDone && !sites.isEmpty())
		{
			completedThisPass.clear();
			workedThisPass.clear();
			if (!shortFinale)
			{
				fullPassesCompleted++;
				if (fullPassesCompleted >= FULL_PASSES_BEFORE_FINALE)
				{
					shortFinale = true;
				}
			}
		}
	}

	private HelperAction actionForSite(
		NortheastSite site,
		List<BlastMineRock> siteRocks,
		boolean finale,
		boolean allowPickup)
	{
		List<BlastMineRock> normal = filter(siteRocks, BlastMineRockType.NORMAL);
		List<BlastMineRock> chiseled = filter(siteRocks, BlastMineRockType.CHISELED);
		List<BlastMineRock> loaded = filter(siteRocks, BlastMineRockType.LOADED);
		List<BlastMineRock> lit = filter(siteRocks, BlastMineRockType.LIT);

		String finalePrefix = finale ? "Finale: " : "";

		if (allowPickup && groundOreTracker.hasOreAtSite(site)
			&& chiseled.isEmpty() && loaded.isEmpty() && lit.isEmpty())
		{
			List<WorldPoint> oreTiles = groundOreTracker.oreTilesForSite(site);
			if (oreTiles.isEmpty())
			{
				oreTiles = List.of(site.getTile());
			}
			return new HelperAction(
				HelperAction.Kind.COLLECT_ORE,
				finalePrefix + "Pick up ore at pair " + site.getLabel() + ", then excavate",
				oreTiles,
				DEPOSIT_COLOR);
		}

		if (siteRocks.isEmpty())
		{
			return new HelperAction(
				HelperAction.Kind.EXCAVATE,
				finalePrefix + "Excavate pair " + site.getLabel(),
				site.getWallTiles(),
				NEXT_COLOR);
		}

		if (!normal.isEmpty())
		{
			return new HelperAction(
				HelperAction.Kind.EXCAVATE,
				finalePrefix + "Excavate pair " + site.getLabel(),
				tilesOf(normal, site),
				NEXT_COLOR);
		}

		if (!chiseled.isEmpty())
		{
			return new HelperAction(
				HelperAction.Kind.PLACE_DYNAMITE,
				finalePrefix + "Place dynamite on pair " + site.getLabel(),
				tilesOf(chiseled, site),
				NEXT_COLOR);
		}

		if (loaded.size() >= 2)
		{
			return new HelperAction(
				HelperAction.Kind.LIGHT,
				finalePrefix + "Light pair " + site.getLabel() + " together",
				tilesOf(loaded, site),
				NEXT_COLOR);
		}

		if (loaded.size() == 1 && !lit.isEmpty())
		{
			return new HelperAction(
				HelperAction.Kind.LIGHT,
				finalePrefix + "Light the remaining pot on pair " + site.getLabel(),
				tilesOf(loaded, site),
				WARN_COLOR);
		}

		if (loaded.size() == 1)
		{
			return new HelperAction(
				HelperAction.Kind.PLACE_DYNAMITE,
				finalePrefix + "Finish loading pair " + site.getLabel() + " before lighting",
				site.getWallTiles(),
				WARN_COLOR);
		}

		return null;
	}

	private static boolean needsBlastingWork(List<BlastMineRock> siteRocks)
	{
		for (BlastMineRock rock : siteRocks)
		{
			BlastMineRockType type = rock.getType();
			if (type == BlastMineRockType.NORMAL
				|| type == BlastMineRockType.CHISELED
				|| type == BlastMineRockType.LOADED)
			{
				return true;
			}
		}
		return false;
	}

	private static boolean hasBeenFired(List<BlastMineRock> siteRocks)
	{
		for (BlastMineRock rock : siteRocks)
		{
			if (rock.getType() == BlastMineRockType.LIT)
			{
				return true;
			}
		}
		return false;
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
			NortheastSite site = NortheastSite.forWallTile(rock.getGameObject().getWorldLocation());
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

	private static List<WorldPoint> tilesOf(List<BlastMineRock> rocks, NortheastSite site)
	{
		List<WorldPoint> tiles = new ArrayList<>();
		for (BlastMineRock rock : rocks)
		{
			WorldPoint point = rock.getGameObject().getWorldLocation();
			if (site.isWallTile(point) && !tiles.contains(point))
			{
				tiles.add(point);
			}
		}
		if (tiles.isEmpty())
		{
			return site.getWallTiles();
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

	private static final class InventorySnapshot
	{
		boolean hasChisel;
		boolean hasTinderbox;
		boolean hasNotedDynamite;
		int unnotedDynamite;
		int emptySlots;
		int placeholderItems;

		int oreCycleCapacity()
		{
			return emptySlots + unnotedDynamite;
		}
	}
}
