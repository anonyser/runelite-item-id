# Item ID

Adds each item's ID in parentheses to its hover text, so hovering an item shows something like
`Wield Dragon dagger (1215)`. Handy for grabbing an item ID without looking it up on the wiki.

One toggle in the settings, **Item ID on hover**, turns it on or off. It works for inventory, bank,
worn and ground items. Display only: it just appends the ID the game already knows and changes
nothing else.

## Building

Needs JDK 11.

```
./gradlew clean test build
```

or double-click `build.bat` on Windows. `run-client.bat` starts a dev RuneLite client with the
plugin loaded.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
