# Bank Sorter

Client-side bank organiser for RuneLite. Items are only drawn in a different place; nothing in your real bank
moves.

- Categories for each combat style, Slayer, every skill, and supplies (food, potions, runes, teleports).
- Combat gear in best-in-slot outfit columns (head to feet), with set-effect sets kept together.
- Potions in dose columns (raid potions first), food by heal amount with combo food separate, teleports by
  usefulness, blighted supplies last.
- Multi-use items (barrows gloves, glory, Graceful, logs...) also appear as withdrawable copies in their other
  categories.
- Tabs down the left edge filter the bank to a group of categories.
- Right-click an item, **Move to**, to put it in another category (saved under Custom categories).

## Compatibility

Don't use Bank Tags **tag tabs** with this plugin. While a tag tab is open the sorter steps aside, but the two
share the left edge of the bank. The Bank Tags plugin itself can stay enabled.

## Development

```sh
./gradlew run     # dev client with the plugin loaded
./gradlew build   # compile and run tests
```

Item data lives in `src/main/resources/com/banksorter/`: `items.txt` (categories and tiers), `food.txt`
(heal amounts), `sets.txt` (set-effect sets), `overrides.txt`.

## Running day to day

The normal RuneLite launcher can't load plugins that aren't on the Plugin Hub, so this runs its own RuneLite:

1. `gradlew.bat shadowJar` once (and again after changing the plugin) builds `build/libs/bank-sorter-unspecified-all.jar`.
2. Double-click `RuneLite (Bank Sorter).bat` (pin a shortcut to it if you like).

It logs in with the Jagex session saved in `%USERPROFILE%\.runelite\credentials.properties`. To create or refresh that
file, start RuneLite once from the Jagex Launcher with `--insecure-write-credentials` added in RuneLite (configure).
It uses your normal RuneLite profile and settings.
