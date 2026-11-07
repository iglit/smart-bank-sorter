package com.banksorter;

import java.util.Arrays;
import java.util.List;
import net.runelite.api.widgets.Widget;

final class WidgetPool
{
	private WidgetPool()
	{
	}

	/**
	 * Returns our widget at {@code index}, creating it if the bank rebuild deleted it.
	 */
	static Widget pooled(Widget container, List<Widget> pool, int index, int type)
	{
		Widget[] children = container.getChildren();
		if (index < pool.size())
		{
			Widget w = pool.get(index);
			if (children != null && Arrays.asList(children).contains(w))
			{
				return w;
			}
			// The bank interface was rebuilt from scratch; start the pool over.
			pool.subList(index, pool.size()).clear();
		}
		Widget w = container.createChild(-1, type);
		pool.add(w);
		return w;
	}
}
