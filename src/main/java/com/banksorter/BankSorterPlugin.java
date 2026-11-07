package com.banksorter;

import com.google.inject.Provides;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.FontID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.ScriptID;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.tabs.TabSprites;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Bank Sorter",
	description = "Groups and sorts your bank by category, client-side only",
	tags = {"bank", "sort", "category", "organize", "layout"}
)
public class BankSorterPlugin extends Plugin
{
	// Bank grid geometry; the bank is 8 columns wide in both fixed and resizable mode.
	private static final int COLUMNS = 8;
	private static final int START_X = 51;
	private static final int PITCH_X = 48;
	private static final int PITCH_Y = 36;
	private static final int ITEM_WIDTH = 36;
	private static final int ITEM_HEIGHT = 32;
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
	private static final int GRID_WIDTH = COLUMNS * PITCH_X - 12;
	private static final int SECTION_GAP = 4;
	private static final int DIVIDER_COLOR = 0x4E4639;
	private static final List<String> GEAR_CATEGORIES = Collections.unmodifiableList(Arrays.asList(
		Categories.MELEE, Categories.RANGED, Categories.MAGIC));
	private static final String BUILT_IN_SETS = readResource("sets.txt");
	private static final Map<String, Integer> FOOD_HEALS = parseHeals(readResource("food.txt"));
	private static final String SEARCH_TITLE = "Showing items:";
	// Number of items in bank tabs 1-9; tab N's items follow those of tabs 1..N-1.
	private static final int[] TAB_SIZE_VARBITS = {
		VarbitID.BANK_TAB_1, VarbitID.BANK_TAB_2, VarbitID.BANK_TAB_3, VarbitID.BANK_TAB_4, VarbitID.BANK_TAB_5,
		VarbitID.BANK_TAB_6, VarbitID.BANK_TAB_7, VarbitID.BANK_TAB_8, VarbitID.BANK_TAB_9,
	};
	private static final Pattern DOSE = Pattern.compile("^(.*)\\((\\d+)\\)$");

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private BankSorterConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private BankSearch bankSearch;


	private Categoriser categoriser;
	private UserRules gearSets;
	private List<String> categoryOrder;
	private final Set<String> collapsed = new HashSet<>();
	private final Map<Integer, String> categoryCache = new HashMap<>();
	// Header text + divider widgets we created inside the bank item container, reused between builds.
	private final List<Widget> headerWidgets = new ArrayList<>();
	private final List<Widget> dividerWidgets = new ArrayList<>();
	// Faded display-only copies of hybrid gear shown in a second combat style.
	private final List<Widget> copyWidgets = new ArrayList<>();
	// Category tabs on the left edge of the bank, and the category they currently filter to (null = all).
	private final List<Widget> tabWidgets = new ArrayList<>();
	private final List<Widget> tabBackgrounds = new ArrayList<>();
	// Invisible widget under the strip that takes the mouse wheel, as Bank Tags does for its tag tabs.
	private final List<Widget> tabScrollWidgets = new ArrayList<>();
	private String focusedTab;
	private UserRules tabGroups;
	private int tabScroll;
	private int maxTabScroll;
	private boolean bankDumped;
	// Bank Tags is showing tag tabs of its own, so its arrows are busy scrolling those.
	private boolean bankTagsHasTabs;
	private List<String> lastTabs = Collections.emptyList();
	private List<Integer> lastTabIcons = Collections.emptyList();
	private boolean scrollToTop;
	// Copy widget -> the real bank slot it mirrors.
	private final Map<Widget, Slot> copyTargets = new HashMap<>();
	private int originalDragDeadZone = -1;
	private int originalDragDeadTime = -1;

	private static final class Slot
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

