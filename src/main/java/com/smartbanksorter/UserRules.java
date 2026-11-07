package com.smartbanksorter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * User-defined category rules, one per line:
 * <pre>
 * Slayer: slayer helm*, *slayer ring*, enchanted gem, 4155
 * </pre>
 * Patterns are item names (case-insensitive, {@code *} wildcard) or item IDs.
 * The first matching line wins. Lines starting with {@code #} are ignored.
 */
final class UserRules
{
	private static final class Rule
	{
		final String category;
		final Set<Integer> ids = new HashSet<>();
		final List<Pattern> names = new ArrayList<>();

		Rule(String category)
		{
			this.category = category;
		}
	}

	private final List<Rule> rules;

	private UserRules(List<Rule> rules)
	{
		this.rules = rules;
	}

	static UserRules parse(String text)
	{
		List<Rule> rules = new ArrayList<>();
		if (text == null)
		{
			return new UserRules(rules);
		}

		for (String line : text.split("\\R"))
		{
			line = line.trim();
			int colon = line.indexOf(':');
			if (line.startsWith("#") || colon <= 0)
			{
				continue;
			}

			Rule rule = new Rule(line.substring(0, colon).trim());
			for (String pattern : line.substring(colon + 1).split(","))
			{
				pattern = pattern.trim();
				if (pattern.isEmpty())
				{
					continue;
				}

				if (pattern.matches("\\d+"))
				{
					rule.ids.add(Integer.parseInt(pattern));
				}
				else
				{
					rule.names.add(glob(pattern));
				}
			}
			rules.add(rule);
		}
		return new UserRules(rules);
	}

	/**
	 * @return the category of the first matching rule, or null if none match
	 */
	String match(int itemId, String name)
	{
		for (Rule rule : rules)
		{
			if (rule.ids.contains(itemId))
			{
				return rule.category;
			}
			for (Pattern pattern : rule.names)
			{
				if (pattern.matcher(name).matches())
				{
					return rule.category;
				}
			}
		}
		return null;
	}

	/**
	 * Category names in the order they were declared.
	 */
	Set<String> categories()
	{
		Set<String> names = new LinkedHashSet<>();
		for (Rule rule : rules)
		{
			names.add(rule.category);
		}
		return names;
	}

	private static Pattern glob(String pattern)
	{
		StringBuilder regex = new StringBuilder();
		for (String part : pattern.split("\\*", -1))
		{
			if (regex.length() > 0)
			{
				regex.append(".*");
			}
			regex.append(Pattern.quote(part));
		}
		return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
	}
}
