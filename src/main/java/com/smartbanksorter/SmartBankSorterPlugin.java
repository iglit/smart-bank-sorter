package com.smartbanksorter;

import com.google.common.base.Strings;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.event.KeyEvent;
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
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Smart Bank Sorter",
	description = "Groups and sorts your bank by category, client-side only. If you use Bank Tags tag tabs, turn off Category tabs.",
	tags = {"bank", "sort", "category", "organize", "layout"}
)
public class SmartBankSorterPlugin extends Plugin
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
	private static final String BUILT_IN_SETS = Resources.read("sets.txt");
	private static final Map<String, Integer> FOOD_HEALS = parseHeals(Resources.read("food.txt"));

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	private SmartBankSorterConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private BankSearch bankSearch;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private SpriteManager spriteManager;

	private Categoriser categoriser;
	private UserRules gearSets;
	private List<String> categoryOrder;
	private final Set<String> collapsed = new HashSet<>();
	private final Map<Integer, String> categoryCache = new HashMap<>();
	// Header text + divider widgets we created inside the bank item container, reused between builds.
	private final List<Widget> headerWidgets = new ArrayList<>();
	private final List<Widget> dividerWidgets = new ArrayList<>();
	// The "jump to <category>" line shown in the chatbox above the bank search prompt.
	private final List<Widget> suggestionWidgets = new ArrayList<>();
	// Category the tab strip currently filters to (null = all).
	private String focusedTab;
	private UserRules tabGroups;
	private boolean scrollToTop;
	// Scroll position before the bank build script clamps it to the vanilla grid, which is shorter than ours.
	// Read from the varc the game keeps it in, which also survives closing the bank.
	private int scrollBeforeBuild = -1;
	// Category the next layout scrolls to.
	private String jumpCategory;
	// Categories each item was last shown in, copies included, so a category search finds the copies too, and the
	// categories that had anything in them, which are the ones worth suggesting.
	private Map<Integer, Set<String>> shownIn = new HashMap<>();
	private List<String> shownCategories = Collections.emptyList();
	// Whether the current bank build is ours to sort. Worked out once per build, as the search filter runs per item.
	private boolean sortingThisBuild;
	// The last search the filter saw and the category it names, kept since the filter runs once per item.
	private String filterText;
	private String filterCategory;
	private TabStrip tabStrip;
	private ItemCopies itemCopies;
	private MoveToMenu moveToMenu;
	private int originalDragDeadZone = -1;
	private int originalDragDeadTime = -1;

	@Provides
	SmartBankSorterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SmartBankSorterConfig.class);
	}

	@Override
	protected void startUp()
	{
		tabStrip =new TabStrip(client, config, () -> focusedTab, this::focus);
		eventBus.register(afterBankTags);
		itemCopies = new ItemCopies(this::equipmentOf);
		moveToMenu = new MoveToMenu(client, config, configManager, itemManager, () -> categoryOrder);
		ItemData.load();
		loadSettings();
		keyManager.registerKeyListener(searchEnter);
		spriteManager.addSpriteOverrides(TabSprite.values());
		clientThread.invokeLater(this::rebuildBank);
	}

	@Override
	protected void shutDown()
	{
		eventBus.unregister(afterBankTags);
		keyManager.unregisterKeyListener(searchEnter);
		spriteManager.removeSpriteOverrides(TabSprite.values());
		clientThread.invokeLater(() ->
		{
			hideOurWidgets();
			headerWidgets.clear();
			dividerWidgets.clear();
			suggestionWidgets.clear();
			itemCopies.clear();
			tabStrip.clear();
			focusedTab = null;
			rebuildBank();
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!SmartBankSorterConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		// On the client thread: the config panel changes settings from the Swing thread, mid-layout for all we know.
		clientThread.invokeLater(() ->
		{
			loadSettings();
			if (!config.enabled())
			{
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
				.onClick(e -> configManager.setConfiguration(SmartBankSorterConfig.GROUP, "enabled", !config.enabled()));
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		tabStrip.onMenuOptionClicked(event);
		itemCopies.onMenuOptionClicked(event);
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_BUILD)
		{
			scrollBeforeBuild = client.getVarcIntValue(VarClientID.BANK_SCROLLPOS);
			sortingThisBuild = config.enabled() && shouldSort();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			hideSuggestion();
		}
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (!"bankSearchFilter".equals(event.getEventName()) || !sortingThisBuild || !config.categorySearch())
		{
			return;
		}
		int[] intStack = client.getIntStack();
		int intStackSize = client.getIntStackSize();
		Object[] objectStack = client.getObjectStack();
		int itemId = intStack[intStackSize - 1];
		String text = (String) objectStack[client.getObjectStackSize() - 1];
		if (itemId <= 0 || text.isEmpty())
		{
			return;
		}
		if (!text.equals(filterText))
		{
			filterText = text;
			filterCategory = CategorySearch.parse(text, categoryOrder);
		}
		if (filterCategory == null)
		{
			return;
		}

		int id = itemManager.canonicalize(itemId);
		Set<String> categories = shownIn.get(id);
		if (categories == null)
		{
			categories = Collections.singleton(categoryOf(id, itemManager.getItemComposition(id).getName()));
		}
		// On top of the game's own name matches.
		if (categories.contains(filterCategory))
		{
			intStack[intStackSize - 2] = 1;
		}
	}

	/**
	 * Enter in the bank search while a category is suggested closes the search and jumps to that category.
	 */
	private final KeyListener searchEnter = new KeyListener()
	{
		@Override
		public void keyTyped(KeyEvent e)
		{
		}

		@Override
		public void keyPressed(KeyEvent e)
		{
			// Read before the game handles the key, which may clear the input.
			if (e.getKeyCode() == KeyEvent.VK_ENTER && config.categorySearch()
				&& client.getVarcIntValue(VarClientID.MESLAYERMODE) == InputType.SEARCH.getType())
			{
				String text = client.getVarcStrValue(VarClientID.MESLAYERINPUT);
				clientThread.invokeLater(() -> jumpFromSearch(text));
			}
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
		}
	};

	private void jumpFromSearch(String text)
	{
		if (Strings.isNullOrEmpty(text) || !config.enabled() || client.getWidget(InterfaceID.Bankmain.ITEMS) == null
			|| !shouldSort())
		{
			return;
		}
		String category = CategorySearch.suggest(text, shownCategories);
		if (category != null)
		{
			jumpTo(category);
		}
	}

	// Closes the search and scrolls the full bank to a category.
	private void jumpTo(String category)
	{
		jumpCategory = category;
		focusedTab = null;
		bankSearch.reset(true);
	}

	private void placeSuggestion(String category)
	{
		Widget layer = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
		Widget prompt = client.getWidget(InterfaceID.Chatbox.MES_TEXT);
		Widget input = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
		if (layer == null || prompt == null || input == null)
		{
			return;
		}
		// In the empty space above the search prompt, styled like it, with the category in the colour of the input.
		int height = 16;
		Widget w = WidgetPool.pooled(layer, suggestionWidgets, 0, WidgetType.TEXT);
		w.setText("Press Enter to jump to " + ColorUtil.wrapWithColorTag(category, new Color(input.getTextColor())));
		w.setFontId(prompt.getFontId());
		w.setTextColor(prompt.getTextColor());
		w.setTextShadowed(prompt.getTextShadowed());
		w.setXTextAlignment(WidgetTextAlignment.CENTER);
		w.setYTextAlignment(WidgetTextAlignment.CENTER);
		w.setOriginalX(prompt.getRelativeX());
		w.setOriginalY(Math.max(0, prompt.getRelativeY() - height - 2));
		w.setOriginalWidth(prompt.getWidth());
		w.setOriginalHeight(height);
		w.setName(ColorUtil.wrapWithColorTag(category, config.headerColor()));
		w.setAction(0, "Jump to");
		w.setHasListener(true);
		w.setOnOpListener((JavaScriptCallback) e -> jumpTo(category));
		w.setHidden(false);
		w.revalidate();
	}

	private void hideSuggestion()
	{
		suggestionWidgets.forEach(w -> w.setHidden(true));
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.MESSAGE_LAYER_OPEN || event.getScriptId() == ScriptID.MESSAGE_LAYER_CLOSE)
		{
			// The chatbox reuses this layer for other prompts (Withdraw-X, ...); the next bank build puts it back.
			hideSuggestion();
			return;
		}
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
	/**
	 * RuneLite requires a subscriber method to be named after its event, and the plugin already has an
	 * onScriptPostFired, so this lower-priority one lives on its own object registered with the event bus.
	 */
	public static final class AfterBankTags
	{
		private final SmartBankSorterPlugin plugin;

		AfterBankTags(SmartBankSorterPlugin plugin)
		{
			this.plugin = plugin;
		}

		@Subscribe(priority = -1)
		public void onScriptPostFired(ScriptPostFired event)
		{
			plugin.afterBankTagsScript(event);
		}
	}

	private final AfterBankTags afterBankTags = new AfterBankTags(this);

	private void afterBankTagsScript(ScriptPostFired event)
	{
		// This sees every script the client runs, so rule out the rest before the dearer checks.
		int script = event.getScriptId();
		if (script != ScriptID.BANKMAIN_POPUP_TAB_DRAW && script != ScriptID.BANKMAIN_SIZE_CHECK)
		{
			return;
		}
		if (!config.enabled() || !config.categoryTabs() || !shouldSort())
		{
			return;
		}
		if (script == ScriptID.BANKMAIN_POPUP_TAB_DRAW)
		{
			tabStrip.refresh();
		}
		else
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
		filterText = null;
		// Tab lines are "Tab: category, category"; matching category names with the same rule syntax.
		tabGroups = UserRules.parse(config.tabGroups());
		// The user's sets first, so they can redefine a built-in one.
		gearSets = UserRules.parse(config.customSets() + "\n" + BUILT_IN_SETS);

		// Rules only say what goes in a category, not where it sits: new categories they name go after the built-ins.
		Set<String> order = new LinkedHashSet<>(splitList(config.categoryOrder()));
		order.addAll(Categories.DEFAULT_ORDER);
		order.addAll(rules.categories());
		categoryOrder = new ArrayList<>(order);

		collapsed.clear();
		collapsed.addAll(splitList(config.collapsed()));
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
		// Tag tabs, and other plugins' views opened through Bank Tags (e.g. an Inventory Setups setup), lay out the
		// bank themselves.
		if (activeBankTag() != null)
		{
			return false;
		}
		Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
		if (title != null && title.getText() != null && title.getText().startsWith("Tag tab"))
		{
			return false;
		}
		return config.applyInTabs() || client.getVarbitValue(VarbitID.BANK_CURRENTTAB) == 0;
	}

	/**
	 * The tag Bank Tags currently shows, or null. Looked up rather than injected: injecting BankTagsService would
	 * need a plugin dependency, which forces Bank Tags on.
	 */
	private String activeBankTag()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin instanceof BankTagsService && pluginManager.isPluginEnabled(plugin))
			{
				return ((BankTagsService) plugin).getActiveTag();
			}
		}
		return null;
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
			if (i >= items.length || w.isHidden() || items[i].getId() <= 0)
			{
				// Tab separators, "tab N" labels and empty slots, and what the game has already hidden: search
				// misses and items in other tabs.
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
		boolean large = config.headerSize() == SmartBankSorterConfig.HeaderSize.LARGE;
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
		// Without the strip there'd be no tab to click to show everything again.
		if (focusedTab != null && (!config.categoryTabs() || !tabIcons.containsKey(focusedTab)))
		{
			focusedTab = null;
		}
		boolean searching = searching();
		String suggestion = searching && config.categorySearch()
			? CategorySearch.suggest(client.getVarcStrValue(VarClientID.MESLAYERINPUT), shownCategories) : null;
		if (suggestion != null)
		{
			placeSuggestion(suggestion);
		}
		else
		{
			hideSuggestion();
		}
		int jumpY = -1;
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
				if (jumpY < 0 && category.equals(jumpCategory))
				{
					jumpY = y;
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
					Widget w = slot.copy ? itemCopies.placeCopy(copies++, slot) : slot.widget;
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
		if (!searching)
		{
			Map<Integer, Set<String>> shown = new HashMap<>();
			List<String> shownGroups = new ArrayList<>();
			groups.forEach((category, groupSlots) ->
			{
				groupSlots.forEach(s -> shown.computeIfAbsent(s.itemId, k -> new HashSet<>()).add(category));
				if (!groupSlots.isEmpty())
				{
					shownGroups.add(category);
				}
			});
			shownIn = shown;
			shownCategories = shownGroups;
		}

		int scrollHeight = Math.max(y, container.getHeight());
		container.setScrollHeight(scrollHeight);
		int previous = scrollBeforeBuild >= 0 ? scrollBeforeBuild : container.getScrollY();
		int wanted = jumpY >= 0 ? jumpY : scrollToTop ? 0 : previous;
		scrollToTop = false;
		scrollBeforeBuild = -1;
		jumpCategory = null;
		container.setScrollY(Math.min(wanted, Math.max(0, scrollHeight - container.getHeight())));
		container.revalidateScroll();
		// bankmain_finishbuilding has just clamped the saved position to the vanilla grid height. Put ours back now
		// rather than from the deferred scrollbar redraw: the bank is often built several times in one client cycle
		// (on open, the init build is followed by the inv and var transmit builds), and a later build would otherwise
		// read the clamped value as the position to keep.
		client.setVarcIntValue(VarClientID.BANK_SCROLLPOS, wanted);
		// We're inside the bank build script's callback and scripts can't nest, so redraw the scrollbar after it. It
		// clamps the saved position against our scroll height and stores the result. Read the position when it runs,
		// so a redraw queued by an earlier build in the same cycle can't put back a stale value.
		clientThread.invokeLater(() ->
		{
			if (client.getWidget(InterfaceID.Bankmain.ITEMS) != null)
			{
				client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Bankmain.SCROLLBAR, InterfaceID.Bankmain.ITEMS,
					client.getVarcIntValue(VarClientID.BANK_SCROLLPOS));
			}
		});
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
		// Doses of one potion stay together, highest first.
		Comparator<Slot> byName = Comparator.<Slot, String>comparing(s -> Doses.baseName(s.name), String.CASE_INSENSITIVE_ORDER)
			.thenComparing(Comparator.comparingInt((Slot s) -> Doses.dose(s.name)).reversed())
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
		if (config.gearByQuality() && Categories.COMBAT_STYLES.contains(category))
		{
			return GearOrder.order(slots, category, this::equipmentOf,
				s -> itemManager.getItemPrice(s.itemId), s -> gearSets.match(s.itemId, s.name), comparator,
				config.outfitLayout() == SmartBankSorterConfig.OutfitLayout.COLUMNS, COLUMNS);
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

	// Mirrors the bank's ~bankmain_searching: the search box is open, or closed with a search still applied.
	private boolean searching()
	{
		int mode = client.getVarcIntValue(VarClientID.MESLAYERMODE);
		return mode == InputType.SEARCH.getType()
			|| mode <= 0 && !Strings.isNullOrEmpty(client.getVarcStrValue(VarClientID.MESLAYERINPUT));
	}

	// Hands the bank back to the game, or to whichever plugin is laying it out.
	private void hideOurWidgets()
	{
		hideHeaders(0);
		hideSuggestion();
		tabStrip.hideTabs(0);
		itemCopies.hideFrom(0);
		// The spare slots become real ones as the bank fills; a click there must not go to a copy's old target.
		itemCopies.clearTargets();
		// The game only sets the drag dead zone when the bank opens, so later builds don't undo ours.
		restoreDrag();
	}

	private void toggleCollapsed(String category)
	{
		if (!collapsed.remove(category))
		{
			collapsed.add(category);
		}
		List<String> sorted = new ArrayList<>(collapsed);
		Collections.sort(sorted);
		configManager.setConfiguration(SmartBankSorterConfig.GROUP, SmartBankSorterConfig.COLLAPSED_KEY, Text.toCSV(sorted));
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
