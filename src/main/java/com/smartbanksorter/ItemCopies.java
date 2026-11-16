package com.smartbanksorter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemEquipmentStats;

class ItemCopies
{
	private final Function<Slot, ItemEquipmentStats> equipmentOf;
	// Spare empty-slot widgets the bank interface already has past the last item. Copies reuse these, like
	// core Bank Tags layouts do for duplicates: the game treats them as real bank slots, so it builds their
	// menus itself, and onMenuOptionClicked points the click at the real item's slot.
	private List<Widget> spares = new ArrayList<>();
	// Copy widget's child index -> the real item's bank slot index.
	private final Map<Integer, Integer> copyTargets = new HashMap<>();

	ItemCopies(Function<Slot, ItemEquipmentStats> equipmentOf)
	{
		this.equipmentOf = equipmentOf;
	}

	void setSpares(List<Widget> spares)
	{
		this.spares = spares;
	}

	void clearTargets()
	{
		copyTargets.clear();
	}

	boolean isCopy(Widget w)
	{
		return copyTargets.containsKey(w.getIndex());
	}

	void hideFrom(int from)
	{
		for (int i = from; i < spares.size(); i++)
		{
			spares.get(i).setHidden(true);
		}
	}

	void clear()
	{
		spares = new ArrayList<>();
		copyTargets.clear();
	}

