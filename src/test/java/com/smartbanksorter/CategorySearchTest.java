package com.smartbanksorter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class CategorySearchTest
{
	private static final List<String> ORDER = Arrays.asList("Melee", "Magic", "Herblore", "Crafting", "Misc",
		"Runes & teleports", "Other gear");

	@Test
	public void wholeCategoryName()
	{
		assertEquals("Crafting", CategorySearch.parse("crafting", ORDER));
		assertEquals("Crafting", CategorySearch.parse(" Crafting ", ORDER));
	}

	@Test
	public void partialNameIsAPlainSearch()
	{
		assertNull(CategorySearch.parse("craft", ORDER));
		assertNull(CategorySearch.parse("ranarr", ORDER));
		assertNull(CategorySearch.parse("", ORDER));
	}

	@Test
	public void suggestsFromThreeLetters()
	{
		assertNull(CategorySearch.suggest("cr", ORDER));
		assertEquals("Crafting", CategorySearch.suggest("cra", ORDER));
		assertEquals("Crafting", CategorySearch.suggest("Crafting", ORDER));
		assertNull(CategorySearch.suggest(null, ORDER));
		assertNull(CategorySearch.suggest("ranarr", ORDER));
	}

	@Test
	public void suggestsFirstInOrder()
	{
		assertEquals("Melee", CategorySearch.suggest("mel", ORDER));
		assertEquals("Magic", CategorySearch.suggest("mag", ORDER));
	}

	@Test
	public void suggestsFromLaterWords()
	{
		assertEquals("Other gear", CategorySearch.suggest("gea", ORDER));
		assertEquals("Runes & teleports", CategorySearch.suggest("tele", ORDER));
	}

	@Test
	public void nameStartBeatsLaterWord()
	{
		assertEquals("Runes & teleports", CategorySearch.suggest("run", Arrays.asList("Other runes", "Runes & teleports")));
	}
}
