package com.smartbanksorter;

import static org.junit.Assert.assertEquals;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.client.plugins.banktags.tabs.TagTab;
import org.junit.Test;

/**
 * How the saved order of tag tabs and category tabs is put back together with what Bank Tags has loaded.
 */
public class BankTagsStripTest
{
	@Test
	public void categoryTabsGoBackWhereTheyWere()
	{
		List<TagTab> tags = tabs("bossing", "slayer");
		List<TagTab> categories = tabs("Combat", "Skilling");
		assertEquals(Arrays.asList("Combat", "bossing", "Skilling", "slayer"),
			names(BankTagsStrip.merge(Arrays.asList("Combat", "bossing", "Skilling", "slayer"), tags, categories)));
	}

	@Test
	public void unknownTabsGoLastTagTabsFirst()
	{
		List<TagTab> tags = tabs("bossing", "zulrah");
		List<TagTab> categories = tabs("Combat", "Skilling");
		assertEquals(Arrays.asList("bossing", "Combat", "zulrah", "Skilling"),
			names(BankTagsStrip.merge(Arrays.asList("bossing", "Combat"), tags, categories)));
	}

	@Test
	public void withNoOrderTheCategoryTabsFollowTheTagTabs()
	{
		assertEquals(Arrays.asList("bossing", "slayer", "Combat", "Skilling"),
			names(BankTagsStrip.merge(Collections.emptyList(), tabs("bossing", "slayer"), tabs("Combat", "Skilling"))));
	}

	@Test
	public void tagTabsKeepBankTagsOrderWhenItChangedMeanwhile()
	{
		// Reordered while the sorter was off: Bank Tags' order wins, and Combat still comes after bossing.
		List<TagTab> tags = tabs("slayer", "bossing");
		assertEquals(Arrays.asList("slayer", "bossing", "Combat"),
			names(BankTagsStrip.merge(Arrays.asList("bossing", "Combat", "slayer"), tags, tabs("Combat"))));
	}

	@Test
	public void deletedTabsAreSkipped()
	{
		// bossing and the Skilling tab are gone; Combat still comes before slayer.
		assertEquals(Arrays.asList("Combat", "slayer"),
			names(BankTagsStrip.merge(Arrays.asList("bossing", "Combat", "Skilling", "slayer"), tabs("slayer"), tabs("Combat"))));
	}

	@Test
	public void theInputListsAreNotChanged()
	{
		List<TagTab> tags = tabs("bossing");
		List<TagTab> categories = tabs("Combat");
		BankTagsStrip.merge(Arrays.asList("Combat", "bossing"), tags, categories);
		assertEquals(Arrays.asList("bossing"), names(tags));
		assertEquals(Arrays.asList("Combat"), names(categories));
	}

	private static List<TagTab> tabs(String... names)
	{
		List<TagTab> tabs = new ArrayList<>();
		for (String name : names)
		{
			TagTab tab = new TagTab();
			tab.setTag(name);
			tabs.add(tab);
		}
		return tabs;
	}

	private static List<String> names(List<TagTab> tabs)
	{
		return tabs.stream().map(TagTab::getTag).collect(Collectors.toList());
	}
}