	@Provides
	BankSorterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankSorterConfig.class);
	}

	@Override
	protected void startUp()
	{
		loadSettings();
		clientThread.invokeLater(this::rebuildBank);
	}

	@Override
	protected void shutDown()
	{
		clientThread.invokeLater(() ->
		{
			restoreDrag();
			hideOurWidgets();
			headerWidgets.clear();
			dividerWidgets.clear();
			copyWidgets.clear();
			copyTargets.clear();
			tabWidgets.clear();
			tabBackgrounds.clear();
			tabScrollWidgets.clear();
			focusedTab = null;
			rebuildBank();
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!BankSorterConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		loadSettings();
		clientThread.invokeLater(() ->
		{
			if (!config.enabled())
			{
				restoreDrag();
				hideOurWidgets();
			}
			rebuildBank();
		});
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		// Cancel is the first entry the client adds each frame; anything we add after it sits above it.
		if (event.getMenuEntry().getType() == MenuAction.CANCEL)
		{
			addCopyMenu();
			return;
		}
		if (event.getActionParam1() == InterfaceID.Bankmain.SEARCH && "Search".equals(event.getOption()))
		{
			client.getMenu().createMenuEntry(-1)
				.setOption(config.enabled() ? "Unsort" : "Sort")
				.setTarget(ColorUtil.wrapWithColorTag("Bank", config.headerColor()))
				.setType(MenuAction.RUNELITE)
				.onClick(e -> configManager.setConfiguration(BankSorterConfig.GROUP, "enabled", !config.enabled()));
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
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

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.BANKMAIN_BUILD)
		{
			return;
		}
		if (config.enabled() && shouldSort())
		{
			layout();
		}
		else
		{
			// e.g. a Bank Tags tag tab is open: leave the bank to it.
			hideOurWidgets();
		}
	}

	/**
	 * The client builds no menu for widgets we create, so when hovering a copy add the real item's options,
	 * each targeting the real bank slot. Picking one does exactly what picking it on the real item does.
	 */
	private void addCopyMenu()
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

	private void loadSettings()
	{
		UserRules rules = UserRules.parse(config.customRules());
		categoriser = new Categoriser(rules);
		categoryCache.clear();
		// Tab lines are "Tab: category, category"; matching category names with the same rule syntax.
		tabGroups = UserRules.parse(config.tabGroups());
		// The user's sets first, so they can redefine a built-in one.
		gearSets = UserRules.parse(config.customSets() + "\n" + BUILT_IN_SETS);

		Set<String> order = new LinkedHashSet<>(splitList(config.categoryOrder()));
		order.addAll(rules.categories());
		order.addAll(Categories.DEFAULT_ORDER);
		categoryOrder = new ArrayList<>(order);

		collapsed.clear();
		collapsed.addAll(splitList(config.collapsed()));
	}

	private static String readResource(String name)
	{
		try (InputStream in = BankSorterPlugin.class.getResourceAsStream(name))
		{
			return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			log.warn("Couldn't read {}", name, e);
			return "";
		}
	}

	// "Name|heal" lines, keyed by lower-case name.
	private static Map<String, Integer> parseHeals(String text)
	{
		Map<String, Integer> heals = new HashMap<>();
		for (String line : text.split("\\R"))
		{
			int bar = line.lastIndexOf('|');
			if (line.startsWith("#") || bar <= 0)
			{
				continue;
			}
			try
			{
				heals.put(line.substring(0, bar).trim().toLowerCase(), Integer.parseInt(line.substring(bar + 1).trim()));
			}
			catch (NumberFormatException e)
			{
				log.debug("Bad food line: {}", line);
			}
		}
		return heals;
	}

	private static List<String> splitList(String text)
	{
		List<String> out = new ArrayList<>();
		for (String s : text.split("[\\n,]"))
		{
			if (!s.trim().isEmpty())
			{
				out.add(s.trim());
			}
		}
		return out;
	}

	private void rebuildBank()
	{
		if (client.getWidget(InterfaceID.Bankmain.ITEMS) != null)
		{
			bankSearch.layoutBank();
		}
	}

	private boolean shouldSort()
	{
		// RuneLite's Bank Tags plugin lays out tag tabs itself.
		Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
		if (title != null && title.getText() != null && title.getText().startsWith("Tag tab"))
		{
			return false;
		}
		return config.applyInTabs() || client.getVarbitValue(VarbitID.BANK_CURRENTTAB) == 0;
	}

	private void layout()
	{
		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (container == null || bank == null || container.getChildren() == null)
		{
			return;
		}

		Widget[] children = container.getChildren();
		Item[] items = bank.getItems();
		Set<Widget> ours = new HashSet<>(headerWidgets);
		ours.addAll(dividerWidgets);
		ours.addAll(copyWidgets);

		// Work out the selected tab and search ourselves: the game also hides items scrolled out of view, so its
		// hidden flags can't tell us what's filtered out.
		int from = 0;
		int to = items.length;
		int bankTab = client.getVarbitValue(VarbitID.BANK_CURRENTTAB);
		if (bankTab > 0 && bankTab <= TAB_SIZE_VARBITS.length)
		{
			for (int t = 1; t < bankTab; t++)
			{
				from += client.getVarbitValue(TAB_SIZE_VARBITS[t - 1]);
			}
			to = Math.min(items.length, from + client.getVarbitValue(TAB_SIZE_VARBITS[bankTab - 1]));
		}
		String query = searchQuery();

		List<Slot> slots = new ArrayList<>();
		for (int i = 0; i < children.length; i++)
		{
			Widget w = children[i];
			if (w == null || ours.contains(w))
			{
				continue;
			}
			if (i >= items.length || i < from || i >= to || items[i].getId() <= 0)
			{
				// Tab separators, "tab N" labels, empty slots and items in other tabs.
				w.setHidden(true);
				continue;
			}
			if (config.hideFillers() && items[i].getId() == ItemID.BANK_FILLER)
			{
				w.setHidden(true);
				continue;
			}

			int id = itemManager.canonicalize(items[i].getId());
			String name = itemManager.getItemComposition(id).getName();
			if (query != null && !name.toLowerCase().contains(query))
			{
				w.setHidden(true);
				continue;
			}
			slots.add(new Slot(w, i, id, items[i].getQuantity(), name, categoryOf(id, name)));
		}

		// TEMP: one-time dump of the bank for building item data; only complete with no search and all-items view.
		if (!bankDumped && query == null && bankTab == 0)
		{
			bankDumped = true;
			for (Slot s : slots)
			{
				log.debug("BANKDUMP {}|{}|{}", s.itemId, s.name, s.category);
			}
		}

		Map<String, List<Slot>> groups = new LinkedHashMap<>();
		for (String category : categoryOrder)
		{
			groups.put(category, new ArrayList<>());
		}
		for (Slot slot : slots)
		{
			groups.computeIfAbsent(slot.category, k -> new ArrayList<>()).add(slot);
		}

		if (config.gearByQuality())
		{
			addHybridCopies(groups);
		}
		addDataCopies(groups);
		if (config.gearByQuality())
		{
			addTeleportGearCopies(groups);
		}

		Comparator<Slot> comparator = comparator();
		boolean large = config.headerSize() == BankSorterConfig.HeaderSize.LARGE;
		int headerHeight = large ? 18 : 14;
		int y = 0;
		int section = 0;
		int copies = 0;
		copyTargets.clear();
		// Tab name -> icon item, for tabs that have something in them.
		Map<String, Integer> tabIcons = new HashMap<>();
		for (Map.Entry<String, List<Slot>> group : groups.entrySet())
		{
			String tab = tabGroups.match(-1, group.getKey());
			if (tab != null && !group.getValue().isEmpty())
			{
				tabIcons.putIfAbsent(tab, -1);
			}
		}
		if (focusedTab != null && !tabIcons.containsKey(focusedTab))
		{
			focusedTab = null;
		}
		for (Map.Entry<String, List<Slot>> group : groups.entrySet())
		{
			List<Slot> groupSlots = group.getValue();
			if (groupSlots.isEmpty())
			{
				continue;
			}
			String category = group.getKey();

			// Blighted supplies (Wilderness-only) always go last, laid out the same way as the rest.
			List<Slot> normal = new ArrayList<>();
			List<Slot> blighted = new ArrayList<>();
			for (Slot s : groupSlots)
			{
				(s.name.toLowerCase().startsWith("blighted ") ? blighted : normal).add(s);
			}
			List<List<Slot>> rows = new ArrayList<>(orderRows(category, normal, comparator));
			if (!blighted.isEmpty())
			{
				rows.addAll(orderRows(category, blighted, comparator));
			}
			String tab = tabGroups.match(-1, category);
			if (tab != null && tabIcons.get(tab) == -1)
			{
				tabIcons.put(tab, firstItem(rows));
			}

			boolean isCollapsed = collapsed.contains(category);
			boolean filteredOut = focusedTab != null && !focusedTab.equals(tab);
			if (!filteredOut)
			{
				if (section > 0)
				{
					y += SECTION_GAP;
				}
				int realCount = (int) groupSlots.stream().filter(s -> !s.copy).count();
				placeHeader(container, section++, category, realCount, isCollapsed, y, headerHeight, large);
				y += headerHeight + 2;
			}

			for (List<Slot> row : rows)
			{
				for (int i = 0; i < row.size(); i++)
				{
					Slot slot = row.get(i);
					if (slot == null || (slot.copy && filteredOut))
					{
						// Gap in an outfit column, or a copy we don't need to draw.
						continue;
					}
					Widget w = slot.copy ? placeCopy(container, copies++, slot) : slot.widget;
					if (isCollapsed || filteredOut)
					{
						w.setHidden(true);
						continue;
					}
					w.setHidden(false);
					w.setOriginalX(START_X + (i % COLUMNS) * PITCH_X);
					w.setOriginalY(y + (i / COLUMNS) * PITCH_Y);
					if (!slot.copy)
					{
						blockDrag(w);
					}
					w.revalidate();
				}
				if (!isCollapsed && !filteredOut)
				{
					y += ((row.size() + COLUMNS - 1) / COLUMNS) * PITCH_Y;
				}
			}
		}
		if (config.categoryTabs())
		{
			List<String> tabs = new ArrayList<>(tabGroups.categories());
			tabs.retainAll(tabIcons.keySet());
			List<Integer> icons = new ArrayList<>();
			tabs.forEach(t -> icons.add(tabIcons.get(t)));
			placeTabs(tabs, icons);
			// On bank open the first placement draws wrong (icons hidden behind the backgrounds) until the strip
			// is laid out again, which scrolling did; so lay it out once more on the next frame.
			clientThread.invokeLater(() ->
			{
				if (config.enabled() && config.categoryTabs() && client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER) != null)
				{
					placeTabs(lastTabs, lastTabIcons);
				}
			});
		}
		else
		{
			hideTabs(0);
		}
		for (int i = copies; i < copyWidgets.size(); i++)
		{
			copyWidgets.get(i).setHidden(true);
		}
		hideHeaders(section);

		int scrollHeight = Math.max(y, container.getHeight());
		container.setScrollHeight(scrollHeight);
		int scrollY = scrollToTop ? 0 : Math.min(container.getScrollY(), Math.max(0, scrollHeight - container.getHeight()));
		scrollToTop = false;
		container.setScrollY(scrollY);
		container.revalidateScroll();
		// We're inside the bank build script's callback and scripts can't nest, so redraw the scrollbar after it.
		clientThread.invokeLater(() ->
			client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Bankmain.SCROLLBAR, InterfaceID.Bankmain.ITEMS, scrollY));
	}

	/**
	 * Gear useful to several styles (barrows gloves, fire cape) lives in one category. Show a faded copy in
	 * each other combat style where it beats everything that style already has in that slot.
	 */
	/**
	 * Items the bundled data lists under extra categories (logs for Firemaking, Graceful for Farming...) get a
	 * withdrawable copy in each of them.
	 */
	private void addDataCopies(Map<String, List<Slot>> groups)
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
	private void addTeleportGearCopies(Map<String, List<Slot>> groups)
	{
		List<Slot> teleports = groups.get(Categories.TELEPORTS);
		if (teleports == null)
		{
			return;
		}

		Map<String, Slot> best = new HashMap<>();
		for (Slot s : teleports)
		{
			ItemEquipmentStats e = equipmentOf(s);
			if (s.copy || e == null || e.getSlot() == EquipmentInventorySlot.WEAPON.getSlotIdx())
			{
				continue;
			}
			best.merge(baseName(s.name).toLowerCase(), s, (a, b) -> dose(a.name) >= dose(b.name) ? a : b);
		}
		for (Slot s : best.values())
		{
			ItemEquipmentStats e = equipmentOf(s);
			String style = Categoriser.gear(s.name.toLowerCase(), e);
			if (GEAR_CATEGORIES.contains(style) && GearOrder.offence(style, e) > 0)
			{
				List<Slot> target = groups.computeIfAbsent(style, k -> new ArrayList<>());
				if (target.stream().noneMatch(t -> t.itemId == s.itemId))
				{
					target.add(new Slot(s.widget, s.index, s.itemId, s.quantity, s.name, style, true));
				}
			}
		}
	}

	private void addHybridCopies(Map<String, List<Slot>> groups)
	{
		for (String style : GEAR_CATEGORIES)
		{
			List<Slot> styleSlots = groups.get(style);
			if (styleSlots == null)
			{
				continue;
			}

			Map<Integer, Integer> bestBySlot = new HashMap<>();
			for (Slot s : styleSlots)
			{
				ItemEquipmentStats e = equipmentOf(s);
				if (e != null)
				{
					bestBySlot.merge(e.getSlot(), GearOrder.score(style, e), Math::max);
				}
			}

			Map<Integer, Slot> candidates = new HashMap<>();
			for (String other : GEAR_CATEGORIES)
			{
				if (other.equals(style) || !groups.containsKey(other))
				{
					continue;
				}
				for (Slot s : groups.get(other))
				{
					ItemEquipmentStats e = equipmentOf(s);
					boolean blessing = e != null && e.getSlot() == EquipmentInventorySlot.AMMO.getSlotIdx();
					if (s.copy || e == null || e.getSlot() == EquipmentInventorySlot.WEAPON.getSlotIdx()
						|| (!blessing && GearOrder.offence(style, e) <= 0))
					{
						continue;
					}
					int score = GearOrder.score(style, e);
					Slot current = candidates.get(e.getSlot());
					if (score > bestBySlot.getOrDefault(e.getSlot(), 0)
						&& (current == null || score > GearOrder.score(style, equipmentOf(current))))
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
	 * A copy shows the real item's icon and quantity; its menu comes from {@link #addCopyMenu}.
	 */
	private Widget placeCopy(Widget container, int index, Slot slot)
	{
		Widget real = slot.widget;
		Widget w = pooled(container, copyWidgets, index, WidgetType.GRAPHIC);
		w.setItemId(real.getItemId());
		w.setItemQuantity(real.getItemQuantity());
		w.setItemQuantityMode(real.getItemQuantityMode());
		w.setOriginalWidth(ITEM_WIDTH);
		w.setOriginalHeight(ITEM_HEIGHT);
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

	private String categoryOf(int itemId, String name)
	{
		return categoryCache.computeIfAbsent(itemId, id ->
		{
			ItemComposition comp = itemManager.getItemComposition(id);
			ItemStats stats = itemManager.getItemStats(id);
			return categoriser.categorise(id, name, comp.getInventoryActions(),
				stats != null && stats.isEquipable() ? stats.getEquipment() : null);
		});
	}

	private Comparator<Slot> comparator()
	{
		Comparator<Slot> byIndex = Comparator.comparingInt(s -> s.index);
		Comparator<Slot> byName = Comparator.<Slot, String>comparing(s -> baseName(s.name), String.CASE_INSENSITIVE_ORDER)
			.thenComparing(Comparator.comparingInt((Slot s) -> dose(s.name)).reversed())
			.thenComparing(byIndex);
		switch (config.itemOrder())
		{
			case VALUE_HIGH_TO_LOW:
				return Comparator.comparingLong(this::stackValue).reversed().thenComparing(byName);
			case VALUE_LOW_TO_HIGH:
				return Comparator.comparingLong(this::stackValue).thenComparing(byName);
			case BANK_ORDER:
				return byIndex;
			default:
				return byName;
		}
	}

	/**
	 * Hitpoints a food heals per bite, from the bundled wiki table; 0 if unknown.
	 */
	private int healOf(Slot slot)
	{
		String name = slot.name.toLowerCase();
		Integer heal = FOOD_HEALS.get(name);
		if (heal == null)
		{
			// Part-eaten food heals the same per bite as the whole: "2/3 cake", "Half a meat pie", "Slice of cake".
			String whole = name.replaceFirst("^(2/3 |1/2 |half an? |slice of )", "")
				.replace("chocolate slice", "chocolate cake");
			heal = FOOD_HEALS.get(whole);
		}
		return heal == null ? 0 : heal;
	}

	private ItemEquipmentStats equipmentOf(Slot slot)
	{
		ItemStats stats = itemManager.getItemStats(slot.itemId);
		return stats != null && stats.isEquipable() ? stats.getEquipment() : null;
	}

	private long stackValue(Slot slot)
	{
		return (long) itemManager.getItemPrice(slot.itemId) * slot.quantity;
	}

	// "Prayer potion(4)" -> "Prayer potion" so doses of one potion stay together, highest first.
	private static String baseName(String name)
	{
		Matcher m = DOSE.matcher(name);
		return m.matches() ? m.group(1) : name;
	}

	private static int dose(String name)
	{
		Matcher m = DOSE.matcher(name);
		return m.matches() ? Integer.parseInt(m.group(2)) : 0;
	}

	private void placeHeader(Widget container, int index, String category, int count, boolean isCollapsed,
		int y, int height, boolean large)
	{
		Widget header = pooled(container, headerWidgets, index, WidgetType.TEXT);
		String text = (isCollapsed ? "+ " : "- ") + category;
		if (config.showCounts())
		{
			text += ColorUtil.wrapWithColorTag(" (" + count + ")", config.headerColor().darker());
		}
		header.setText(text);
		header.setFontId(large ? FontID.BOLD_12 : FontID.PLAIN_11);
		header.setTextColor(config.headerColor().getRGB() & 0xFFFFFF);
		header.setTextShadowed(true);
		header.setXTextAlignment(WidgetTextAlignment.LEFT);
		header.setYTextAlignment(WidgetTextAlignment.CENTER);
		header.setOriginalX(START_X);
		header.setOriginalY(y);
		header.setOriginalWidth(GRID_WIDTH);
		header.setOriginalHeight(height);
		header.setName(ColorUtil.wrapWithColorTag(category, config.headerColor()));
		header.setAction(0, isCollapsed ? "Expand" : "Collapse");
		header.setHasListener(true);
		header.setOnOpListener((JavaScriptCallback) e -> toggleCollapsed(category));
		header.setHidden(false);
		header.revalidate();

		Widget divider = pooled(container, dividerWidgets, index, WidgetType.RECTANGLE);
		divider.setFilled(true);
		divider.setTextColor(DIVIDER_COLOR);
		divider.setOriginalX(START_X);
		divider.setOriginalY(y + height);
		divider.setOriginalWidth(GRID_WIDTH);
		divider.setOriginalHeight(1);
		divider.setHidden(!config.dividers());
		divider.revalidate();
	}

	/**
	 * Returns our widget at {@code index}, creating it if the bank rebuild deleted it.
	 */
	private Widget pooled(Widget container, List<Widget> pool, int index, int type)
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

	private void hideHeaders(int from)
	{
		for (List<Widget> pool : Arrays.asList(headerWidgets, dividerWidgets))
		{
			for (int i = from; i < pool.size(); i++)
			{
				pool.get(i).setHidden(true);
			}
		}
	}

	private List<List<Slot>> orderRows(String category, List<Slot> slots, Comparator<Slot> comparator)
	{
		if (slots.isEmpty())
		{
			return Collections.emptyList();
		}
		if (config.gearByQuality() && GEAR_CATEGORIES.contains(category))
		{
			return GearOrder.order(slots, category, this::equipmentOf,
				s -> itemManager.getItemPrice(s.itemId), s -> gearSets.match(s.itemId, s.name), comparator,
				config.outfitLayout() == BankSorterConfig.OutfitLayout.COLUMNS, COLUMNS);
		}
		if (config.smartConsumables() && Categories.FOOD.equals(category))
		{
			return ConsumableOrder.food(slots, s -> s.name, this::healOf, comparator);
		}
		if (config.smartConsumables() && Categories.POTIONS.equals(category))
		{
			return ConsumableOrder.potions(slots, s -> s.name, comparator, COLUMNS);
		}
		if (config.smartConsumables() && Categories.TELEPORTS.equals(category))
		{
			return Collections.singletonList(TeleportOrder.order(slots, s -> s.name, comparator));
		}
		slots.sort(Comparator.<Slot>comparingInt(s -> ItemData.tier(s.name)).thenComparing(comparator));
		return Collections.singletonList(slots);
	}

	/**
	 * The bank search text, lower case, or null when not searching. Read from the bank title, which shows
	 * "Showing items: <query>" while a search is active.
	 */
	private String searchQuery()
	{
		Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
		String text = title == null || title.getText() == null ? "" : Text.removeTags(title.getText());
		if (!text.startsWith(SEARCH_TITLE))
		{
			return null;
		}
		String query = text.substring(SEARCH_TITLE.length()).replace("*", "").trim().toLowerCase();
		return query.isEmpty() ? null : query;
	}

	private static int firstItem(List<List<Slot>> rows)
	{
		for (List<Slot> row : rows)
		{
			for (Slot slot : row)
			{
				if (slot != null)
				{
					return slot.itemId;
				}
			}
		}
		return -1;
	}

	/**
	 * Tabs down the left edge of the bank, under Bank Tags' own tab buttons if it shows any. Each tab is a
	 * group of categories; clicking one shows only those categories, clicking it again shows everything.
	 * Laid out the way Bank Tags lays out its tag tabs: full-size tabs, a window of them when they don't all
	 * fit, and an invisible widget under the strip that takes the mouse wheel.
	 */
	private void placeTabs(List<String> categories, List<Integer> icons)
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
		// its own. Its tag tabs use the same sprites as ours, so skip our own backgrounds while looking.
		Widget up = null;
		Widget down = null;
		int belowOtherTabs = 0;
		Set<Widget> ours = new HashSet<>(tabBackgrounds);
		Widget[] existing = parent.getDynamicChildren();
		for (Widget w : existing == null ? new Widget[0] : existing)
		{
			if (w == null || w.isHidden() || ours.contains(w))
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
		// DEBUG: strip geometry, logged on every bank build and every scroll step.
		log.debug("Tab strip: container height {}, x {}, top {}, bottom {}, {} of {} tabs visible, scroll {}, Bank Tags arrows {}, Bank Tags tabs {}",
			parent.getHeight(), x, top, bottom, visible, categories.size(), tabScroll, up != null && down != null, bankTagsHasTabs);
		if (visible <= 0)
		{
			// Bank Tags has filled the strip with its own tabs; stay out of its way.
			hideTabs(0);
			return;
		}
		maxTabScroll = Math.max(0, categories.size() - visible);
		tabScroll = Math.max(0, Math.min(tabScroll, maxTabScroll));
		resetTabsIfMisordered(parent);

		// Created before the tabs so it sits under them, like Bank Tags' own. Covering the margins too means the
		// wheel never reaches the bank items through a gap in the strip.
		Widget scroll = pooled(parent, tabScrollWidgets, 0, WidgetType.TEXT);
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
			boolean active = category.equals(focusedTab);
			int row = i - tabScroll;
			boolean shown = row >= 0 && row < visible;
			int y = top + row * TAB_PITCH;

			Widget back = pooled(parent, tabBackgrounds, i, WidgetType.GRAPHIC);
			back.setSpriteId(active ? TabSprites.TAB_BACKGROUND_ACTIVE.getSpriteId() : TabSprites.TAB_BACKGROUND.getSpriteId());
			back.setOriginalX(x);
			back.setOriginalY(y);
			back.setOriginalWidth(width);
			back.setOriginalHeight(TAB_HEIGHT);
			back.setHidden(!shown);
			back.revalidate();

			Widget tab = pooled(parent, tabWidgets, i, WidgetType.GRAPHIC);
			tab.setItemId(icons.get(i));
			tab.setItemQuantityMode(ItemQuantityMode.NEVER);
			tab.setBorderType(1);
			tab.setOriginalX(x + TAB_ICON_X);
			tab.setOriginalY(y + TAB_ICON_Y);
			tab.setOriginalWidth(ITEM_WIDTH);
			tab.setOriginalHeight(ITEM_HEIGHT);
			tab.setName(ColorUtil.wrapWithColorTag(category, config.headerColor()));
			tab.setAction(0, active ? "Show all" : "Show");
			tab.setHasListener(true);
			tab.setOnOpListener((JavaScriptCallback) e -> focus(category));
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

	private void focus(String category)
	{
		focusedTab = category.equals(focusedTab) ? null : category;
		scrollToTop = true;
		clientThread.invokeLater(this::rebuildBank);
	}

	private void scrollTabs(int direction)
	{
		int next = Math.max(0, Math.min(tabScroll + Integer.signum(direction), maxTabScroll));
		// DEBUG: every wheel notch or arrow click over the strip lands here.
		log.debug("Tab strip scroll by {}: {} -> {} (max {})", direction, tabScroll, next, maxTabScroll);
		// At an end already (or everything fits): leave the strip alone so it doesn't flicker.
		if (next != tabScroll)
		{
			// Only the strip moves; rebuilding the bank here made the item view jump.
			tabScroll = next;
			placeTabs(lastTabs, lastTabIcons);
		}
	}

	/**
	 * Widgets draw in child order. When the bank interface is rebuilt some of our tab widgets can be dropped
	 * while others survive, and re-creating only the dropped ones puts backgrounds above surviving icons. If
	 * anything is missing or out of order, start the whole strip over so it's created wheel area, then
	 * background and icon per tab.
	 */
	private void resetTabsIfMisordered(Widget parent)
	{
		Widget[] children = parent.getDynamicChildren();
		Map<Widget, Integer> index = new HashMap<>();
		for (int i = 0; children != null && i < children.length; i++)
		{
			index.put(children[i], i);
		}

		boolean ok = tabBackgrounds.size() == tabWidgets.size();
		int previous = tabScrollWidgets.isEmpty() ? -1 : index.getOrDefault(tabScrollWidgets.get(0), Integer.MAX_VALUE);
		for (int i = 0; ok && i < tabWidgets.size(); i++)
		{
			int back = index.getOrDefault(tabBackgrounds.get(i), -1);
			int icon = index.getOrDefault(tabWidgets.get(i), -1);
			ok = back > previous && icon > back;
			previous = icon;
		}
		if (!ok)
		{
			for (List<Widget> pool : Arrays.asList(tabScrollWidgets, tabBackgrounds, tabWidgets))
			{
				pool.forEach(w -> w.setHidden(true));
				pool.clear();
			}
		}
	}

	private void hideTabs(int from)
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

	private void hideOurWidgets()
	{
		hideHeaders(0);
		hideTabs(0);
		copyWidgets.forEach(w -> w.setHidden(true));
	}

	private void toggleCollapsed(String category)
	{
		if (!collapsed.remove(category))
		{
			collapsed.add(category);
		}
		List<String> sorted = new ArrayList<>(collapsed);
		Collections.sort(sorted);
		configManager.setConfiguration(BankSorterConfig.GROUP, BankSorterConfig.COLLAPSED_KEY, Text.toCSV(sorted));
	}

	// Items are only moved visually, so dragging one would swap real bank slots the user can't see.
	private void blockDrag(Widget w)
	{
		if (originalDragDeadZone < 0)
		{
			originalDragDeadZone = w.getDragDeadZone();
			originalDragDeadTime = w.getDragDeadTime();
		}
		w.setDragDeadZone(Integer.MAX_VALUE);
		w.setDragDeadTime(Integer.MAX_VALUE);
	}

	private void restoreDrag()
	{
		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (container == null || container.getChildren() == null || originalDragDeadZone < 0)
		{
			return;
		}
		for (Widget w : container.getChildren())
		{
			if (w != null && w.getDragDeadZone() == Integer.MAX_VALUE)
			{
				w.setDragDeadZone(originalDragDeadZone);
				w.setDragDeadTime(originalDragDeadTime);
			}
		}
	}
}
