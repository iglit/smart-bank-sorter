package com.smartbanksorter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;

/**
 * A window for arranging the categories: which there are and their order, the tab each shows in, and the items the
 * user has put in each. Every change is saved as it's made, so the bank re-sorts behind the window.
 */
final class CategoryEditor
{
	private static final Color SUBTLE = ColorScheme.LIGHT_GRAY_COLOR;
	private static final Color ERROR = new Color(0xE0605A);
	private static final String NO_TAB = "No tab";
	private static final String NEW_TAB = "New tab...";
	private static final int SEARCH_DELAY_MS = 250;

	private final Supplier<CategorySettings> load;
	private final Consumer<CategorySettings> save;
	private final ItemLookup items;

	private JDialog dialog;
	private CategorySettings settings;
	private String selected;
	// Set while the editor itself changes the list, whose selection events would otherwise select as they go.
	private boolean listUpdating;
	private boolean tabsUpdating;
	// Answers for an earlier category or search than the current one are dropped.
	private int describeToken;
	private int searchToken;
	private List<ItemLookup.Row> results = Collections.emptyList();

	private final DefaultListModel<String> categoryModel = new DefaultListModel<>();
	private JList<String> categoryList;
	private int hoverIndex = -1;
	// The list index of the category being dragged, while the mouse is down on the list.
	private int dragFrom = -1;
	private JLabel title;
	private JLabel kind;
	private JButton rename;
	private JButton delete;
	private JComboBox<String> tabBox;
	private JLabel tabHint;
	private JLabel itemsTitle;
	private JLabel itemsHint;
	private JPanel itemRows;
	private JLabel addTitle;
	private IconTextField search;
	private JPanel resultRows;
	private Timer searchTimer;

	CategoryEditor(Supplier<CategorySettings> load, Consumer<CategorySettings> save, ItemLookup items)
	{
		this.load = load;
		this.save = save;
		this.items = items;
	}

	/**
	 * Opens the window, or brings it to the front.
	 *
	 * @param near a component in the window to open it over
	 */
	void open(Component near)
	{
		prepare(SwingUtilities.getWindowAncestor(near));
		if (!dialog.isVisible())
		{
			dialog.setLocationRelativeTo(dialog.getOwner());
			dialog.setVisible(true);
		}
		dialog.toFront();
	}

	/**
	 * Builds the window if it isn't yet, showing the current settings, without opening it.
	 */
	JDialog prepare(Window owner)
	{
		if (dialog == null)
		{
			build(owner);
		}
		settings = load.get();
		refresh();
		return dialog;
	}

	/**
	 * Picks up settings changed elsewhere: Move to or Add to in the bank, or the text settings.
	 */
	void reload()
	{
		if (dialog == null || !dialog.isVisible())
		{
			return;
		}
		CategorySettings fresh = load.get();
		if (!signature(fresh).equals(signature(settings)))
		{
			settings = fresh;
			refresh();
		}
	}

	void close()
	{
		if (dialog != null)
		{
			searchTimer.stop();
			dialog.dispose();
			dialog = null;
		}
	}

	private static String signature(CategorySettings s)
	{
		return s.rulesText() + "\0" + s.alsoShowText() + "\0" + s.orderText() + "\0" + s.tabsText();
	}

	private void edit(Consumer<CategorySettings> change)
	{
		CategorySettings s = load.get();
		change.accept(s);
		save.accept(s);
		settings = s;
		refresh();
	}

	// Building the window

	private void build(Window owner)
	{
		dialog = new JDialog(owner, "Bank categories", Dialog.ModalityType.MODELESS);
		dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(ColorScheme.DARK_GRAY_COLOR);
		root.add(buildCategoryColumn(), BorderLayout.WEST);

		JPanel detail = new JPanel(new BorderLayout(0, 10));
		detail.setOpaque(false);
		detail.setBorder(new EmptyBorder(10, 4, 0, 10));
		detail.add(buildHeader(), BorderLayout.NORTH);
		JPanel columns = new JPanel(new GridLayout(1, 2, 10, 0));
		columns.setOpaque(false);
		columns.add(buildItemsColumn());
		columns.add(buildAddColumn());
		detail.add(columns, BorderLayout.CENTER);
		root.add(detail, BorderLayout.CENTER);
		root.add(buildFooter(), BorderLayout.SOUTH);

		dialog.setContentPane(root);
		dialog.setSize(1000, 600);
		dialog.setMinimumSize(new Dimension(780, 440));
	}

