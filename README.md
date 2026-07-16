<p align="center">
  <img src="docs/banner.png" alt="my osrs character, made out of text" width="400">
</p>

# Item ID and Lookup

Two ways to grab an item's ID without leaving the game:

**On hover.** Each item's hover text gets its ID in parentheses, e.g. `Wield Dragon dagger (1215)`.
Works for inventory, bank, worn and ground items. On by default; turn it off with **Item ID on hover**
in the settings.

**Search panel.** Open the side panel (magnifier icon in the toolbar) and start typing, or tick
**Open item search panel** in the settings, which opens it and unticks itself. Matching items populate
as you type; click one to see its icon, name, ID (click to copy), Grand Exchange price, high-alch value
and, for equipment, its full bonus stats. Repairable untradeables (fire cape, fighter torso, defenders
and the like) also show their repair cost. It searches every item in the game, so untradeables and
variants show up too. The panel says "Loading item list..." for a moment at startup while it reads the
item names.

Search is by name, not by ID number. Names that start with what you typed come first, then names that
contain it, shortest first. You get the top 25 matches, so narrow the query if what you want isn't
there.

- **Reading the result list.** The right-hand column shows the item's GE price (abbreviated, e.g.
  `150K`, `7.3M`), or its repair cost, or `(not on GE)` / `(untradeable)`. Items with no GE price are
  greyed out, which is usually how you spot the real item next to its LMS and beta look-alikes.
- **Variants and ornaments.** Ornament, Bounty Hunter corrupted, degraded and Deadman-style variants
  price as their tradeable base and say "Prices as <item>"; click that to jump to the base item.
- **Combination items.** Items built from other items (the nightmare staves, godswords, visage shields
  and more) show a "Made from" list of the parts with each part's price and the total to make. Every
  part is clickable. Recipes live in `recipes.csv` and resolve their component IDs from the game.
  Repair costs come from `repair-costs.csv`, checked against the wiki.
- **Wiki.** "Open wiki page" opens the item's OSRS wiki page in your browser. Separately, a short
  description is fetched from the wiki when you click an item. That fetch is one web request per item
  (cached per item), it's on by default, and **Wiki description in lookup** turns it off if you want
  the lookup to stay fully offline. Nothing else in the plugin goes out to the internet.
- **Compare.** "Compare with another item" opens a second search; pick an item and the two are laid out
  side by side with the better value of each stat highlighted.

Display only: it just surfaces information the game already knows and changes nothing else.

## Update note

A few ticks after you log in, the plugin puts one line in your chatbox describing what changed in the
version you're running. It shows once per version and won't come back until the next update.

## Building

Needs JDK 11.

```
./gradlew clean test build
```

or double-click `build.bat` on Windows. `run-client.bat` starts a dev RuneLite client with the
plugin loaded.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
