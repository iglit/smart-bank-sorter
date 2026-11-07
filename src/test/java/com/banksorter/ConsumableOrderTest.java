package com.banksorter;

import static org.junit.Assert.assertEquals;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.Test;

public class ConsumableOrderTest
{
	@Test
	public void foodByHealWithComboFoodSeparate()
	{
		Map<String, Integer> heal = new HashMap<>();
		heal.put("Shark", 20);
		heal.put("Manta ray", 22);
		heal.put("Lobster", 12);
		heal.put("Cooked karambwan", 18);
		heal.put("Toad batta", 8);
		heal.put("Halibut", 20);
		assertEquals(Arrays.asList(
			Arrays.asList("Manta ray", "Shark", "Lobster"),
			Arrays.asList("Halibut", "Cooked karambwan", "Toad batta")),
			ConsumableOrder.food(new ArrayList<>(heal.keySet()), Function.identity(), heal::get,
				Comparator.naturalOrder()));
	}

	@Test
	public void potionsInColumnsDosesDownCombatFirst()
	{
		List<String> pots = Arrays.asList("Stamina potion(1)", "Prayer potion(3)", "Super combat potion(2)",
			"Prayer potion(4)", "Super combat potion(4)", "Ranging potion(4)", "Agility potion(3)", "Jug of wine");
		assertEquals(Arrays.asList(
			Arrays.asList("Super combat potion(4)", "Ranging potion(4)", "Prayer potion(4)"),
			Arrays.asList(null, null, "Prayer potion(3)", null, "Agility potion(3)"),
			Arrays.asList("Super combat potion(2)"),
			Arrays.asList(null, null, null, "Stamina potion(1)"),
			Arrays.asList("Jug of wine")),
			ConsumableOrder.potions(new ArrayList<>(pots), Function.identity(), Comparator.naturalOrder(), 8));
	}

	@Test
	public void divineVariantsRankWithTheirBase()
	{
		assertEquals(0, ConsumableOrder.potionRank("divine super combat potion"));
		assertEquals(1, ConsumableOrder.potionRank("divine ranging potion"));
		assertEquals(2, ConsumableOrder.potionRank("saradomin brew"));
		assertEquals(3, ConsumableOrder.potionRank("super restore"));
		assertEquals(4, ConsumableOrder.potionRank("super combat potion"));
		assertEquals(5, ConsumableOrder.potionRank("ranging potion"));
	}
}
