package com.smartbanksorter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;

/**
 * Orders combat gear by how close it is to your best-in-slot. Weapons come first, best first. Armour is
 * ranked within its slot; tier 0 is your best piece for each slot, tier 1 the next best, and so on.
 * <p>
 * In column layout each tier is one bank column read head to feet, so the first column is your best
 * outfit. In row layout each tier is one row instead (shields then follow the weapons, to keep rows at 8).
 */
final class GearOrder
{
	// Head-to-feet order of an outfit column.
	private static final EquipmentInventorySlot[] COLUMN_ORDER = {
		EquipmentInventorySlot.HEAD, EquipmentInventorySlot.CAPE, EquipmentInventorySlot.AMULET,
		EquipmentInventorySlot.BODY, EquipmentInventorySlot.SHIELD, EquipmentInventorySlot.LEGS,
		EquipmentInventorySlot.GLOVES, EquipmentInventorySlot.BOOTS, EquipmentInventorySlot.RING,
	};
	// Head-to-feet order of an outfit row; exactly 8 slots, one bank row.
	private static final EquipmentInventorySlot[] ROW_ORDER = {
		EquipmentInventorySlot.HEAD, EquipmentInventorySlot.CAPE, EquipmentInventorySlot.AMULET,
		EquipmentInventorySlot.BODY, EquipmentInventorySlot.LEGS, EquipmentInventorySlot.GLOVES,
		EquipmentInventorySlot.BOOTS, EquipmentInventorySlot.RING,
	};
	// Row layout: once tiers get this sparse, stop starting new rows and pack the rest.
	private static final int MIN_ROW_ITEMS = 3;

	private GearOrder()
	{
	}

	/**
	 * @param price GE price per item; weapons are ranked by it because their stats miss special effects
	 * @param set the item's set-effect set name, or null
	 * @param columns lay outfits out as columns rather than rows
	 * @return rows of cells, each starting on a new bank row; null cells are left empty
	 */
	static <T> List<List<T>> order(List<T> items, String category, Function<T, ItemEquipmentStats> stats,
		ToLongFunction<T> price, Function<T, String> set, Comparator<T> tiebreak, boolean columns, int bankColumns)
	{
		Comparator<T> byStats = Comparator.comparingInt((T t) -> score(category, stats.apply(t))).reversed();
		Comparator<T> byPrice = Comparator.comparingLong(price).reversed();

		List<T> weapons = new ArrayList<>();
		List<T> blessings = new ArrayList<>();
		List<T> other = new ArrayList<>();
		Map<Integer, List<T>> bySlot = new HashMap<>();
		for (T item : items)
		{
			ItemEquipmentStats e = stats.apply(item);
			if (e == null)
			{
				other.add(item);
			}
			else if (e.getSlot() == EquipmentInventorySlot.WEAPON.getSlotIdx())
			{
				weapons.add(item);
			}
			else if (e.getSlot() == EquipmentInventorySlot.AMMO.getSlotIdx())
			{
				blessings.add(item);
			}
			else
			{
				bySlot.computeIfAbsent(e.getSlot(), k -> new ArrayList<>()).add(item);
			}
		}
		for (List<T> slotItems : bySlot.values())
		{
			slotItems.sort(byStats.thenComparing(byPrice).thenComparing(tiebreak));
		}

		List<List<T>> rows = new ArrayList<>();
		// Tradeable weapons by price, then untradeables (price 0) by stats.
		weapons.sort(byPrice.thenComparing(byStats).thenComparing(tiebreak));
		blessings.sort(byStats.thenComparing(byPrice).thenComparing(tiebreak));
		List<T> hands = new ArrayList<>(weapons);
		hands.addAll(blessings);
		if (!columns)
		{
			List<T> shields = bySlot.remove(EquipmentInventorySlot.SHIELD.getSlotIdx());
			if (shields != null)
			{
				hands.addAll(shields);
			}
		}
		if (!hands.isEmpty())
		{
			rows.add(hands);
		}

		EquipmentInventorySlot[] order = columns ? COLUMN_ORDER : ROW_ORDER;
		// Gear in slots outside an outfit (e.g. ammo moved here by a custom rule) goes at the end.
		for (Map.Entry<Integer, List<T>> entry : bySlot.entrySet())
		{
			if (!inOrder(order, entry.getKey()))
			{
				other.addAll(0, entry.getValue());
			}
		}
		Map<Integer, List<T>> grid = assignTiers(bySlot, order, set);
		int tiers = 0;
		for (List<T> slotColumn : grid.values())
		{
			tiers = Math.max(tiers, slotColumn.size());
		}

		if (columns)
		{
			// Blocks of up to bankColumns outfits side by side.
			for (int first = 0; first < tiers; first += bankColumns)
			{
				for (EquipmentInventorySlot slot : COLUMN_ORDER)
				{
					List<T> slotColumn = grid.getOrDefault(slot.getSlotIdx(), Collections.emptyList());
					List<T> row = new ArrayList<>();
					for (int tier = first; tier < first + bankColumns && tier < slotColumn.size(); tier++)
					{
						row.add(slotColumn.get(tier));
					}
					if (!row.isEmpty())
					{
						rows.add(row);
					}
				}
			}
			if (!other.isEmpty())
			{
				rows.add(other);
			}
			return rows;
		}

		List<T> tail = new ArrayList<>();
		for (int tier = 0; tier < tiers; tier++)
		{
			List<T> row = new ArrayList<>();
			for (EquipmentInventorySlot slot : ROW_ORDER)
			{
				List<T> slotColumn = grid.get(slot.getSlotIdx());
				if (slotColumn != null && tier < slotColumn.size() && slotColumn.get(tier) != null)
				{
					row.add(slotColumn.get(tier));
				}
			}
			if (tail.isEmpty() && row.size() >= MIN_ROW_ITEMS)
			{
				rows.add(row);
			}
			else
			{
				tail.addAll(row);
			}
		}
		tail.addAll(other);
		if (!tail.isEmpty())
		{
			rows.add(tail);
		}
		return rows;
	}

