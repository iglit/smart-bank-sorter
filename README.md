# Smart Bank Sorter

A client-side bank organiser for RuneLite. It only changes where items are *drawn*; nothing in your real bank
moves, and turning the plugin off gives you your normal bank back.

## What it does

- **Categories**: one per combat style, Slayer, every skill, plus Food, Potions, Runes, Teleports, Prayer, Clues,
  Quest, Jewellery, Cosmetic and Misc. Each has a header you can click to collapse it.
- **Combat gear as outfits**: weapons first, then armour in best-in-slot columns read head to feet (your best
  outfit is the leftmost column). Armour sets with set effects (Barrows, Void, Blood moon, Inquisitor's...) stay
  together.
- **Supplies laid out properly**: potions in dose columns with raid potions first, food by how much it heals with
  combo food on its own row, teleports by how often they're used, blighted supplies last.
- **Items in more than one place**: multi-use items (Barrows gloves, glory, Graceful, logs...) also appear in
  their other categories. These copies work exactly like the real item, including withdrawing.
- **Tabs**: tabs down the left edge show just Combat, Runes & teleports, Food & potions, or Skilling.
- **Your own rules**: right-click any item, **Move to**, and pick a category. Or write rules under
  *Custom categories*, e.g. `Slayer: slayer helm*, enchanted gem`.

Right-click the bank's search button to turn sorting on and off.

## Searching by category

Type three or more letters of a category's name in the bank search, e.g. `cra`, and a **Jump to Crafting** row
appears above the results: press **Enter** or click it to close the search and jump to that category in the full
bank. Searching a category's full name also adds its items to the results.

## Settings

| Setting | What it does |
|---|---|
| Sort bank | Turn the sorted view on or off |
| Order within category | Name, value, or bank order for categories without a special layout |
| Rank combat gear | Outfit columns for Melee, Ranged and Magic |
| Arrange food & potions | Heal order for food, dose columns for potions, usefulness order for teleports |
| Sort inside bank tabs | Also group items when a numbered bank tab is open |
| Category search | Suggest a category to jump to while searching, and show a category's items when you search its name |
| Category tabs / Tabs | Show the left-edge tabs and choose what each tab contains |
| Outfit layout | Outfits as columns (default) or rows |
| Custom categories / Category order / Gear sets | Your own rules, the order of sections, and extra armour sets to keep together |

## Compatibility

Works alongside Bank Tags, including tag tabs: opening a tag tab shows it as normal, and the sorted view comes
back when you close it. Both plugins use the left edge of the bank for tabs, so if you use tag tabs, turn off
**Category tabs** in this plugin's settings to keep that edge for Bank Tags.

Copies need free bank space: each copy uses one empty bank slot, so they don't show when your bank is full.

## Item data

Categories come from bundled data classified from the OSRS Wiki (`src/main/resources/com/smartbanksorter/`):
`items.txt` (category and tier for ~5,000 items), `food.txt` (heal amounts), `sets.txt` (set-effect armour sets)
and `overrides.txt`. Items not listed fall back to rules based on their stats and right-click options.
Corrections are welcome as pull requests.

## Development

```sh
./gradlew run     # RuneLite dev client with the plugin loaded
./gradlew build   # compile and run tests
```

Licensed under BSD 2-Clause.
