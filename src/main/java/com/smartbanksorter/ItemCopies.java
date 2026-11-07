package com.smartbanksorter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemEquipmentStats;

class ItemCopies
{
	private final Client client;
	private final Function<Slot, ItemEquipmentStats> equipmentOf;
	// Spare empty-slot widgets the bank interface already has past the last item. Copies reuse these, like
	// core Bank Tags layouts do for duplicates: the game treats them as real bank slots, so it builds their
	// menus itself, and onMenuOptionClicked points the click at the real item's slot.
	private List<Widget> spares = new ArrayList<>();
	// Copy widget's child index -> the real item's bank slot index.
	private final Map<Integer, Integer> copyTargets = new HashMap<>();

	ItemCopies(Client client, Function<Slot, ItemEquipmentStats> equipmentOf)
	{
		this.client = client;
		this.equipmentOf = equipmentOf;
	}

	void addWidgetsTo(Set<Widget> ours)
	{
		// Spares are the game's own widgets and are reset by every bank build; nothing to exclude.
	}

	void setSpares(List<Widget> spares)
	{
		this.spares = spares;
	}

	void clearTargets()
	{
		copyTargets.clear();
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
	 * each other combat style where it beats everything that style already has in that slot.
	 */
	void addHybridCopies(Map<String, List<Slot>> groups)
	{
		for (String style : SmartBankSorterPlugin.GEAR_CATEGORIES)
		{
			List<Slot> styleSlots = groups.get(style);
			if (styleSlots == null)
			{
				continue;
			}

			Map<Integer, Integer> bestBySlot = new HashMap<>();
			for (Slot s : styleSlots)
			{
				ItemEquipmentStats e = equipmentOf.apply(s);
				if (e != null)
				{
					bestBySlot.merge(e.getSlot(), GearOrder.score(style, e), Math::max);
				}
			}

			Map<Integer, Slot> candidates = new HashMap<>();
			for (String other : SmartBankSorterPlugin.GEAR_CATEGORIES)
			{
				if (other.equals(style) || !groups.containsKey(other))
				{
					continue;
				}
				for (Slot s : groups.get(other))
				{
					ItemEquipmentStats e = equipmentOf.apply(s);
					boolean blessing = e != null && e.getSlot() == EquipmentInventorySlot.AMMO.getSlotIdx();
					if (s.copy || e == null || e.getSlot() == EquipmentInventorySlot.WEAPON.getSlotIdx()
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
	void addDataCopies(Map<String, List<Slot>> groups)
	{
		List<Slot> originals = new ArrayList<>();
		groups.values().forEach(list -> list.stream().filter(s -> !s.copy).forEach(originals::add));
		for (Slot s : originals)
		{
			ItemData.Entry data = ItemData.get(s.name);
			if (data == null || !data.category.equals(s.category))
			{
				// A user rule moved it; their placement wins.
				continue;
			}
			for (String extra : data.extras)
			{
				List<Slot> target = groups.computeIfAbsent(extra, k -> new ArrayList<>());
				if (target.stream().noneMatch(t -> t.itemId == s.itemId))
				{
					target.add(new Slot(s.widget, s.index, s.itemId, s.quantity, s.name, extra, true));
				}
			}
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
			best.merge(SmartBankSorterPlugin.baseName(s.name).toLowerCase(), s, (a, b) -> SmartBankSorterPlugin.dose(a.name) >= SmartBankSorterPlugin.dose(b.name) ? a : b);
		}
		for (Slot s : best.values())
		{
			ItemEquipmentStats e = equipmentOf.apply(s);
			String style = Categoriser.gear(s.name.toLowerCase(), e);
			if (SmartBankSorterPlugin.GEAR_CATEGORIES.contains(style) && GearOrder.offence(style, e) > 0)
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
	 * A copy shows the real item's icon and quantity; its menu comes from {@link #addCopyMenu}.
	 */
	/**
	 * Draws a copy of {@code slot}'s item in the next spare slot widget, with the real item's options.
	 *
	 * @return the copy widget, or null when the bank has no spare slots left
	 */
	Widget placeCopy(Widget container, int index, Slot slot)
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
		String[] actions = real.getActions();
		for (int i = 0; i < 10; i++)
		{
			w.setAction(i, actions != null && i < actions.length ? actions[i] : null);
		}
		w.setDragDeadZone(Integer.MAX_VALUE);
		w.setDragDeadTime(Integer.MAX_VALUE);
		w.setHidden(false);
		copyTargets.put(w.getIndex(), slot.index);
		return w;
	}
}
