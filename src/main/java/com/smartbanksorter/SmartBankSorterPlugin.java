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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.FontID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetDrag;
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
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.TabManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Smart Bank Sorter",
	description = "Groups and sorts your bank by category, client-side only, with best-in-slot outfits and what you"
		+ " deposited lately at the top. Category tabs can share Bank Tags' tag tab strip.",
	tags = {"bank", "sort", "category", "organize", "layout"}
)
// For Bank Tags' tag tab strip, which the category tabs can join. This only has Bank Tags load first; it doesn't
// turn it on, and the plugin works without it.
@PluginDependency(BankTagsPlugin.class)
public class SmartBankSorterPlugin extends Plugin
{
	// Bank grid geometry; the bank is 8 columns wide in both fixed and resizable mode.
	private static final int COLUMNS = 8;
	private static final int START_X = 51;
	private static final int PITCH_X = 48;
	private static final int PITCH_Y = 36;
	static final int ITEM_WIDTH = 36;
	static final int ITEM_HEIGHT = 32;
	// The bank's item container has a widget per bank slot first. The tab separators, "Tab N" labels and row padding
	// rectangles after them can't show an item, so copies never go there.
	static final int BANK_SLOTS = 1410;
	private static final int GRID_WIDTH = COLUMNS * PITCH_X - 12;
	private static final int SECTION_GAP = 4;
	private static final int DIVIDER_COLOR = 0x4E4639;
	// The colour the game names items in their menus.
	private static final Color ITEM_NAME_COLOR = new Color(0xFF9040);
	private static final String BANK_TAGS_GROUP = "banktags";
	private static final String BUILT_IN_SETS = Resources.read("sets.txt");
	private static final Map<String, Integer> FOOD_HEALS = parseHeals(Resources.read("food.txt"));
	// Categories as they'd be without the user's rules.
	private static final Categoriser BUILT_IN = new Categoriser(UserRules.parse(""));
	// How many items each bank tab, 1 to 9, holds.
	private static final int[] TAB_SIZES = {
		VarbitID.BANK_TAB_1, VarbitID.BANK_TAB_2, VarbitID.BANK_TAB_3, VarbitID.BANK_TAB_4, VarbitID.BANK_TAB_5,
		VarbitID.BANK_TAB_6, VarbitID.BANK_TAB_7, VarbitID.BANK_TAB_8, VarbitID.BANK_TAB_9
	};

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

	// Bank Tags' tag tab list, which it draws its strip from. Shared with Bank Tags: a singleton of the client's.
	@Inject
	private TabManager tabManager;

	// Bank Tags' item tags, which a rename it doesn't finish needs moved; from its public module.
	@Inject
	private TagManager tagManager;

