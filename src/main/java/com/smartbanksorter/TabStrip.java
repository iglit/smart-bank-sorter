package com.smartbanksorter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.plugins.banktags.tabs.TabSprites;
import net.runelite.client.util.ColorUtil;

class TabStrip
{
	// Tab strip geometry copied from Bank Tags' TabInterface so ours looks like its own; position and width come
	// from its arrows when present. The tab sprite is 39x40 and doesn't scale, so tabs are never squeezed.
	private static final int TAB_X = 1;
	private static final int TAB_WIDTH = 39;
	private static final int TAB_HEIGHT = 40;
	private static final int TAB_MARGIN = 1;
	private static final int TAB_PITCH = TAB_HEIGHT + TAB_MARGIN;
	// Bank Tags draws the icon 3px in and 4px down from its tab background. Item sprites carry their shadow on
	// the right, so this looks centred where true centring looks left-heavy.
	private static final int TAB_ICON_X = 3;
	private static final int TAB_ICON_Y = 4;
	// Bank Tags keeps this much of the item container's bottom clear of its strip.
	private static final int TAB_BOTTOM_OFFSET = 39;

	private final Client client;
	private final SmartBankSorterConfig config;
	// The category the plugin currently filters to (null = all), and what to do when a tab is clicked.
	private final Supplier<String> focused;
	private final Consumer<String> onFocus;
	// Category tabs on the left edge of the bank.
	private final List<Widget> tabWidgets = new ArrayList<>();
	private final List<Widget> tabBackgrounds = new ArrayList<>();
	// Invisible widget under the strip that takes the mouse wheel, as Bank Tags does for its tag tabs.
	private final List<Widget> tabScrollWidgets = new ArrayList<>();
	private int tabScroll;
	private int maxTabScroll;
	// Bank Tags is showing tag tabs of its own, so its arrows are busy scrolling those.
	private boolean bankTagsHasTabs;
	private List<String> lastTabs = Collections.emptyList();
	private List<Integer> lastTabIcons = Collections.emptyList();

	TabStrip(Client client, SmartBankSorterConfig config, Supplier<String> focused, Consumer<String> onFocus)
	{
		this.client = client;
		this.config = config;
		this.focused = focused;
		this.onFocus = onFocus;
	}

	void clear()
	{
		tabWidgets.clear();
		tabBackgrounds.clear();
		tabScrollWidgets.clear();
	}

	void refresh()
	{
		placeTabs(lastTabs, lastTabIcons);
	}

	void onMenuOptionClicked(MenuOptionClicked event)
	{
		// Bank Tags' arrows scroll its tag tabs. With none of those shown they'd do nothing, so scroll ours.
		Widget w = event.getWidget();
		if (bankTagsHasTabs || maxTabScroll == 0 || w == null
			|| event.getMenuAction() != MenuAction.CC_OP || event.getParam1() != InterfaceID.Bankmain.ITEMS_CONTAINER)
		{
			return;
		}
		if (w.getSpriteId() == TabSprites.UP_ARROW.getSpriteId())
		{
			scrollTabs(-1);
		}
		else if (w.getSpriteId() == TabSprites.DOWN_ARROW.getSpriteId())
		{
			scrollTabs(1);
		}
	}

	/**
	 * Tabs down the left edge of the bank, under Bank Tags' own tab buttons if it shows any. Each tab is a
	 * group of categories; clicking one shows only those categories, clicking it again shows everything.
	 * Laid out the way Bank Tags lays out its tag tabs: full-size tabs, a window of them when they don't all
	 * fit, and an invisible widget under the strip that takes the mouse wheel.
	 */
	void placeTabs(List<String> categories, List<Integer> icons)
	{
		lastTabs = categories;
		lastTabIcons = icons;
		Widget parent = client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);
		if (parent == null)
		{
			return;
		}
		if (categories.isEmpty())
		{
			hideTabs(0);
			return;
		}

		// Fit between Bank Tags' up and down arrows, below any tag tabs it is showing, so the strip looks like
		// its own.
		Widget up = null;
		Widget down = null;
		int belowOtherTabs = 0;
		Widget[] existing = parent.getDynamicChildren();
		for (Widget w : existing == null ? new Widget[0] : existing)
		{
			if (w == null || w.isHidden())
			{
				continue;
			}
			int sprite = w.getSpriteId();
			if (sprite == TabSprites.UP_ARROW.getSpriteId())
			{
				up = w;
			}
			else if (sprite == TabSprites.DOWN_ARROW.getSpriteId())
			{
				down = w;
			}
			else if (sprite == TabSprites.TAB_BACKGROUND.getSpriteId() || sprite == TabSprites.TAB_BACKGROUND_ACTIVE.getSpriteId())
			{
				belowOtherTabs = Math.max(belowOtherTabs, w.getOriginalY() + w.getOriginalHeight() + TAB_MARGIN);
			}
		}
		bankTagsHasTabs = belowOtherTabs > 0;
		int x = up != null ? up.getOriginalX() : TAB_X;
		int width = up != null ? up.getOriginalWidth() : TAB_WIDTH;
		int top = Math.max(belowOtherTabs, up != null ? up.getOriginalY() + up.getOriginalHeight() + TAB_MARGIN : TAB_MARGIN);
		int bottom = down != null ? down.getOriginalY() - TAB_MARGIN : stripBottom(parent) - TAB_MARGIN;
		int visible = (bottom - top + TAB_MARGIN) / TAB_PITCH;
		if (visible <= 0)
		{
			// Bank Tags has filled the strip with its own tabs; stay out of its way.
			hideTabs(0);
			return;
		}
		maxTabScroll = Math.max(0, categories.size() - visible);
		tabScroll = Math.max(0, Math.min(tabScroll, maxTabScroll));

