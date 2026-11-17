package com.smartbanksorter;

import net.runelite.api.widgets.Widget;

final class Slot
{
	final Widget widget;
	final int index;
	final int itemId;
	final int quantity;
	final String name;
	final String category;
	final boolean copy;
	// An item the user added to a category but doesn't have: drawn as a placeholder, with no widget or bank slot
	// behind it.
	final boolean ghost;
	// For a ghost, the rule that added it; null otherwise.
	final String pattern;

	Slot(Widget widget, int index, int itemId, int quantity, String name, String category)
	{
		this(widget, index, itemId, quantity, name, category, false);
	}

	Slot(Widget widget, int index, int itemId, int quantity, String name, String category, boolean copy)
	{
		this(widget, index, itemId, quantity, name, category, copy, null);
	}

	private Slot(Widget widget, int index, int itemId, int quantity, String name, String category, boolean copy,
		String pattern)
	{
		this.copy = copy;
		this.ghost = pattern != null;
		this.pattern = pattern;
		this.widget = widget;
		this.index = index;
		this.itemId = itemId;
		this.quantity = quantity;
		this.name = name;
		this.category = category;
	}

	/**
	 * A not-owned item to show in {@code category}. It takes a spare slot like a copy does, and is laid out like a
	 * placeholder: an added weapon heads its own outfit. In bank order it goes after the real items.
	 */
	static Slot ghost(int itemId, String name, String category, String pattern)
	{
		return new Slot(null, Integer.MAX_VALUE, itemId, 0, name, category, true, pattern);
	}
}