	/**
	 * Gives every piece a tier (outfit index). Pieces are ranked within their slot, except that two or more
	 * owned pieces of a set (by {@code set}) share one tier: that of the set's best piece. A set wins ties
	 * with single pieces, since set pieces are only strong together.
	 *
	 * @param bySlot items per slot, best first
	 * @return per slot, the item at each tier; null where that outfit has nothing for the slot
	 */
	private static <T> Map<Integer, List<T>> assignTiers(Map<Integer, List<T>> bySlot, EquipmentInventorySlot[] order,
		Function<T, String> set)
	{
		final class Unit
		{
			final Map<Integer, T> pieces = new LinkedHashMap<>();
			int rank = Integer.MAX_VALUE;
		}

		Map<String, Unit> sets = new LinkedHashMap<>();
		List<Unit> units = new ArrayList<>();
		for (EquipmentInventorySlot slot : order)
		{
			List<T> slotItems = bySlot.getOrDefault(slot.getSlotIdx(), Collections.emptyList());
			for (int rank = 0; rank < slotItems.size(); rank++)
			{
				T item = slotItems.get(rank);
				String setName = set.apply(item);
				Unit unit = setName == null ? null : sets.computeIfAbsent(setName, k -> new Unit());
				// A second piece for a slot the set already fills (e.g. another void helm) stands alone.
				if (unit == null || unit.pieces.containsKey(slot.getSlotIdx()))
				{
					unit = new Unit();
					units.add(unit);
				}
				unit.pieces.put(slot.getSlotIdx(), item);
				unit.rank = Math.min(unit.rank, rank);
			}
		}
		units.addAll(sets.values());
		units.sort(Comparator.comparingInt((Unit u) -> u.rank).thenComparing(u -> u.pieces.size() < 2));

		Map<Integer, List<T>> grid = new HashMap<>();
		for (Unit unit : units)
		{
			int tier = 0;
			while (occupied(grid, unit.pieces.keySet(), tier))
			{
				tier++;
			}
			for (Map.Entry<Integer, T> piece : unit.pieces.entrySet())
			{
				List<T> slotColumn = grid.computeIfAbsent(piece.getKey(), k -> new ArrayList<>());
				while (slotColumn.size() <= tier)
				{
					slotColumn.add(null);
				}
				slotColumn.set(tier, piece.getValue());
			}
		}
		return grid;
	}

	private static <T> boolean occupied(Map<Integer, List<T>> grid, Iterable<Integer> slots, int tier)
	{
		for (int slot : slots)
		{
			List<T> slotColumn = grid.get(slot);
			if (slotColumn != null && tier < slotColumn.size() && slotColumn.get(tier) != null)
			{
				return true;
			}
		}
		return false;
	}

	private static boolean inOrder(EquipmentInventorySlot[] order, int slotIdx)
	{
		for (EquipmentInventorySlot slot : order)
		{
			if (slot.getSlotIdx() == slotIdx)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * How good a piece is for the given combat style. Damage bonuses dominate; defence breaks ties
	 * between pure-defence pieces (e.g. rune vs dragon platebody).
	 */
	static int score(String category, ItemEquipmentStats e)
	{
		if (e == null)
		{
			return 0;
		}
		// Scaled by 20 so small defence differences (lunar vs beacon ring) aren't lost to rounding:
		// offence + prayer + average defence / 4.
		int defenceSum = e.getDstab() + e.getDslash() + e.getDcrush() + e.getDmagic() + e.getDrange();
		int offence;
		switch (category)
		{
			case Categories.RANGED:
				offence = 60 * e.getRstr() + 20 * e.getArange();
				break;
			case Categories.MAGIC:
				offence = Math.round(60 * e.getMdmg()) + 20 * e.getAmagic();
				break;
			default:
				offence = 60 * e.getStr() + 20 * Math.max(e.getAstab(), Math.max(e.getAslash(), e.getAcrush()));
		}
		return offence + 20 * e.getPrayer() + defenceSum;
	}

	/**
	 * Offensive value for one style only; zero or less means the piece does nothing for that style.
	 */
	static int offence(String category, ItemEquipmentStats e)
	{
		switch (category)
		{
			case Categories.RANGED:
				return 3 * e.getRstr() + e.getArange();
			case Categories.MAGIC:
				return Math.round(3 * e.getMdmg()) + e.getAmagic();
			default:
				int attack = Math.max(e.getAstab(), Math.max(e.getAslash(), e.getAcrush()));
				return 3 * e.getStr() + attack;
		}
	}
}
