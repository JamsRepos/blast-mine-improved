# Context

## Glossary

### Status Panel

The single `OverlayPanel`-based HUD (`overlay/StatusOverlay.java`) showing current helper
guidance and live ore-sack contents. It hides entirely — rendering nothing — when there is
nothing actionable to show, rather than displaying an empty panel.

This is the same UI concept as the "status panel" in the sibling plugin
[zeah-rc-helper](https://github.com/JamsRepos/zeah-rc-helper); both plugins use the term to
mean a single overlay-based HUD (no side panel), built from `LineComponent` rows, as the
primary place a player looks for "what should I do next."
