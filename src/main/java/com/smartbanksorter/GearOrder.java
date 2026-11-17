package com.smartbanksorter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;

/**
 * Orders combat gear into outfits by damage. Each weapon gets an outfit: the pieces that give it the most
 * {@link DpsCalc damage} against a typical boss, from those the outfits before it left. Weapons go best first, by
 * the damage of their best outfit; ones the player can't wield yet go last. Pieces no outfit took follow, ranked by
 * what they'd add to the best outfit, then by stats. Without weapons, pieces rank by stats alone.
 * <p>
 * In column layout each outfit is one bank column read head to feet, under its weapon, so the first column is your
 * best setup. In row layout each outfit is one row instead (weapons, then shields, come first, to keep rows at 8).
 */
final class GearOrder
{
	/** What GearOrder needs to know about an item. */
	interface Gear<T>
	{
		ItemEquipmentStats stats(T item);

		String name(T item);

		/** GE price per item, the last tie-break. */
		long price(T item);

		/** {@link #upgradeRank} */
		int upgrade(T item);

		/** {@link WeaponLevels#rank} for this style */
		int weaponRank(T item);

		/** Level needed to wield it in this style's skill; 0 if none or unknown. */
		int wieldLevel(T item);

		/** Set-effect set name, or null. */
		String set(T item);
	}

	// Head-to-feet order of an outfit column.
	private static final EquipmentInventorySlot[] COLUMN_ORDER = {
		EquipmentInventorySlot.HEAD, EquipmentInventorySlot.CAPE, EquipmentInventorySlot.AMULET,
		EquipmentInventorySlot.BODY, EquipmentInventorySlot.SHIELD, EquipmentInventorySlot.LEGS,
		EquipmentInventorySlot.GLOVES, EquipmentInventorySlot.BOOTS, EquipmentInventorySlot.RING,
	};
	// Row layout: once tiers get this sparse, stop starting new rows and pack the rest.
	private static final int MIN_ROW_ITEMS = 3;

	private GearOrder()
	{
	}

