package com.smartbanksorter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

/**
 * The custom rules (items moved and items also shown), category order, tabs and collapsed categories, edited together
 * by the category editor. The
 * settings stay plain text: each edit rewrites only the lines it touches, so hand-written lines and comments stay as
 * they were.
 */
final class CategorySettings
{
	private final String savedRules;
	private final String savedAlsoShow;
	private final String savedOrder;
	private final String savedTabs;
	private final String savedCollapsed;
	private final Lines rules;
	private final Lines alsoShow;
	private final Lines tabs;
	private final List<String> collapsed;
	private List<String> order;
	private boolean orderChanged;

	CategorySettings(String rules, String alsoShow, String order, String tabs, String collapsed)
	{
		savedRules = rules;
		savedAlsoShow = alsoShow;
		savedOrder = order;
		savedTabs = tabs;
		savedCollapsed = collapsed;
		this.rules = new Lines(rules);
		this.alsoShow = new Lines(alsoShow);
		this.tabs = new Lines(tabs);
		this.collapsed = new ArrayList<>(Text.fromCSV(collapsed));
		this.order = order(order, UserRules.parse(rules), UserRules.parse(alsoShow));
	}

	static CategorySettings load(SmartBankSorterConfig config)
	{
		return new CategorySettings(config.customRules(), config.alsoShow(), config.categoryOrder(), config.tabGroups(),
			config.collapsed());
	}

	/**
	 * Every category top to bottom: the ones the order lists, then the built-ins it doesn't, then new ones rules name.
	 */
	static List<String> order(String orderText, UserRules rules, UserRules alsoShow)
	{
		Set<String> order = new LinkedHashSet<>();
		for (String s : orderText.split("[\\n,]"))
		{
			if (!s.trim().isEmpty())
			{
				order.add(s.trim());
			}
		}
		order.addAll(Categories.DEFAULT_ORDER);
		order.addAll(rules.categories());
		order.addAll(alsoShow.categories());
		return new ArrayList<>(order);
	}

	/**
	 * Why a category or tab can't have this name, or null if it can.
	 */
	static String nameProblem(String name, Collection<String> taken)
	{
		if (name.isEmpty())
		{
			return "Enter a name.";
		}
		if (name.matches(".*[:,*\\n].*") || name.startsWith("#"))
		{
			return "Names can't contain : , or *";
		}
		if (name.length() > 30)
		{
			return "Keep it to 30 letters or fewer.";
		}
		if (name.equalsIgnoreCase(RecentDeposits.CATEGORY))
		{
			return "'" + RecentDeposits.CATEGORY + "' is the plugin's own.";
		}
		for (String other : taken)
		{
			if (other.equalsIgnoreCase(name))
			{
				return "'" + other + "' already exists.";
			}
		}
		return null;
	}

	List<String> categories()
	{
		return new ArrayList<>(order);
	}

	static boolean isBuiltIn(String category)
	{
		return Categories.DEFAULT_ORDER.contains(category);
	}

	/**
	 * The patterns the user's rules move into this category, in the order they're tried.
	 */
	List<String> items(String category)
	{
		return rules.values(category);
	}

	/**
	 * The patterns whose items this category also shows, while they stay where they are.
	 */
	List<String> alsoShown(String category)
	{
		return alsoShow.values(category);
	}

	List<String> tabs()
	{
		return tabs.keys();
	}

	/**
	 * The tab this category shows in, or null.
	 */
	String tabOf(String category)
	{
		return tabs.keyOf(category);
	}

	/**
	 * Puts an item, or the items a pattern matches, in a category. It takes over any rule the user had for the same
	 * pattern, and goes first so that it wins over broader lines.
	 */
	void addItem(String category, String pattern)
	{
		rules.removeValue(null, pattern);
		alsoShow.removeValue(category, pattern);
		rules.add(0, category, pattern);
	}

	/**
	 * Shows an item, or the items a pattern matches, in a category as well as where it is. An item can be shown in
	 * any number of categories this way.
	 */
	void addAlsoShown(String category, String pattern)
	{
		rules.removeValue(category, pattern);
		alsoShow.removeValue(category, pattern);
		alsoShow.append(category, pattern);
	}

	// Whether it was moved here or also shown here.
	void removeItem(String category, String pattern)
	{
		rules.removeValue(category, pattern);
		alsoShow.removeValue(category, pattern);
	}

	/**
	 * A new, empty category at the bottom. Only the order knows it until something is put in it.
	 */
	void create(String name)
	{
		order.add(name);
		orderChanged = true;
	}

	void rename(String from, String to)
	{
		rules.renameKey(from, to);
		alsoShow.renameKey(from, to);
		tabs.renameValue(from, to);
		order.set(order.indexOf(from), to);
		orderChanged = true;
		collapsed.replaceAll(c -> c.equals(from) ? to : c);
	}

	/**
	 * Removes a custom category: its items go back to where they'd be without it.
	 */
	void delete(String name)
	{
		rules.removeKey(name);
		alsoShow.removeKey(name);
		tabs.removeValue(null, name);
		order.remove(name);
		orderChanged = true;
		collapsed.remove(name);
	}

