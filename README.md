# Jam's Blast Mine

RuneLite plugin that replaces the built-in **Blast Mine** plugin with a north-east rotation helper, mistake-prevention menus, inventory ore timers, and sack XP estimates.

Enabling this plugin disables the core **Blast Mine** plugin and **Blast Mine Dynamite Restriction** (via plugin conflicts).

## Features

### Core overlays (replaces built-in Blast Mine)
- Rock step icons (chisel / dynamite / tinderbox), optionally limited to the helper’s current targets
- Fuse timers and explosion radius warning
- Ore sack HUD with counts for coal / gold / mithril / adamantite / runite
- Estimated Mining XP currently sitting in the sack (optional +2.5% prospector estimate)

### North-east rotation helper
Two methods (dropdown). **Loot as you go** is the default so existing setups stay the same.

#### Loot as you go (default)
Typical **20 dynamite** (helper fills 5 placeholder slots for you):

1. Full lap: pairs **1-2 → 3-4 → 5-6 → 7-8**
2. Second full lap (pick up ore at each pair, then excavate)
3. Short finale: **1-2 → 3-4**, then deposit

#### Blast then loot
Typical **21 dynamite** (helper fills 4 placeholder slots for you: chisel + tinderbox + noted dynamite + 21 unnoted):

1. Keep cycling **1-2 → 7-8** until unnoted dynamite is gone
2. Light leftover pots, including an odd last pot
3. Pick up stacked ground ore (unless pickups are turned off)
4. Deposit whatever is in inventory, then bank

**Guide ore pickups** (default on) can be turned off if you area-loot or loot another way. The helper then never asks you to pick up, and will not wait for ground piles before the next trip.

Also guides:
| Marker | Role |
|--------|------|
| Sack | Deposit when inventory ore reaches your trip size |
| Bank chest | Use noted dynamite on the chest when low / empty |
| Operator | Collect washed ore (wear prospectors first when sack is full) |

Set **Dynamite per trip** and the helper sizes the rest: placeholders are **28 − dynamite − chisel − tinderbox − noted dynamite**, so using noted dynamite on the chest yields exactly that many unnoted.

### Menu safety
- Deprioritize **Excavate** when you have no unnoted dynamite (Light stays available so a leftover pot can still be fired)
- Hide **Light** on a pot unless its pair partner is also ready — except when dynamite is gone
- Prefer the helper’s recommended option as left-click on the current target tiles
- Optionally hide off-path excavate / place / light while the helper is guiding a pair (tile-keyed, because Hard Rock shares a name and Object ID)

### Inventory ore timers
Blasted ore disintegrates after **3 minutes**. Each inventory slot shows a progress pie so you know when to deposit.

### Dynamite alerts
Chat + optional sound when you run out of or replenish unnoted dynamite.

## Credits

- [Blast Mine Dynamite Restriction](https://github.com/Fabletownn/blast-mine-dynamite-restriction) by Fabletownn — dynamite chat/sound alerts, excavate deprioritization without dynamite, and related menu-safety ideas were adapted from that plugin (this plugin conflicts with it so only one runs at a time).
- Built-in RuneLite **Blast Mine** — rock icons, fuse timers, and explosion warnings are reimplemented here as the replacement overlay set.

## Development

Requirements: JDK 11+.

```bash
./gradlew run
```

For Jagex accounts, follow [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

## License

BSD-2-Clause (same as the RuneLite example plugin).
