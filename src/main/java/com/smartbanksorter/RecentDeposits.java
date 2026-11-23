package com.smartbanksorter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What an account deposited lately: every item whose stack grew within the window, or only those it has more of now
 * than at the window's start (leftover supplies put back after a trip are fewer than were taken out; loot isn't). Each
 * item's quantity is kept over time, but only while it has changed within the window.
 */
final class RecentDeposits
{
	static final String CATEGORY = "Recently deposited";
	// Enough changes for a week of trips; past this an item's oldest changes are merged into its starting quantity.
	private static final int MAX_CHANGES = 50;

	// Per item, [time, quantity] pairs, oldest first. The first is the quantity before the changes after it, with
	// time 0.
	private final Map<Integer, List<long[]>> history = new HashMap<>();
	// Quantities when the bank was last seen, by item; null until it has been seen since this was loaded. Bank
	// changes made meanwhile (on another client, or with the plugin off) show only for items that have a history.
	private Map<Integer, Long> seen;

	/**
	 * @param saved what {@link #save} returned, or null
	 */
	RecentDeposits(String saved)
	{
		if (saved == null)
		{
			return;
		}
		for (String line : saved.split("\n"))
		{
			String[] parts = line.split("=", 2);
			if (parts.length < 2)
			{
				continue;
			}
			try
			{
				List<long[]> changes = new ArrayList<>();
				for (String change : parts[1].split(","))
				{
					String[] tq = change.split(":");
					changes.add(new long[]{Long.parseLong(tq[0]), Long.parseLong(tq[1])});
				}
				if (changes.size() > 1)
				{
					history.put(Integer.parseInt(parts[0]), changes);
				}
			}
			catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored)
			{
				// A damaged line: forget that item.
			}
		}
	}

	/**
	 * Records the bank's quantities and forgets changes older than the window.
	 *
	 * @param quantities every item in the bank by id, placeholders as 0
	 * @return whether anything to save changed
	 */
	boolean update(Map<Integer, Long> quantities, long now, long window)
	{
		boolean changed = false;
		if (seen == null)
		{
			// The first look since loading: only items with a history can show what changed in between.
			for (Map.Entry<Integer, List<long[]>> e : history.entrySet())
			{
				long quantity = quantities.getOrDefault(e.getKey(), 0L);
				if (latest(e.getValue()) != quantity)
				{
					e.getValue().add(new long[]{now, quantity});
					changed = true;
				}
			}
		}
		else
		{
			Set<Integer> ids = new HashSet<>(seen.keySet());
			ids.addAll(quantities.keySet());
			for (int id : ids)
			{
				long before = seen.getOrDefault(id, 0L);
				long quantity = quantities.getOrDefault(id, 0L);
				if (before != quantity)
				{
					List<long[]> changes = history.computeIfAbsent(id, k ->
					{
						List<long[]> start = new ArrayList<>();
						start.add(new long[]{0, before});
						return start;
					});
					changes.add(new long[]{now, quantity});
					changed = true;
				}
			}
		}
		seen = new HashMap<>(quantities);
		return prune(now - window) || changed;
	}

	/**
	 * Items deposited in the window, each with when it last grew.
	 *
	 * @param netGain only those there are more of now than at the window's start, not supplies taken out and partly
	 *                put back
	 */
	Map<Integer, Long> recent(long now, long window, boolean netGain)
	{
		long start = now - window;
		Map<Integer, Long> recent = new HashMap<>();
		history.forEach((id, changes) ->
		{
			long then = changes.get(0)[1];
			long grew = 0;
			for (int i = 1; i < changes.size(); i++)
			{
				long[] change = changes.get(i);
				if (change[0] <= start)
				{
					then = change[1];
				}
				else if (change[1] > changes.get(i - 1)[1])
				{
					grew = change[0];
				}
			}
			if (grew > 0 && (!netGain || latest(changes) > then))
			{
				recent.put(id, grew);
			}
		});
		return recent;
	}

	String save()
	{
		StringBuilder out = new StringBuilder();
		history.forEach((id, changes) ->
		{
			out.append(id).append('=');
			for (int i = 0; i < changes.size(); i++)
			{
				out.append(i > 0 ? "," : "").append(changes.get(i)[0]).append(':').append(changes.get(i)[1]);
			}
			out.append('\n');
		});
		return out.toString();
	}

	// Folds changes before the window into the starting quantity, and drops items left with nothing after it.
	private boolean prune(long start)
	{
		boolean changed = false;
		for (List<long[]> changes : history.values())
		{
			int keep = 1;
			while (keep < changes.size() && (changes.get(keep)[0] <= start || changes.size() - keep > MAX_CHANGES))
			{
				keep++;
			}
			if (keep > 1)
			{
				long[] first = changes.get(keep - 1);
				changes.subList(0, keep - 1).clear();
				first[0] = 0;
				changed = true;
			}
		}
		changed |= history.values().removeIf(changes -> changes.size() < 2);
		return changed;
	}

	private static long latest(List<long[]> changes)
	{
		return changes.get(changes.size() - 1)[1];
	}
}
