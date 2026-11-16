package com.smartbanksorter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.tabs.TabManager;
import net.runelite.client.plugins.banktags.tabs.TabSprites;
import net.runelite.client.plugins.banktags.tabs.TagTab;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

/**
 * The category tabs in Bank Tags' tag tab strip, mixed with the user's tag tabs so one strip holds both and they
 * can be dragged into one order.
 * <p>
 * Bank Tags draws every tab in its TabManager's list each time the bank is built, so the category tabs go in that
 * list, in memory only. Bank Tags' widgets for them get the sorter's op and listener in place of Bank Tags' own
 * (view, rename, delete...), so Bank Tags never opens a category tab as a tag, and drags that involve a category
 * tab are handled here before Bank Tags sees them. Bank Tags saves its whole list to its own settings whenever the
 * user changes a tag tab; the category tabs are taken straight back out of what it saved, and the mixed order kept
 * in the sorter's settings instead.
 */
@Slf4j
class BankTagsStrip
{
	// Bank Tags' scroll catcher, arrows and new-tab button come before its tabs in the item container.
	private static final int TAGTAB_CHILD_OFFSET = 4;
	// Bank Tags' "Use tag tabs" setting. Not a public constant there, unlike its group and tab list keys.
	private static final String BANK_TAGS_USE_TABS = "useTabs";

	private final Client client;
	private final ClientThread clientThread;
	private final SmartBankSorterConfig config;
	private final ConfigManager configManager;
	private final TabManager tabManager;
	private final BooleanSupplier bankTagsOn;
	// The category the plugin currently filters to (null = all), and what to do when a tab is clicked.
	private final Supplier<String> focused;
	private final Consumer<String> onFocus;
	private final Runnable rebuildBank;
	// Our tabs in Bank Tags' list, by name. The same objects go back in the list on every build, so they are told
	// from the user's tag tabs by identity: TagTab's equals is by name.
	private final Map<String, TagTab> ourTabs = new LinkedHashMap<>();
	// The tag tabs and category tabs together, as last arranged; what the strip is rebuilt to on each bank open.
	private List<String> order = new ArrayList<>();
	// The strip as it was last put together, every tab in order.
	private List<String> drawn = Collections.emptyList();
	// The category tabs the last layout wanted, with their icons, and what the strip was last synced to.
	private List<String> lastTabs = Collections.emptyList();
	private List<Integer> lastIcons = Collections.emptyList();
	private List<String> syncedTabs = Collections.emptyList();
	private boolean rebuildQueued;