	/**
	 * @param levels the player's levels, for damage and for which weapons they can wield
	 * @param columns lay outfits out as columns rather than rows
	 * @param hideEmpty weapons that get no gear go after the outfits instead of heading an empty one
	 * @param pack pack each row of gear left, with no gaps
	 * @return rows of cells, each starting on a new bank row; null cells are left empty
	 */
	static <T> List<List<T>> order(List<T> items, String category, Gear<T> gear, DpsCalc.Levels levels,
		Comparator<T> tiebreak, boolean columns, boolean hideEmpty, boolean pack, int bankColumns)
	{
		Comparator<T> byStats = Comparator.comparingInt((T t) -> score(category, gear.stats(t))).reversed();
		Comparator<T> byPrice = Comparator.comparingLong(gear::price).reversed();
		// Equal scores: the piece that hits harder wins.
		Comparator<T> byOffence = Comparator.comparingInt((T t) -> offence(category, gear.stats(t))).reversed();
		Comparator<T> upgradesFirst = Comparator.comparingInt(gear::upgrade).reversed();
		Comparator<T> byScore = byStats.thenComparing(byOffence).thenComparing(upgradesFirst).thenComparing(byPrice)
			.thenComparing(tiebreak);

		List<T> weapons = new ArrayList<>();
		List<T> blessings = new ArrayList<>();
		List<T> other = new ArrayList<>();
		Map<Integer, List<T>> bySlot = new HashMap<>();
		for (T item : items)
		{
			ItemEquipmentStats e = gear.stats(item);
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
		// Gear in slots outside an outfit (e.g. ammo moved here by a custom rule) goes at the end.
		for (Map.Entry<Integer, List<T>> entry : new ArrayList<>(bySlot.entrySet()))
		{
			if (!inOrder(COLUMN_ORDER, entry.getKey()))
			{
				other.addAll(0, entry.getValue());
				bySlot.remove(entry.getKey());
			}
		}
		for (List<T> slotItems : bySlot.values())
		{
			slotItems.sort(byScore);
		}
		blessings.sort(byStats.thenComparing(byPrice).thenComparing(tiebreak));
		// Ranged ammo goes with the launchers that fire it; blessings suit any weapon.
		boolean ranged = Categories.RANGED.equals(category);

		Outfits<T> outfits = new Outfits<>(category, gear, levels, bySlot, ranged ? blessings : Collections.emptyList());
		Map<Integer, List<T>> grid = new HashMap<>();
		if (!weapons.isEmpty())
		{
			// Weapons by the damage of their best outfit from everything owned; ones the player can't wield yet last.
			Map<T, Double> damage = new HashMap<>();
			for (T weapon : weapons)
			{
				damage.put(weapon, outfits.damage(weapon, outfits.best(weapon, Collections.emptySet())));
			}
			int skillLevel = skillLevel(category, levels);
			weapons.sort(Comparator.comparing((T w) -> gear.wieldLevel(w) > skillLevel)
				.thenComparing(Comparator.comparingDouble((T w) -> round(damage.get(w))).reversed())
				.thenComparing(Comparator.comparingInt(gear::weaponRank).reversed())
				.thenComparing(byStats).thenComparing(upgradesFirst).thenComparing(byPrice).thenComparing(tiebreak));

			// Each weapon, in that order, gets the best outfit from what's left.
			Set<T> used = new HashSet<>();
			for (int i = 0; i < weapons.size(); i++)
			{
				for (Map.Entry<Integer, T> piece : outfits.best(weapons.get(i), used).entrySet())
				{
					List<T> slotColumn = grid.computeIfAbsent(piece.getKey(), k -> new ArrayList<>());
					while (slotColumn.size() <= i)
					{
						slotColumn.add(null);
					}
					slotColumn.set(i, piece.getValue());
					used.add(piece.getValue());
				}
			}
			blessings.removeIf(used::contains);

			// The rest: by what they'd add to the best outfit, then by stats.
			Map<Integer, T> best = outfits.best(weapons.get(0), Collections.emptySet());
			Map<Integer, List<T>> rest = new HashMap<>();
			for (Map.Entry<Integer, List<T>> entry : bySlot.entrySet())
			{
				List<T> left = new ArrayList<>();
				for (T item : entry.getValue())
				{
					if (!used.contains(item))
					{
						left.add(item);
					}
				}
				Map<T, Double> added = new HashMap<>();
				for (T item : left)
				{
					Map<Integer, T> swapped = new HashMap<>(best);
					swapped.put(entry.getKey(), item);
					added.put(item, round(outfits.damage(weapons.get(0), swapped)));
				}
				left.sort(Comparator.comparingDouble((T t) -> added.get(t)).reversed().thenComparing(byScore));
				rest.put(entry.getKey(), left);
			}
			int first = weapons.size();
			for (Map.Entry<Integer, List<T>> entry : assignTiers(rest, COLUMN_ORDER, gear::set).entrySet())
			{
				List<T> slotColumn = grid.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
				List<T> tiered = entry.getValue();
				for (int i = 0; i < tiered.size(); i++)
				{
					while (slotColumn.size() <= first + i)
					{
						slotColumn.add(null);
					}
					slotColumn.set(first + i, tiered.get(i));
				}
			}
		}
		else
		{
			grid = assignTiers(bySlot, COLUMN_ORDER, gear::set);
		}

		// Weapons whose outfit got nothing: out of the outfits, and their empty columns closed up.
		List<T> spareWeapons = new ArrayList<>();
		if (hideEmpty)
		{
			for (int i = weapons.size() - 1; i >= 0; i--)
			{
				int column = i;
				if (grid.values().stream().noneMatch(c -> column < c.size() && c.get(column) != null))
				{
					spareWeapons.add(0, weapons.remove(i));
					for (List<T> slotColumn : grid.values())
					{
						if (column < slotColumn.size())
						{
							slotColumn.remove(column);
						}
					}
				}
			}
		}

		List<List<T>> rows = new ArrayList<>();
		int tiers = weapons.size();
		for (List<T> slotColumn : grid.values())
		{
			tiers = Math.max(tiers, slotColumn.size());
		}

		if (columns)
		{
			// Blocks of up to bankColumns outfits side by side, each column under its weapon and the ammo it fires.
			// Blessings follow the first row of weapons; spare ammo follows the outfits.
			if (weapons.isEmpty() && !blessings.isEmpty())
			{
				rows.add(blessings);
			}
			for (int first = 0; first < tiers; first += bankColumns)
			{
				int last = Math.min(tiers, first + bankColumns);
				if (first < weapons.size())
				{
					rows.add(new ArrayList<>(weapons.subList(first, Math.min(weapons.size(), last))));
					if (first == 0 && !ranged && !blessings.isEmpty())
					{
						rows.add(blessings);
					}
				}
				if (pack)
				{
					// Each column's ammo and gear stacked straight under its weapon, no gaps.
					List<List<T>> stacks = new ArrayList<>();
					int height = 0;
					for (int tier = first; tier < last; tier++)
					{
						List<T> stack = outfit(grid, tier);
						stacks.add(stack);
						height = Math.max(height, stack.size());
					}
					for (int r = 0; r < height; r++)
					{
						List<T> row = new ArrayList<>();
						for (List<T> stack : stacks)
						{
							row.add(r < stack.size() ? stack.get(r) : null);
						}
						rows.add(trimmed(row));
					}
				}
				else
				{
					// One row per slot, so each piece lines up with the same slot of the other outfits.
					List<EquipmentInventorySlot> slots = new ArrayList<>();
					slots.add(EquipmentInventorySlot.AMMO);
					slots.addAll(Arrays.asList(COLUMN_ORDER));
					for (EquipmentInventorySlot slot : slots)
					{
						List<T> slotColumn = grid.getOrDefault(slot.getSlotIdx(), Collections.emptyList());
						List<T> row = new ArrayList<>();
						for (int tier = first; tier < last && tier < slotColumn.size(); tier++)
						{
							row.add(slotColumn.get(tier));
						}
						if (row.stream().anyMatch(Objects::nonNull))
						{
							rows.add(row);
						}
					}
				}
			}
			if (!spareWeapons.isEmpty())
			{
				rows.add(spareWeapons);
			}
			if (ranged && !weapons.isEmpty() && !blessings.isEmpty())
			{
				rows.add(blessings);
			}
			if (!other.isEmpty())
			{
				rows.add(other);
			}
			return rows;
		}

		// Row layout: one outfit per row, its weapon first, then its ammo and gear head to feet. A full outfit is
		// longer than a bank row, so it carries on in the next. Gear no weapon took, blessings and spare ammo follow.
		if (weapons.isEmpty())
		{
			// No weapons: shields and blessings first, so each outfit fits in one row.
			List<T> hands = new ArrayList<>(blessings);
			for (T shield : grid.getOrDefault(EquipmentInventorySlot.SHIELD.getSlotIdx(), Collections.emptyList()))
			{
				if (shield != null)
				{
					hands.add(shield);
				}
			}
			if (!hands.isEmpty())
			{
				rows.add(hands);
			}
			grid.remove(EquipmentInventorySlot.SHIELD.getSlotIdx());
			blessings.clear();
		}
		List<T> tail = new ArrayList<>();
		for (int tier = 0; tier < tiers; tier++)
		{
			List<T> row = outfit(grid, tier);
			if (tier < weapons.size())
			{
				row.add(0, weapons.get(tier));
				for (int i = 0; i < row.size(); i += bankColumns)
				{
					rows.add(new ArrayList<>(row.subList(i, Math.min(row.size(), i + bankColumns))));
				}
			}
			else if (tail.isEmpty() && row.size() >= MIN_ROW_ITEMS)
			{
				rows.add(row);
			}
			else
			{
				tail.addAll(row);
			}
		}
		tail.addAll(spareWeapons);
		tail.addAll(blessings);
		tail.addAll(other);
		if (!tail.isEmpty())
		{
			rows.add(tail);
		}
		return rows;
	}

	/** An outfit's ammo and gear, head to feet, without gaps. */
	private static <T> List<T> outfit(Map<Integer, List<T>> grid, int tier)
	{
		List<T> pieces = new ArrayList<>();
		List<EquipmentInventorySlot> slots = new ArrayList<>();
		slots.add(EquipmentInventorySlot.AMMO);
		slots.addAll(Arrays.asList(COLUMN_ORDER));
		for (EquipmentInventorySlot slot : slots)
		{
			List<T> slotColumn = grid.get(slot.getSlotIdx());
			if (slotColumn != null && tier < slotColumn.size() && slotColumn.get(tier) != null)
			{
				pieces.add(slotColumn.get(tier));
			}
		}
		return pieces;
	}

	private static <T> List<T> trimmed(List<T> row)
	{
		int end = row.size();
		while (end > 0 && row.get(end - 1) == null)
		{
			end--;
		}
		return new ArrayList<>(row.subList(0, end));
	}

	/**
	 * Finds each weapon's best outfit: per slot, the piece that adds the most damage, in two passes so pieces that
	 * work together (accuracy and strength) settle. Pieces that add no damage are chosen by stats, so defence and
	 * prayer still fill empty slots.
	 * <p>
	 * Owned sets (two or more pieces of one {@link Gear#set}) are never split: a set goes into an outfit whole, its
	 * best piece per slot, or not at all. Weapons pick in order of strength, so a set goes to the strongest weapon
	 * it does the most for (Blue moon armour with the eye of Ayak), else its own weapon (Dharok's greataxe) wears it.
	 * Ranged weapons also take the strongest owned ammo they can fire.
	 */
	private static final class Outfits<T>
	{
		private final String category;
		private final Gear<T> gear;
		private final DpsCalc.Levels levels;
		private final Map<Integer, List<T>> bySlot;
		private final Map<T, DpsCalc.Piece> pieces = new HashMap<>();
		// Owned sets, members best first.
		private final Map<String, List<T>> sets = new LinkedHashMap<>();
		// Ranged ammo, best first.
		private final List<T> ammo;
		// Per slot, the pieces sorted by each stat damage depends on, then the pieces with modelled effects.
		private final Map<Integer, List<List<T>>> byKey = new HashMap<>();
		private static final int SHORTLIST = 6;

		Outfits(String category, Gear<T> gear, DpsCalc.Levels levels, Map<Integer, List<T>> bySlot, List<T> ammo)
		{
			this.category = category;
			this.gear = gear;
			this.levels = levels;
			this.bySlot = bySlot;
			this.ammo = ammo;
			for (EquipmentInventorySlot slot : COLUMN_ORDER)
			{
				for (T item : bySlot.getOrDefault(slot.getSlotIdx(), Collections.emptyList()))
				{
					String set = gear.set(item);
					if (set != null)
					{
						sets.computeIfAbsent(set, k -> new ArrayList<>()).add(item);
					}
				}
			}
			sets.values().removeIf(members -> members.size() < 2);
			List<ToIntFunction<ItemEquipmentStats>> keys = Arrays.asList(ItemEquipmentStats::getAstab,
				ItemEquipmentStats::getAslash, ItemEquipmentStats::getAcrush, ItemEquipmentStats::getArange,
				ItemEquipmentStats::getAmagic, ItemEquipmentStats::getStr, ItemEquipmentStats::getRstr,
				e -> Math.round(e.getMdmg() * 10), e -> score(category, e));
			for (Map.Entry<Integer, List<T>> entry : bySlot.entrySet())
			{
				List<List<T>> lists = new ArrayList<>();
				for (ToIntFunction<ItemEquipmentStats> key : keys)
				{
					List<T> sorted = new ArrayList<>();
					for (T item : entry.getValue())
					{
						if (!inOwnedSet(item))
						{
							sorted.add(item);
						}
					}
					sorted.sort(Comparator.comparingInt((T t) -> key.applyAsInt(gear.stats(t))).reversed());
					lists.add(sorted);
				}
				List<T> effects = new ArrayList<>();
				for (T item : entry.getValue())
				{
					String name = gear.name(item).toLowerCase();
					if (!inOwnedSet(item) && (name.startsWith("crystal") || name.contains("void")
						|| name.startsWith("confliction") || name.startsWith("inquisitor's")))
					{
						effects.add(item);
					}
				}
				lists.add(effects);
				byKey.put(entry.getKey(), lists);
			}
		}

		/**
		 * The unused pieces of a slot worth trying: the best few still unused by each stat damage depends on, the
		 * best few by stats overall, and pieces with an effect DpsCalc models. Keeps the search fast in big banks;
		 * an unused piece outside every list is beaten on every stat that matters by one that's in it.
		 */
		private Set<T> candidates(int slot, Set<T> used)
		{
			Set<T> keep = new LinkedHashSet<>();
			List<List<T>> lists = byKey.getOrDefault(slot, Collections.emptyList());
			for (int k = 0; k < lists.size(); k++)
			{
				boolean effects = k == lists.size() - 1;
				int taken = 0;
				for (T item : lists.get(k))
				{
					if (!effects && taken >= SHORTLIST)
					{
						break;
					}
					if (!used.contains(item))
					{
						keep.add(item);
						taken++;
					}
				}
			}
			return keep;
		}

		/** The best outfit for the weapon from pieces not in {@code used}, by slot. */
		Map<Integer, T> best(T weapon, Set<T> used)
		{
			Map<Integer, T> base = new HashMap<>();
			for (T a : ammo)
			{
				if (!used.contains(a) && DpsCalc.fires(gear.name(weapon), gear.name(a)))
				{
					base.put(EquipmentInventorySlot.AMMO.getSlotIdx(), a);
					break;
				}
			}
			String own = gear.set(weapon);
			if (own != null && sets.containsKey(own))
			{
				Map<Integer, T> set = block(own, weapon, used);
				if (set != null)
				{
					set.putAll(base);
					return fill(weapon, set, used);
				}
			}
			Map<Integer, T> best = fill(weapon, base, used);
			double bestDamage = round(damage(weapon, best));
			for (String name : sets.keySet())
			{
				Map<Integer, T> set = block(name, weapon, used);
				if (set != null)
				{
					set.putAll(base);
					// Quick check: the set swapped into the current best outfit. Only search around it if it helps.
					Map<Integer, T> swapped = new HashMap<>(best);
					swapped.putAll(set);
					if (round(damage(weapon, swapped)) <= bestDamage)
					{
						continue;
					}
					Map<Integer, T> withSet = fill(weapon, set, used);
					double d = round(damage(weapon, withSet));
					if (d > bestDamage)
					{
						best = withSet;
						bestDamage = d;
					}
				}
			}
			return best;
		}

		double damage(T weapon, Map<Integer, T> outfit)
		{
			Map<Integer, DpsCalc.Piece> worn = new HashMap<>();
			worn.put(EquipmentInventorySlot.WEAPON.getSlotIdx(), piece(weapon));
			for (Map.Entry<Integer, T> e : outfit.entrySet())
			{
				worn.put(e.getKey(), piece(e.getValue()));
			}
			return DpsCalc.perTick(category, worn, levels);
		}

		/** The set's best unused piece per slot the weapon leaves free, or null if fewer than two are left. */
		private Map<Integer, T> block(String name, T weapon, Set<T> used)
		{
			boolean twoHanded = gear.stats(weapon).isTwoHanded();
			Map<Integer, T> set = new HashMap<>();
			for (T item : sets.get(name))
			{
				int slot = gear.stats(item).getSlot();
				if (!used.contains(item) && !(twoHanded && slot == EquipmentInventorySlot.SHIELD.getSlotIdx()))
				{
					set.putIfAbsent(slot, item);
				}
			}
			return set.size() >= 2 ? set : null;
		}

		private boolean inOwnedSet(T item)
		{
			String set = gear.set(item);
			return set != null && sets.containsKey(set);
		}

		/** Fills the free slots of {@code fixed} with the single pieces that add the most. */
		private Map<Integer, T> fill(T weapon, Map<Integer, T> fixed, Set<T> used)
		{
			Map<Integer, T> outfit = new HashMap<>(fixed);
			boolean twoHanded = gear.stats(weapon).isTwoHanded();
			for (int pass = 0; pass < 2; pass++)
			{
				for (EquipmentInventorySlot slot : COLUMN_ORDER)
				{
					int idx = slot.getSlotIdx();
					if (fixed.containsKey(idx) || twoHanded && slot == EquipmentInventorySlot.SHIELD)
					{
						continue;
					}
					outfit.remove(idx);
					double bestDamage = round(damage(weapon, outfit));
					T bestPiece = null;
					for (T item : candidates(idx, used))
					{
						if (used.contains(item) || inOwnedSet(item))
						{
							continue;
						}
						outfit.put(idx, item);
						double d = round(damage(weapon, outfit));
						// Same damage: the better stats win, and any piece beats an empty slot.
						if (d > bestDamage || d == bestDamage && (bestPiece == null
							|| score(category, gear.stats(item)) > score(category, gear.stats(bestPiece))))
						{
							bestDamage = d;
							bestPiece = item;
						}
					}
					if (bestPiece == null)
					{
						outfit.remove(idx);
					}
					else
					{
						outfit.put(idx, bestPiece);
					}
				}
			}
			return outfit;
		}

		private DpsCalc.Piece piece(T item)
		{
			return pieces.computeIfAbsent(item, i -> new DpsCalc.Piece(gear.name(i), gear.stats(i)));
		}
	}

	// Damage compared to 9 decimals, so float noise doesn't decide between equal pieces.
	private static double round(double damage)
	{
		return Math.round(damage * 1e9) / 1e9;
	}

	private static int skillLevel(String category, DpsCalc.Levels levels)
	{
		switch (category)
		{
			case Categories.RANGED:
				return levels.ranged;
			case Categories.MAGIC:
				return levels.magic;
			default:
				return levels.attack;
		}
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
	 * Breaks ties between pieces with the same stats; higher goes first. An untradeable piece with a tradeable
	 * one's stats is its upgrade (blood fury) or a locked or cosmetic copy. Max cape versions and the blessed quiver
	 * add perks to an untradeable cape with the same stats.
	 */
	static int upgradeRank(String name, boolean tradeable)
	{
		String lower = name.toLowerCase();
		if (lower.endsWith("max cape") || lower.startsWith("blessed dizana's quiver"))
		{
			return 2;
		}
		return tradeable ? 0 : 1;
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
