package com.banksorter;

import static com.banksorter.Categories.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;

/**
 * Decides an item's category. Equipment is classified from its combat stats rather than its name,
 * everything else from its inventory actions (Eat, Drink, Bury, Break...) and then a few name rules.
 */
final class Categoriser
{
	private static final Set<String> TOOLS_EXACT = new HashSet<>(Arrays.asList(
		"hammer", "chisel", "knife", "tinderbox", "needle", "saw", "crystal saw", "spade", "rake",
		"seed dibber", "secateurs", "magic secateurs", "gardening trowel", "small fishing net",
		"big fishing net", "fishing rod", "fly fishing rod", "oily fishing rod", "lobster pot",
		"karambwan vessel", "glassblowing pipe", "pestle and mortar", "machete", "imcando hammer",
		"ammo mould", "amulet mould", "ring mould", "necklace mould", "bracelet mould", "tiara mould",
		"holy mould", "sickle mould", "bolt mould", "unholy mould", "bucket", "empty bucket", "pot",
		"jug", "shears", "noose wand", "butterfly net", "bird snare", "box trap", "rabbit snare",
		"teasing stick", "rope"
	));
	private static final String[] TOOL_SUFFIXES = {
		" pickaxe", " axe", " felling axe", " harpoon", " hammer", " saw", " chisel"
	};
	private static final Set<String> CLEAN_HERBS = new HashSet<>(Arrays.asList(
		"guam leaf", "marrentill", "tarromin", "harralander", "ranarr weed", "toadflax", "irit leaf",
		"avantoe", "kwuarm", "huasca", "snapdragon", "cadantine", "lantadyme", "dwarf weed", "torstol"
	));
	private static final Set<String> GEMS_EXACT = new HashSet<>(Arrays.asList(
		"opal", "jade", "red topaz", "sapphire", "emerald", "ruby", "diamond", "dragonstone", "onyx",
		"zenyte"
	));

	private static final Set<String> COMBAT_STYLES = new HashSet<>(Arrays.asList(MELEE, RANGED, MAGIC));
	private static final UserRules OVERRIDES = UserRules.parse(readResource("overrides.txt"));

	private final UserRules userRules;

	Categoriser(UserRules userRules)
	{
		this.userRules = userRules;
	}

	/**
	 * @param equipment combat stats, or null if the item is not equipable
	 */
	String categorise(int itemId, String name, String[] actions, ItemEquipmentStats equipment)
	{
		String custom = userRules.match(itemId, name);
		if (custom == null)
		{
			custom = OVERRIDES.match(itemId, name);
		}
		if (custom == null)
		{
			ItemData.Entry data = ItemData.get(name);
			custom = data == null ? null : data.category;
			// For combat gear the item's own stats decide the style; the data only moves gear out of the styles
			// (Slayer, a skill, Cosmetic...).
			if (custom != null && equipment != null && COMBAT_STYLES.contains(custom))
			{
				String byStats = gear(name.toLowerCase(), equipment);
				if (COMBAT_STYLES.contains(byStats))
				{
					custom = byStats;
				}
			}
		}
		return custom != null ? custom : builtIn(name.toLowerCase(), actions, equipment);
	}

