package com.banksorter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orders teleports by how commonly they're used: everyday jewellery and tablets first, niche ones after,
 * anything unlisted alphabetically at the end. Charge variants of one item stay together, most charges first.
 */
final class TeleportOrder
{
	private static final Pattern CHARGES = Pattern.compile("^(.*?)\\s*\\((\\d+)\\)$");
	// Matched by "name contains", first match wins, so more specific names come before general ones.
	private static final List<String> BY_USE = Arrays.asList(
		// Unlimited / most used
		"amulet of eternal glory", "amulet of glory", "ring of wealth", "royal seed pod", "xeric's talisman",
		"ectophial", "drakan's medallion", "teleport crystal", "pharaoh's sceptre",
		// Charged jewellery
		"ring of dueling", "games necklace", "skills necklace", "combat bracelet", "slayer ring",
		"necklace of passage", "digsite pendant", "burning amulet", "ring of the elements", "ring of returning",
		"ring of shadows",
		// Tablets and scrolls
		"teleport to house", "varrock teleport", "lumbridge teleport", "falador teleport", "camelot teleport",
		"ardougne teleport", "watchtower teleport", "kourend castle teleport", "civitas illa fortis teleport",
		"teleport scroll", "teleport tablet", "teleport"
	);

	private TeleportOrder()
	{
	}

	static <T> List<T> order(List<T> items, Function<T, String> name, Comparator<T> tiebreak)
	{
		List<T> out = new ArrayList<>(items);
		out.sort(Comparator.<T>comparingInt(t -> rank(baseName(name.apply(t))))
			.thenComparing(t -> baseName(name.apply(t)))
			.thenComparing(Comparator.<T>comparingInt(t -> charges(name.apply(t))).reversed())
			.thenComparing(tiebreak));
		return out;
	}

	static int rank(String base)
	{
		for (int i = 0; i < BY_USE.size(); i++)
		{
			if (base.contains(BY_USE.get(i)))
			{
				return i;
			}
		}
		return BY_USE.size();
	}

	private static String baseName(String name)
	{
		Matcher m = CHARGES.matcher(name);
		return (m.matches() ? m.group(1) : name).toLowerCase();
	}

	private static int charges(String name)
	{
		Matcher m = CHARGES.matcher(name);
		return m.matches() ? Integer.parseInt(m.group(2)) : 0;
	}
}
