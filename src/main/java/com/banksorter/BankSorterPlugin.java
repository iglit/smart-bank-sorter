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
import net.runelite.api.FontID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.ScriptID;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
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
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Bank Sorter",
	description = "Groups and sorts your bank by category, client-side only. Not for use with Bank Tags tag tabs.",
	tags = {"bank", "sort", "category", "organize", "layout"}
)
public class BankSorterPlugin extends Plugin
{
	// Bank grid geometry; the bank is 8 columns wide in both fixed and resizable mode.
	private static final int COLUMNS = 8;
	private static final int START_X = 51;
	private static final int PITCH_X = 48;
	private static final int PITCH_Y = 36;
	static final int ITEM_WIDTH = 36;
	static final int ITEM_HEIGHT = 32;
	private static final int GRID_WIDTH = COLUMNS * PITCH_X - 12;
	private static final int SECTION_GAP = 4;
	private static final int DIVIDER_COLOR = 0x4E4639;
	static final List<String> GEAR_CATEGORIES = Collections.unmodifiableList(Arrays.asList(
		Categories.MELEE, Categories.RANGED, Categories.MAGIC));
	private static final String BUILT_IN_SETS = readResource("sets.txt");
	private static final Map<String, Integer> FOOD_HEALS = parseHeals(readResource("food.txt"));
	// Number of items in bank tabs 1-9; tab N's items follow those of tabs 1..N-1.
	private static final int[] TAB_SIZE_VARBITS = {
		VarbitID.BANK_TAB_1, VarbitID.BANK_TAB_2, VarbitID.BANK_TAB_3, VarbitID.BANK_TAB_4, VarbitID.BANK_TAB_5,
		VarbitID.BANK_TAB_6, VarbitID.BANK_TAB_7, VarbitID.BANK_TAB_8, VarbitID.BANK_TAB_9,
	};
	static final Pattern DOSE = Pattern.compile("^(.*)\\((\\d+)\\)$");

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
	// Category the tab strip currently filters to (null = all).
	private String focusedTab;
	private UserRules tabGroups;
	private boolean scrollToTop;
	private TabStrip tabStrip;
	private ItemCopies itemCopies;
	private MoveToMenu moveToMenu;
	private int originalDragDeadZone = -1;
	private int originalDragDeadTime = -1;

	@Provides
	BankSorterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankSorterConfig.class);
	}

	@Override
	protected void startUp()
	{
		tabStrip = new TabStrip(client, config, () -> focusedTab, this::focus);
		itemCopies = new ItemCopies(client, this::equipmentOf);
		moveToMenu = new MoveToMenu(client, config, configManager, itemManager, () -> categoryOrder);
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
			itemCopies.clear();
			tabStrip.clear();
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
		if (config.enabled() && event.getActionParam1() == InterfaceID.Bankmain.ITEMS && "Examine".equals(event.getOption())
			&& event.getMenuEntry().getItemId() > 0)
		{
			moveToMenu.addMenu(event.getMenuEntry().getItemId());
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
		tabStrip.onMenuOptionClicked(event);
		itemCopies.onMenuOptionClicked(event);
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

	// Bank Tags' layoutTabs runs on this script, after the bank build, and treats every child of the item container
	// past its first four as one of its own tab backgrounds and icons: it hides them all and repositions whichever
	// pairs it thinks it owns, which includes ours. Run after it (priority -1) to put our tabs back.
	@Subscribe(priority = -1)
	public void onScriptPostFiredAfterBankTags(ScriptPostFired event)
	{
		if (!config.enabled() || !config.categoryTabs() || !shouldSort())
		{
			return;
		}
		if (event.getScriptId() == ScriptID.BANKMAIN_POPUP_TAB_DRAW)
		{
			tabStrip.refresh();
		}
		else if (event.getScriptId() == ScriptID.BANKMAIN_SIZE_CHECK)
		{
			// On resize Bank Tags re-lays out its strip from an invokeLater; queue ours after it.
			clientThread.invokeLater(tabStrip::refresh);
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
		itemCopies.addWidgetsTo(ours);

		// The game has already hidden items that don't match the search; the tab range is checked too.
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

		List<Slot> slots = new ArrayList<>();
		List<Widget> spares = new ArrayList<>();
		for (int i = 0; i < children.length; i++)
		{
			Widget w = children[i];
			if (w == null || ours.contains(w))
			{
				continue;
			}
			if (i >= items.length && w.getOriginalHeight() == ITEM_HEIGHT)
			{
				spares.add(w);
			}
			if (i >= items.length || i < from || i >= to || w.isHidden() || items[i].getId() <= 0)
			{
				// Tab separators, "tab N" labels, empty slots, search misses and items in other tabs.
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
			slots.add(new Slot(w, i, id, items[i].getQuantity(), name, categoryOf(id, name)));
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
			itemCopies.addHybridCopies(groups);
		}
		itemCopies.addDataCopies(groups);
		if (config.gearByQuality())
		{
			itemCopies.addTeleportGearCopies(groups);
		}

		Comparator<Slot> comparator = comparator();
		boolean large = config.headerSize() == BankSorterConfig.HeaderSize.LARGE;
		int headerHeight = large ? 18 : 14;
		int y = 0;
		int section = 0;
		int copies = 0;
		itemCopies.clearTargets();
		itemCopies.setSpares(spares);
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
					Widget w = slot.copy ? itemCopies.placeCopy(container, copies++, slot) : slot.widget;
					if (w == null)
					{
						// No spare bank slot left for this copy.
						continue;
					}
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
			tabStrip.placeTabs(tabs, icons);
		}
		else
		{
			tabStrip.hideTabs(0);
		}
		itemCopies.hideFrom(copies);
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

	ItemEquipmentStats equipmentOf(Slot slot)
	{
		ItemStats stats = itemManager.getItemStats(slot.itemId);
		return stats != null && stats.isEquipable() ? stats.getEquipment() : null;
	}

	private long stackValue(Slot slot)
	{
		return (long) itemManager.getItemPrice(slot.itemId) * slot.quantity;
	}

	// "Prayer potion(4)" -> "Prayer potion" so doses of one potion stay together, highest first.
	static String baseName(String name)
	{
		Matcher m = DOSE.matcher(name);
		return m.matches() ? m.group(1) : name;
	}

	static int dose(String name)
	{
		Matcher m = DOSE.matcher(name);
		return m.matches() ? Integer.parseInt(m.group(2)) : 0;
	}

	private void placeHeader(Widget container, int index, String category, int count, boolean isCollapsed,
		int y, int height, boolean large)
	{
		Widget header = WidgetPool.pooled(container, headerWidgets, index, WidgetType.TEXT);
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

		Widget divider = WidgetPool.pooled(container, dividerWidgets, index, WidgetType.RECTANGLE);
		divider.setFilled(true);
		divider.setTextColor(DIVIDER_COLOR);
		divider.setOriginalX(START_X);
		divider.setOriginalY(y + height);
		divider.setOriginalWidth(GRID_WIDTH);
		divider.setOriginalHeight(1);
		divider.setHidden(!config.dividers());
		divider.revalidate();
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

	private void focus(String category)
	{
		focusedTab = category.equals(focusedTab) ? null : category;
		scrollToTop = true;
		clientThread.invokeLater(this::rebuildBank);
	}

	private void hideOurWidgets()
	{
		hideHeaders(0);
		tabStrip.hideTabs(0);
		itemCopies.hideFrom(0);
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
