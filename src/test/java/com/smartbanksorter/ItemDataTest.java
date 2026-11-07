package com.smartbanksorter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class ItemDataTest
{
	@Test
	public void variantsFallBackToBaseName()
	{
		// Bundled data has the base names of these.
		assertEquals("Slayer", ItemData.get("Slayer helmet (i)").category);
		assertEquals("Slayer", ItemData.get("Slayer helmet (i) (or)").category);
		assertNull(ItemData.get("Definitely not an item (4)"));
	}
}
