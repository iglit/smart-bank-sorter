package com.smartbanksorter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.Map;
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
		// Unfinished items aren't variants: these would find Guthix balance(4) and Iron bolts.
		assertNull(ItemData.get("Guthix balance (unf)"));
		assertNull(ItemData.get("Iron bolts (unf)"));
	}

	@Test
	public void numberedEntriesCoverTheirSiblings()
	{
		Map<String, ItemData.Entry> items = ItemData.parse("Ardougne cloak 1|Teleports||3\nBlack mask (10)|Slayer||3\n"
			+ "Ring of dueling(8)|Teleports||2\nRing of dueling(1)|Teleports||3\nGames necklace|Misc||5\n"
			+ "Games necklace(8)|Teleports||2\nSaika's hood (broken)|Misc||5");
		assertEquals("Teleports", items.get("ardougne cloak").category);
		assertEquals("Slayer", items.get("black mask").category);
		// The first sibling listed wins; a listed base name wins over any sibling.
		assertEquals(2, items.get("ring of dueling").tier);
		assertEquals("Misc", items.get("games necklace").category);
		// "(broken)" isn't a number: the real hood mustn't follow the broken one.
		assertNull(items.get("saika's hood"));

		// Through lookup's suffix stripping, with the bundled data.
		assertEquals("Teleports", ItemData.get("Ardougne cloak 4").category);
		assertEquals("Slayer", ItemData.get("Black mask (5)").category);
		assertEquals("Slayer", ItemData.get("Black mask (i)").category);
		assertEquals("Mining", ItemData.get("Varrock armour 3").category);
	}
}
