package com.banksorter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Layouts for Food (best heal first, combo food on its own rows) and Potions (one column per potion,
 * doses going down, combat potions first).
 */
final class ConsumableOrder
{
	private static final Pattern DOSE = Pattern.compile("^(.*?)\\s*\\((\\d+)\\)$");
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
	 * @return rows of cells; each block of up to {@code columns} potions has one row per dose, highest first,
	 * with null where a potion lacks that dose. Undosed drinks follow.
	 */
	static <T> List<List<T>> potions(List<T> items, Function<T, String> name, Comparator<T> tiebreak, int columns)
	{
		Map<String, Map<Integer, T>> families = new LinkedHashMap<>();
		List<T> loose = new ArrayList<>();
		List<T> sorted = new ArrayList<>(items);
		sorted.sort(tiebreak);
		for (T item : sorted)
		{
			Matcher m = DOSE.matcher(name.apply(item));
			if (!m.matches())
			{
				loose.add(item);
				continue;
			}
			Map<Integer, T> doses = families.computeIfAbsent(m.group(1).toLowerCase(), k -> new LinkedHashMap<>());
			// A second item with the same name and dose (rare) can't share the cell.
			if (doses.putIfAbsent(Integer.parseInt(m.group(2)), item) != null)
			{
				loose.add(item);
			}
		}

		List<String> order = new ArrayList<>(families.keySet());
		order.sort(Comparator.comparingInt(ConsumableOrder::potionRank).thenComparing(Comparator.naturalOrder()));

		List<List<T>> rows = new ArrayList<>();
		for (int first = 0; first < order.size(); first += columns)
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
