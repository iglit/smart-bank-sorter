# Smart Bank Sorter

A client-side bank organiser for RuneLite. It only changes where items are *drawn*; nothing in your real bank
moves, and turning the plugin off gives you your normal bank back.

## What it does

- **Categories**: one per combat style, Slayer, every skill, plus Food, Potions, Runes, Teleports, Prayer, Clues,
  Quest, Jewellery, Cosmetic and Misc. Each has a header you can click to collapse it.
- **Combat gear as outfits**: each weapon heads a column with the gear that does the most damage with it, read head
  to feet, strongest weapon leftmost. Damage is worked out from the OSRS Wiki's formulas at your levels against a
  typical boss, including weapon effects (Scythe of Vitur, Twisted bow, Tumeken's shadow...). Armour sets with set
  effects (Barrows, the Moons, Void, Inquisitor's...) stay together when the effect adds damage, under their own
  weapon when you have it.
- **Supplies laid out properly**: potions in dose columns with raid potions first, food by how much it heals with
  combo food on its own row, teleports by how often they're used, blighted supplies last.
- **Items in more than one place**: multi-use items (Barrows gloves, glory, Graceful, logs...) also appear in
  their other categories. These copies work exactly like the real item, including withdrawing.
- **Tabs**: tabs down the left edge show just Combat, Runes & teleports, Food & potions, or Skilling.
- **Your own categories**: click the filing cabinet at the top of the bank to open the category editor. Make
  categories, search for items and **Move** them in, or **Add** them so they also stay where they are. Items you
  add but don't have show as placeholders, as a reminder of what to get; right-click, **Release** to drop one. A
  `*` matches any part of a name, e.g. `*salve amulet*`. Drag categories to reorder them and pick the tab each
  shows in. For a quick fix, right-click an item in the bank, **Move to**, and pick a category.

**Turning it on and off**: right-click the bank's search button (the magnifying glass under the items) and pick
**Sort** or **Unsort**, or use *Sort bank* in the plugin's settings.

## Searching by category

Type three or more letters of a category's name in the bank search, e.g. `cra`, and a **Jump to Crafting** row
appears above the results: press **Enter** or click it to close the search and jump to that category in the full
bank. Searching a category's full name also adds its items to the results.

## Settings

| Setting | What it does |
|---|---|
| Sort bank | Turn the sorted view on or off |
| Order within category | Most valuable first (stack or item, GE or HA value), or your own bank order, for categories without a special layout |
| Rank combat gear | Outfit columns for Melee, Ranged and Magic |
| Arrange food & potions | Heal order for food, dose columns for potions, usefulness order for teleports |
| Sort inside bank tabs | Also group items when a numbered bank tab is open |
| Hide bank fillers | Leave bank fillers out of the sorted view |
| Hide placeholders | Leave placeholders out of the sorted view, so you only see items you have (off by default) |
| Category search | Suggest a category to jump to while searching, and show a category's items when you search its name |
| Category tabs | Show the left-edge tabs |
| Outfit layout | Outfits as columns (default) or rows |
| Compact layout | Close up gaps in the outfits, or in the outfits and the potion columns (off by default) |
| Best outfits only | Give a weapon its own outfit only if at least half of it adds damage; the rest go with the spare gear (off by default) |
| Header size / Header colour / Show item counts / Divider lines | How category headers look |
| Gear sets | Extra armour sets to keep together in one outfit |
| Edit categories button | Show the filing cabinet button that opens the category editor |

## Compatibility

Works alongside Bank Tags, including tag tabs: opening a tag tab shows it as normal, and the sorted view comes
back when you close it. Both plugins use the left edge of the bank for tabs. The category tabs join Bank Tags'
strip, and you can drag any tab, tag or category, to put the strip in the order you like. Like a tag tab, the open
category tab is reopened with the bank when Bank Tags' **Remember last tag tab** is on. The order is kept in this
plugin's settings; Bank Tags' own only ever get your tag tabs' order, as when you reorder them there. Drag a
sorted item, or a copy, onto a tag tab to tag it; an item dropped on a category tab is not tagged, and the
category tabs leave with the plugin.

Copies need free bank space: each copy uses one empty bank slot, so they don't show when your bank is full.

## Item data

Categories come from bundled data classified from the OSRS Wiki (`src/main/resources/com/smartbanksorter/`):
`items.txt` (categories for ~5,000 items), `food.txt` (heal amounts), `sets.txt` (set-effect armour sets)
and `overrides.txt`. Items not listed fall back to rules based on their stats and right-click options.
Corrections are welcome as pull requests.

## Support

The plugin is free and always will be. If it saves you some bank-scrolling and you'd like to say thanks, you can
[buy me a coffee on Ko-fi](https://ko-fi.com/iglit).

## Development

```sh
./gradlew run     # RuneLite dev client with the plugin loaded
./gradlew build   # compile and run tests
```

Licensed under BSD 2-Clause.
