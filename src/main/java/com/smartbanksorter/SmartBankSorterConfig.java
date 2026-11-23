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
	String RULES_KEY = "customRules";
	String ALSO_SHOW_KEY = "alsoShow";
	String ORDER_KEY = "categoryOrder";
	String TABS_KEY = "tabGroupsV3";
	String COLLAPSED_KEY = "collapsed";
	String TAB_ORDER_KEY = "tabOrder";
	String OPEN_TAB_KEY = "openTab";
	// Per account: what RecentDeposits keeps.
	String DEPOSITS_KEY = "depositHistory";

	// 1.7.0 replaced NAME, VALUE_HIGH_TO_LOW and VALUE_LOW_TO_HIGH. RuneLite resets a saved value it can't read to the
	// default before the plugin starts, so those become STACK_GE_VALUE with no migration. Don't unset the key: the config
	// panel needs a stored value.
	enum ItemOrder
	{
		STACK_GE_VALUE("Stack GE value", false, false),
		ITEM_GE_VALUE("Item GE value", true, false),
		STACK_HA_VALUE("Stack HA value", false, true),
		ITEM_HA_VALUE("Item HA value", true, true),
		BANK_ORDER("Bank order", false, false);

		private final String label;
		// Ranks by the price of one item rather than the whole stack.
		final boolean perItem;
		// Ranks everything by HA value, ignoring GE prices.
		final boolean haOnly;

		ItemOrder(String label, boolean perItem, boolean haOnly)
		{
			this.label = label;
			this.perItem = perItem;
			this.haOnly = haOnly;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum RecentWindow
	{
		OFF("Off", 0),
		ONE_HOUR("Last hour", 1),
		SIX_HOURS("Last 6 hours", 6),
		ONE_DAY("Last 24 hours", 24),
		ONE_WEEK("Last 7 days", 24 * 7);

		private final String label;
		final long millis;

		RecentWindow(String label, int hours)
		{
			this.label = label;
			this.millis = hours * 3_600_000L;
		}

		// How long the history is kept, whatever is shown: changing to a longer window loses nothing.
		static long longest()
		{
			return ONE_WEEK.millis;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum RecentCounts
	{
		NET_GAIN("Only changes"),
		EVERY_DEPOSIT("All deposits");

		private final String label;

		RecentCounts(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum OutfitLayout
	{
		COLUMNS,
		ROWS,
	}

	enum CompactLayout
	{
		OFF("Off"),
		OUTFITS("Outfits"),
		EVERYTHING("Outfits and potions");

		private final String label;

		CompactLayout(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum HeaderSize
	{
		SMALL,
		LARGE,
	}

	@ConfigSection(name = "Recently deposited", description = "A category at the top of the bank with copies of what you"
		+ " deposited lately", position = 9)
	String recentSection = "recent";

	@ConfigSection(name = "Layout", description = "How the sorted bank looks", position = 10)
	String layoutSection = "layout";

	@ConfigSection(name = "Categories", description = "Your own categories, edited in the bank", position = 20)
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
		description = "Most valuable first, by the whole stack (value x quantity) or one item. GE value: items without a GE"
			+ " price (untradeables) go after the rest, by their HA value. HA value: everything by high alchemy value. Bank"
			+ " order: as in the bank's All items view.",
		position = 1
	)
	default ItemOrder itemOrder()
	{
		return ItemOrder.STACK_GE_VALUE;
	}

	@ConfigItem(
		keyName = "gearByQuality",
		name = "Rank combat gear",
		description = "In Melee, Ranged and Magic: each weapon with the gear that does the most damage with it,"
			+ " strongest first. Overrides 'Order within category' for those categories.",
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
			+ " doses going down, combat potions first. Teleports by how often they're used. Overrides 'Order within"
			+ " category' for those categories.",
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
		position = 4
	)
	default boolean applyInTabs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideFillers",
		name = "Hide bank fillers",
		description = "Don't show bank filler items",
		position = 5
	)
	default boolean hideFillers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hidePlaceholders",
		name = "Hide placeholders",
		description = "Don't show placeholders, only items you have. A category holding nothing but placeholders"
			+ " is hidden too.",
		position = 6
	)
	default boolean hidePlaceholders()
	{
		return false;
	}

	@ConfigItem(
		keyName = "categorySearch",
		name = "Category search",
		description = "While searching the bank, typing three or more letters of a category's name suggests it above"
			+ " the search box: press Enter or click to jump to it. Searching a category's full name also shows its items.",
		position = 7
	)
	default boolean categorySearch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "recentDeposits",
		name = "Show",
		description = "What you deposited in this time, newest first, as copies at the top of the bank. Copies need free"
			+ " bank slots, and these get only what the others leave.",
		position = 0,
		section = recentSection
	)
	default RecentWindow recentDeposits()
	{
		return RecentWindow.SIX_HOURS;
	}

	@ConfigItem(
		keyName = "recentCounts",
		name = "Filter",
		description = "Only changes: items you have more of than at the start of that time, so supplies you took out and"
			+ " put back don't show. All deposits: everything you deposited in that time, even if you have since taken"
			+ " as much or more out.",
		position = 1,
		section = recentSection
	)
	default RecentCounts recentCounts()
	{
		return RecentCounts.NET_GAIN;
	}

	@ConfigItem(
		keyName = "categoryTabs",
		name = "Category tabs",
		description = "Tabs down the left edge of the bank. Click one to show only its categories, again to show all.",
		position = -4,
		section = layoutSection
	)
	default boolean categoryTabs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "outfitLayout",
		name = "Outfit layout",
		description = "With 'Rank combat gear': Columns puts each outfit in a column under its weapon, read head to"
			+ " feet, best leftmost. Rows puts each outfit on its own row, best on top.",
		position = -3,
		section = layoutSection
	)
	default OutfitLayout outfitLayout()
	{
		return OutfitLayout.COLUMNS;
	}

	@ConfigItem(
		keyName = "compactLayout",
		name = "Compact layout",
		description = "Closes up the gaps, keeping the layout. Outfits: each outfit's gear stacks under its weapon."
			+ " Outfits and potions: each potion column also stacks straight down.",
		position = -2,
		section = layoutSection
	)
	default CompactLayout compactLayout()
	{
		return CompactLayout.OFF;
	}

	@ConfigItem(
		keyName = "bestOutfitsOnly",
		name = "Best outfits only",
		description = "Gives a weapon its own outfit only if at least half the outfit adds damage. Weapons whose"
			+ " outfits are mostly plain armour go with the spare weapons, and that armour with the spare gear.",
		position = -1,
		section = layoutSection
	)
	default boolean bestOutfitsOnly()
	{
		return false;
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
		keyName = "editButton",
		name = "Edit categories button",
		description = "A filing cabinet button at the top of the bank that opens the category editor",
		position = 0,
		section = categoriesSection
	)
	default boolean editButton()
	{
		return true;
	}

	// The category editor's settings, kept as text. Hidden: the editor is the way to change them.
	@ConfigItem(
		keyName = RULES_KEY,
		name = "Custom categories",
		description = "One per line, e.g. 'Slayer: slayer helm*, enchanted gem, 4155'. Item names (* = wildcard) or IDs."
			+ " The first matching line wins and overrides the built-in categories.",
		position = 0,
		section = categoriesSection,
		hidden = true
	)
	default String customRules()
	{
		return "";
	}

	// Items shown in a category as well as where they'd be anyway, as copies: "Category: item, item", like the rules.
	@ConfigItem(
		keyName = ALSO_SHOW_KEY,
		name = "Added items",
		description = "One per line, e.g. 'Bossing: *salve amulet*'. Items stay where they are and are added to the"
			+ " category too.",
		position = 1,
		section = categoriesSection,
		hidden = true
	)
	default String alsoShow()
	{
		return "";
	}

	@ConfigItem(
		keyName = "customSets",
		name = "Gear sets",
		description = "Extra armour sets to keep together in one outfit, one per line, e.g."
			+ " 'Graceful: graceful*'. Built in: sets with set effects (Moons, Barrows, Void, Obsidian, Justiciar,"
			+ " Inquisitor's, Virtus, Crystal, Bloodbark, Swampbark, Dagon'hai).",
		position = 4,
		section = layoutSection
	)
	default String customSets()
	{
		return "";
	}

	@ConfigItem(
		keyName = TABS_KEY,
		name = "Tabs",
		description = "The bank tabs, one per line: 'Tab name: category, category'. Categories not in any tab"
			+ " only show in the full bank.",
		position = 3,
		section = categoriesSection,
		hidden = true
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
		keyName = ORDER_KEY,
		name = "Category order",
		description = "Category names, one per line, top to bottom. Unlisted categories follow in their default order."
			+ " Built-in: Currency, Melee, Ranged, Magic, Slayer, Ammo, Runes, Teleports, Potions, Food, Prayer,"
			+ " one per skill (Mining ... Sailing), Tools, Clues, Quest, Jewellery, Cosmetic, Other gear, Misc",
		position = 1,
		section = categoriesSection,
		hidden = true
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

	// The order of the tag tabs and category tabs together in Bank Tags' strip, as last arranged.
	@ConfigItem(
		keyName = TAB_ORDER_KEY,
		name = "",
		description = "",
		hidden = true
	)
	default String tabOrder()
	{
		return "";
	}

	// The category tab that was open when the bank closed, reopened with it when Bank Tags remembers its last tab.
	@ConfigItem(
		keyName = OPEN_TAB_KEY,
		name = "",
		description = "",
		hidden = true
	)
	default String openTab()
	{
		return "";
	}
}
