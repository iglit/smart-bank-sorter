package com.banksorter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bundled per-item data, classified from the OSRS Wiki. One line per item:
 * {@code Item name|Main category|Extra categories, comma separated|Tier 1-5}. Extra categories get
 * withdrawable copies of the item; tier orders items within a category (1 = most used).
 */
final class ItemData
{
	static final int UNKNOWN_TIER = 6;

	static final class Entry
	{
		final String category;
		final List<String> extras;
		final int tier;

		Entry(String category, List<String> extras, int tier)
		{
			this.category = category;
			this.extras = extras;
			this.tier = tier;
		}
	}

	private static final Map<String, Entry> ITEMS = parse(readResource("items.txt"));

	private ItemData()
	{
	}

	static Entry get(String name)
	{
		return ITEMS.get(name.toLowerCase());
	}

	static int tier(String name)
	{
		Entry e = get(name);
		return e == null ? UNKNOWN_TIER : e.tier;
	}

	static Map<String, Entry> parse(String text)
	{
		Map<String, Entry> items = new HashMap<>();
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
			int tier = UNKNOWN_TIER;
			if (parts.length > 3)
			{
				try
				{
					tier = Integer.parseInt(parts[3].trim());
				}
				catch (NumberFormatException ignored)
				{
					// Leave as unknown.
				}
			}
			items.put(parts[0].trim().toLowerCase(), new Entry(parts[1].trim(), Collections.unmodifiableList(extras), tier));
		}
		return items;
	}

	private static String readResource(String name)
	{
		try (InputStream in = ItemData.class.getResourceAsStream(name))
		{
			return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			return "";
		}
	}
}
