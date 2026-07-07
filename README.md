# Item ID and Lookup

Two ways to grab an item's ID without leaving the game:

**On hover.** With **Item ID on hover** turned on (in the settings), each item's hover text gets its ID
in parentheses, e.g. `Wield Dragon dagger (1215)`. Works for inventory, bank, worn and ground items.

**Search panel.** Open the side panel (magnifier icon in the toolbar) and start typing. Matching items
populate as you type; click one to see its icon, name, ID (click to copy), Grand Exchange price and
high-alch value. Repairable untradeables (fire cape, fighter torso, defenders and the like) also show
their repair cost. Ornament, Bounty Hunter corrupted and degraded items price as their tradeable base
and say which item that is. Search covers the tradeable item list, so the hover option is the fallback
for anything that isn't tradeable.

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
