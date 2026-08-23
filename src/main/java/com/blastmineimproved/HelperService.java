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
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

@Singleton
public class HelperService
{
	private static final int SACK_FULL_THRESHOLD = 900;
	/** Player is "at" the NE rotation when within this distance of any NE marker. */
	private static final int NE_AREA_RADIUS = 10;
	private static final int INVENTORY_SIZE = 28;
	private static final Color NEXT_COLOR = new Color(0, 200, 255, 180);
	private static final Color WARN_COLOR = new Color(255, 170, 0, 180);
	private static final Color DEPOSIT_COLOR = new Color(10, 255, 0, 180);
	private static final Color GUIDE_COLOR = new Color(255, 220, 0, 200);

	private final Client client;
	private final BlastMineImprovedConfig config;
	private final BlastedOreTracker oreTracker;
	private final GroundOreTracker groundOreTracker;

	@Getter
	private HelperAction currentAction = HelperAction.idle();

	/** Cached highlight tiles for O(1) focus checks in overlays (updated only when action changes). */
	private Set<WorldPoint> focusTiles = Collections.emptySet();

	/**
	 * Loot-as-you-go only: after the planned full laps, guide the short finale pairs.
	 * Blast-then-loot never uses this — it keeps cycling 1-2→7-8 until dynamite is gone.
	 */
	private boolean shortFinale;

	private int fullPassesCompleted;

	/** Sites blasted at least once this trip (eligible for pickup on a later loot-as-you-go pass). */
	private final Set<NortheastSite> blastedThisTrip = EnumSet.noneOf(NortheastSite.class);

	/** Sites already fired this pass — keep moving; don't rework or wait on them. */
	private final Set<NortheastSite> completedThisPass = EnumSet.noneOf(NortheastSite.class);

	/**
	 * Sites that needed excavate/place/load work this pass. Required before LIT can count as
	 * completed — otherwise leftover fuses from the previous lap auto-skip 7-8.
	 */
	private final Set<NortheastSite> workedThisPass = EnumSet.noneOf(NortheastSite.class);

	/** Set when a blast-then-loot trip spends its last dynamite, so the next refill starts a clean lap. */
	private boolean tripExhausted;

