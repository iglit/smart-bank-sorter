package com.smartbanksorter;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.Test;

public class TeleportOrderTest
{
	@Test
	public void mostUsedFirstChargesDescending()
	{
		assertEquals(Arrays.asList(
			"Amulet of glory(6)", "Amulet of glory(2)", "Ring of dueling(8)", "Games necklace(3)",
			"Varrock teleport", "Zul-andra teleport", "Ancient shard"),
			TeleportOrder.order(Arrays.asList("Ancient shard", "Games necklace(3)", "Zul-andra teleport",
				"Amulet of glory(2)", "Varrock teleport", "Ring of dueling(8)", "Amulet of glory(6)"),
				Function.identity(), Comparator.naturalOrder()));
	}
}
