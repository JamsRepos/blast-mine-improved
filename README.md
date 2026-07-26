# Blast Mine Improved

RuneLite plugin that replaces the built-in **Blast Mine** plugin with a north-east rotation helper, mistake-prevention menus, inventory ore timers, and sack XP estimates.

Enabling this plugin disables the core **Blast Mine** plugin and **Blast Mine Dynamite Restriction** (via plugin conflicts).

## Features

### Core overlays (replaces built-in Blast Mine)
- Rock step icons (chisel / dynamite / tinderbox), optionally limited to the helper’s current targets
- Fuse timers and explosion radius warning
- Ore sack HUD with counts for coal / gold / mithril / adamantite / runite
- Estimated Mining XP currently sitting in the sack (optional +2.5% prospector estimate)

### North-east rotation helper
Guides the common NE pattern:

1. Full lap: pairs **1-2 → 3-4 → 5-6 → 7-8**
2. Second full lap (pick up ore at each pair, then excavate)
3. Short finale: **1-2 → 3-4**, then deposit

Also guides:
| Marker | Role |
|--------|------|
| Sack | Deposit when you have 20 blasted ore |
| Bank chest | Use noted dynamite on the chest when low / empty |
| Operator | Collect washed ore (wear prospectors first when sack is full) |

Inventory prep before starting: chisel, tinderbox, noted dynamite, 5 placeholder items, and 20 empty slots or 20 unnoted dynamite.

### Menu safety
- Deprioritize **Excavate** when you have no unnoted dynamite
- Hide **Light** on a pot unless its pair partner is also ready to light
- Prefer the helper’s recommended option as left-click when possible
- Optionally hide off-path excavate / place / light while the helper is guiding

### Inventory ore timers
Blasted ore disintegrates after **3 minutes**. Each inventory slot shows a progress pie so you know when to deposit.

### Dynamite alerts
Chat + optional sound when you run out of or replenish unnoted dynamite (ported from [blast-mine-dynamite-restriction](https://github.com/Fabletownn/blast-mine-dynamite-restriction)).

## Development

Requirements: JDK 11+.

```bash
./gradlew run
```

For Jagex accounts, follow [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

## License

BSD-2-Clause (same as the RuneLite example plugin).
