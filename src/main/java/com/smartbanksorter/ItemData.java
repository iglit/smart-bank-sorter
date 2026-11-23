package com.smartbanksorter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bundled per-item data, classified from the OSRS Wiki. One line per item:
 * {@code Item name|Main category|Extra categories, comma separated}. Extra categories get withdrawable copies of
 * the item.
 */
final class ItemData
{
	static final class Entry
	{
		final String category;
		final List<String> extras;

		Entry(String category, List<String> extras)
		{
			this.category = category;
			this.extras = extras;
		}
	}

	// A trailing number alone: a charge, dose or level like "(10)" or " 1". Not "(i)", "(broken)" or "(uncharged)".
	// Before ITEMS, which parse() fills using it.
	private static final Pattern NUMBER = Pattern.compile("(\\s*\\(\\d+\\)|\\s+\\d+)$");

	private static final Map<String, Entry> ITEMS = parse(Resources.read("items.txt"));

	private ItemData()
	{
	}

	/**
	 * Reads the bundled data if it hasn't been already. Called at start-up, so that the first bank open doesn't
	 * read it on the client thread.
	 */
	static void load()
	{
		// Initialising the class is what reads it.
	}

	// Trailing variant markers: "(4)", "(i)", "(uncharged)", or a Barrows degrade level like " 75". Not "(unf)": an
	// unfinished potion or bolt isn't a variant of the finished one.
	private static final Pattern VARIANT = Pattern.compile("(\\s*\\((?!unf\\))[^()]*\\)|\\s+\\d+)$");

	/**
	 * Entry for this exact name, else for the name with variant suffixes stripped one at a time, so
	 * "Dharok's helm 75" and "Amulet of glory(4)" find "Dharok's helm" and "Amulet of glory".
	 */
	static Entry get(String name)
	{
		return lookup(ITEMS, name);
	}

	/**
	 * Value for this exact name (lower-case keys), else for the name with variant suffixes stripped one at a time.
	 */
	static <V> V lookup(Map<String, V> byName, String name)
	{
		String n = name.toLowerCase().trim();
		while (true)
		{
			V e = byName.get(n);
			if (e != null)
			{
				return e;
			}
			Matcher m = VARIANT.matcher(n);
			if (!m.find() || m.start() == 0)
			{
				return null;
			}
			n = n.substring(0, m.start()).trim();
		}
	}

	static Map<String, Entry> parse(String text)
	{
		Map<String, Entry> items = new HashMap<>();
		Map<String, Entry> bases = new HashMap<>();
		for (String line : text.split("\\R"))
		{
			String[] parts = line.split("\\|", -1);
			if (line.startsWith("#") || parts.length < 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty())
			{
				continue;
			}

			List<String> extras = new ArrayList<>();
			if (parts.length > 2)
			{
				for (String extra : parts[2].split(","))
				{
					if (!extra.trim().isEmpty() && !extra.trim().equals(parts[1].trim()))
					{
						extras.add(extra.trim());
					}
				}
			}
			String name = parts[0].trim().toLowerCase();
			Entry entry = new Entry(parts[1].trim(), Collections.unmodifiableList(extras));
			items.put(name, entry);
			Matcher m = NUMBER.matcher(name);
			if (m.find() && m.start() > 0)
			{
				bases.putIfAbsent(name.substring(0, m.start()).trim(), entry);
			}
		}
		// A numbered entry stands for its siblings: lookup strips "Ardougne cloak 3" and "Black mask (5)" to
		// "ardougne cloak" and "black mask", which find "Ardougne cloak 1" and "Black mask (10)" this way. The first
		// sibling listed wins, and a base name listed itself wins over all of them.
		bases.forEach(items::putIfAbsent);
		return items;
	}
}