	private Categoriser categoriser;
	private UserRules gearSets;
	// Recent combat layouts by category contents, levels and settings (see gearRows).
	private final Map<String, List<List<String>>> gearRowsCache = new LinkedHashMap<String, List<List<String>>>(16, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, List<List<String>>> eldest)
		{
			return size() > 12;
		}
	};
	// Items the user shows in categories as well as where they are.
	private UserRules alsoShow;
	private List<String> categoryOrder;
	private final Set<String> collapsed = new HashSet<>();
	// Categories with a header in the last layout, in order: what Collapse all and Expand all act on.
	private List<String> headedCategories = Collections.emptyList();
	private final Map<Integer, String> categoryCache = new HashMap<>();
	private final Map<Integer, String> builtInCache = new HashMap<>();
	// Header text + divider widgets we created inside the bank item container, reused between builds.
	private final List<Widget> headerWidgets = new ArrayList<>();
	private final List<Widget> dividerWidgets = new ArrayList<>();
	// The button in the bank's title bar that opens the category editor.
	private final List<Widget> editButton = new ArrayList<>();
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
	private BankTagsStrip bankTagsStrip;
	private ItemCopies itemCopies;
	private NotOwnedItems notOwnedItems;
	private MoveToMenu moveToMenu;
	private CategoryEditor categoryEditor;
	// What the logged-in account deposited lately, loaded for recentProfile.
	private RecentDeposits recentDeposits;
	private String recentProfile;
	// The items in the Recently deposited category, each with when it last grew. Worked out each layout.
	private Map<Integer, Long> recentTimes = Collections.emptyMap();
	// Milliseconds since the epoch.
	LongSupplier clock = System::currentTimeMillis;

	@Provides
	SmartBankSorterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SmartBankSorterConfig.class);
	}

	@Override
	protected void startUp()
	{
		forgetDeposits();
		tabStrip =new TabStrip(client, config, () -> focusedTab, this::focus);
		bankTagsStrip = new BankTagsStrip(client, clientThread, config, configManager, tabManager, tagManager,
			() -> bankTags() != null, this::activeBankTag, () -> focusedTab, this::focus, this::rebuildBank);
		eventBus.register(beforeBankTags);
		eventBus.register(afterBankTags);
		itemCopies = new ItemCopies(this::equipmentOf);
		notOwnedItems = new NotOwnedItems(client, itemManager);
		moveToMenu = new MoveToMenu(client, config, configManager, itemManager, () -> categoryOrder,
			this::categoryOf);
		ItemData.load();
		WeaponLevels.load();
		loadSettings();
		// Empty unless Bank Tags remembers its last tab; dropped by the first layout if it has gone.
		focusedTab = Strings.emptyToNull(config.openTab());
		keyManager.registerKeyListener(searchEnter);
		spriteManager.addSpriteOverrides(TabSprite.values());
		spriteManager.addSpriteOverrides(EditButtonSprite.values());
		clientThread.invokeLater(this::rebuildBank);

		categoryEditor = new CategoryEditor(() -> CategorySettings.load(config), s -> s.save(configManager),
			new GameItems(client, clientThread, itemManager, this::categoryOf));
	}

	@Override
	protected void shutDown()
	{
		CategoryEditor editor = categoryEditor;
		SwingUtilities.invokeLater(editor::close);
		eventBus.unregister(beforeBankTags);
		eventBus.unregister(afterBankTags);
		keyManager.unregisterKeyListener(searchEnter);
		spriteManager.removeSpriteOverrides(TabSprite.values());
		spriteManager.removeSpriteOverrides(EditButtonSprite.values());
		clientThread.invokeLater(() ->
		{
			hideOurWidgets();
			headerWidgets.clear();
			dividerWidgets.clear();
			suggestionWidgets.clear();
			editButton.forEach(w -> w.setHidden(true));
			editButton.clear();
			itemCopies.clear();
			tabStrip.clear();
			// Out of Bank Tags' list before the rebuild has it redraw the strip.
			bankTagsStrip.remove();
			focusedTab = null;
			rebuildBank();
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// Bank Tags saving its tab list, on whichever thread it does; in the same call, so nothing of ours stays there.
		bankTagsStrip.onConfigChanged(event);
		if (!SmartBankSorterConfig.GROUP.equals(event.getGroup())
			|| SmartBankSorterConfig.TAB_ORDER_KEY.equals(event.getKey())
			|| SmartBankSorterConfig.OPEN_TAB_KEY.equals(event.getKey())
			|| SmartBankSorterConfig.DEPOSITS_KEY.equals(event.getKey()))
		{
			// The tab order is the strip's own: it writes it and rebuilds the bank itself. The open tab is only
			// saved as the bank closes. The deposit history is saved as the bank changes, which rebuilds it anyway.
			return;
		}
		if (SmartBankSorterConfig.RULES_KEY.equals(event.getKey()) || SmartBankSorterConfig.ALSO_SHOW_KEY.equals(event.getKey())
			|| SmartBankSorterConfig.ORDER_KEY.equals(event.getKey()) || SmartBankSorterConfig.TABS_KEY.equals(event.getKey()))
		{
			// Move to or Add to, or the text settings: the category editor shows them if it's open.
			SwingUtilities.invokeLater(categoryEditor::reload);
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
		if (event.getActionParam1() == InterfaceID.Bankmain.SEARCH && "Search".equals(event.getOption()))
		{
			client.getMenu().createMenuEntry(-1)
				.setOption(config.enabled() ? "Unsort" : "Sort")
				.setTarget(ColorUtil.wrapWithColorTag("Bank", config.headerColor()))
				.setType(MenuAction.RUNELITE)
				.onClick(e -> configManager.setConfiguration(SmartBankSorterConfig.GROUP, "enabled", !config.enabled()));
		}
	}

	// Built when the menu opens rather than as its entries are added, which is every tick the mouse is over an item.
	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!config.enabled())
		{
			return;
		}
		Slot ghost = itemCopies.ghostAt(client.getMouseCanvasPosition(), client.getWidget(InterfaceID.Bankmain.ITEMS));
		if (ghost != null)
		{
			// As a placeholder's Release, but it only takes the item out of the category it was added to. Nothing is
			// sent to the server.
			client.getMenu().createMenuEntry(-1)
				.setOption("Release")
				.setTarget(ColorUtil.wrapWithColorTag(ghost.name, ITEM_NAME_COLOR))
				.setType(MenuAction.RUNELITE)
				.onClick(e ->
				{
					CategorySettings settings = CategorySettings.load(config);
					settings.removeItem(ghost.category, ghost.pattern);
					settings.save(configManager);
				});
			return;
		}
		for (MenuEntry entry : event.getMenuEntries())
		{
			if (entry.getParam1() == InterfaceID.Bankmain.ITEMS && "Examine".equals(entry.getOption()) && entry.getItemId() > 0)
			{
				moveToMenu.addMenu(entry.getItemId());
				return;
			}
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
	public void onGameStateChanged(GameStateChanged event)
	{
		// The bank may change on another client before the next login; what was last seen of it says nothing then.
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			forgetDeposits();
		}
	}

	// Drops the loaded history and the bank last seen. The history stays saved, for the next look at the bank.
	private void forgetDeposits()
	{
		recentDeposits = null;
		recentProfile = null;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.BANK)
		{
			trackDeposits(event.getItemContainer());
		}
	}

	/**
	 * Records the bank's quantities for the Recently deposited category, for the logged-in account.
	 */
	private void trackDeposits(ItemContainer bank)
	{
		SmartBankSorterConfig.RecentWindow window = config.recentDeposits();
		String profile = configManager.getRSProfileKey();
		if (window == SmartBankSorterConfig.RecentWindow.OFF || profile == null)
		{
			// Turned back on, it starts from what it sees then, as after a restart.
			forgetDeposits();
			return;
		}
		if (!profile.equals(recentProfile))
		{
			recentProfile = profile;
			recentDeposits = new RecentDeposits(
				configManager.getRSProfileConfiguration(SmartBankSorterConfig.GROUP, SmartBankSorterConfig.DEPOSITS_KEY));
		}
		Map<Integer, Long> quantities = new HashMap<>();
		for (Item item : bank.getItems())
		{
			int id = item.getId();
			if (id <= 0 || id == ItemID.BANK_FILLER)
			{
				continue;
			}
			boolean placeholder = itemManager.getItemComposition(id).getPlaceholderTemplateId() != -1;
			quantities.merge(itemManager.canonicalize(id), placeholder ? 0L : item.getQuantity(), Long::sum);
		}
		if (recentDeposits.update(quantities, clock.getAsLong(), SmartBankSorterConfig.RecentWindow.longest()))
		{
			String saved = recentDeposits.save();
			if (saved.isEmpty())
			{
				configManager.unsetRSProfileConfiguration(SmartBankSorterConfig.GROUP, SmartBankSorterConfig.DEPOSITS_KEY);
			}
			else
			{
				configManager.setRSProfileConfiguration(SmartBankSorterConfig.GROUP, SmartBankSorterConfig.DEPOSITS_KEY,
					saved);
			}
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			hideSuggestion();
			rememberOpenTab();
		}
	}

	// Quitting with the bank open closes no interface. RuneLite saves settings to disk after this.
	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		rememberOpenTab();
	}

	// Like Bank Tags with its tag tabs: the open category tab comes back with the bank only if it remembers its own.
	private void rememberOpenTab()
	{
		if (bankTags() == null)
		{
			return;
		}
		Boolean remember = configManager.getConfiguration(BANK_TAGS_GROUP, "rememberTab", Boolean.class);
		if (remember != null && !remember)
		{
			focusedTab = null;
		}
		if (!Strings.nullToEmpty(focusedTab).equals(config.openTab()))
		{
			configManager.setConfiguration(SmartBankSorterConfig.GROUP, SmartBankSorterConfig.OPEN_TAB_KEY,
				Strings.nullToEmpty(focusedTab));
		}
	}

	// Items are only moved visually, so dropping one on another would swap real bank slots the user can't see. Other
	// drops still go through: onto a Bank Tags tag tab to tag the item, or onto a bank tab. A copy sits in an empty
	// slot, so it may only be dropped on a tag tab (Bank Tags' tabs are in the items container).
	@Subscribe
	public void onWidgetDrag(WidgetDrag event)
	{
		Widget dragged = client.getDraggedWidget();
		Widget draggedOn = client.getDraggedOnWidget();
		if (!sortingThisBuild || dragged == null || dragged.getId() != InterfaceID.Bankmain.ITEMS || draggedOn == null)
		{
			return;
		}
		if (draggedOn.getId() == InterfaceID.Bankmain.ITEMS
			|| itemCopies.isCopy(dragged) && draggedOn.getId() != InterfaceID.Bankmain.ITEMS_CONTAINER)
		{
			// Nulling the drop target is the only way to cancel a drop; Bank Tags does the same.
			client.setDraggedOnWidget(null);
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
			if (bankTagsStrip.active() && activeBankTag() != null)
			{
				// Its strip still has the category tabs, even if the bank opened on a remembered tag tab.
				updateTabsUnsorted();
			}
		}
		placeEditButton();
	}

	/**
	 * The category editor's button, in the bank's title bar after the game's own buttons there (settings and
	 * tutorial), matching them in size and spacing.
	 */
	private void placeEditButton()
	{
		Widget universe = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		Widget settings = shown(client.getWidget(InterfaceID.Bankmain.MENU_BUTTON));
		// The tutorial button can be turned off in the bank's settings.
		Widget tutorial = shown(client.getWidget(InterfaceID.Bankmain.BANK_TUT));
		Widget anchor = tutorial != null ? tutorial : settings;
		if (universe == null || anchor == null || !config.enabled() || !config.editButton())
		{
			editButton.forEach(w -> w.setHidden(true));
			return;
		}

		int gap = settings != null && tutorial != null ? tutorial.getRelativeX() - settings.getRelativeX() - settings.getWidth()
			: 3;
		Widget button = WidgetPool.pooled(universe, editButton, 0, WidgetType.GRAPHIC);
		button.setSpriteId(EditButtonSprite.EDIT.getSpriteId());
		// The anchor may sit in a layer of its own: place by where both are on screen.
		button.setOriginalX(anchor.getCanvasLocation().getX() - universe.getCanvasLocation().getX() + anchor.getWidth() + gap);
		button.setOriginalY(anchor.getCanvasLocation().getY() - universe.getCanvasLocation().getY());
		button.setOriginalWidth(anchor.getWidth());
		button.setOriginalHeight(anchor.getHeight());
		button.setName(ColorUtil.wrapWithColorTag("Categories", config.headerColor()));
		button.setAction(0, "Edit");
		button.setHasListener(true);
		button.setOnOpListener((JavaScriptCallback) e -> SwingUtilities.invokeLater(() ->
			categoryEditor.open(client.getCanvas())));
		button.setOnMouseOverListener((JavaScriptCallback) e -> button.setSpriteId(EditButtonSprite.EDIT_HOVER.getSpriteId()));
		button.setOnMouseLeaveListener((JavaScriptCallback) e -> button.setSpriteId(EditButtonSprite.EDIT.getSpriteId()));
		button.setHidden(false);
		button.revalidate();
	}

	private static Widget shown(Widget w)
	{
		return w != null && !w.isHidden() ? w : null;
	}

	/**
	 * The category tabs and their icons as the sorted bank would have them, worked out from the bank's items
	 * without laying them out.
	 */
	private void updateTabsUnsorted()
	{
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank == null)
		{
			return;
		}
		Map<String, List<Slot>> groups = new LinkedHashMap<>();
		for (String category : categoryOrder)
		{
			groups.put(category, new ArrayList<>());
		}
		Item[] items = bank.getItems();
		for (int i = 0; i < items.length; i++)
		{
			int itemId = items[i].getId();
			if (itemId <= 0 || config.hideFillers() && itemId == ItemID.BANK_FILLER
				|| config.hidePlaceholders() && itemManager.getItemComposition(itemId).getPlaceholderTemplateId() != -1)
			{
				continue;
			}
			int id = itemManager.canonicalize(itemId);
			String name = itemManager.getItemComposition(id).getName();
			Slot slot = new Slot(null, i, id, items[i].getQuantity(), name, categoryOf(id, name));
			groups.computeIfAbsent(slot.category, k -> new ArrayList<>()).add(slot);
		}
		Map<String, Integer> tabIcons = tabsWithItems(groups);
		Comparator<Slot> comparator = comparator();
		groups.forEach((category, groupSlots) ->
		{
			String tab = tabGroups.match(-1, category);
			if (tab != null && !groupSlots.isEmpty() && tabIcons.get(tab) == -1)
			{
				tabIcons.put(tab, firstItem(rows(category, groupSlots, comparator)));
			}
		});
		List<String> tabs = new ArrayList<>(tabGroups.categories());
		tabs.retainAll(tabIcons.keySet());
		List<Integer> icons = new ArrayList<>();
		tabs.forEach(t -> icons.add(tabIcons.get(t)));
		bankTagsStrip.update(tabs, icons);
	}

	/**
	 * Subscribers that run before Bank Tags' (priority 1). RuneLite requires a subscriber method to be named after
	 * its event, and the plugin has its own at the default priority, so these live on their own object registered
	 * with the event bus.
	 */
	public static final class BeforeBankTags
	{
		private final SmartBankSorterPlugin plugin;

		BeforeBankTags(SmartBankSorterPlugin plugin)
		{
			this.plugin = plugin;
		}

		// Bank Tags rebuilds its strip from its tab list when the bank finishes building; the category tabs go in
		// the list just before.
		@Subscribe(priority = 1)
		public void onScriptPreFired(ScriptPreFired event)
		{
			if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING)
			{
				plugin.beforeBankTagsRebuild();
			}
		}

		// A drag involving a category tab in Bank Tags' strip is ours, not a tag drop or a tag tab reorder.
		@Subscribe(priority = 1)
		public void onWidgetDrag(WidgetDrag event)
		{
			plugin.bankTagsStrip.onDragBefore();
		}
	}

	/**
	 * Subscribers that run after Bank Tags' (priority -1).
	 */
	public static final class AfterBankTags
	{
		private final SmartBankSorterPlugin plugin;

		AfterBankTags(SmartBankSorterPlugin plugin)
		{
			this.plugin = plugin;
		}

		// Bank Tags' layoutTabs runs on this script, after the bank build, and treats every child of the item
		// container past its first four as one of its own tab backgrounds and icons: it hides them all and
		// repositions whichever pairs it thinks it owns, which includes ours. Run after it to put our tabs back.
		@Subscribe(priority = -1)
		public void onScriptPostFired(ScriptPostFired event)
		{
			plugin.afterBankTagsScript(event);
		}

		// Bank Tags has just drawn its strip, the category tabs in it with its own tag tab ops.
		@Subscribe(priority = -1)
		public void onScriptPreFired(ScriptPreFired event)
		{
			if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING)
			{
				plugin.bankTagsStrip.patch();
			}
		}

		@Subscribe(priority = -1)
		public void onWidgetDrag(WidgetDrag event)
		{
			plugin.afterBankTagsDrag();
		}
	}

	private final BeforeBankTags beforeBankTags = new BeforeBankTags(this);
	private final AfterBankTags afterBankTags = new AfterBankTags(this);

	private void beforeBankTagsRebuild()
	{
		if (bankTagsStrip.active() && activeBankTag() != null)
		{
			// A tag tab is open, with the strip's active sprite: the category tab gives up its focus rather than
			// show as active too.
			focusedTab = null;
		}
		bankTagsStrip.sync();
	}

	// A drop on one of Bank Tags' tag tabs makes it rebuild its strip, which cuts the item container's children back
	// to its own four: the category tabs in it are redrawn with it and only need their ops back.
	private void afterBankTagsDrag()
	{
		if (client.getMouseCurrentButton() == 0 && bankTagsStrip.active())
		{
			bankTagsStrip.onDragAfter();
		}
	}

	private void afterBankTagsScript(ScriptPostFired event)
	{
		// This sees every script the client runs, so rule out the rest before the dearer checks.
		int script = event.getScriptId();
		if (script != ScriptID.BANKMAIN_POPUP_TAB_DRAW && script != ScriptID.BANKMAIN_SIZE_CHECK)
		{
			return;
		}
		// In Bank Tags' strip the category tabs are its to lay out.
		if (!config.enabled() || !config.categoryTabs() || bankTagsStrip.active() || !shouldSort())
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
		builtInCache.clear();
		filterText = null;
		bankTagsStrip.loadOrder(config.tabOrder());
		// Tab lines are "Tab: category, category"; matching category names with the same rule syntax.
		tabGroups = UserRules.parse(config.tabGroups());
		// The user's sets first, so they can redefine a built-in one.
		gearSets = UserRules.parse(config.customSets() + "\n" + BUILT_IN_SETS);

		// Rules only say what goes in a category, not where it sits: new categories they name go after the built-ins.
		alsoShow = UserRules.parse(config.alsoShow());
		categoryOrder = CategorySettings.order(config.categoryOrder(), rules, alsoShow);

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
	 * The tag Bank Tags currently shows, or null.
	 */
	private String activeBankTag()
	{
		BankTagsService bankTags = bankTags();
		return bankTags != null ? bankTags.getActiveTag() : null;
	}

	/**
	 * Bank Tags, if the user has it on; null otherwise. Looked up rather than injected so that it is only used
	 * while it is on, which the plugin dependency alone doesn't give.
	 */
	private BankTagsService bankTags()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin instanceof BankTagsService && pluginManager.isPluginEnabled(plugin))
			{
				return (BankTagsService) plugin;
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

		// The bank may be built before ItemContainerChanged has told us of the change.
		trackDeposits(bank);
		boolean searching = searching();
		// Only the whole bank shows what isn't in it, and what was deposited; a search or bank tab shows what's there.
		boolean wholeBank = !searching && client.getVarbitValue(VarbitID.BANK_CURRENTTAB) == 0;
		SmartBankSorterConfig.RecentWindow window = config.recentDeposits();
		recentTimes = wholeBank && recentDeposits != null && window != SmartBankSorterConfig.RecentWindow.OFF
			? recentDeposits.recent(clock.getAsLong(), window.millis,
				config.recentCounts() == SmartBankSorterConfig.RecentCounts.NET_GAIN)
			: Collections.emptyMap();

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
			if (i >= items.length && i < BANK_SLOTS)
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
			// A placeholder is templated on the placeholder object. Its widget isn't a spare for copies: the game still
			// treats it as the placeholder's slot.
			if (config.hidePlaceholders()
				&& itemManager.getItemComposition(items[i].getId()).getPlaceholderTemplateId() != -1)
			{
				w.setHidden(true);
				continue;
			}

			int id = itemManager.canonicalize(items[i].getId());
			String name = itemManager.getItemComposition(id).getName();
			slots.add(new Slot(w, i, id, items[i].getQuantity(), name, categoryOf(id, name)));
		}

		Map<String, List<Slot>> groups = new LinkedHashMap<>();
		if (!recentTimes.isEmpty())
		{
			groups.put(RecentDeposits.CATEGORY, new ArrayList<>());
		}
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
			itemCopies.addTeleportGearCopies(groups);
			itemCopies.addHybridCopies(groups);
		}
		itemCopies.addDataCopies(groups, s -> categoriser.ruleCategory(s.itemId, s.name) != null);
		itemCopies.addAlsoShown(groups, s -> alsoShow.matchAll(s.itemId, s.name));
		itemCopies.addCopies(groups, RecentDeposits.CATEGORY, s -> s.quantity > 0 && recentTimes.containsKey(s.itemId));
		if (wholeBank)
		{
			notOwnedItems.addGhosts(groups, alsoShow, slots);
		}

		Comparator<Slot> comparator = comparator();
		boolean large = config.headerSize() == SmartBankSorterConfig.HeaderSize.LARGE;
		int headerHeight = large ? 18 : 14;
		int y = 0;
		int section = 0;
		List<String> headed = new ArrayList<>();
		int copies = 0;
		itemCopies.clearTargets();
		itemCopies.setSpares(spares);
		Map<String, Integer> tabIcons = tabsWithItems(groups);
		checkFocus(tabIcons);

		// Copies get the bank's empty slots in the order they're drawn, except Recently deposited's, which get what's
		// left. Those that don't fit are left out before the layout, so their rows close up and a category of nothing
		// but those gets no header. Collapsed and filtered-out categories draw no copies, so they take no slots.
		Map<String, List<List<Slot>>> rowsByCategory = new HashMap<>();
		int freeSlots = spares.size();
		List<Map.Entry<String, List<Slot>>> byPriority = new ArrayList<>(groups.entrySet());
		// Recently deposited copies get only the slots the rest leave: their items show where they belong anyway.
		byPriority.sort(Comparator.comparing(e -> isRecent(e.getKey())));
		for (Map.Entry<String, List<Slot>> group : byPriority)
		{
			String category = group.getKey();
			List<List<Slot>> rows = rows(category, group.getValue(), comparator);
			if (!collapsed.contains(category) && !filteredOut(category))
			{
				List<Slot> unplaced = new ArrayList<>();
				for (List<Slot> row : rows)
				{
					for (Slot slot : row)
					{
						if (slot == null || !slot.copy)
						{
							continue;
						}
						if (freeSlots > 0)
						{
							freeSlots--;
						}
						else
						{
							unplaced.add(slot);
						}
					}
				}
				if (!unplaced.isEmpty())
				{
					group.getValue().removeAll(unplaced);
					rows = rows(category, group.getValue(), comparator);
				}
			}
			rowsByCategory.put(category, rows);
		}
		// A tab whose categories only had copies that didn't fit has nothing left to show.
		tabIcons.keySet().retainAll(tabsWithItems(groups).keySet());
		checkFocus(tabIcons);

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
			List<List<Slot>> rows = rowsByCategory.get(category);
			String tab = tabGroups.match(-1, category);
			if (tab != null && tabIcons.get(tab) == -1)
			{
				tabIcons.put(tab, firstItem(rows));
			}

			boolean isCollapsed = collapsed.contains(category);
			boolean filteredOut = filteredOut(category);
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
				int realCount = isRecent(category) ? groupSlots.size()
					: (int) groupSlots.stream().filter(s -> !s.copy).count();
				placeHeader(container, section++, category, realCount, isCollapsed, y, headerHeight, large);
				headed.add(category);
				y += headerHeight + 2;
			}

			for (List<Slot> row : rows)
			{
				for (int i = 0; i < row.size(); i++)
				{
					Slot slot = row.get(i);
					if (slot == null || (slot.copy && (filteredOut || isCollapsed)))
					{
						// Gap in an outfit column, or a copy we don't need to draw.
						continue;
					}
					Widget w = slot.ghost
						? itemCopies.placeGhost(copies++, slot, itemManager.getItemComposition(slot.itemId).getPlaceholderId())
						: slot.copy ? itemCopies.placeCopy(copies++, slot) : slot.widget;
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
					w.revalidate();
				}
				if (!isCollapsed && !filteredOut)
				{
					y += ((row.size() + COLUMNS - 1) / COLUMNS) * PITCH_Y;
				}
			}
		}
		List<String> tabs = new ArrayList<>();
		List<Integer> icons = new ArrayList<>();
		if (config.categoryTabs())
		{
			tabs.addAll(tabGroups.categories());
			tabs.retainAll(tabIcons.keySet());
			tabs.forEach(t -> icons.add(tabIcons.get(t)));
		}
		if (bankTagsStrip.active())
		{
			tabStrip.hideTabs(0);
			bankTagsStrip.update(tabs, icons);
		}
		else
		{
			// With no tabs this hides the strip.
			tabStrip.placeTabs(tabs, icons);
		}
		itemCopies.hideFrom(copies);
		hideHeaders(section);
		headedCategories = headed;
		boolean anyCollapsed = headed.stream().anyMatch(collapsed::contains);
		boolean anyExpanded = !headed.stream().allMatch(collapsed::contains);
		for (int i = 0; i < section; i++)
		{
			Widget header = headerWidgets.get(i);
			header.setAction(1, anyExpanded ? "Collapse all" : null);
			header.setAction(2, anyCollapsed ? "Expand all" : null);
		}
		if (!searching)
		{
			Map<Integer, Set<String>> shown = new HashMap<>();
			List<String> shownGroups = new ArrayList<>();
			groups.forEach((category, groupSlots) ->
			{
				groupSlots.stream().filter(s -> !s.ghost)
					.forEach(s -> shown.computeIfAbsent(s.itemId, k -> new HashSet<>()).add(category));
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
		return categoryCache.computeIfAbsent(itemId, id -> categorise(categoriser, id, name));
	}

	// Where an item would be if the user hadn't moved it.
	private String builtInCategoryOf(Slot slot)
	{
		return builtInCache.computeIfAbsent(slot.itemId, id -> categorise(BUILT_IN, id, slot.name));
	}

	private String categorise(Categoriser categoriser, int itemId, String name)
	{
		ItemComposition comp = itemManager.getItemComposition(itemId);
		ItemStats stats = itemManager.getItemStats(itemId);
		return categoriser.categorise(itemId, name, comp.getInventoryActions(),
			stats != null && stats.isEquipable() ? stats.getEquipment() : null);
	}

	private Comparator<Slot> comparator()
	{
		// As "All items" shows the bank: untabbed items first, then tab 1 to 9. The bank holds the tabbed ones first.
		// Not-owned items, with no bank slot, go last.
		int tabbed = 0;
		for (int varbit : TAB_SIZES)
		{
			tabbed += client.getVarbitValue(varbit);
		}
		int firstUntabbed = tabbed;
		Comparator<Slot> byIndex = Comparator.comparing((Slot s) -> s.ghost)
			.thenComparing(s -> s.index < firstUntabbed)
			.thenComparingInt(s -> s.index);
		// Doses of one potion stay together, highest first.
		Comparator<Slot> byName = Comparator.<Slot, String>comparing(s -> Doses.baseName(s.name), String.CASE_INSENSITIVE_ORDER)
			.thenComparing(Comparator.comparingInt((Slot s) -> Doses.dose(s.name)).reversed())
			.thenComparing(byIndex);
		if (config.itemOrder() == SmartBankSorterConfig.ItemOrder.BANK_ORDER)
		{
			return byIndex;
		}
		Comparator<Slot> byValue = Comparator.comparingLong(this::value).reversed();
		if (!config.itemOrder().haOnly)
		{
			// Untradeables and anything else the GE doesn't price go after what it does, ranked by HA value.
			byValue = Comparator.comparing((Slot s) -> itemManager.getItemPrice(s.itemId) <= 0).thenComparing(byValue);
		}
		return byValue.thenComparing(byName);
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

	/**
	 * The GE value, or HA value of an item the GE doesn't price (or of every item, as set), of the whole stack or one
	 * item as set.
	 */
	private long value(Slot slot)
	{
		SmartBankSorterConfig.ItemOrder order = config.itemOrder();
		long price = order.haOnly ? 0 : itemManager.getItemPrice(slot.itemId);
		if (price <= 0)
		{
			price = itemManager.getItemComposition(slot.itemId).getHaPrice();
		}
		// A placeholder or not-owned item has no stack: it ranks by one of its item.
		return order.perItem || slot.quantity <= 0 ? price : price * slot.quantity;
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
		header.setOnOpListener((JavaScriptCallback) e -> onHeaderOp(category, e.getOp()));
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

	private List<List<Slot>> rows(String category, List<Slot> slots, Comparator<Slot> comparator)
	{
		if (isRecent(category))
		{
			return orderRows(category, slots, comparator);
		}
		// Blighted supplies (Wilderness-only) always go last, laid out the same way as the rest.
		List<Slot> normal = new ArrayList<>();
		List<Slot> blighted = new ArrayList<>();
		for (Slot s : slots)
		{
			(s.name.toLowerCase().startsWith("blighted ") ? blighted : normal).add(s);
		}
		List<List<Slot>> rows = new ArrayList<>(orderRows(category, normal, comparator));
		if (!blighted.isEmpty())
		{
			rows.addAll(orderRows(category, blighted, comparator));
		}
		return rows;
	}

	// Tab name -> icon item, for tabs that have something in them. The icons are filled in as the layout goes.
	private Map<String, Integer> tabsWithItems(Map<String, List<Slot>> groups)
	{
		Map<String, Integer> tabIcons = new HashMap<>();
		for (Map.Entry<String, List<Slot>> group : groups.entrySet())
		{
			String tab = tabGroups.match(-1, group.getKey());
			if (tab != null && !group.getValue().isEmpty())
			{
				tabIcons.putIfAbsent(tab, -1);
			}
		}
		return tabIcons;
	}

	private void checkFocus(Map<String, Integer> tabIcons)
	{
		// Without the strip there'd be no tab to click to show everything again.
		if (focusedTab != null && (!config.categoryTabs() || !tabIcons.containsKey(focusedTab)))
		{
			focusedTab = null;
		}
	}

	// A tab is showing and this category isn't in it.
	private boolean filteredOut(String category)
	{
		return focusedTab != null && !focusedTab.equals(tabGroups.match(-1, category));
	}

	private static boolean isRecent(String category)
	{
		return RecentDeposits.CATEGORY.equals(category);
	}

	private GearOrder.Gear<Slot> gear(String category)
	{
		return new GearOrder.Gear<Slot>()
		{
			@Override
			public ItemEquipmentStats stats(Slot s)
			{
				return equipmentOf(s);
			}

			@Override
			public String name(Slot s)
			{
				return s.name;
			}

			@Override
			public long price(Slot s)
			{
				return itemManager.getItemPrice(s.itemId);
			}

			@Override
			public int upgrade(Slot s)
			{
				return GearOrder.upgradeRank(s.name, itemManager.getItemComposition(s.itemId).isTradeable());
			}

			@Override
			public int weaponRank(Slot s)
			{
				return WeaponLevels.rank(s.name, category);
			}

			@Override
			public int wieldLevel(Slot s)
			{
				return WeaponLevels.level(s.name, category);
			}

			@Override
			public String set(Slot s)
			{
				return gearSets.match(s.itemId, s.name);
			}
		};
	}

	// The player's real (unboosted) combat levels, for weapon damage and which weapons they can wield.
	private DpsCalc.Levels combatLevels()
	{
		return new DpsCalc.Levels(client.getRealSkillLevel(Skill.ATTACK), client.getRealSkillLevel(Skill.STRENGTH),
			client.getRealSkillLevel(Skill.RANGED), client.getRealSkillLevel(Skill.MAGIC));
	}

	/**
	 * Combat outfits, which are the slow part of a layout. They only change with the category's items, the
	 * player's levels and a few settings, so recent results are kept as item ids and reused on later layouts.
	 */
	private List<List<Slot>> gearRows(String category, List<Slot> slots, Comparator<Slot> comparator)
	{
		boolean columns = config.outfitLayout() == SmartBankSorterConfig.OutfitLayout.COLUMNS;
		DpsCalc.Levels levels = combatLevels();
		Map<String, Slot> byKey = new HashMap<>();
		for (Slot s : slots)
		{
			byKey.put(s.itemId + (s.copy ? "c" : ""), s);
		}
		List<String> items = new ArrayList<>(byKey.keySet());
		Collections.sort(items);
		String key = category + "|" + columns + config.compactLayout() + config.bestOutfitsOnly() + "|" + config.itemOrder()
			+ "|" + config.customSets().hashCode() + "|"
			+ levels.attack + "," + levels.strength + "," + levels.ranged + "," + levels.magic + "|" + items;

		List<List<String>> cached = gearRowsCache.get(key);
		if (cached == null)
		{
			List<List<Slot>> rows = GearOrder.order(slots, category, gear(category), levels, comparator, columns,
				config.compactLayout() != SmartBankSorterConfig.CompactLayout.OFF, config.bestOutfitsOnly(), COLUMNS);
			cached = new ArrayList<>();
			for (List<Slot> row : rows)
			{
				List<String> ids = new ArrayList<>();
				for (Slot s : row)
				{
					ids.add(s == null ? null : s.itemId + (s.copy ? "c" : ""));
				}
				cached.add(ids);
			}
			gearRowsCache.put(key, cached);
			return rows;
		}
		List<List<Slot>> rows = new ArrayList<>();
		for (List<String> ids : cached)
		{
			List<Slot> row = new ArrayList<>();
			for (String id : ids)
			{
				row.add(id == null ? null : byKey.get(id));
			}
			rows.add(row);
		}
		return rows;
	}

	private List<List<Slot>> orderRows(String category, List<Slot> slots, Comparator<Slot> comparator)
	{
		if (slots.isEmpty())
		{
			return Collections.emptyList();
		}
		if (isRecent(category))
		{
			// Newest first.
			slots.sort(Comparator.comparingLong((Slot s) -> recentTimes.getOrDefault(s.itemId, 0L)).reversed()
				.thenComparing(comparator));
			return Collections.singletonList(slots);
		}
		if (config.gearByQuality() && Categories.COMBAT_STYLES.contains(category))
		{
			return gearRows(category, slots, comparator);
		}
		if (config.smartConsumables() && Categories.FOOD.equals(category))
		{
			return ConsumableOrder.food(slots, s -> s.name, this::healOf, comparator);
		}
		if (config.smartConsumables() && Categories.POTIONS.equals(category))
		{
			return ConsumableOrder.potions(slots, s -> s.name, comparator, COLUMNS, compactCategories());
		}
		if (config.smartConsumables() && Categories.TELEPORTS.equals(category))
		{
			return Collections.singletonList(TeleportOrder.order(slots, s -> s.name, comparator));
		}
		if (config.smartConsumables())
		{
			// Potions and food moved into another category are laid out as in Potions and Food, after its other items.
			List<Slot> potions = new ArrayList<>();
			List<Slot> food = new ArrayList<>();
			List<Slot> rest = new ArrayList<>();
			for (Slot slot : slots)
			{
				String home = builtInCategoryOf(slot);
				(Categories.POTIONS.equals(home) ? potions : Categories.FOOD.equals(home) ? food : rest).add(slot);
			}
			if (!potions.isEmpty() || !food.isEmpty())
			{
				List<List<Slot>> rows = new ArrayList<>(orderRows(category, rest, comparator));
				rows.addAll(orderRows(Categories.POTIONS, potions, comparator));
				rows.addAll(orderRows(Categories.FOOD, food, comparator));
				return rows;
			}
		}
		slots.sort(comparator);
		return Collections.singletonList(slots);
	}

	private boolean compactCategories()
	{
		return config.compactLayout() == SmartBankSorterConfig.CompactLayout.EVERYTHING;
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
		BankTagsService bankTags = bankTags();
		if (bankTags != null && bankTags.getActiveTag() != null)
		{
			// A category tab clicked in Bank Tags' strip while one of its tag tabs is open: closing the tag tab
			// rebuilds the bank.
			bankTags.closeBankTag();
		}
		else
		{
			clientThread.invokeLater(this::rebuildBank);
		}
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
	}

	// Op 1 is the header's own Collapse or Expand, ops 2 and 3 Collapse all and Expand all.
	private void onHeaderOp(String category, int op)
	{
		switch (op)
		{
			case 2:
				collapsed.addAll(headedCategories);
				break;
			case 3:
				collapsed.removeAll(headedCategories);
				break;
			default:
				if (!collapsed.remove(category))
				{
					collapsed.add(category);
				}
		}
		saveCollapsed();
	}

	private void saveCollapsed()
	{
		List<String> sorted = new ArrayList<>(collapsed);
		Collections.sort(sorted);
		configManager.setConfiguration(SmartBankSorterConfig.GROUP, SmartBankSorterConfig.COLLAPSED_KEY, Text.toCSV(sorted));
	}
}