	void move(String category, int index)
	{
		order.remove(category);
		order.add(index, category);
		orderChanged = true;
	}

	/**
	 * @param tab the tab to show the category in, a new one if no tab has this name yet; null for none
	 */
	void setTab(String category, String tab)
	{
		tabs.removeValue(null, category);
		if (tab != null)
		{
			tabs.append(tab, category);
		}
	}

	String rulesText()
	{
		return rules.text();
	}

	String alsoShowText()
	{
		return alsoShow.text();
	}

	String tabsText()
	{
		return tabs.text();
	}

	String orderText()
	{
		return orderChanged ? String.join("\n", order) : savedOrder;
	}

	String collapsedText()
	{
		return collapsed.equals(Text.fromCSV(savedCollapsed)) ? savedCollapsed : Text.toCSV(collapsed);
	}

	/**
	 * Writes the settings that changed; the plugin re-sorts the bank as each one does.
	 */
	void save(ConfigManager configManager)
	{
		save(configManager, SmartBankSorterConfig.RULES_KEY, savedRules, rulesText());
		save(configManager, SmartBankSorterConfig.ALSO_SHOW_KEY, savedAlsoShow, alsoShowText());
		save(configManager, SmartBankSorterConfig.ORDER_KEY, savedOrder, orderText());
		save(configManager, SmartBankSorterConfig.TABS_KEY, savedTabs, tabsText());
		save(configManager, SmartBankSorterConfig.COLLAPSED_KEY, savedCollapsed, collapsedText());
	}

	private static void save(ConfigManager configManager, String key, String saved, String text)
	{
		if (!saved.equals(text))
		{
			configManager.setConfiguration(SmartBankSorterConfig.GROUP, key, text);
		}
	}

	/**
	 * "Key: value, value" lines, the syntax of both the rules and the tabs. Lines that aren't, comments and blank
	 * lines, are kept as they are.
	 */
	private static final class Lines
	{
		private static final class Line
		{
			final String raw;
			String key;
			final List<String> values = new ArrayList<>();
			boolean edited;

			Line(String raw)
			{
				this.raw = raw;
				String trimmed = raw.trim();
				int colon = trimmed.indexOf(':');
				if (!trimmed.startsWith("#") && colon > 0)
				{
					key = trimmed.substring(0, colon).trim();
					for (String value : trimmed.substring(colon + 1).split(","))
					{
						if (!value.trim().isEmpty())
						{
							values.add(value.trim());
						}
					}
				}
			}

			Line(String key, String value)
			{
				raw = null;
				this.key = key;
				values.add(value);
				edited = true;
			}

			String text()
			{
				return edited ? key + ": " + String.join(", ", values) : raw;
			}
		}

		private final List<Line> lines = new ArrayList<>();

		Lines(String text)
		{
			if (!text.isEmpty())
			{
				for (String raw : text.split("\\R", -1))
				{
					lines.add(new Line(raw));
				}
			}
		}

		List<String> values(String key)
		{
			List<String> values = new ArrayList<>();
			for (Line line : lines)
			{
				if (key.equals(line.key))
				{
					values.addAll(line.values);
				}
			}
			return values;
		}

		List<String> keys()
		{
			Set<String> keys = new LinkedHashSet<>();
			for (Line line : lines)
			{
				if (line.key != null)
				{
					keys.add(line.key);
				}
			}
			return new ArrayList<>(keys);
		}

		String keyOf(String value)
		{
			for (Line line : lines)
			{
				if (line.key != null && line.values.stream().anyMatch(value::equalsIgnoreCase))
				{
					return line.key;
				}
			}
			return null;
		}

		void add(int index, String key, String value)
		{
			lines.add(index, new Line(key, value));
		}

		// Onto the key's first line, or a new line at the end.
		void append(String key, String value)
		{
			for (Line line : lines)
			{
				if (key.equals(line.key))
				{
					line.values.add(value);
					line.edited = true;
					return;
				}
			}
			lines.add(new Line(key, value));
		}

		// From the key's lines, or every line when key is null. A line left with no values goes.
		void removeValue(String key, String value)
		{
			lines.removeIf(line ->
			{
				if (line.key == null || key != null && !key.equals(line.key)
					|| !line.values.removeIf(value::equalsIgnoreCase))
				{
					return false;
				}
				line.edited = true;
				return line.values.isEmpty();
			});
		}

		void removeKey(String key)
		{
			lines.removeIf(line -> key.equals(line.key));
		}

		void renameKey(String from, String to)
		{
			for (Line line : lines)
			{
				if (from.equals(line.key))
				{
					line.key = to;
					line.edited = true;
				}
			}
		}

		void renameValue(String from, String to)
		{
			for (Line line : lines)
			{
				if (line.key != null && line.values.stream().anyMatch(from::equalsIgnoreCase))
				{
					line.values.replaceAll(v -> v.equalsIgnoreCase(from) ? to : v);
					line.edited = true;
				}
			}
		}

		String text()
		{
			List<String> out = new ArrayList<>();
			lines.forEach(line -> out.add(line.text()));
			return String.join("\n", out);
		}
	}
}
