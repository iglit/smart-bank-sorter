package com.smartbanksorter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Layouts for Food (best heal first, combo food on its own rows) and Potions (one column per potion,
 * doses going down, combat potions first).
 */
final class ConsumableOrder
{
	// Foods that can be eaten on the same tick as normal food.
	private static final List<String> COMBO_FOOD = Arrays.asList(
		"karambwan", "halibut", "batta", "crunchies", "chocolate bomb", "toad's legs", "worm hole", "veg ball", "tangled toad"
	);
	// Potion families in display order; matched by "name contains", most specific first. Raid essentials lead
	// (divine super combat, divine ranging, brews, restores), then other combat boosts, prayer, cures, utility.
	private static final List<String> POTION_ORDER = Arrays.asList(
		"divine super combat", "divine ranging", "saradomin brew", "super restore",
		"super combat", "ranging", "divine bastion", "bastion", "divine magic", "magic potion",
		"divine battlemage", "battlemage", "divine super attack", "super attack", "divine super strength",
		"super strength", "divine super defence", "super defence",
		"prayer potion", "prayer regeneration", "sanfew", "zamorak brew",
		"anti-venom+", "anti-venom", "antidote++", "antidote+", "superantipoison", "antipoison",
		"extended super antifire", "super antifire", "extended antifire", "antifire",
		"stamina", "super energy", "energy potion", "attack potion", "strength potion", "defence potion"
	);

	private ConsumableOrder()
	{
	}

	static <T> List<List<T>> food(List<T> items, Function<T, String> name, ToIntFunction<T> heal,
		Comparator<T> tiebreak)
	{
		List<T> normal = new ArrayList<>();
		List<T> combo = new ArrayList<>();
		for (T item : items)
		{
			(isCombo(name.apply(item)) ? combo : normal).add(item);
		}
		Comparator<T> order = Comparator.comparingInt(heal).reversed().thenComparing(tiebreak);
		normal.sort(order);
		combo.sort(order);

		List<List<T>> rows = new ArrayList<>();
		if (!normal.isEmpty())
		{
			rows.add(normal);
		}
		if (!combo.isEmpty())
		{
			rows.add(combo);
		}
		return rows;
	}

	static boolean isCombo(String name)
	{
		String n = name.toLowerCase();
		return COMBO_FOOD.stream().anyMatch(n::contains);
	}

	/**
	 * @param compact stack each column's potions straight down, each potion's doses highest first, with no gaps
	 * @return rows of cells; each block of up to {@code columns} potions has one row per dose, highest first,
	 * with null where a potion lacks that dose (unless compact). Undosed drinks follow.
	 */
	static <T> List<List<T>> potions(List<T> items, Function<T, String> name, Comparator<T> tiebreak, int columns,
		boolean compact)
	{
		Map<String, Map<Integer, T>> families = new LinkedHashMap<>();
		List<T> loose = new ArrayList<>();
		List<String> order = families(items, name, tiebreak, families, loose);

		List<List<T>> rows = new ArrayList<>();
		if (compact)
		{
			// Each column keeps the potions it has in the block layout, stacked straight down with no gaps between
			// blocks.
			List<List<T>> stacks = new ArrayList<>();
			for (int i = 0; i < order.size(); i++)
			{
				if (i < columns)
				{
					stacks.add(new ArrayList<>());
				}
				Map<Integer, T> byDose = new TreeMap<>(Comparator.reverseOrder());
				byDose.putAll(families.get(order.get(i)));
				stacks.get(i % columns).addAll(byDose.values());
			}
			int height = stacks.stream().mapToInt(List::size).max().orElse(0);
			for (int r = 0; r < height; r++)
			{
				List<T> row = new ArrayList<>();
				for (List<T> stack : stacks)
				{
					row.add(r < stack.size() ? stack.get(r) : null);
				}
				while (!row.isEmpty() && row.get(row.size() - 1) == null)
				{
					row.remove(row.size() - 1);
				}
				rows.add(row);
			}
		}
		for (int first = 0; !compact && first < order.size(); first += columns)
		{
			List<String> block = order.subList(first, Math.min(first + columns, order.size()));
			TreeSet<Integer> doses = new TreeSet<>(Comparator.reverseOrder());
			block.forEach(f -> doses.addAll(families.get(f).keySet()));
			for (int dose : doses)
			{
				List<T> row = new ArrayList<>();
				for (String family : block)
				{
					row.add(families.get(family).get(dose));
				}
				while (!row.isEmpty() && row.get(row.size() - 1) == null)
				{
					row.remove(row.size() - 1);
				}
				rows.add(row);
			}
		}
		if (!loose.isEmpty())
		{
			rows.add(loose);
		}
		return rows;
	}

	/**
	 * Sorts the items into potions by name, each a map of dose to item, and drinks without doses.
	 *
	 * @return the potions in layout order
	 */
	private static <T> List<String> families(List<T> items, Function<T, String> name, Comparator<T> tiebreak,
		Map<String, Map<Integer, T>> families, List<T> loose)
	{
		List<T> sorted = new ArrayList<>(items);
		sorted.sort(tiebreak);
		for (T item : sorted)
		{
			String itemName = name.apply(item);
			int dose = Doses.dose(itemName);
			if (dose < 0)
			{
				loose.add(item);
				continue;
			}
			Map<Integer, T> doses = families.computeIfAbsent(Doses.baseName(itemName).toLowerCase(), k -> new LinkedHashMap<>());
			// A second item with the same name and dose (rare) can't share the cell.
			if (doses.putIfAbsent(dose, item) != null)
			{
				loose.add(item);
			}
		}

		List<String> order = new ArrayList<>(families.keySet());
		order.sort(Comparator.comparingInt(ConsumableOrder::potionRank).thenComparing(Comparator.naturalOrder()));
		return order;
	}

	static int potionRank(String family)
	{
		for (int i = 0; i < POTION_ORDER.size(); i++)
		{
			if (family.contains(POTION_ORDER.get(i)))
			{
				return i;
			}
		}
		return POTION_ORDER.size();
	}
}
