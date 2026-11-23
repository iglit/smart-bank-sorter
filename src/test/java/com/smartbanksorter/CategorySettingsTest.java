package com.smartbanksorter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class CategorySettingsTest
{
	private static CategorySettings settings(String rules, String order, String tabs)
	{
		return new CategorySettings(rules, "", order, tabs, "");
	}

	@Test
	public void addedItemsGoFirstAndTakeOverTheSamePattern()
	{
		CategorySettings s = settings("# mine\nSlayer: slayer helm*, Enchanted gem\nBossing: *visage", "", "");
		s.addItem("Bossing", "enchanted gem");
		assertEquals("Bossing: enchanted gem\n# mine\nSlayer: slayer helm*\nBossing: *visage", s.rulesText());
		assertEquals(Arrays.asList("enchanted gem", "*visage"), s.items("Bossing"));
	}

	@Test
	public void removingTheLastItemOfALineDropsTheLine()
	{
		CategorySettings s = settings("Bossing: dragon defender\nSlayer: enchanted gem", "", "");
		s.removeItem("Bossing", "Dragon defender");
		assertEquals("Slayer: enchanted gem", s.rulesText());
	}

	@Test
	public void untouchedLinesKeepTheirFormatting()
	{
		CategorySettings s = settings("Slayer:enchanted gem ,  4155\n\nBossing: x", "", "");
		s.removeItem("Bossing", "x");
		assertEquals("Slayer:enchanted gem ,  4155\n", s.rulesText());
	}

	@Test
	public void newCategoriesGoToTheBottomOfTheFullOrder()
	{
		CategorySettings s = settings("", "", "");
		s.create("Bossing");
		assertEquals("Bossing", s.categories().get(s.categories().size() - 1));
		assertEquals(Categories.DEFAULT_ORDER.get(0), s.orderText().split("\n")[0]);
	}

	@Test
	public void orderIsOnlyWrittenOnceChanged()
	{
		CategorySettings s = settings("", "Food", "");
		s.addItem("Food", "shark");
		assertEquals("Food", s.orderText());
		s.move("Food", 0);
		assertEquals("Food", s.categories().get(0));
		assertEquals(s.categories(), Arrays.asList(s.orderText().split("\n")));
	}

	@Test
	public void renameFollowsTheCategoryEverywhere()
	{
		CategorySettings s = new CategorySettings("Bossing: x\nSlayer: y", "", "Bossing", "Combat: Melee, bossing", "Bossing,Food");
		s.rename("Bossing", "PvM");
		assertEquals("PvM: x\nSlayer: y", s.rulesText());
		assertEquals("PvM", s.categories().get(0));
		assertEquals("Combat: Melee, PvM", s.tabsText());
		assertEquals("Combat", s.tabOf("PvM"));
		assertEquals(Collections.emptyList(), s.items("Bossing"));
		assertEquals("PvM,Food", s.collapsedText());
	}

	@Test
	public void deleteRemovesTheCategoryAndItsRules()
	{
		CategorySettings s = new CategorySettings("Bossing: x\nSlayer: y", "", "Bossing", "Boss: Bossing\nCombat: Melee", "");
		s.delete("Bossing");
		assertEquals("Slayer: y", s.rulesText());
		assertEquals("Combat: Melee", s.tabsText());
		assertEquals(Categories.DEFAULT_ORDER, s.categories());
	}

	@Test
	public void setTabMovesBetweenTabsAndMakesNewOnes()
	{
		CategorySettings s = settings("", "", "Combat: Melee, Ranged\nSkilling: Mining");
		s.setTab("Ranged", "Skilling");
		assertEquals("Combat: Melee\nSkilling: Mining, Ranged", s.tabsText());
		s.setTab("Melee", "Fighting");
		assertEquals("Skilling: Mining, Ranged\nFighting: Melee", s.tabsText());
		s.setTab("Mining", null);
		assertNull(s.tabOf("Mining"));
		assertEquals(Arrays.asList("Skilling", "Fighting"), s.tabs());
	}

	@Test
	public void alsoShownItemsStayWhereTheyAre()
	{
		CategorySettings s = new CategorySettings("Melee: dragon defender", "", "", "", "");
		s.addAlsoShown("Bossing", "dragon defender");
		s.addAlsoShown("Raids", "dragon defender");
		s.addAlsoShown("Bossing", "*salve amulet*");
		assertEquals("Melee: dragon defender", s.rulesText());
		assertEquals("Bossing: dragon defender, *salve amulet*\nRaids: dragon defender", s.alsoShowText());
		assertEquals(Arrays.asList("dragon defender", "*salve amulet*"), s.alsoShown("Bossing"));
		List<String> reloaded = new CategorySettings(s.rulesText(), s.alsoShowText(), "", "", "").categories();
		assertEquals("Raids", reloaded.get(reloaded.size() - 1));
	}

	@Test
	public void movingAndAlsoShowingInOneCategorySwitchBetweenThem()
	{
		CategorySettings s = new CategorySettings("Bossing: dragon defender", "", "", "", "");
		s.addAlsoShown("Bossing", "dragon defender");
		assertEquals("", s.rulesText());
		assertEquals(Arrays.asList("dragon defender"), s.alsoShown("Bossing"));
		s.addItem("Bossing", "dragon defender");
		assertEquals("Bossing: dragon defender", s.rulesText());
		assertEquals("", s.alsoShowText());
	}

	@Test
	public void renameAndDeleteCoverAlsoShown()
	{
		CategorySettings s = new CategorySettings("", "Bossing: a\nRaids: b", "", "", "");
		s.rename("Bossing", "PvM");
		assertEquals("PvM: a\nRaids: b", s.alsoShowText());
		s.delete("Raids");
		assertEquals("PvM: a", s.alsoShowText());
	}

	@Test
	public void namesMustFitTheTextSyntax()
	{
		assertNull(CategorySettings.nameProblem("Bossing", Arrays.asList("Melee")));
		assertNotNull(CategorySettings.nameProblem("", Collections.emptyList()));
		assertNotNull(CategorySettings.nameProblem("A: b", Collections.emptyList()));
		assertNotNull(CategorySettings.nameProblem("a, b", Collections.emptyList()));
		assertNotNull(CategorySettings.nameProblem("melee", Arrays.asList("Melee")));
		// The plugin's own.
		assertNotNull(CategorySettings.nameProblem("recently Deposited", Collections.emptyList()));
	}

	@Test
	public void sameOrderAsThePluginBuilds()
	{
		assertEquals(Arrays.asList("Food", "Currency"),
			CategorySettings.order("Food,\nCurrency", UserRules.parse("Bossing: x"), UserRules.parse("")).subList(0, 2));
		List<String> order = CategorySettings.order("", UserRules.parse("Bossing: x"), UserRules.parse("Raids: y"));
		assertEquals(Arrays.asList("Bossing", "Raids"), order.subList(Categories.DEFAULT_ORDER.size(), order.size()));
	}
}
