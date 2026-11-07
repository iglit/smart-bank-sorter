package com.smartbanksorter;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(SmartBankSorterConfig.GROUP)
public interface SmartBankSorterConfig extends Config
{
	String GROUP = "smartbanksorter";
	String COLLAPSED_KEY = "collapsed";

	enum ItemOrder
	{
		NAME,
		VALUE_HIGH_TO_LOW,
		VALUE_LOW_TO_HIGH,
		BANK_ORDER,
	}

	enum OutfitLayout
	{
		COLUMNS,
		ROWS,
	}

	enum HeaderSize
	{
		SMALL,
		LARGE,
	}

	@ConfigSection(name = "Layout", description = "How the sorted bank looks", position = 10)
	String layoutSection = "layout";

	@ConfigSection(name = "Categories", description = "Custom categories and their order", position = 20)
	String categoriesSection = "categories";

	@ConfigItem(
		keyName = "enabled",
		name = "Sort bank",
		description = "Show the bank grouped by category. Also toggleable by right-clicking the bank's Search button.",
		position = 0
	)
	default boolean enabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "itemOrder",
		name = "Order within category",
		description = "How items are ordered inside each category",
		position = 1
	)
	default ItemOrder itemOrder()
	{
		return ItemOrder.NAME;
	}

	@ConfigItem(
		keyName = "gearByQuality",
		name = "Rank combat gear",
		description = "In Melee, Ranged and Magic: weapons first, then armour sets kept together head to feet,"
			+ " best gear first. Overrides 'Order within category' for those categories.",
		position = 2
	)
	default boolean gearByQuality()
	{
		return true;
	}

	@ConfigItem(
		keyName = "smartConsumables",
		name = "Arrange food & potions",
		description = "Food by how much it heals, with combo food on its own rows. Potions in columns, one per potion,"
			+ " doses going down, combat potions first. Overrides 'Order within category' for those categories.",
		position = 3
	)
	default boolean smartConsumables()
	{
		return true;
	}

	@ConfigItem(
		keyName = "applyInTabs",
		name = "Sort inside bank tabs",
		description = "Also group items when a numbered bank tab is open, not just the all-items view",
		position = 3
	)
	default boolean applyInTabs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideFillers",
		name = "Hide bank fillers",
		description = "Don't show bank filler items",
		position = 4
	)
	default boolean hideFillers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "categoryTabs",
		name = "Category tabs",
		description = "Tabs down the left edge of the bank. Click one to show only its categories, again to show all.",
		position = -2,
		section = layoutSection
	)
	default boolean categoryTabs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "outfitLayout",
		name = "Outfit layout",
		description = "With 'Rank combat gear': Columns puts each outfit in a column read head to feet, best outfit"
			+ " leftmost. Rows puts each outfit on its own row, best on top.",
		position = -1,
		section = layoutSection
	)
	default OutfitLayout outfitLayout()
	{
		return OutfitLayout.COLUMNS;
	}

	@ConfigItem(
		keyName = "headerSize",
		name = "Header size",
		description = "Small headers fit more items on screen in fixed (classic) mode",
		position = 0,
		section = layoutSection
	)
	default HeaderSize headerSize()
	{
		return HeaderSize.SMALL;
	}

	@ConfigItem(
		keyName = "headerColor",
		name = "Header colour",
		description = "Colour of category header text",
		position = 1,
		section = layoutSection
	)
	default Color headerColor()
	{
		return new Color(0xFF981F);
	}

	@ConfigItem(
		keyName = "showCounts",
		name = "Show item counts",
		description = "Show how many items each category holds in its header",
		position = 2,
		section = layoutSection
	)
	default boolean showCounts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dividers",
		name = "Divider lines",
		description = "Draw a thin line under each category header",
		position = 3,
		section = layoutSection
	)
	default boolean dividers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "customRules",
		name = "Custom categories",
		description = "One per line, e.g. 'Slayer: slayer helm*, enchanted gem, 4155'. Item names (* = wildcard) or IDs."
			+ " The first matching line wins and overrides the built-in categories.",
		position = 0,
		section = categoriesSection
	)
	default String customRules()
	{
		return "";
	}

	@ConfigItem(
		keyName = "customSets",
		name = "Gear sets",
		description = "Extra armour sets to keep together in one outfit, one per line, e.g."
			+ " 'Graceful: graceful*'. Built in: sets with set effects (Moons, Barrows, Void, Obsidian, Justiciar,"
			+ " Inquisitor's, Virtus, Crystal, Bloodbark, Swampbark, Dagon'hai).",
		position = 2,
		section = categoriesSection
	)
	default String customSets()
	{
		return "";
	}

	@ConfigItem(
		keyName = "tabGroupsV3",
		name = "Tabs",
		description = "The bank tabs, one per line: 'Tab name: category, category'. Categories not in any tab"
			+ " only show in the full bank.",
		position = 3,
		section = categoriesSection
	)
	default String tabGroups()
	{
		return "Combat: Melee, Ranged, Magic, Slayer, Ammo\n"
			+ "Runes & teleports: Runes, Teleports\n"
			+ "Food & potions: Food, Potions\n"
			+ "Skilling: Mining, Smithing, Fishing, Cooking, Woodcutting, Firemaking, Fletching, Crafting, Herblore,"
			+ " Farming, Hunter, Construction, Agility, Thieving, Runecraft, Sailing, Prayer, Tools";
	}

	@ConfigItem(
		keyName = "categoryOrder",
		name = "Category order",
		description = "Category names, one per line, top to bottom. Unlisted categories follow in their default order."
			+ " Built-in: Currency, Melee, Ranged, Magic, Slayer, Ammo, Runes, Teleports, Potions, Food, Prayer,"
			+ " one per skill (Mining ... Sailing), Tools, Clues, Quest, Jewellery, Cosmetic, Other gear, Misc",
		position = 1,
		section = categoriesSection
	)
	default String categoryOrder()
	{
		return "";
	}

	@ConfigItem(
		keyName = COLLAPSED_KEY,
		name = "",
		description = "",
		hidden = true
	)
	default String collapsed()
	{
		return "";
	}
}
