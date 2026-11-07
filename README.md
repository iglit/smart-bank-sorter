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
