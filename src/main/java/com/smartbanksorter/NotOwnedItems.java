package com.smartbanksorter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;

/**
 * Items the user added to a category but doesn't have. Each shows there drawn and laid out as a placeholder of it, but
 * nothing about it reaches the game: its only option, Release, is the plugin's own. Only single items are shown (a
 * name, an ID, or every dose of one item); a wildcard group would bring in far too many.
 */
final class NotOwnedItems
{
	private final Client client;
	private final ItemManager itemManager;
	// Lower-case pattern -> the item shown for it, -1 for none. Item definitions don't change while the client runs.
	private final Map<String, Integer> resolved = new HashMap<>();

	NotOwnedItems(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	/**
	 * Adds a ghost to each category for every single item added there that nothing in the bank matches.
	 *
	 * @param owned the bank's items
	 */
	void addGhosts(Map<String, List<Slot>> groups, UserRules alsoShow, List<Slot> owned)
	{
		Map<String, List<String>> patterns = alsoShow.patterns();
		patterns.values().forEach(this::resolve);
		patterns.forEach((category, list) ->
		{
			for (String pattern : list)
			{
				int id = resolved.getOrDefault(pattern.toLowerCase(), -1);
				if (id < 0 || owned(pattern, owned))
				{
					continue;
				}
				List<Slot> target = groups.computeIfAbsent(category, k -> new ArrayList<>());
				if (target.stream().noneMatch(t -> t.itemId == id))
				{
					target.add(Slot.ghost(id, itemManager.getItemComposition(id).getName(), category, pattern));
				}
			}
		});
	}

	private static boolean owned(String pattern, List<Slot> owned)
	{
		if (pattern.matches("\\d+"))
		{
			int id = Integer.parseInt(pattern);
			return owned.stream().anyMatch(s -> s.itemId == id);
		}
		Pattern glob = UserRules.glob(pattern);
		return owned.stream().anyMatch(s -> glob.matcher(s.name).matches());
	}

	// Finds the item for each new single-item pattern in one pass over the game's items. Of every dose of an item, the
	// most doses or charges.
	private void resolve(List<String> patterns)
	{
		Map<String, Pattern> names = new HashMap<>();
		for (String pattern : patterns)
		{
			String key = pattern.toLowerCase();
			if (resolved.containsKey(key))
			{
				continue;
			}
			if (ItemLookup.isGroup(pattern))
			{
				resolved.put(key, -1);
			}
			else if (pattern.matches("\\d+"))
			{
				int id = Integer.parseInt(pattern);
				resolved.put(key, id < client.getItemCount() ? itemManager.canonicalize(id) : -1);
			}
			else
			{
				names.put(key, UserRules.glob(pattern));
			}
		}
		if (names.isEmpty())
		{
			return;
		}

		Map<String, Integer> best = new HashMap<>();
		Map<String, String> bestName = new HashMap<>();
		for (int id = 0; id < client.getItemCount(); id++)
		{
			ItemComposition item = itemManager.getItemComposition(id);
			String name = item.getName();
			if (item.getNote() != -1 || item.getPlaceholderTemplateId() != -1 || "null".equals(name))
			{
				continue;
			}
			for (Map.Entry<String, Pattern> e : names.entrySet())
			{
				String key = e.getKey();
				if (e.getValue().matcher(name).matches()
					&& (!best.containsKey(key) || Doses.dose(name) > Doses.dose(bestName.get(key))))
				{
					best.put(key, id);
					bestName.put(key, name);
				}
			}
		}
		names.keySet().forEach(key -> resolved.put(key, best.getOrDefault(key, -1)));
	}
}
