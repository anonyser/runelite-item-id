# Item ID and Lookup

Two ways to grab an item's ID without leaving the game:

**On hover.** With **Item ID on hover** turned on (in the settings), each item's hover text gets its ID
in parentheses, e.g. `Wield Dragon dagger (1215)`. Works for inventory, bank, worn and ground items.

**Search panel.** Open the side panel (magnifier icon in the toolbar) and start typing. Matching items
populate as you type; click one to see its icon, name, ID (click to copy), Grand Exchange price,
high-alch value and, for equipment, its full bonus stats. Repairable untradeables (fire cape, fighter
torso, defenders and the like) also show their repair cost. It searches every item in the game, so
untradeables and variants show up too.

- **Variants and ornaments.** Ornament, Bounty Hunter corrupted, degraded and Deadman-style variants
  price as their tradeable base and say "Prices as <item>"; click that to jump to the base item.
- **Combination items.** Items built from other items (the nightmare staves, godswords, visage shields
  and more) show a "Made from" list of the parts with each part's price and the total to make. Every
  part is clickable. Recipes live in `recipes.csv` and resolve their component IDs from the game.
- **Wiki description.** A short description is fetched from the OSRS wiki when you click an item
  (toggleable; cached per item).
- **Compare.** "Compare with another item" opens a second search; pick an item and the two are laid out
  side by side with the better value of each stat highlighted.

Display only: it just surfaces information the game already knows and changes nothing else.

## Building

Needs JDK 11.

```
./gradlew clean test build
```

or double-click `build.bat` on Windows. `run-client.bat` starts a dev RuneLite client with the
plugin loaded.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