		// Created before the tabs so it sits under them, like Bank Tags' own. Covering the margins too means the
		// wheel never reaches the bank items through a gap in the strip.
		Widget scroll = WidgetPool.pooled(parent, tabScrollWidgets, 0, WidgetType.TEXT);
		scroll.setOriginalX(x - TAB_MARGIN);
		scroll.setOriginalY(top - TAB_MARGIN);
		scroll.setOriginalWidth(width + 2 * TAB_MARGIN);
		scroll.setOriginalHeight(visible * TAB_PITCH);
		scroll.setHasListener(true);
		scroll.setNoScrollThrough(true);
		scroll.setOnScrollWheelListener((JavaScriptCallback) e -> scrollTabs(e.getMouseY()));
		scroll.setHidden(false);
		scroll.revalidate();

		for (int i = 0; i < categories.size(); i++)
		{
			String category = categories.get(i);
			boolean active = category.equals(focused.get());
			int row = i - tabScroll;
			boolean shown = row >= 0 && row < visible;
			int y = top + row * TAB_PITCH;

			Widget back = WidgetPool.pooled(parent, tabBackgrounds, i, WidgetType.GRAPHIC);
			back.setSpriteId(active ? TabSprite.TAB_BACKGROUND_ACTIVE.getSpriteId() : TabSprite.TAB_BACKGROUND.getSpriteId());
			back.setOriginalX(x);
			back.setOriginalY(y);
			back.setOriginalWidth(width);
			back.setOriginalHeight(TAB_HEIGHT);
			back.setHidden(!shown);
			back.revalidate();

			Widget tab = WidgetPool.pooled(parent, tabWidgets, i, WidgetType.GRAPHIC);
			tab.setItemId(icons.get(i));
			tab.setItemQuantityMode(ItemQuantityMode.NEVER);
			tab.setBorderType(1);
			tab.setOriginalX(x + TAB_ICON_X);
			tab.setOriginalY(y + TAB_ICON_Y);
			tab.setOriginalWidth(SmartBankSorterPlugin.ITEM_WIDTH);
			tab.setOriginalHeight(SmartBankSorterPlugin.ITEM_HEIGHT);
			tab.setName(ColorUtil.wrapWithColorTag(category, config.headerColor()));
			tab.setAction(0, active ? "Show all" : "Show");
			tab.setHasListener(true);
			tab.setOnOpListener((JavaScriptCallback) e -> onFocus.accept(category));
			tab.setHidden(!shown);
			tab.revalidate();
		}
		hideTabs(categories.size());
	}

	/**
	 * Without Bank Tags' arrows, use the space its strip would: the item container less the bank's bottom bar
	 * and whichever buttons (incinerator, storage popup) are shown down there. Mirrors its repositionButtons.
	 */
	private int stripBottom(Widget parent)
	{
		int height = parent.getHeight() - TAB_BOTTOM_OFFSET;
		for (int id : new int[]{InterfaceID.Bankmain.INCINERATOR_TARGET, InterfaceID.Bankmain.POPUP_BUTTON_OUT})
		{
			Widget w = client.getWidget(id);
			if (w != null && !w.isHidden())
			{
				height = Math.min(height, w.getRelativeY());
			}
		}
		Widget popup = client.getWidget(InterfaceID.Bankmain.STORAGE_POPUP_TAB);
		if (popup != null && !popup.isHidden())
		{
			for (int id : new int[]{InterfaceID.Bankmain.GIM_STORAGE, InterfaceID.Bankmain.POTIONSTORE_BUTTON})
			{
				Widget w = client.getWidget(id);
				if (w != null && !w.isHidden())
				{
					height = Math.min(height, popup.getRelativeY() + w.getRelativeY());
				}
			}
		}
		return height;
	}

	private void scrollTabs(int direction)
	{
		int next = Math.max(0, Math.min(tabScroll + Integer.signum(direction), maxTabScroll));
		// At an end already (or everything fits): leave the strip alone so it doesn't flicker.
		if (next != tabScroll)
		{
			// Only the strip moves; rebuilding the bank here made the item view jump.
			tabScroll = next;
			placeTabs(lastTabs, lastTabIcons);
		}
	}

	void hideTabs(int from)
	{
		for (List<Widget> pool : Arrays.asList(tabWidgets, tabBackgrounds))
		{
			for (int i = from; i < pool.size(); i++)
			{
				pool.get(i).setHidden(true);
			}
		}
		if (from == 0)
		{
			// No tabs showing, so nothing to scroll: this also stops arrow clicks from bringing them back.
			tabScrollWidgets.forEach(w -> w.setHidden(true));
			maxTabScroll = 0;
		}
	}
}