	/**
	 * Points a click on a copy at the real item's bank slot, so the game withdraws the real item.
	 * Same as core Bank Tags' LayoutManager.onMenuOptionClicked for layout duplicates: only the slot index of
	 * the game's own menu action is changed; no menu entries are added.
	 */
	void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getParam1() == InterfaceID.Bankmain.ITEMS)
		{
			Integer real = copyTargets.get(event.getParam0());
			if (real != null)
			{
				event.getMenuEntry().setParam0(real);
			}
		}
	}

	/**
	 * Gear useful to several styles (barrows gloves, fire cape) lives in one category. Show a faded copy in
	 * each other combat style where it beats everything that style already has in that slot. Copies count too,
	 * so teleport gear copied into its best style (a charged glory in Melee) can reach the others.
	 */
	void addHybridCopies(Map<String, List<Slot>> groups)
	{
		for (String style : Categories.COMBAT_STYLES)
		{
			List<Slot> styleSlots = groups.get(style);
			if (styleSlots == null)
			{
				continue;
			}

			Map<Integer, Integer> bestBySlot = new HashMap<>();
			Set<Integer> shown = new HashSet<>();
			for (Slot s : styleSlots)
			{
				shown.add(s.itemId);
				ItemEquipmentStats e = equipmentOf.apply(s);
				if (e != null)
				{
					bestBySlot.merge(e.getSlot(), GearOrder.score(style, e), Math::max);
				}
			}

			Map<Integer, Slot> candidates = new HashMap<>();
			for (String other : Categories.COMBAT_STYLES)
			{
				if (other.equals(style) || !groups.containsKey(other))
				{
					continue;
				}
				for (Slot s : groups.get(other))
				{
					ItemEquipmentStats e = equipmentOf.apply(s);
					boolean blessing = e != null && e.getSlot() == EquipmentInventorySlot.AMMO.getSlotIdx();
					if (shown.contains(s.itemId) || e == null || e.getSlot() == EquipmentInventorySlot.WEAPON.getSlotIdx()
						|| (!blessing && GearOrder.offence(style, e) <= 0))
					{
						continue;
					}
					int score = GearOrder.score(style, e);
					Slot current = candidates.get(e.getSlot());
					if (score > bestBySlot.getOrDefault(e.getSlot(), 0)
						&& (current == null || score > GearOrder.score(style, equipmentOf.apply(current))))
					{
						candidates.put(e.getSlot(), s);
					}
				}
			}
			for (Slot s : candidates.values())
			{
				styleSlots.add(new Slot(s.widget, s.index, s.itemId, s.quantity, s.name, style, true));
			}
		}
	}

	/**
	 * Items the bundled data lists under extra categories (logs for Firemaking, Graceful for Farming...) get a
	 * withdrawable copy in each of them.
	 */
	void addDataCopies(Map<String, List<Slot>> groups, Predicate<Slot> placedByRule)
	{
		List<Slot> originals = new ArrayList<>();
		groups.values().forEach(list -> list.stream().filter(s -> !s.copy).forEach(originals::add));
		for (Slot s : originals)
		{
			ItemData.Entry data = ItemData.get(s.name);
			// A rule's placement wins. Combat gear can still sit outside its data category: its stats pick the style.
			if (data == null || placedByRule.test(s))
			{
				continue;
			}
			for (String extra : data.extras)
			{
				addCopy(groups, s, extra);
			}
		}
	}

	/**
	 * Items the user also shows in other categories get a withdrawable copy in each of them.
	 */
	void addAlsoShown(Map<String, List<Slot>> groups, Function<Slot, Set<String>> alsoShownIn)
	{
		List<Slot> originals = new ArrayList<>();
		groups.values().forEach(list -> list.stream().filter(s -> !s.copy).forEach(originals::add));
		for (Slot s : originals)
		{
			for (String category : alsoShownIn.apply(s))
			{
				if (!category.equals(s.category))
				{
					addCopy(groups, s, category);
				}
			}
		}
	}

	// Unless the category already shows the item.
	private static void addCopy(Map<String, List<Slot>> groups, Slot s, String category)
	{
		List<Slot> target = groups.computeIfAbsent(category, k -> new ArrayList<>());
		if (target.stream().noneMatch(t -> t.itemId == s.itemId))
		{
			target.add(new Slot(s.widget, s.index, s.itemId, s.quantity, s.name, category, true));
		}
	}

	/**
	 * Teleport items that are also combat gear (charged glory, combat bracelet, max cape...) get a copy in
	 * their combat style, where the outfit columns rank them by stats. Only the most-charged version of each
	 * item is copied, so five glories don't fill the amulet row.
	 */
	void addTeleportGearCopies(Map<String, List<Slot>> groups)
	{
		List<Slot> teleports = groups.get(Categories.TELEPORTS);
		if (teleports == null)
		{
			return;
		}

		Map<String, Slot> best = new HashMap<>();
		for (Slot s : teleports)
		{
			ItemEquipmentStats e = equipmentOf.apply(s);
			if (s.copy || e == null || e.getSlot() == EquipmentInventorySlot.WEAPON.getSlotIdx())
			{
				continue;
			}
			best.merge(Doses.baseName(s.name).toLowerCase(), s, (a, b) -> Doses.dose(a.name) >= Doses.dose(b.name) ? a : b);
		}
		for (Slot s : best.values())
		{
			ItemEquipmentStats e = equipmentOf.apply(s);
			String style = Categoriser.gear(s.name.toLowerCase(), e);
			if (Categories.COMBAT_STYLES.contains(style) && GearOrder.offence(style, e) > 0)
			{
				List<Slot> target = groups.computeIfAbsent(style, k -> new ArrayList<>());
				if (target.stream().noneMatch(t -> t.itemId == s.itemId))
				{
					target.add(new Slot(s.widget, s.index, s.itemId, s.quantity, s.name, style, true));
				}
			}
		}
	}

	/**
	 * Draws a copy of {@code slot}'s item in the next spare slot widget, showing the real item's icon and
	 * quantity and carrying its options.
	 *
	 * @return the copy widget, or null when the bank has no spare slots left
	 */
	Widget placeCopy(int index, Slot slot)
	{
		if (index >= spares.size())
		{
			return null;
		}
		Widget real = slot.widget;
		Widget w = spares.get(index);
		w.setItemId(real.getItemId());
		w.setItemQuantity(real.getItemQuantity());
		w.setItemQuantityMode(real.getItemQuantityMode());
		w.setOpacity(real.getOpacity());
		// Item outline and drop shadow, so the copy is drawn exactly like the real item.
		w.setBorderType(real.getBorderType());
		w.setTextShadowed(real.getTextShadowed());
		w.setName(real.getName());
		// Every op, Examine (op 11) included, so the copy's menu is the real item's, Move to and all.
		w.clearActions();
		String[] actions = real.getActions();
		for (int i = 0; actions != null && i < actions.length; i++)
		{
			w.setAction(i, actions[i]);
		}
		w.setDragDeadZone(real.getDragDeadZone());
		w.setDragDeadTime(real.getDragDeadTime());
		w.setHidden(false);
		copyTargets.put(w.getIndex(), slot.index);
		return w;
	}
}
