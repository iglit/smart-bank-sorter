package com.smartbanksorter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

/**
 * Item lookups for the category editor, from the game's item definitions and the bank.
 */
final class GameItems implements ItemLookup
{
	private static final int MAX_RESULTS = 40;
	// Matches gathered from the whole game before sorting them; a short search can match thousands.
	private static final int MAX_CANDIDATES = 400;

	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	// The category the plugin sorts an item into, from its canonical ID and name. Client thread only.
	private final BiFunction<Integer, String, String> categoryOf;

	GameItems(Client client, ClientThread clientThread, ItemManager itemManager,
		BiFunction<Integer, String, String> categoryOf)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.categoryOf = categoryOf;
	}

	@Override
	public void search(String text, Consumer<List<Row>> done)
	{
		clientThread.invokeLater(() ->
		{
			List<Row> rows = searchNow(text.trim());
			SwingUtilities.invokeLater(() -> done.accept(rows));
		});
	}

	@Override
	public void describe(List<String> patterns, Consumer<List<Row>> done)
	{
		clientThread.invokeLater(() ->
		{
			Set<Integer> bank = bankItems();
			List<Row> rows = new ArrayList<>();
			patterns.forEach(p -> rows.add(describeNow(p, bank)));
			SwingUtilities.invokeLater(() -> done.accept(rows));
		});
	}

	@Override
	public void icon(int itemId, JLabel label)
	{
		itemManager.getImage(itemId).addTo(label);
	}

	private List<Row> searchNow(String text)
	{
		List<Row> rows = new ArrayList<>();
		if (text.isEmpty())
		{
			return rows;
		}
		String lower = text.toLowerCase();
		boolean wildcard = text.contains("*");
		Pattern glob = UserRules.glob(text);
		Predicate<String> matches = wildcard ? n -> glob.matcher(n).matches() : n -> n.toLowerCase().contains(lower);
		// Names starting with the search first, then alphabetical.
		Comparator<Row> order = Comparator.<Row>comparingInt(r -> r.name.toLowerCase().startsWith(lower) ? 0 : 1)
			.thenComparing(r -> r.name, String.CASE_INSENSITIVE_ORDER);

		Set<String> seen = new HashSet<>();
		Set<Integer> bank = bankItems();
		List<Row> banked = new ArrayList<>();
		for (int id : bank)
		{
			String name = itemManager.getItemComposition(id).getName();
			if (matches.test(name) && seen.add(MoveToMenu.movePattern(name).toLowerCase()))
			{
				banked.add(row(id, name, true));
			}
		}
		banked.sort(order);

		List<Row> others = new ArrayList<>();
		for (int id = 0; id < client.getItemCount() && others.size() < MAX_CANDIDATES; id++)
		{
			ItemComposition item = itemManager.getItemComposition(id);
			String name = item.getName();
			if (item.getNote() != -1 || item.getPlaceholderTemplateId() != -1 || "null".equals(name)
				|| !matches.test(name) || !seen.add(MoveToMenu.movePattern(name).toLowerCase()))
			{
				continue;
			}
			others.add(row(itemManager.canonicalize(id), name, false));
		}
		others.sort(order);

		Row group = ItemLookup.patternRow(text, banked, others);
		if (group != null)
		{
			rows.add(group);
		}
		rows.addAll(banked);
		rows.addAll(others);
		return rows.size() > MAX_RESULTS ? new ArrayList<>(rows.subList(0, MAX_RESULTS)) : rows;
	}

	// An item as a search result, with what a rule for it would say: doses and charges of it are one rule.
	private Row row(int id, String name, boolean inBank)
	{
		String pattern = MoveToMenu.movePattern(name);
		boolean doses = !pattern.equals(name);
		String category = categoryOf.apply(id, name);
		List<String> detail = new ArrayList<>();
		if (doses)
		{
			detail.add("All doses/charges");
		}
		detail.add("Now in " + category);
		if (inBank)
		{
			detail.add("In your bank");
		}
		return new Row(id, doses ? Doses.baseName(name) : name, pattern, String.join(" · ", detail), category, inBank);
	}

	private Row describeNow(String pattern, Set<Integer> bank)
	{
		if (pattern.matches("\\d+"))
		{
			int id = Integer.parseInt(pattern);
			if (id >= client.getItemCount())
			{
				return new Row(-1, "Item " + id, pattern, "No item has this ID", null, false);
			}
			return new Row(id, itemManager.getItemComposition(id).getName(), pattern, "Item ID " + id, categoryOf(id),
				bank.contains(id));
		}

		Pattern glob = UserRules.glob(pattern);
		int first = -1;
		int banked = 0;
		for (int id : bank)
		{
			if (glob.matcher(itemManager.getItemComposition(id).getName()).matches())
			{
				banked++;
				first = first < 0 ? id : first;
			}
		}
		if (first < 0)
		{
			first = firstInGame(glob);
		}

		// "Prayer potion(*", as Move to and the editor write for every dose of an item.
		String base = pattern.endsWith("(*") ? pattern.substring(0, pattern.length() - 2) : null;
		if (base != null && !base.contains("*"))
		{
			return new Row(first, base.trim(), pattern, banked > 0 ? "All doses/charges · In your bank"
				: "All doses/charges", first < 0 ? null : categoryOf(first), banked > 0);
		}
		if (pattern.contains("*"))
		{
			return new Row(first, pattern, pattern, banked == 0 ? "All matches · none in your bank"
				: "All matches · " + banked + " in your bank", null, banked > 0);
		}
		if (first < 0)
		{
			return new Row(-1, pattern, pattern, "No item has this name", null, false);
		}
		return new Row(first, itemManager.getItemComposition(first).getName(), pattern,
			banked > 0 ? "In your bank" : "Not in your bank", categoryOf(first), banked > 0);
	}

	private String categoryOf(int itemId)
	{
		int id = itemManager.canonicalize(itemId);
		return categoryOf.apply(id, itemManager.getItemComposition(id).getName());
	}

	private int firstInGame(Pattern glob)
	{
		for (int id = 0; id < client.getItemCount(); id++)
		{
			ItemComposition item = itemManager.getItemComposition(id);
			if (item.getNote() == -1 && item.getPlaceholderTemplateId() == -1 && glob.matcher(item.getName()).matches())
			{
				return id;
			}
		}
		return -1;
	}

	// The bank's items as the sorted bank shows them: placeholders as their items, no fillers.
	private Set<Integer> bankItems()
	{
		Set<Integer> ids = new LinkedHashSet<>();
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank != null)
		{
			for (Item item : bank.getItems())
			{
				if (item.getId() > 0 && item.getId() != ItemID.BANK_FILLER)
				{
					ids.add(itemManager.canonicalize(item.getId()));
				}
			}
		}
		return ids;
	}
}
