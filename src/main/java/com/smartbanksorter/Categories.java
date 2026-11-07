package com.smartbanksorter;

import java.util.Arrays;
import java.util.List;

/**
 * Built-in category names, in their default display order.
 */
final class Categories
{
	static final String CURRENCY = "Currency";
	static final String MELEE = "Melee";
	static final String RANGED = "Ranged";
	static final String MAGIC = "Magic";
	static final String SLAYER = "Slayer";
	static final String JEWELLERY = "Jewellery";
	static final String AMMO = "Ammo";
	static final String RUNES = "Runes";
	static final String TELEPORTS = "Teleports";
	static final String POTIONS = "Potions";
	static final String FOOD = "Food";
	static final String PRAYER = "Prayer";
	static final String TOOLS = "Tools";
	static final String HERBLORE = "Herblore";
	static final String FARMING = "Farming";
	// Materials go to the skill that uses them.
	static final String ORES_BARS = "Smithing";
	static final String GEMS = "Crafting";
	static final String LOGS_PLANKS = "Fletching";
	static final String CLUES = "Clues";
	static final String QUEST = "Quest";
	static final String COSMETIC = "Cosmetic";
	static final String OTHER_GEAR = "Other gear";
	static final String MISC = "Misc";

	static final List<String> SKILLS = Arrays.asList(
		"Mining", "Smithing", "Fishing", "Cooking", "Woodcutting", "Firemaking", "Fletching", "Crafting",
		HERBLORE, FARMING, "Hunter", "Construction", "Agility", "Thieving", "Runecraft", "Sailing"
	);

	static final List<String> DEFAULT_ORDER = Arrays.asList(
		CURRENCY, MELEE, RANGED, MAGIC, SLAYER, AMMO, RUNES, TELEPORTS, POTIONS, FOOD, PRAYER,
		"Mining", "Smithing", "Fishing", "Cooking", "Woodcutting", "Firemaking", "Fletching", "Crafting",
		HERBLORE, FARMING, "Hunter", "Construction", "Agility", "Thieving", "Runecraft", "Sailing",
		// Jewellery left here has no combat or teleport use (those go to the styles / Teleports).
		TOOLS, CLUES, QUEST, JEWELLERY, COSMETIC, OTHER_GEAR, MISC
	);

	private Categories()
	{
	}
}