	private JComponent buildCategoryColumn()
	{
		JPanel column = new JPanel(new BorderLayout(0, 6));
		column.setOpaque(false);
		column.setBorder(new EmptyBorder(10, 10, 0, 10));
		column.setPreferredSize(new Dimension(240, 0));
		column.add(heading(new JLabel("Categories"), hint("As in your bank. Drag to reorder.")),
			BorderLayout.NORTH);

		categoryList = new JList<>(categoryModel);
		categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		categoryList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		categoryList.setCellRenderer(new CategoryCell());
		categoryList.addListSelectionListener(e ->
		{
			// While dragging, the list selects whatever is under the mouse; the drop selects the dragged category.
			if (!listUpdating && dragFrom < 0 && !e.getValueIsAdjusting() && categoryList.getSelectedValue() != null)
			{
				select(categoryList.getSelectedValue());
			}
		});
		ReorderMouse reorder = new ReorderMouse();
		categoryList.addMouseListener(reorder);
		categoryList.addMouseMotionListener(reorder);
		column.add(scroll(categoryList), BorderLayout.CENTER);

		JButton add = new JButton("+ New category");
		add.setToolTipText("Make a category of your own, then add items to it");
		add.addActionListener(e -> newCategory());
		column.add(add, BorderLayout.SOUTH);
		return column;
	}

	private JComponent buildHeader()
	{
		JPanel header = new JPanel(new BorderLayout(0, 6));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(new EmptyBorder(8, 10, 8, 10));

		JPanel top = new JPanel(new BorderLayout(10, 0));
		top.setOpaque(false);
		title = new JLabel();
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		kind = hint("");
		JPanel names = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		names.setOpaque(false);
		names.add(title);
		names.add(kind);
		top.add(names, BorderLayout.CENTER);

		rename = new JButton("Rename");
		rename.addActionListener(e -> renameCategory());
		delete = new JButton("Delete");
		delete.addActionListener(e -> deleteCategory());
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.setOpaque(false);
		buttons.add(rename);
		buttons.add(delete);
		// Hidden for built-in categories; the header keeps its height so the columns below don't jump.
		buttons.setPreferredSize(buttons.getPreferredSize());
		top.add(buttons, BorderLayout.EAST);
		header.add(top, BorderLayout.NORTH);

		JPanel tabRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		tabRow.setOpaque(false);
		JLabel tabLabel = new JLabel("Bank tab");
		tabLabel.setFont(FontManager.getRunescapeFont());
		tabLabel.setForeground(ColorScheme.TEXT_COLOR);
		tabBox = new JComboBox<>();
		tabBox.setPreferredSize(new Dimension(190, tabBox.getPreferredSize().height));
		tabBox.addActionListener(e -> tabChosen());
		tabHint = hint("");
		tabRow.add(tabLabel);
		tabRow.add(tabBox);
		tabRow.add(tabHint);
		header.add(tabRow, BorderLayout.SOUTH);
		return header;
	}

	private JComponent buildItemsColumn()
	{
		JPanel column = new JPanel(new BorderLayout(0, 6));
		column.setOpaque(false);
		itemsTitle = new JLabel();
		itemsHint = hint("");
		column.add(heading(itemsTitle, itemsHint), BorderLayout.NORTH);
		itemRows = rowList();
		column.add(scroll(top(itemRows)), BorderLayout.CENTER);
		return column;
	}

