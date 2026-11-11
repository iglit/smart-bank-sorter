package com.smartbanksorter;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class DosesTest
{
	@Test
	public void dosesAndCharges()
	{
		assertEquals("Prayer potion", Doses.baseName("Prayer potion(4)"));
		assertEquals(4, Doses.dose("Prayer potion(4)"));
		assertEquals("Slayer ring", Doses.baseName("Slayer ring (8)"));
		assertEquals(8, Doses.dose("Slayer ring (8)"));
	}

	@Test
	public void otherNamesAreLeftAlone()
	{
		assertEquals("Slayer helmet (i)", Doses.baseName("Slayer helmet (i)"));
		assertEquals(-1, Doses.dose("Slayer helmet (i)"));
		assertEquals(-1, Doses.dose("Amulet of glory"));
	}
}
