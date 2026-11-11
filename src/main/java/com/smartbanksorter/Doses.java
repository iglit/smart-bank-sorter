package com.smartbanksorter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The bracketed number ending an item's name: a potion's doses, or a piece of jewellery's charges.
 */
final class Doses
{
	private static final Pattern DOSE = Pattern.compile("^(.*?)\\s*\\((\\d+)\\)$");

	private Doses()
	{
	}

	// "Prayer potion(4)" -> "Prayer potion", so doses of one potion stay together.
	static String baseName(String name)
	{
		Matcher m = DOSE.matcher(name);
		return m.matches() ? m.group(1) : name;
	}

	/**
	 * @return the number of doses or charges, or -1 if the name doesn't end in one
	 */
	static int dose(String name)
	{
		Matcher m = DOSE.matcher(name);
		return m.matches() ? Integer.parseInt(m.group(2)) : -1;
	}
}