	private JComponent buildAddColumn()
	{
		JPanel column = new JPanel(new BorderLayout(0, 6));
		column.setOpaque(false);

		addTitle = new JLabel();
		search = new IconTextField();
		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(0, 30));
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setHoverBackgroundColor(ColorScheme.DARKER_GRAY_HOVER_COLOR);
		searchTimer = new Timer(SEARCH_DELAY_MS, e -> runSearch());
		searchTimer.setRepeats(false);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				searchTimer.restart();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				searchTimer.restart();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				searchTimer.restart();
			}
		});
		search.addClearListener(this::runSearch);

		JPanel top = new JPanel(new BorderLayout(0, 6));
		top.setOpaque(false);
		top.add(heading(addTitle, hint("Search your bank, or any item in the game.")), BorderLayout.NORTH);
		top.add(search, BorderLayout.CENTER);
		top.add(hint("Tip: * matches anything, e.g. *slayer ring*"), BorderLayout.SOUTH);
		column.add(top, BorderLayout.NORTH);

		resultRows = rowList();
		column.add(scroll(top(resultRows)), BorderLayout.CENTER);
		return column;
	}

	private JComponent buildFooter()
	{
		JPanel footer = new JPanel(new BorderLayout());
		footer.setOpaque(false);
		footer.setBorder(new EmptyBorder(8, 10, 10, 10));
		footer.add(hint("Changes show in your bank straight away. In the bank, right-click an item and pick Move to"
			+ " or Add to for a quick change."),BorderLayout.CENTER);
		JButton done = new JButton("Done");
		done.addActionListener(e -> dialog.setVisible(false));
		footer.add(done, BorderLayout.EAST);
		return footer;
	}

	// Showing the settings

	private void refresh()
	{
		List<String> categories = settings.categories();
		if (selected == null || !categories.contains(selected))
		{
			selected = categories.get(0);
		}
		listUpdating = true;
		categoryModel.clear();
		categories.forEach(categoryModel::addElement);
		categoryList.setSelectedValue(selected, true);
		listUpdating = false;
		showSelected();
		runSearch();
	}

	private void select(String category)
	{
		if (!category.equals(selected))
		{
			selected = category;
			showSelected();
			showResults();
		}
	}

	private void showSelected()
	{
		boolean custom = !CategorySettings.isBuiltIn(selected);
		title.setText(selected);
		kind.setText(custom ? "Your category" : "Built-in category");
		rename.setVisible(custom);
		delete.setVisible(custom);

		tabsUpdating = true;
		tabBox.removeAllItems();
		tabBox.addItem(NO_TAB);
		settings.tabs().forEach(tabBox::addItem);
		tabBox.addItem(NEW_TAB);
		String tab = settings.tabOf(selected);
		tabBox.setSelectedItem(tab != null ? tab : NO_TAB);
		tabsUpdating = false;
		tabHint.setText(tab != null ? "Its tab down the left of the bank shows it." : "It only shows in the full bank.");

		// Moved items first, then the ones added here, which stay where they are too.
		List<String> patterns = new ArrayList<>(settings.items(selected));
		int moved = patterns.size();
		patterns.addAll(settings.alsoShown(selected));
		itemsTitle.setText((custom ? "In " : "Added to ") + selected + (patterns.isEmpty() ? "" : " (" + patterns.size() + ")"));
		itemsHint.setText(custom ? "Items you moved or added here."
			: "Items you moved or added here. Others are sorted in automatically.");
		addTitle.setText("Add to " + selected);

		int token = ++describeToken;
		if (patterns.isEmpty())
		{
			showItems(Collections.emptyList(), 0);
			return;
		}
		items.describe(patterns, rows ->
		{
			if (token == describeToken)
			{
				showItems(rows, moved);
			}
		});
	}

	/**
	 * @param moved how many of the rows, from the first, are moved here; the rest are added here
	 */
	private void showItems(List<ItemLookup.Row> rows, int moved)
	{
		itemRows.removeAll();
		if (rows.isEmpty())
		{
			itemRows.add(emptyState(CategorySettings.isBuiltIn(selected)
				? "You haven't moved or added anything here. Search on the right to find items."
				: "Nothing here yet. Search on the right to add items."));
		}
		for (int i = 0; i < rows.size(); i++)
		{
			ItemLookup.Row row = rows.get(i);
			boolean wasMoved = i < moved;
			// An added item the bank doesn't have shows as a placeholder.
			boolean notOwned = !wasMoved && !row.inBank && row.itemId >= 0 && !ItemLookup.isGroup(row);
			ItemLookup.Row labelled = new ItemLookup.Row(row.itemId, row.name, row.pattern,
				(wasMoved ? "Moved here" : notOwned ? "Added here · shown as a placeholder" : "Added here · " + stillIn(row))
					+ (row.detail.isEmpty() ? "" : " · " + row.detail),
				row.category, row.inBank);
			JButton remove = new JButton("Remove");
			remove.setToolTipText(wasMoved ? "Send it back to its usual category"
				: notOwned ? "Stop showing it in " + selected
				: "Take it out of " + selected + ". It stays in " + stillIn(row).substring("still in ".length()) + ".");
			remove.addActionListener(e -> edit(s -> s.removeItem(selected, row.pattern)));
			itemRows.add(itemRow(labelled, remove));
		}
		itemRows.revalidate();
		itemRows.repaint();
	}

	private void runSearch()
	{
		searchTimer.stop();
		String text = search.getText().trim();
		int token = ++searchToken;
		if (text.isEmpty())
		{
			results = Collections.emptyList();
			search.setIcon(IconTextField.Icon.SEARCH);
			showResults();
			return;
		}
		search.setIcon(IconTextField.Icon.LOADING_DARKER);
		items.search(text, rows ->
		{
			if (token == searchToken)
			{
				results = rows;
				search.setIcon(IconTextField.Icon.SEARCH);
				showResults();
			}
		});
	}

	private void showResults()
	{
		resultRows.removeAll();
		if (results.isEmpty())
		{
			resultRows.add(emptyState(search.getText().trim().isEmpty()
				? "Type part of an item's name." : "No items match."));
		}
		List<String> movedHere = settings.items(selected);
		List<String> shownHere = settings.alsoShown(selected);
		for (ItemLookup.Row row : results)
		{
			boolean group = ItemLookup.isGroup(row);
			JButton move = new JButton("Move");
			JButton add = new JButton("Add");
			// Told apart from Move at a glance.
			add.setForeground(ColorScheme.BRAND_ORANGE);
			if (movedHere.stream().anyMatch(row.pattern::equalsIgnoreCase))
			{
				move.setText("Moved");
				move.setEnabled(false);
			}
			else if (selected.equals(row.category) && (row.inBank || ItemLookup.isGroup(row)))
			{
				// It's here without a rule: nothing to move or show.
				move.setText("Here");
				move.setEnabled(false);
				add.setEnabled(false);
				move.setToolTipText("Already sorted into " + selected);
				add.setToolTipText("Already sorted into " + selected);
			}
			else if (selected.equals(row.category))
			{
				// Its category, but not in the bank: it can still be added to show it as a placeholder.
				move.setText("Here");
				move.setEnabled(false);
				move.setToolTipText("Already sorted into " + selected);
			}
			if (shownHere.stream().anyMatch(row.pattern::equalsIgnoreCase))
			{
				add.setText("Added");
				add.setEnabled(false);
			}
			String from = row.category != null ? row.category : "their categories";
			if (move.isEnabled())
			{
				move.setToolTipText((group ? "Take them out of their categories" : "Take it out of " + from)
					+ " and put " + (group ? "them" : "it") + " in " + selected);
				move.addActionListener(e -> edit(s -> s.addItem(selected, row.pattern)));
			}
			if (add.isEnabled())
			{
				add.setToolTipText(!group && !row.inBank ? "Show it as a placeholder in " + selected + " while it isn't in your bank"
					: (group ? "Keep them where they are" : "Keep it in " + from)
					+ " and add " + (group ? "them" : "it") + " to " + selected + " too");
				add.addActionListener(e -> edit(s -> s.addAlsoShown(selected, row.pattern)));
			}
			resultRows.add(itemRow(row, move, add));
		}
		resultRows.revalidate();
		resultRows.repaint();
	}

	// Actions

	private void newCategory()
	{
		String name = askName("New category", "Name your new category:", "", settings.categories());
		if (name != null)
		{
			selected = name;
			edit(s -> s.create(name));
			search.requestFocusInWindow();
		}
	}

	private void renameCategory()
	{
		List<String> taken = settings.categories();
		taken.remove(selected);
		String name = askName("Rename category", "New name for " + selected + ":", selected, taken);
		if (name != null && !name.equals(selected))
		{
			String from = selected;
			selected = name;
			edit(s -> s.rename(from, name));
		}
	}

	private void deleteCategory()
	{
		int count = settings.items(selected).size();
		String message = "Delete " + selected + "?" + (count == 0 ? ""
			: count == 1 ? "\nThe item in it goes back to its usual category."
			: "\nThe " + count + " items in it go back to their usual categories.");
		if (JOptionPane.showConfirmDialog(dialog, message, "Delete category", JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
		{
			return;
		}
		String gone = selected;
		int index = categoryModel.indexOf(gone);
		selected = categoryModel.get(index > 0 ? index - 1 : Math.min(1, categoryModel.size() - 1));
		edit(s -> s.delete(gone));
	}

	private void tabChosen()
	{
		Object choice = tabBox.getSelectedItem();
		if (tabsUpdating || choice == null)
		{
			return;
		}
		String category = selected;
		if (NEW_TAB.equals(choice))
		{
			String tab = askName("New tab", "Name the new tab:", "", settings.tabs());
			if (tab == null)
			{
				showSelected();
				return;
			}
			edit(s -> s.setTab(category, tab));
		}
		else if (!choice.equals(settings.tabOf(category) == null ? NO_TAB : settings.tabOf(category)))
		{
			edit(s -> s.setTab(category, NO_TAB.equals(choice) ? null : (String) choice));
		}
	}

	private String askName(String heading, String message, String initial, List<String> taken)
	{
		String name = initial;
		while (true)
		{
			name = (String) JOptionPane.showInputDialog(dialog, message, heading, JOptionPane.PLAIN_MESSAGE, null, null,
				name);
			if (name == null)
			{
				return null;
			}
			name = name.trim();
			String problem = CategorySettings.nameProblem(name, taken);
			if (problem == null)
			{
				return name;
			}
			JOptionPane.showMessageDialog(dialog, problem, heading, JOptionPane.WARNING_MESSAGE);
		}
	}

	// Where an item added to a category also stays.
	private static String stillIn(ItemLookup.Row row)
	{
		return "still in " + (row.category != null ? row.category : ItemLookup.isGroup(row) ? "their categories"
			: "its category");
	}

	// Pieces

	private static JLabel hint(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(SUBTLE);
		return label;
	}

	private static JComponent heading(JLabel title, JLabel hint)
	{
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);
		JPanel panel = new JPanel(new GridLayout(2, 1));
		panel.setOpaque(false);
		panel.add(title);
		panel.add(hint);
		return panel;
	}

	private static JPanel rowList()
	{
		JPanel rows = new JPanel(new DynamicGridLayout(0, 1, 0, 3));
		rows.setOpaque(false);
		return rows;
	}

	private static JComponent top(JComponent rows)
	{
		ScrollableRows wrapper = new ScrollableRows();
		wrapper.add(rows, BorderLayout.NORTH);
		return wrapper;
	}

	/**
	 * Rows at the top of their scroll pane and as wide as it, so that long names are cut short rather than push the
	 * buttons out of sight.
	 */
	private static final class ScrollableRows extends JPanel implements Scrollable
	{
		ScrollableRows()
		{
			super(new BorderLayout());
			setOpaque(false);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return visibleRect.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	private static JScrollPane scroll(JComponent view)
	{
		JScrollPane scroll = new JScrollPane(view, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setOpaque(false);
		scroll.setOpaque(false);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	private static JComponent emptyState(String text)
	{
		JLabel label = new JLabel("<html>" + text + "</html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(SUBTLE);
		label.setBorder(new EmptyBorder(8, 4, 8, 4));
		return label;
	}

	private JComponent itemRow(ItemLookup.Row row, JButton... buttons)
	{
		JPanel panel = new JPanel(new BorderLayout(8, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(2, 4, 2, 6));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		if (row.itemId >= 0)
		{
			items.icon(row.itemId, icon);
		}
		panel.add(icon, BorderLayout.WEST);

		JLabel name = new JLabel(row.name);
		name.setFont(FontManager.getRunescapeFont());
		name.setForeground(Color.WHITE);
		JLabel detail = hint(row.detail);
		// An item name or ID that nothing has, likely a typo; a pattern may be for items not in the game yet.
		if (row.itemId < 0 && !ItemLookup.isGroup(row))
		{
			detail.setForeground(ERROR);
		}
		JPanel text = new JPanel(new GridLayout(2, 1));
		text.setOpaque(false);
		text.add(name);
		text.add(detail);
		panel.add(text, BorderLayout.CENTER);

		JPanel right = new JPanel(new GridBagLayout());
		right.setOpaque(false);
		JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		buttonRow.setOpaque(false);
		for (JButton button : buttons)
		{
			button.setFont(FontManager.getRunescapeSmallFont());
			button.setMargin(new Insets(2, 6, 2, 6));
			buttonRow.add(button);
		}
		right.add(buttonRow);
		panel.add(right, BorderLayout.EAST);

		MouseAdapter hover = new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				panel.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		};
		panel.addMouseListener(hover);
		return panel;
	}

	/**
	 * A category in the list: a grip to drag it by, its name and the tab it shows in. The user's own categories
	 * have an orange edge.
	 */
	private final class CategoryCell extends JPanel implements ListCellRenderer<String>
	{
		private final JLabel name = new JLabel();
		private final JLabel tab = hint("");
		private boolean custom;

		CategoryCell()
		{
			super(new BorderLayout(6, 0));
			setBorder(new EmptyBorder(5, 18, 5, 8));
			name.setFont(FontManager.getRunescapeFont());
			add(name, BorderLayout.CENTER);
			add(tab, BorderLayout.EAST);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
			boolean isSelected, boolean cellHasFocus)
		{
			name.setText(value);
			name.setForeground(isSelected ? Color.WHITE : ColorScheme.TEXT_COLOR);
			String tabName = settings.tabOf(value);
			tab.setText(tabName != null ? tabName : "");
			custom = !CategorySettings.isBuiltIn(value);
			setBackground(isSelected ? ColorScheme.MEDIUM_GRAY_COLOR
				: index == hoverIndex ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			return this;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			g.setColor(ColorScheme.MEDIUM_GRAY_COLOR.brighter());
			int top = getHeight() / 2 - 4;
			for (int row = 0; row < 3; row++)
			{
				for (int col = 0; col < 2; col++)
				{
					g.fillRect(7 + col * 3, top + row * 3, 2, 2);
				}
			}
			if (custom)
			{
				g.setColor(ColorScheme.BRAND_ORANGE);
				g.fillRect(0, 0, 3, getHeight());
			}
		}
	}

	/**
	 * Dragging a category moves it through the list as the mouse goes; the order is saved when it's dropped.
	 */
	private final class ReorderMouse extends MouseAdapter
	{
		private boolean moved;

		@Override
		public void mousePressed(MouseEvent e)
		{
			dragFrom = categoryList.locationToIndex(e.getPoint());
			moved = false;
		}

		@Override
		public void mouseDragged(MouseEvent e)
		{
			int to = categoryList.locationToIndex(e.getPoint());
			if (dragFrom < 0 || to < 0 || to == dragFrom)
			{
				return;
			}
			listUpdating = true;
			String category = categoryModel.remove(dragFrom);
			categoryModel.add(to, category);
			categoryList.setSelectedIndex(to);
			listUpdating = false;
			categoryList.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
			hoverIndex = to;
			dragFrom = to;
			moved = true;
		}

		@Override
		public void mouseReleased(MouseEvent e)
		{
			categoryList.setCursor(Cursor.getDefaultCursor());
			int index = dragFrom;
			dragFrom = -1;
			if (index < 0)
			{
				return;
			}
			String category = categoryModel.get(index);
			if (moved)
			{
				selected = category;
				edit(s -> s.move(category, index));
			}
			else
			{
				select(category);
			}
		}

		@Override
		public void mouseMoved(MouseEvent e)
		{
			setHover(categoryList.locationToIndex(e.getPoint()));
		}

		@Override
		public void mouseExited(MouseEvent e)
		{
			setHover(-1);
		}

		private void setHover(int index)
		{
			if (index != hoverIndex)
			{
				hoverIndex = index;
				categoryList.repaint();
			}
		}
	}
}
