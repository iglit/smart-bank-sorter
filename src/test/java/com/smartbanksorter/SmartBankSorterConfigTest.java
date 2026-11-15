package com.smartbanksorter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SmartBankSorterConfigTest
{
	private final SmartBankSorterConfig defaults = new SmartBankSorterConfig()
	{
	};

	@Test
	public void placeholdersShowByDefault()
	{
		// Existing users see no change: only fillers are hidden out of the box.
		assertFalse(defaults.hidePlaceholders());
		assertTrue(defaults.hideFillers());
	}
}
