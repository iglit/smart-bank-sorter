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

	Slot(Widget widget, int index, int itemId, int quantity, String name, String category)
	{
		this(widget, index, itemId, quantity, name, category, false);
	}

	Slot(Widget widget, int index, int itemId, int quantity, String name, String category, boolean copy)
	{
		this.copy = copy;
		this.widget = widget;
		this.index = index;
		this.itemId = itemId;
		this.quantity = quantity;
		this.name = name;
		this.category = category;
	}
}