	@Getter
	private int cachedSackXp;
	@Getter
	private int cachedTotalSackOres;
	@Getter
	private boolean cachedSackFull;

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
		tripExhausted = false;
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
		return currentAction != null && currentAction.getKind().hidesOffPathIcons();
	}

	/**
	 * When true with {@link #shouldHideOffPathRockIcons()}, only draw rock action icons on focus
	 * tiles. When false, suppress all rock action icons for this step (deposit/bank/etc.).
	 */
	public boolean restrictRockIconsToFocusTiles()
	{
		return currentAction != null && currentAction.getKind().iconsOnFocusOnly();
	}

	/**
	 * True when off-path excavate/place/light menus should be removed.
	 * Collect/deposit/bank do not share this — they hide icons but must not strip Light.
	 */
	public boolean isFocusingBlastingStep()
	{
		return currentAction != null && currentAction.getKind().stripsOffPathRockMenus();
	}

	public void update(Map<WorldPoint, BlastMineRock> rocks)
	{
		refreshSackCache();

		if (!config.enableHelper() || !BlastMineArea.isInBlastMine(client))
		{
			setCurrentAction(HelperAction.idle());
			return;
		}

		InventorySnapshot inv = scanInventory();
		int oreCount = oreTracker.getOreCount();
		int dynamite = inv.getUnnotedDynamite();
		TripPlan plan = TripPlan.of(config.dynamitePerTrip());
		boolean blastThenLoot = config.rotationMethod() == RotationMethod.BLAST_THEN_LOOT;
		boolean guidePickup = config.guideOrePickup();
		boolean groundClear = !groundOreTracker.hasAnyOre();
		boolean groundOkForReset = !HelperPolicy.tripResetRequiresClearGround(guidePickup) || groundClear;

		if (blastThenLoot)
		{
			if (tripExhausted && dynamite > 0)
			{
				resetRotation();
			}
		}
		else if (shortFinale && oreCount == 0 && dynamite > 0 && groundOkForReset)
		{
			resetRotation();
		}

		if (!isNearNortheast())
		{
			setCurrentAction(new HelperAction(
				HelperAction.Kind.IDLE,
				"Head to the north-east pairs (start at 1-2)",
				List.of(NortheastSite.PAIR_1_2.getTile()),
				GUIDE_COLOR));
			return;
		}

		List<NortheastSite> sites = activeSites(plan, blastThenLoot);
		Map<NortheastSite, List<BlastMineRock>> bySite = groupBySite(rocks);
		updatePassProgress(sites, bySite, plan, blastThenLoot);

		if (cachedSackFull)
		{
			setCurrentAction(new HelperAction(
				HelperAction.Kind.WEAR_PROSPECTORS,
				"Sack full — wear prospectors, then collect",
				List.of(NortheastSite.OPERATOR),
				WARN_COLOR));
			return;
		}

		if (oreCount >= plan.getDynamitePerTrip())
		{
			setCurrentAction(new HelperAction(
				HelperAction.Kind.DEPOSIT_SACK,
				"Deposit ore into the sack",
				List.of(NortheastSite.SACK),
				DEPOSIT_COLOR));
			return;
		}

		if (dynamite == 0)
		{
			HelperAction pendingLight = pendingLightAction(bySite, dynamite);
			if (pendingLight != null)
			{
				setCurrentAction(pendingLight);
				return;
			}

			if (guidePickup && groundOreTracker.hasAnyOre())
			{
				setCurrentAction(new HelperAction(
					HelperAction.Kind.COLLECT_ORE,
					"Pick up remaining ore",
					groundOreTracker.allOreTiles(),
					DEPOSIT_COLOR));
				return;
			}
			if (oreCount > 0)
			{
				setCurrentAction(new HelperAction(
					HelperAction.Kind.DEPOSIT_SACK,
					"Deposit ore (" + oreCount + ")",
					List.of(NortheastSite.SACK),
					DEPOSIT_COLOR));
				return;
			}
			if (blastThenLoot)
			{
				tripExhausted = true;
			}
			setCurrentAction(bankDynamiteAction(inv, "Use noted dynamite on the bank chest"));
			return;
		}

		HelperAction prep = prepInventoryAction(inv, plan, oreCount == 0 && groundClear);
		if (prep != null)
		{
			setCurrentAction(prep);
			return;
		}

		if (dynamite <= config.lowDynamiteThreshold() && oreCount == 0 && groundClear)
		{
			setCurrentAction(bankDynamiteAction(inv, "Low dynamite (" + dynamite + ") — refill at the chest"));
			return;
		}

		boolean allowPickup = !blastThenLoot
			&& HelperPolicy.emitCollectAtPair(config.rotationMethod(), guidePickup)
			&& (shortFinale
				? blastedThisTrip.containsAll(plan.finaleSites())
				: blastedThisTrip.containsAll(NortheastSite.ORDER));

		for (NortheastSite site : sites)
		{
			if (completedThisPass.contains(site))
			{
				continue;
			}

			List<BlastMineRock> siteRocks = bySite.getOrDefault(site, Collections.emptyList());
			HelperAction action = actionForSite(site, siteRocks, shortFinale && !blastThenLoot, allowPickup, dynamite);
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
			blastThenLoot
				? "Ready — blast every pair, then loot"
				: shortFinale
					? "Finale done — pick up and deposit"
					: "Ready — start at pair 1-2",
			shortFinale && !blastThenLoot
				? highlightForFinale(plan)
				: NortheastSite.PAIR_1_2.getWallTiles(),
			NEXT_COLOR));
	}

	private List<NortheastSite> activeSites(TripPlan plan, boolean blastThenLoot)
	{
		if (blastThenLoot)
		{
			return NortheastSite.ORDER;
		}
		if (!shortFinale)
		{
			return NortheastSite.ORDER;
		}

		List<NortheastSite> finale = new ArrayList<>(plan.finaleSites());
		NortheastSite leftover = plan.leftoverSite();
		if (leftover != null && !finale.contains(leftover))
		{
			finale.add(leftover);
		}
		return finale;
	}

	private static List<WorldPoint> highlightForFinale(TripPlan plan)
	{
		List<WorldPoint> tiles = new ArrayList<>();
		for (NortheastSite site : plan.finaleSites())
		{
			tiles.add(site.getTile());
		}
		return tiles.isEmpty() ? NortheastSite.PAIR_1_2.getWallTiles() : tiles;
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
		if (!inv.isNotedDynamite())
		{
			detail = "Need noted dynamite — use it on the bank chest";
		}
		return new HelperAction(
			HelperAction.Kind.BANK_DYNAMITE,
			detail,
			List.of(NortheastSite.BANK_CHEST),
			WARN_COLOR);
	}

	/**
	 * Require chisel, tinderbox, noted dynamite, auto-sized placeholders, and trip-size
	 * empty/unnoted slots before starting. Placeholders are 28 − dynamite − tools so the
	 * chest fill matches the trip.
	 */
	private HelperAction prepInventoryAction(InventorySnapshot inv, TripPlan plan, boolean fullKitRequired)
	{
		int placeholders = plan.placeholderSlots();
		int tripSize = plan.getDynamitePerTrip();
		List<String> missing = new ArrayList<>();
		if (!inv.isChisel())
		{
			missing.add("chisel");
		}
		if (!inv.isTinderbox())
		{
			missing.add("tinderbox");
		}

		if (!fullKitRequired && missing.isEmpty())
		{
			return null;
		}

		if (fullKitRequired)
		{
			if (!inv.isNotedDynamite())
			{
				missing.add("noted dynamite");
			}
			if (inv.getPlaceholderItems() < placeholders)
			{
				missing.add((placeholders - inv.getPlaceholderItems()) + " more filler item(s)");
			}
			if (inv.oreCycleCapacity() < tripSize)
			{
				missing.add(tripSize + " empty slots or unnoted dynamite");
			}
		}

		if (missing.isEmpty())
		{
			return null;
		}

		WorldPoint highlight = (!inv.isNotedDynamite() || inv.oreCycleCapacity() < tripSize)
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
		boolean chisel = false;
		boolean tinderbox = false;
		boolean notedDynamite = false;
		int emptySlots = 0;
		int placeholderItems = 0;

		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return new InventorySnapshot(false, false, false, 0, INVENTORY_SIZE, 0);
		}

		Item[] items = inventory.getItems();
		for (int i = 0; i < INVENTORY_SIZE; i++)
		{
			Item item = i < items.length ? items[i] : null;
			if (item == null || item.getId() < 0)
			{
				emptySlots++;
				continue;
			}

			int id = item.getId();
			if (id == ItemID.CHISEL)
			{
				chisel = true;
				continue;
			}
			if (id == ItemID.TINDERBOX)
			{
				tinderbox = true;
				continue;
			}
			if (id == ItemID.LOVAKENGJ_DYNAMITE_FUSED)
			{
				continue;
			}

			ItemComposition def = client.getItemDefinition(id);
			if (def.getNote() != -1 && def.getLinkedNoteId() == ItemID.LOVAKENGJ_DYNAMITE_FUSED)
			{
				notedDynamite = true;
				continue;
			}

			placeholderItems++;
		}

		return new InventorySnapshot(
			chisel,
			tinderbox,
			notedDynamite,
			inventory.count(ItemID.LOVAKENGJ_DYNAMITE_FUSED),
			emptySlots,
			placeholderItems);
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

	private HelperAction pendingLightAction(Map<NortheastSite, List<BlastMineRock>> bySite, int dynamite)
	{
		for (NortheastSite site : NortheastSite.ORDER)
		{
			List<BlastMineRock> siteRocks = bySite.getOrDefault(site, Collections.emptyList());
			List<BlastMineRock> loaded = filter(siteRocks, BlastMineRockType.LOADED);
			List<BlastMineRock> lit = filter(siteRocks, BlastMineRockType.LIT);

			if (!HelperPolicy.canLightLoadedPots(dynamite, loaded.size(), lit.size()))
			{
				continue;
			}

			String detail = loaded.size() >= 2
				? "Light pair " + site.getLabel()
				: "Light leftover pot on " + site.getLabel();
			return new HelperAction(
				HelperAction.Kind.LIGHT,
				detail,
				tilesOf(loaded, site),
				WARN_COLOR);
		}
		return null;
	}

	private void updatePassProgress(
		List<NortheastSite> sites,
		Map<NortheastSite, List<BlastMineRock>> bySite,
		TripPlan plan,
		boolean blastThenLoot)
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

		boolean allDone = !sites.isEmpty();
		for (NortheastSite site : sites)
		{
			if (!completedThisPass.contains(site))
			{
				allDone = false;
				break;
			}
		}
		if (allDone)
		{
			completedThisPass.clear();
			workedThisPass.clear();
			if (!blastThenLoot && !shortFinale)
			{
				fullPassesCompleted++;
				if (fullPassesCompleted >= plan.getFullLaps())
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
		boolean allowPickup,
		int dynamite)
	{
		List<BlastMineRock> normal = filter(siteRocks, BlastMineRockType.NORMAL);
		List<BlastMineRock> chiseled = filter(siteRocks, BlastMineRockType.CHISELED);
		List<BlastMineRock> loaded = filter(siteRocks, BlastMineRockType.LOADED);
		List<BlastMineRock> lit = filter(siteRocks, BlastMineRockType.LIT);

		String finalePrefix = finale ? "Finale — " : "";

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
				finalePrefix + "Pick up ore at " + site.getLabel(),
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
				finalePrefix + "Place dynamite on " + site.getLabel(),
				tilesOf(chiseled, site),
				NEXT_COLOR);
		}

		if (HelperPolicy.canLightLoadedPots(dynamite, loaded.size(), lit.size()))
		{
			return new HelperAction(
				HelperAction.Kind.LIGHT,
				loaded.size() >= 2
					? finalePrefix + "Light pair " + site.getLabel()
					: finalePrefix + "Light leftover pot on " + site.getLabel(),
				tilesOf(loaded, site),
				loaded.size() == 1 ? WARN_COLOR : NEXT_COLOR);
		}

		if (loaded.size() == 1)
		{
			return new HelperAction(
				HelperAction.Kind.PLACE_DYNAMITE,
				finalePrefix + "Load the other pot on " + site.getLabel(),
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

	private void refreshSackCache()
	{
		cachedSackFull = computeSackFull();
		cachedTotalSackOres = computeTotalSackOres();
		cachedSackXp = computeSackXp();
	}

	public boolean isSackFull()
	{
		return cachedSackFull;
	}

	public int estimateSackXp()
	{
		return cachedSackXp;
	}

	public int totalSackOres()
	{
		return cachedTotalSackOres;
	}

	private boolean computeSackFull()
	{
		return client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_COAL_BIGGER) >= SACK_FULL_THRESHOLD
			|| client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_GOLD_BIGGER) >= SACK_FULL_THRESHOLD
			|| client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_MITHRIL_BIGGER) >= SACK_FULL_THRESHOLD
			|| client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_ADAMANTITE_BIGGER) >= SACK_FULL_THRESHOLD
			|| client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_RUNITE_BIGGER) >= SACK_FULL_THRESHOLD;
	}

	private int computeSackXp()
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

	private int computeTotalSackOres()
	{
		return client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_COAL_BIGGER)
			+ client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_GOLD_BIGGER)
			+ client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_MITHRIL_BIGGER)
			+ client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_ADAMANTITE_BIGGER)
			+ client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_RUNITE_BIGGER);
	}
}
