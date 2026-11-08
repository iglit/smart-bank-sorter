package com.smartbanksorter;

import java.util.List;

/**
 * Bank searches that name categories: a whole category name, e.g. "crafting", adds that category's items to the
 * game's own name matches, and a few letters of one suggest jumping to it.
 */
final class CategorySearch
{
	// Fewer letters than this match too many categories to be worth suggesting.
	static final int MIN_SUGGEST_LENGTH = 3;

	private CategorySearch()
	{
	}

	/**
	 * @return the category a search names in full, or null if it doesn't name one
	 */
	static String parse(String search, List<String> order)
	{
		String text = search.trim();
		for (String category : order)
		{
			if (category.equalsIgnoreCase(text))
			{
				return category;
			}
		}
		return null;
	}

	/**
	 * @return the first category whose name, or failing that one of its words, starts with the search; null if
	 * none does or the search is too short
	 */
	static String suggest(String search, List<String> order)
	{
		String text = search == null ? "" : search.trim().toLowerCase();
		if (text.length() < MIN_SUGGEST_LENGTH)
		{
			return null;
		}
		for (String category : order)
		{
			if (category.toLowerCase().startsWith(text))
			{
				return category;
			}
		}
		for (String category : order)
		{
			for (String word : category.toLowerCase().split("[^a-z0-9]+"))
			{
				if (word.startsWith(text))
				{
					return category;
				}
			}
		}
		return null;
	}
}
