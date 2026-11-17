package com.smartbanksorter;

import java.util.HashMap;
import java.util.Map;

/**
 * Bundled weapon data from the OSRS Wiki. One line per weapon:
 * {@code Weapon name|Skill level, Skill level|Tier}, e.g. {@code Scythe of Vitur|Attack 80|100}. The tier is where
 * the wiki's gear progression guides use the weapon (the stage's level, 99 endgame, 100 megarare); weapons the guides
 * leave out have none.
 */
final class WeaponLevels
{
	private static final class Entry
	{
		final Map<String, Integer> levels = new HashMap<>();
		int tier;
	}

	private static final Map<String, Entry> WEAPONS = parse(Resources.read("weapons.txt"));

	private WeaponLevels()
	{
	}

	/**
	 * Reads the bundled data if it hasn't been already, off the client thread.
	 */
	static void load()
	{
		// Initialising the class is what reads it.
	}

	/**
	 * Level needed to wield the weapon in the skill of this combat style; 0 if it needs none or isn't listed.
	 */
	static int level(String name, String category)
	{
		Entry e = ItemData.lookup(WEAPONS, name);
		return e == null ? 0 : e.levels.getOrDefault(skill(category), 0);
	}

	/**
	 * How good the weapon is; higher is better. The wiki guides' tier comes first (the wield level stands in for
	 * weapons they leave out), then the wield level.
	 */
	static int rank(String name, String category)
	{
		Entry e = ItemData.lookup(WEAPONS, name);
		int level = level(name, category);
		int tier = e != null && e.tier > 0 ? e.tier : level;
		return tier * 1000 + level;
	}

	private static String skill(String category)
	{
		switch (category)
		{
			case Categories.RANGED:
				return "ranged";
			case Categories.MAGIC:
				return "magic";
			default:
				return "attack";
		}
	}

	private static Map<String, Entry> parse(String text)
	{
		Map<String, Entry> weapons = new HashMap<>();
		for (String line : text.split("\\R"))
		{
			String[] parts = line.split("\\|", -1);
			if (line.startsWith("#") || parts.length < 2 || parts[0].trim().isEmpty())
			{
				continue;
			}
			Entry entry = new Entry();
			for (String requirement : parts[1].split(","))
			{
				String[] skillLevel = requirement.trim().split("\\s+");
				if (skillLevel.length == 2)
				{
					entry.levels.put(skillLevel[0].toLowerCase(), parseInt(skillLevel[1]));
				}
			}
			if (parts.length > 2)
			{
				entry.tier = parseInt(parts[2].trim());
			}
			weapons.put(parts[0].trim().toLowerCase(), entry);
		}
		return weapons;
	}

	private static int parseInt(String s)
	{
		try
		{
			return Integer.parseInt(s);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
}