	static String builtIn(String name, String[] actions, ItemEquipmentStats equipment)
	{
		if (name.equals("coins") || name.equals("platinum token"))
		{
			return CURRENCY;
		}
		// Before equipment: many tools are wieldable and would otherwise land in Melee.
		if (isTool(name))
		{
			return TOOLS;
		}
		// Charged teleport jewellery (glory, games necklace, dueling, wealth, combat bracelet...) is rubbed to
		// teleport; uncharged versions can't be, so they stay with gear. Xeric's talisman works the same way.
		if (hasAction(actions, "Rub") && (equipment != null || name.contains("talisman")))
		{
			return TELEPORTS;
		}
		if (equipment != null)
		{
			return gear(name, equipment);
		}
		// Dwarf multicannon parts and cannonballs.
		if (name.contains("cannon"))
		{
			return RANGED;
		}
		if (hasAction(actions, "Eat"))
		{
			return FOOD;
		}
		if (hasAction(actions, "Drink"))
		{
			return POTIONS;
		}
		if (hasAction(actions, "Bury") || hasAction(actions, "Scatter") || name.startsWith("ensouled "))
		{
			return PRAYER;
		}
		// Blighted spell sacks (ice barrage, entangle, vengeance, teleblock...) are spell supplies.
		if (name.startsWith("blighted ") && name.endsWith(" sack"))
		{
			return RUNES;
		}
		if (hasAction(actions, "Break") || name.contains("teleport") || name.equals("royal seed pod"))
		{
			return TELEPORTS;
		}
		if (name.endsWith(" rune") || name.contains("rune pouch"))
		{
			return RUNES;
		}
		// Crystal armour/weapon/tool seeds are Prifddinas crafting materials, not farming seeds.
		if ((name.contains("seed") && !name.contains("crystal ")) || name.contains("sapling") || name.contains("compost"))
		{
			return FARMING;
		}
		if (name.startsWith("grimy ") || name.endsWith("(unf)") || CLEAN_HERBS.contains(name)
			|| name.startsWith("vial of ") || name.equals("vial"))
		{
			return HERBLORE;
		}
		if (name.startsWith("uncut ") || GEMS_EXACT.contains(name))
		{
			return GEMS;
		}
		if (name.endsWith(" ore") || name.endsWith(" bar") || name.equals("coal"))
		{
			return ORES_BARS;
		}
		if (name.endsWith("logs") || name.endsWith("plank"))
		{
			return LOGS_PLANKS;
		}
		if (name.startsWith("clue scroll") || name.startsWith("reward casket") || name.startsWith("clue bottle")
			|| name.startsWith("clue geode") || name.startsWith("clue nest") || name.startsWith("scroll box"))
		{
			return CLUES;
		}
		return MISC;
	}

	static String gear(String name, ItemEquipmentStats e)
	{
		int slot = e.getSlot();
		if (slot == EquipmentInventorySlot.AMMO.getSlotIdx())
		{
			// God blessings use the ammo slot but suit every style; they live with Melee and get copies elsewhere.
			return name.contains("blessing") ? MELEE : AMMO;
		}

		int meleeAttack = Math.max(e.getAstab(), Math.max(e.getAslash(), e.getAcrush()));
		if (slot == EquipmentInventorySlot.WEAPON.getSlotIdx())
		{
			// Staves carry crush bonuses too, so any real magic bonus wins.
			if (e.getAmagic() >= 10 || e.getMdmg() > 0)
			{
				return MAGIC;
			}
			if (e.getArange() > 0 && e.getArange() >= meleeAttack)
			{
				return RANGED;
			}
			if (meleeAttack > 0 || e.getStr() > 0)
			{
				return MELEE;
			}
			return e.getAmagic() > 0 ? MAGIC : OTHER_GEAR;
		}

		// Armour: pick the style with the highest offensive contribution.
		int melee = Math.max(meleeAttack, 0) + e.getStr();
		int ranged = Math.max(e.getArange(), 0) + e.getRstr();
		int magic = Math.max(e.getAmagic(), 0) + Math.round(e.getMdmg());
		if (melee > 0 && melee >= ranged && melee >= magic)
		{
			return MELEE;
		}
		if (ranged > 0 && ranged >= magic)
		{
			return RANGED;
		}
		if (magic > 0)
		{
			return MAGIC;
		}

		// Amulets and rings without combat bonuses: teleport jewellery, ring of suffering, etc.
		if (slot == EquipmentInventorySlot.AMULET.getSlotIdx() || slot == EquipmentInventorySlot.RING.getSlotIdx())
		{
			return JEWELLERY;
		}

		// No offensive stats: plain defensive armour (plate, shields) is tank/melee gear.
		boolean defensive = e.getDstab() > 0 || e.getDslash() > 0 || e.getDcrush() > 0
			|| e.getDmagic() > 0 || e.getDrange() > 0;
		return defensive ? MELEE : OTHER_GEAR;
	}

	private static String readResource(String name)
	{
		try (InputStream in = Categoriser.class.getResourceAsStream(name))
		{
			return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			return "";
		}
	}

	static boolean isTool(String name)
	{
		if (TOOLS_EXACT.contains(name) || name.startsWith("watering can"))
		{
			return true;
		}
		for (String suffix : TOOL_SUFFIXES)
		{
			if (name.endsWith(suffix))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean hasAction(String[] actions, String action)
	{
		if (actions != null)
		{
			for (String a : actions)
			{
				if (action.equalsIgnoreCase(a))
				{
					return true;
				}
			}
		}
		return false;
	}
}
