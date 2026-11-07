package com.banksorter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.game.ItemEquipmentStats;

class ItemCopies
{
	private final Client client;
	private final Function<Slot, ItemEquipmentStats> equipmentOf;
	// Faded display-only copies of hybrid gear shown in a second combat style.
	private final List<Widget> copyWidgets = new ArrayList<>();
	// Copy widget -> the real bank slot it mirrors.
	private final Map<Widget, Slot> copyTargets = new HashMap<>();

	ItemCopies(Client client, Function<Slot, ItemEquipmentStats> equipmentOf)
	{
		this.client = client;
		this.equipmentOf = equipmentOf;
	}

	void addWidgetsTo(Set<Widget> ours)
	{
		ours.addAll(copyWidgets);
	}

	void clearTargets()
	{
		copyTargets.clear();
	}

	void hideFrom(int from)
	{
		for (int i = from; i < copyWidgets.size(); i++)
		{
			copyWidgets.get(i).setHidden(true);
		}
	}

	void clear()
	{
		copyWidgets.clear();
		copyTargets.clear();
	}

	/**
	 * The client builds no menu for widgets we create, so when hovering a copy add the real item's options,
	 * each targeting the real bank slot. Picking one does exactly what picking it on the real item does.
	 */
	void addCopyMenu()
	{
		if (copyTargets.isEmpty())
		{
			return;
		}
		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		Point mouse = client.getMouseCanvasPosition();
		if (container == null || container.isHidden() || !container.getBounds().contains(mouse.getX(), mouse.getY()))
		{
			return;
		}

		for (Map.Entry<Widget, Slot> entry : copyTargets.entrySet())
		{
			Widget copy = entry.getKey();
			if (copy.isHidden() || !copy.getBounds().contains(mouse.getX(), mouse.getY()))
			{
				continue;
			}

			Slot slot = entry.getValue();
			Widget real = slot.widget;
			String[] actions = real.getActions();
			// Added lowest first; the last entry added becomes the left-click option.
			for (int i = actions == null ? -1 : actions.length - 1; i >= 0; i--)
			{
				if (actions[i] == null || actions[i].isEmpty())
				{
					continue;
				}
				client.getMenu().createMenuEntry(-1)
					.setOption(actions[i])
					.setTarget(real.getName())
					.setType(i >= 5 ? MenuAction.CC_OP_LOW_PRIORITY : MenuAction.CC_OP)
					.setIdentifier(i + 1)
					.setParam0(slot.index)
					.setParam1(InterfaceID.Bankmain.ITEMS)
					.setItemId(real.getItemId());
			}
			return;
		}
	}

	/**
	 * Gear useful to several styles (barrows gloves, fire cape) lives in one category. Show a faded copy in
	 * each other combat style where it beats everything that style already has in that slot.
	 */
	void addHybridCopies(Map<String, List<Slot>> groups)
	{
		for (String style : BankSorterPlugin.GEAR_CATEGORIES)
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
			for (String other : BankSorterPlugin.GEAR_CATEGORIES)
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
			best.merge(BankSorterPlugin.baseName(s.name).toLowerCase(), s, (a, b) -> BankSorterPlugin.dose(a.name) >= BankSorterPlugin.dose(b.name) ? a : b);
		}
		for (Slot s : best.values())
		{
			ItemEquipmentStats e = equipmentOf.apply(s);
			String style = Categoriser.gear(s.name.toLowerCase(), e);
			if (BankSorterPlugin.GEAR_CATEGORIES.contains(style) && GearOrder.offence(style, e) > 0)
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
	Widget placeCopy(Widget container, int index, Slot slot)
	{
		Widget real = slot.widget;
		Widget w = WidgetPool.pooled(container, copyWidgets, index, WidgetType.GRAPHIC);
		w.setItemId(real.getItemId());
		w.setItemQuantity(real.getItemQuantity());
		w.setItemQuantityMode(real.getItemQuantityMode());
		w.setOriginalWidth(BankSorterPlugin.ITEM_WIDTH);
		w.setOriginalHeight(BankSorterPlugin.ITEM_HEIGHT);
		w.setOpacity(real.getOpacity());
		// Item outline and drop shadow, so the copy is drawn exactly like the real item.
		w.setBorderType(real.getBorderType());
		w.setTextShadowed(real.getTextShadowed());
		w.setName(real.getName());
		w.setDragDeadZone(Integer.MAX_VALUE);
		w.setDragDeadTime(Integer.MAX_VALUE);
		w.setHidden(false);
		copyTargets.put(w, slot);
		return w;
	}
}
