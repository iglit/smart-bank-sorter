package com.smartbanksorter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ItemLookupTest
{
	private static ItemLookup.Row item(int id, String name)
	{
		return new ItemLookup.Row(id, name, name, "", "Melee", false);
	}

	private static final List<ItemLookup.Row> BANKED = Collections.singletonList(item(1, "Salve amulet(ei)"));
	private static final List<ItemLookup.Row> OTHERS = Collections.singletonList(item(2, "Salve amulet(i)"));

	@Test
	public void plainSearchOffersEveryNameContainingIt()
	{
		ItemLookup.Row row = ItemLookup.patternRow("salve amulet", BANKED, OTHERS);
		assertEquals("*salve amulet*", row.pattern);
		assertEquals(1, row.itemId);
		assertEquals("All matches · 1 in your bank", row.detail);
		assertTrue(ItemLookup.isGroup(row));
	}

	@Test
	public void plainSearchForOneItemOffersNoGroup()
	{
		assertNull(ItemLookup.patternRow("salve amulet(ei)", BANKED, Collections.emptyList()));
	}

	@Test
	public void wildcardSearchIsAddedAsTyped()
	{
		ItemLookup.Row row = ItemLookup.patternRow("*salve amulet*", Collections.emptyList(), Collections.emptyList());
		assertEquals("*salve amulet*", row.pattern);
		assertEquals("All matches", row.detail);
	}

	@Test
	public void commasWouldSplitTheRule()
	{
		assertNull(ItemLookup.patternRow("a, b*", BANKED, OTHERS));
	}

	@Test
	public void dosesOfOneItemAreNotAGroup()
	{
		assertFalse(ItemLookup.isGroup(new ItemLookup.Row(1, "Prayer potion", "Prayer potion(*", "", null, true)));
		assertFalse(ItemLookup.isGroup(item(1, "Abyssal whip")));
		assertTrue(ItemLookup.isGroup(new ItemLookup.Row(1, "*visage", "*visage", "", null, true)));
	}

	@Test
	public void addingAPatternListsItInTheCategory()
	{
		CategorySettings s = new CategorySettings("", "", "", "", "");
		s.addItem("Bossing", "*salve amulet*");
		assertEquals("Bossing: *salve amulet*", s.rulesText());
		assertEquals(Arrays.asList("*salve amulet*"), s.items("Bossing"));
		assertEquals("Bossing", UserRules.parse(s.rulesText()).match(1, "Salve amulet(ei)"));
	}
}
