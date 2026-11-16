package com.smartbanksorter;

import java.util.List;
import java.util.function.Consumer;
import javax.swing.JLabel;

/**
 * What the category editor needs from the game. Item names can only be looked up on the client thread, so answers
 * come back later, on the Swing thread.
 */
interface ItemLookup
{
	final class Row
	{
		// -1 when no item has this name.
		final int itemId;
		final String name;
		// What a rule for this row says: an item name, a wildcard pattern or an ID.
		final String pattern;
		final String detail;
		// The category the item sorts into; null for a pattern or a name no item has.
		final String category;
		final boolean inBank;

		Row(int itemId, String name, String pattern, String detail, String category, boolean inBank)
		{
			this.itemId = itemId;
			this.name = name;
			this.pattern = pattern;
			this.detail = detail;
			this.category = category;
			this.inBank = inBank;
		}
	}

	/**
	 * A row that adds every item a search found as one rule, or null. With a {@code *} it's the search itself;
	 * otherwise every name containing the search, if that's more than one item.
	 *
	 * @param banked the search's items in the bank
	 * @param others the rest of its items
	 */
	static Row patternRow(String text, List<Row> banked, List<Row> others)
	{
		boolean wildcard = text.contains("*");
		int found = banked.size() + others.size();
		// A comma would split the rule in two.
		if (text.contains(",") || !wildcard && found < 2)
		{
			return null;
		}
		String pattern = wildcard ? text : "*" + text + "*";
		int icon = !banked.isEmpty() ? banked.get(0).itemId : !others.isEmpty() ? others.get(0).itemId : -1;
		String detail = "All matches" + (banked.isEmpty() ? "" : " · " + banked.size() + " in your bank");
		return new Row(icon, pattern, pattern, detail, null, !banked.isEmpty());
	}

	/**
	 * Whether a row stands for every item a pattern matches rather than one item and its doses.
	 */
	static boolean isGroup(Row row)
	{
		return row.pattern.contains("*") && !row.pattern.endsWith("(*");
	}

	/**
	 * Items whose names contain the text, or match it if it has a {@code *}, the bank's first. Doses and charges of
	 * one item are one row. A row to add them all as one rule, if there is one, comes first.
	 */
	void search(String text, Consumer<List<Row>> done);

	/**
	 * A row to show each rule pattern with, in the same order.
	 */
	void describe(List<String> patterns, Consumer<List<Row>> done);

	void icon(int itemId, JLabel label);
}