	BankTagsStrip(Client client, ClientThread clientThread, SmartBankSorterConfig config, ConfigManager configManager,
		TabManager tabManager, BooleanSupplier bankTagsOn, Supplier<String> focused, Consumer<String> onFocus,
		Runnable rebuildBank)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.configManager = configManager;
		this.tabManager = tabManager;
		this.bankTagsOn = bankTagsOn;
		this.focused = focused;
		this.onFocus = onFocus;
		this.rebuildBank = rebuildBank;
	}

	void loadOrder(String saved)
	{
		order = new ArrayList<>(Text.fromCSV(saved));
	}

	/**
	 * Whether the category tabs go in Bank Tags' strip: they're on, and Bank Tags is on with its tag tabs.
	 */
	boolean active()
	{
		return config.enabled() && config.categoryTabs() && bankTagsOn.getAsBoolean()
			&& !"false".equals(configManager.getConfiguration(BankTagsPlugin.CONFIG_GROUP, BANK_TAGS_USE_TABS));
	}

	/**
	 * Puts the category tabs in Bank Tags' list, in the arranged order, right before Bank Tags draws the strip from
	 * it. Bank Tags reloads the list from its settings whenever the bank opens, so this runs on every build.
	 */
	void sync()
	{
		List<TagTab> list = tabManager.getTabs();
		list.removeIf(this::isOurs);
		if (!active())
		{
			ourTabs.clear();
			syncedTabs = Collections.emptyList();
			return;
		}
		List<TagTab> mine = new ArrayList<>();
		for (int i = 0; i < lastTabs.size(); i++)
		{
			String name = lastTabs.get(i);
			if (list.stream().anyMatch(t -> Text.standardize(t.getTag()).equals(Text.standardize(name))))
			{
				log.debug("A tag tab is already called {}; leaving that category tab out", name);
				continue;
			}
			TagTab tab = ourTabs.computeIfAbsent(name, n ->
			{
				TagTab t = new TagTab();
				t.setTag(n);
				return t;
			});
			tab.setIconItemId(lastIcons.get(i));
			mine.add(tab);
		}
		ourTabs.values().removeIf(t -> !mine.contains(t));
		syncedTabs = lastTabs;
		List<TagTab> merged = merge(order, new ArrayList<>(list), mine);
		list.clear();
		list.addAll(merged);
		drawn = new ArrayList<>();
		merged.forEach(t -> drawn.add(t.getTag()));
	}

	/**
	 * The strip in the arranged order: the user's tag tabs in Bank Tags' own order, with each category tab put
	 * back where it was. A tab the order doesn't know goes at the end. Tag tabs the user reordered while the
	 * sorter was off keep Bank Tags' order; the category tabs follow the tag tab they came after.
	 */
	static List<TagTab> merge(List<String> order, List<TagTab> tagTabs, List<TagTab> categoryTabs)
	{
		List<TagTab> out = new ArrayList<>();
		Deque<TagTab> tagsLeft = new ArrayDeque<>(tagTabs);
		List<TagTab> categoriesLeft = new ArrayList<>(categoryTabs);
		for (String name : order)
		{
			TagTab category = null;
			for (TagTab t : categoriesLeft)
			{
				if (t.getTag().equals(name))
				{
					category = t;
					break;
				}
			}
			if (category != null)
			{
				out.add(category);
				categoriesLeft.remove(category);
			}
			else if (tagsLeft.stream().anyMatch(t -> t.getTag().equals(name)))
			{
				TagTab t;
				do
				{
					t = tagsLeft.poll();
					out.add(t);
				}
				while (!t.getTag().equals(name));
			}
		}
		out.addAll(tagsLeft);
		out.addAll(categoriesLeft);
		return out;
	}

	/**
	 * Takes over the widgets Bank Tags has just made for the category tabs: in its strip, and in its "View tag
	 * tabs" view, which draws every tab as an item. Clicking one shows that tab's categories; none of Bank Tags'
	 * tag tab ops apply.
	 */
	void patch()
	{
		if (ourTabs.isEmpty())
		{
			return;
		}
		patchChildren(client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER), TAGTAB_CHILD_OFFSET);
		patchChildren(client.getWidget(InterfaceID.Bankmain.ITEMS), SmartBankSorterPlugin.BANK_SLOTS);
	}

	private void patchChildren(Widget parent, int from)
	{
		Widget[] children = parent == null ? null : parent.getChildren();
		if (children == null)
		{
			return;
		}
		boolean inStrip = parent.getId() == InterfaceID.Bankmain.ITEMS_CONTAINER;
		for (int i = from; i < children.length; i++)
		{
			Widget w = children[i];
			TagTab tab = w == null || w.getType() != WidgetType.GRAPHIC || w.getName() == null ? null
				: ourTabs.get(Text.removeTags(w.getName()));
			if (tab == null)
			{
				continue;
			}
			String category = tab.getTag();
			boolean active = category.equals(focused.get());
			boolean background = w.getSpriteId() == TabSprites.TAB_BACKGROUND.getSpriteId()
				|| w.getSpriteId() == TabSprites.TAB_BACKGROUND_ACTIVE.getSpriteId();
			if (background)
			{
				w.setSpriteId((active ? TabSprites.TAB_BACKGROUND_ACTIVE : TabSprites.TAB_BACKGROUND).getSpriteId());
			}
			// In the strip the ops sit on the background, under the icon; in the tag tab view the icon has them.
			if (background || !inStrip)
			{
				w.clearActions();
				w.setAction(0, active ? "Show all" : "Show");
				w.setHasListener(true);
				w.setOnOpListener((JavaScriptCallback) e -> onFocus.accept(category));
			}
			w.setName(ColorUtil.wrapWithColorTag(category, config.headerColor()));
		}
	}

	/**
	 * The category tabs the layout just drew, with their icons. The strip holds what the previous layout wanted,
	 * so a change to which tabs there are means one more build to put them in; a change of icon is applied on
	 * the spot.
	 */
	void update(List<String> tabs, List<Integer> icons)
	{
		lastTabs = tabs;
		lastIcons = icons;
		if (!tabs.equals(syncedTabs))
		{
			queueRebuild(false);
			return;
		}
		for (int i = 0; i < tabs.size(); i++)
		{
			TagTab tab = ourTabs.get(tabs.get(i));
			int icon = icons.get(i);
			if (tab != null && tab.getIconItemId() != icon)
			{
				tab.setIconItemId(icon);
				setIcon(client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER), TAGTAB_CHILD_OFFSET, tab.getTag(), icon);
				setIcon(client.getWidget(InterfaceID.Bankmain.ITEMS), SmartBankSorterPlugin.BANK_SLOTS, tab.getTag(), icon);
			}
		}
	}

	/**
	 * Rebuilds the bank on a later client tick, which has Bank Tags draw its strip from its list again and
	 * {@link #patch()} take the category tabs over again. Bank Tags queues a redraw of its own after some changes
	 * (a new icon, say), which would undo the patch: {@code afterBankTags} queues the rebuild behind it.
	 */
	private void queueRebuild(boolean afterBankTags)
	{
		if (rebuildQueued)
		{
			return;
		}
		rebuildQueued = true;
		Runnable rebuild = () ->
		{
			rebuildQueued = false;
			rebuildBank.run();
		};
		clientThread.invokeLater(afterBankTags ? () -> clientThread.invokeLater(rebuild) : rebuild);
	}

	private static void setIcon(Widget parent, int from, String category, int icon)
	{
		Widget[] children = parent == null ? null : parent.getChildren();
		if (children == null)
		{
			return;
		}
		for (int i = from; i < children.length; i++)
		{
			Widget w = children[i];
			if (w != null && w.getType() == WidgetType.GRAPHIC && w.getItemId() != -1 && w.getName() != null
				&& category.equals(Text.removeTags(w.getName())))
			{
				w.setItemId(icon);
			}
		}
	}

	/**
	 * A drag involving a category tab, seen before Bank Tags. Two tabs: the dragged one goes where the other is,
	 * the way Bank Tags inserts its own (after it when dragged down the strip), and the order is kept. Never a
	 * swap: the tag tab dropped on would move past other tag tabs, and Bank Tags reloads their order from its own
	 * settings on every bank open. A tag tab dragged onto a category tab does move past other tag tabs, so their
	 * new order goes to Bank Tags' settings, as Bank Tags itself writes it after a reorder; nothing else of the
	 * sorter's goes there. Anything dropped on a category tab: nothing, where Bank Tags would tag the item with the
	 * tab's name. Either way the drop target is cleared so Bank Tags and the game leave it alone.
	 */
	void onDragBefore()
	{
		Widget dragged = client.getDraggedWidget();
		Widget draggedOn = client.getDraggedOnWidget();
		if (ourTabs.isEmpty() || dragged == null || draggedOn == null || client.getMouseCurrentButton() != 0)
		{
			return;
		}
		TagTab from = tabAt(dragged);
		TagTab to = tabAt(draggedOn);
		if (!isOurs(from) && !isOurs(to))
		{
			return;
		}
		client.setDraggedOnWidget(null);
		if (from == null || to == null || from == to)
		{
			return;
		}
		List<TagTab> list = tabManager.getTabs();
		int i = indexOf(list, from);
		int j = indexOf(list, to);
		if (i < 0 || j < 0)
		{
			return;
		}
		List<String> tagTabsBefore = tagTabNames(list);
		list.add(j, list.remove(i));
		saveOrder(list);
		List<String> tagTabsAfter = tagTabNames(list);
		if (!tagTabsAfter.equals(tagTabsBefore))
		{
			configManager.setConfiguration(BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_TABS_CONFIG, Text.toCSV(tagTabsAfter));
		}
		queueRebuild(false);
	}

	/**
	 * The user's tag tabs in the list, in order.
	 */
	private List<String> tagTabNames(List<TagTab> list)
	{
		List<String> names = new ArrayList<>();
		for (TagTab t : list)
		{
			if (!isOurs(t))
			{
				names.add(t.getTag());
			}
		}
		return names;
	}

	/**
	 * After Bank Tags has seen a drop: it redraws its strip after some, with its own ops on our tabs again.
	 */
	void onDragAfter()
	{
		if (client.getMouseCurrentButton() == 0)
		{
			patch();
		}
	}

	/**
	 * The tab a widget stands for: one of the pairs Bank Tags draws in its strip, or one of the items in its
	 * "View tag tabs" view.
	 */
	private TagTab tabAt(Widget w)
	{
		Widget parent = w.getParent();
		if (parent == null || w.getName() == null)
		{
			return null;
		}
		boolean inStrip = parent.getId() == InterfaceID.Bankmain.ITEMS_CONTAINER && w.getIndex() >= TAGTAB_CHILD_OFFSET;
		boolean inTagTabTab = parent.getId() == InterfaceID.Bankmain.ITEMS && w.getIndex() >= SmartBankSorterPlugin.BANK_SLOTS;
		if (!inStrip && !inTagTabTab)
		{
			return null;
		}
		String name = Text.removeTags(w.getName());
		for (TagTab t : tabManager.getTabs())
		{
			if (t.getTag().equals(name))
			{
				return t;
			}
		}
		return null;
	}

	/**
	 * Bank Tags saves its whole tab list, category tabs included, after any change to a tag tab. Keeps the mixed
	 * order the list is in and puts Bank Tags' settings back without the category tabs, in the same call, so they
	 * are never left there.
	 */
	void onConfigChanged(ConfigChanged event)
	{
		if (!BankTagsPlugin.CONFIG_GROUP.equals(event.getGroup()) || ourTabs.isEmpty() || event.getNewValue() == null)
		{
			return;
		}
		String key = event.getKey();
		if (BankTagsPlugin.TAG_TABS_CONFIG.equals(key))
		{
			List<String> saved = Text.fromCSV(event.getNewValue());
			if (saved.stream().noneMatch(ourTabs::containsKey))
			{
				return;
			}
			order = keepRenamedInPlace(drawn, saved);
			if (!order.equals(saved))
			{
				reorderList(order);
			}
			configManager.setConfiguration(SmartBankSorterConfig.GROUP, SmartBankSorterConfig.TAB_ORDER_KEY, Text.toCSV(order));
			List<String> tagTabs = new ArrayList<>(order);
			tagTabs.removeAll(ourTabs.keySet());
			configManager.setConfiguration(BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_TABS_CONFIG, Text.toCSV(tagTabs));
			// Bank Tags redraws its strip after saving, with its own ops on the category tabs again.
			queueRebuild(true);
		}
		else if (key.startsWith(BankTagsPlugin.TAG_ICON_PREFIX))
		{
			String tag = key.substring(BankTagsPlugin.TAG_ICON_PREFIX.length());
			if (ourTabs.keySet().stream().anyMatch(name -> Text.standardize(name).equals(tag)))
			{
				configManager.unsetConfiguration(BankTagsPlugin.CONFIG_GROUP, key);
			}
		}
	}

	/**
	 * Bank Tags renames a tag tab by taking it out of its list and adding it back at the end. With exactly one name
	 * gone and one new, and the rest in the same order, that's a rename: the tab stays where it was.
	 */
	static List<String> keepRenamedInPlace(List<String> before, List<String> after)
	{
		List<String> added = new ArrayList<>(after);
		added.removeAll(before);
		List<String> removed = new ArrayList<>(before);
		removed.removeAll(after);
		if (added.size() != 1 || removed.size() != 1)
		{
			return new ArrayList<>(after);
		}
		List<String> rest = new ArrayList<>(after);
		rest.removeAll(added);
		List<String> restBefore = new ArrayList<>(before);
		restBefore.removeAll(removed);
		if (!rest.equals(restBefore))
		{
			return new ArrayList<>(after);
		}
		List<String> renamed = new ArrayList<>(before);
		renamed.set(before.indexOf(removed.get(0)), added.get(0));
		return renamed;
	}

	// Bank Tags redraws its strip from its list right after saving it.
	private void reorderList(List<String> names)
	{
		List<TagTab> list = tabManager.getTabs();
		List<TagTab> sorted = new ArrayList<>(list);
		sorted.sort(Comparator.comparingInt(t -> names.indexOf(t.getTag())));
		list.clear();
		list.addAll(sorted);
	}

	/**
	 * Takes the category tabs out of Bank Tags' list, for when the plugin stops.
	 */
	void remove()
	{
		tabManager.getTabs().removeIf(this::isOurs);
		ourTabs.clear();
		drawn = Collections.emptyList();
		lastTabs = Collections.emptyList();
		lastIcons = Collections.emptyList();
		syncedTabs = Collections.emptyList();
	}

	private void saveOrder(List<TagTab> list)
	{
		order = new ArrayList<>();
		list.forEach(t -> order.add(t.getTag()));
		configManager.setConfiguration(SmartBankSorterConfig.GROUP, SmartBankSorterConfig.TAB_ORDER_KEY, Text.toCSV(order));
	}

	private boolean isOurs(TagTab tab)
	{
		return tab != null && ourTabs.get(tab.getTag()) == tab;
	}

	private static int indexOf(List<TagTab> list, TagTab tab)
	{
		for (int i = 0; i < list.size(); i++)
		{
			if (list.get(i) == tab)
			{
				return i;
			}
		}
		return -1;
	}
}
