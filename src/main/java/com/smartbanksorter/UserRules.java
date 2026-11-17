package com.smartbanksorter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
		// As written, IDs included.
		final List<String> written = new ArrayList<>();

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

				rule.written.add(pattern);
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
	 * @return the categories of every matching rule, each once, in the order they were declared
	 */
	Set<String> matchAll(int itemId, String name)
	{
		Set<String> categories = new LinkedHashSet<>();
		for (Rule rule : rules)
		{
			if (rule.ids.contains(itemId) || rule.names.stream().anyMatch(p -> p.matcher(name).matches()))
			{
				categories.add(rule.category);
			}
		}
		return categories;
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

	/**
	 * Each category's patterns as written, categories in the order they were declared.
	 */
	Map<String, List<String>> patterns()
	{
		Map<String, List<String>> patterns = new LinkedHashMap<>();
		for (Rule rule : rules)
		{
			patterns.computeIfAbsent(rule.category, k -> new ArrayList<>()).addAll(rule.written);
		}
		return patterns;
	}

	static Pattern glob(String pattern)
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
