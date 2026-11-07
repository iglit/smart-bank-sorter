package com.banksorter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.Arrays;
import java.util.ArrayList;
import org.junit.Test;

public class UserRulesTest
{
	private final UserRules rules = UserRules.parse(
		"# comment\n"
			+ "Slayer: slayer helm*, Enchanted gem, 4155\n"
			+ "\n"
			+ "Boss loot: *visage, *jar of*\n"
			+ "Slayer: dragonfire shield\n"
			+ "not a rule\n");

	@Test
	public void matchesWildcardsCaseInsensitively()
	{
		assertEquals("Slayer", rules.match(1, "Slayer helmet (i)"));
		assertEquals("Slayer", rules.match(1, "enchanted gem"));
		assertEquals("Boss loot", rules.match(1, "Draconic visage"));
		assertEquals("Boss loot", rules.match(1, "Jar of dirt"));
	}

	@Test
	public void matchesItemIds()
	{
		assertEquals("Slayer", rules.match(4155, "Anything"));
	}

	@Test
	public void wildcardIsNotRegex()
	{
		assertNull(UserRules.parse("X: a.c").match(1, "abc"));
		assertNull(rules.match(1, "Visage of something"));
	}

	@Test
	public void categoriesKeepDeclarationOrderWithoutDuplicates()
	{
		assertEquals(Arrays.asList("Slayer", "Boss loot"), new ArrayList<>(rules.categories()));
	}
}
