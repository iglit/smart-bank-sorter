package com.smartbanksorter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.ColorUtil;

class MoveToMenu
{
	private final Client client;
	private final SmartBankSorterConfig config;
	private final ConfigManager configManager;
	private final ItemManager itemManager;
	private final Supplier<List<String>> categoryOrder;
	private final BiFunction<Integer, String, String> categoryOf;

	MoveToMenu(Client client, SmartBankSorterConfig config, ConfigManager configManager, ItemManager itemManager,
		Supplier<List<String>> categoryOrder, BiFunction<Integer, String, String> categoryOf)
	{
		this.client = client;
		this.config = config;
		this.configManager = configManager;
		this.itemManager = itemManager;
		this.categoryOrder = categoryOrder;
		this.categoryOf = categoryOf;
	}

	/**
	 * "Move to" and "Add to" sub-menus on bank items, as the category editor's Move and Add buttons: Move to takes
	 * the item out of its category, Add to shows it in another one as well. Both write to the settings the editor
	 * edits, so one-off fixes don't need a plugin change. Doses and charges are covered by one entry.
	 */
	void addMenu(int itemId)
	{
		int id = itemManager.canonicalize(itemId);
		String name = itemManager.getItemComposition(id).getName();
		String pattern = movePattern(name);
		String sortedInto = categoryOf.apply(id, name);
		CategorySettings settings = CategorySettings.load(config);
		List<String> targets = new ArrayList<>(categoryOrder.get());
		// Entries made later go above earlier ones, so the categories go in from the bottom up.
		Collections.reverse(targets);

		Menu move = subMenu("Move to", name);
		for (String category : targets)
		{
			entry(move, category, null, s -> s.addItem(category, pattern));
		}
		if (settings.movedTo(pattern) != null)
		{
			entry(move, "Reset", "automatic", s -> s.unmove(pattern));
		}

		// Each sub-menu goes in just above Cancel, so Add to, made second, sits below Move to.
		Menu add = subMenu("Add to", name);
		List<String> addedTo = new ArrayList<>();
		for (String category : targets)
		{
			if (settings.alsoShown(category).stream().anyMatch(pattern::equalsIgnoreCase))
			{
				addedTo.add(category);
			}
			else if (!category.equals(sortedInto))
			{
				entry(add, category, null, s -> s.addAlsoShown(category, pattern));
			}
		}
		for (String category : addedTo)
		{
			entry(add, "Remove from", category, s -> s.removeItem(category, pattern));
		}
	}

	private Menu subMenu(String option, String name)
	{
		MenuEntry parent = client.getMenu().createMenuEntry(1)
			.setOption(option)
			.setTarget(ColorUtil.wrapWithColorTag(name, config.headerColor()))
			.setType(MenuAction.RUNELITE);
		return parent.createSubMenu();
	}

	// The settings are read again on click: they may have changed while the menu was open.
	private void entry(Menu menu, String option, String target, Consumer<CategorySettings> edit)
	{
		MenuEntry entry = menu.createMenuEntry(-1)
			.setOption(option)
			.setType(MenuAction.RUNELITE)
			.onClick(e ->
			{
				CategorySettings settings = CategorySettings.load(config);
				edit.accept(settings);
				settings.save(configManager);
			});
		if (target != null)
		{
			entry.setTarget(ColorUtil.wrapWithColorTag(target, config.headerColor()));
		}
	}

	// "Prayer potion(4)" -> "Prayer potion(*"; other names match exactly.
	static String movePattern(String name)
	{
		return Doses.dose(name) < 0 ? name : name.substring(0, name.lastIndexOf('(')) + "(*";
	}
}
