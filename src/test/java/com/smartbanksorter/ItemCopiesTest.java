package com.smartbanksorter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ItemCopiesTest
{
	private final ItemCopies copies = new ItemCopies(s -> null);

	private static Map<String, List<Slot>> bankWith(Slot slot)
	{
		Map<String, List<Slot>> groups = new LinkedHashMap<>();
		groups.put(slot.category, new ArrayList<>());
		groups.get(slot.category).add(slot);
		return groups;
	}

	@Test
	public void copiesWhenStatsPickAnotherStyle()
	{
		// The data says Melee, but the item's stats put it in Ranged.
		Map<String, List<Slot>> groups = bankWith(new Slot(null, 0, 13072, 1, "Elite void robe", Categories.RANGED));
		copies.addDataCopies(groups, s -> false);
		assertEquals(1, groups.get(Categories.RANGED).size());
		assertTrue(groups.get(Categories.MAGIC).get(0).copy);
	}

	@Test
	public void ruleStopsCopies()
	{
		Map<String, List<Slot>> groups = bankWith(new Slot(null, 0, 13072, 1, "Elite void robe", "Void"));
		copies.addDataCopies(groups, s -> true);
		assertEquals(1, groups.size());
	}
}
