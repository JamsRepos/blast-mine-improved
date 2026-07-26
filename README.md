# Blast Mine Improved

RuneLite Plugin Hub plugin that replaces the built-in **Blast Mine** plugin with a fuller north-east rotation helper, mistake-prevention menus, inventory ore timers, and sack XP estimates.

Enabling this plugin disables the core **Blast Mine** plugin and **Blast Mine Dynamite Restriction** (via plugin conflicts).

## Features

### Core overlays (replaces built-in Blast Mine)
- Rock step icons (chisel / dynamite / tinderbox)
- Fuse timers and explosion radius warning
- Ore sack HUD with counts for coal / gold / mithril / adamantite / runite
- Estimated Mining XP currently sitting in the sack (optional +2.5% prospector estimate)

### North-east rotation helper
Guided around the NE pairs from ground markers:

| Label | Role |
|-------|------|
| 1-2, 3-4, 5-6, 7-8 | Paired cavities to excavate → load → light together |
| Sack | Deposit blasted ore |
| Bank chest | Use dynamite on chest when low |
| Operator | Collect washed ore (wear prospectors first) |

Shows an Easy Blast Furnace-style panel and highlights the next tile(s) to click.

### Menu safety
- Deprioritize **Excavate** when you have no unnoted dynamite
- Hide **Light** on a pot unless its pair partner is also ready to light
- Prefer the helper's recommended option as left-click when possible

### Inventory ore timers
Blasted ore disintegrates after **3 minutes**. Each inventory slot shows a progress pie so you know when to deposit.

### Dynamite alerts
Chat + optional sound when you run out of or replenish unnoted dynamite (ported from [blast-mine-dynamite-restriction](https://github.com/Fabletownn/blast-mine-dynamite-restriction)).

## Development

Requirements: JDK 11+, IntelliJ recommended.

```bash
./gradlew run
```

For Jagex accounts, follow [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

## In-game testing checklist

1. Enable **Blast Mine Improved** — confirm core Blast Mine turns off
2. At NE blast mine: icons on rocks, helper panel text, next-tile highlight
3. Load only one pot of a pair — **Light** should be hidden until both are loaded
4. Empty dynamite — Excavate becomes right-click only; sound/chat fires
5. Pick up blasted ore — inventory pie timers appear; deposit highlight when needed
6. Fill sack toward 900 of one ore — XP estimate updates; full sack prompts operator + prospectors

## License

BSD-2-Clause (same as the RuneLite example plugin).
