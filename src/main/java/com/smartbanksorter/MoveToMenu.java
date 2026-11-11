package com.smartbanksorter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

	MoveToMenu(Client client, SmartBankSorterConfig config, ConfigManager configManager, ItemManager itemManager,
		Supplier<List<String>> categoryOrder)
	{
		this.client = client;
		this.config = config;
		this.configManager = configManager;
		this.itemManager = itemManager;
		this.categoryOrder = categoryOrder;
	}

	/**
	 * "Move to" sub-menu on bank items. Picking a category writes a rule to the Custom categories setting, so
	 * one-off fixes don't need a plugin change. Doses and charges are covered by one rule.
	 */
	void addMenu(int itemId)
	{
		String name = itemManager.getItemComposition(itemManager.canonicalize(itemId)).getName();
		String pattern = movePattern(name);
		boolean hasRule = findRule(pattern) >= 0;

		MenuEntry parent = client.getMenu().createMenuEntry(1)
			.setOption("Move to")
			.setTarget(ColorUtil.wrapWithColorTag(name, config.headerColor()))
			.setType(MenuAction.RUNELITE);
		Menu sub = parent.createSubMenu();
		List<String> targets = new ArrayList<>(categoryOrder.get());
		Collections.reverse(targets);
		for (String category : targets)
		{
			sub.createMenuEntry(-1)
				.setOption(category)
				.setType(MenuAction.RUNELITE)
				.onClick(e -> setRule(pattern, category));
		}
		if (hasRule)
		{
			sub.createMenuEntry(-1)
				.setOption("Reset")
				.setTarget(ColorUtil.wrapWithColorTag("automatic", config.headerColor()))
				.setType(MenuAction.RUNELITE)
				.onClick(e -> setRule(pattern, null));
		}
	}

	// "Prayer potion(4)" -> "Prayer potion(*"; other names match exactly.
	private static String movePattern(String name)
	{
		return Doses.dose(name) < 0 ? name : name.substring(0, name.lastIndexOf('(')) + "(*";
	}

	/**
	 * Line index of the "Category: pattern" rule written for this exact pattern, or -1.
	 */
	private int findRule(String pattern)
	{
		String[] lines = config.customRules().split("\\R", -1);
		for (int i = 0; i < lines.length; i++)
		{
			int colon = lines[i].indexOf(':');
			if (colon > 0 && lines[i].substring(colon + 1).trim().equalsIgnoreCase(pattern))
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * Replaces this item's rule with one for {@code category}, or removes it when null. New rules go first so
	 * they win over broader lines the user wrote.
	 */
	private void setRule(String pattern, String category)
	{
		List<String> lines = new ArrayList<>(Arrays.asList(config.customRules().split("\\R", -1)));
		int existing = findRule(pattern);
		if (existing >= 0)
		{
			lines.remove(existing);
		}
		if (category != null)
		{
			lines.add(0, category + ": " + pattern);
		}
		lines.removeIf(String::isEmpty);
		configManager.setConfiguration(SmartBankSorterConfig.GROUP, "customRules", String.join("\n", lines));
	}
}
