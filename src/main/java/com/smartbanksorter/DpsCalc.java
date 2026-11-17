package com.smartbanksorter;

import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;

/**
 * Expected damage per game tick of a loadout against a typical boss, from the OSRS Wiki's formulas (the Maximum
 * melee/ranged/magic hit and Damage per second pages) and the normal-attack effects of the weapons that decide the
 * top of each style. It ranks gear; it isn't a full DPS calculator: no potions, special attacks or monster-specific
 * bonuses, and the best prayer for the style is assumed.
 */
final class DpsCalc
{
	/** The player's real combat levels. */
	static final class Levels
	{
		static final Levels MAXED = new Levels(99, 99, 99, 99);

		final int attack;
		final int strength;
		final int ranged;
		final int magic;

		Levels(int attack, int strength, int ranged, int magic)
		{
			this.attack = attack;
			this.strength = strength;
			this.ranged = ranged;
			this.magic = magic;
		}
	}

	/** An equipped item: its lower-case name and stats. */
	static final class Piece
	{
		final String name;
		final ItemEquipmentStats stats;

		Piece(String name, ItemEquipmentStats stats)
		{
			this.name = name.toLowerCase();
			this.stats = stats;
		}
	}

	// A typical boss: 200 Defence and Magic, +100 in every defence bonus, 3x3 or larger.
	static final int TARGET_DEFENCE = 200;
	static final int TARGET_MAGIC = 200;
	static final int TARGET_DEFENCE_BONUS = 100;
	static final int TARGET_SIZE = 3;
	private static final long DEFENCE_ROLL = (long) (TARGET_DEFENCE + 9) * (TARGET_DEFENCE_BONUS + 64);
	private static final long MAGIC_DEFENCE_ROLL = (long) (TARGET_MAGIC + 9) * (TARGET_DEFENCE_BONUS + 64);

	// Ranged strength of the ammo each launcher is assumed to fire: dragon arrows, bolts, darts, javelins.
	private static final int ARROWS = 60;
	private static final int BOLTS = 122;
	private static final int DARTS = 35;
	private static final int JAVELINS = 150;

	private static final int WEAPON = EquipmentInventorySlot.WEAPON.getSlotIdx();
	private static final int HEAD = EquipmentInventorySlot.HEAD.getSlotIdx();
	private static final int BODY = EquipmentInventorySlot.BODY.getSlotIdx();
	private static final int LEGS = EquipmentInventorySlot.LEGS.getSlotIdx();
	private static final int GLOVES = EquipmentInventorySlot.GLOVES.getSlotIdx();
	private static final int AMMO = EquipmentInventorySlot.AMMO.getSlotIdx();

	private DpsCalc()
	{
	}

	/**
	 * @param gear equipped pieces by equipment slot index, the weapon included
	 * @return expected damage per tick, 0 without a weapon
	 */
	static double perTick(String style, Map<Integer, Piece> gear, Levels levels)
	{
		Piece weapon = gear.get(WEAPON);
		if (weapon == null || weapon.stats == null)
		{
			return 0;
		}
		switch (style)
		{
			case Categories.RANGED:
				return ranged(weapon, gear, levels);
			case Categories.MAGIC:
				return magic(weapon, gear, levels);
			default:
				return melee(weapon, gear, levels);
		}
	}

	private static double melee(Piece weapon, Map<Integer, Piece> gear, Levels levels)
	{
		ItemEquipmentStats w = weapon.stats;
		String n = weapon.name;
		// The weapon attacks with its best attack type; gear bonuses of that type count.
		boolean crushing = w.getAcrush() > w.getAslash() && w.getAcrush() > w.getAstab();
		boolean stabbing = !crushing && w.getAstab() > w.getAslash();
		int bonus = crushing ? sum(gear, ItemEquipmentStats::getAcrush)
			: stabbing ? sum(gear, ItemEquipmentStats::getAstab) : sum(gear, ItemEquipmentStats::getAslash);
		int strength = sum(gear, ItemEquipmentStats::getStr);
		int speed = speed(w, 4);
		// Piety; each Soulreaper axe soul stack (5 when attacking steadily) adds 6% like a prayer.
		int strengthPercent = 123 + (n.startsWith("soulreaper axe") ? 30 : 0);
		int voidPercent = wearsVoid(gear, "void melee helm", false) ? 110 : 100;
		int inquisitor = crushing ? inquisitorPermille(gear) : 0;

		double best = 0;
		for (int stance = 0; stance < 2; stance++)
		{
			// Accurate (+3 Attack) or aggressive (+3 Strength), whichever does more.
			int effectiveStrength = (levels.strength * strengthPercent / 100 + (stance == 1 ? 3 : 0) + 8) * voidPercent / 100;
			int effectiveAttack = (levels.attack * 120 / 100 + (stance == 0 ? 3 : 0) + 8) * voidPercent / 100;
			int max = (effectiveStrength * (strength + 64) + 320) / 640;
			long attack = (long) effectiveAttack * (bonus + 64);
			if (inquisitor > 0)
			{
				attack = attack * (1000 + inquisitor) / 1000;
				max = max * (1000 + inquisitor) / 1000;
			}
			double perTick;
			if (n.contains("scythe of vitur") && !n.contains("uncharged"))
			{
				// One hit per tile of the target's width, up to three, each with half the max of the one before.
				double damage = 0;
				for (int i = 0; i < Math.min(3, TARGET_SIZE); i++)
				{
					damage += hit(attack, DEFENCE_ROLL) * average(max >> i);
				}
				perTick = damage / speed;
			}
			else if (n.startsWith("osmumten's fang"))
			{
				// Rolls accuracy twice; damage is 15-85% of the max, which keeps the same mean.
				perTick = twoRolls(attack, DEFENCE_ROLL) * average(max) / speed;
			}
			else if (n.equals("obligator"))
			{
				// Charged Thrust (5 ticks): +40% max hit against large targets, best of two damage rolls.
				// Smash (3 ticks) has no effect.
				int thrustMax = TARGET_SIZE >= 3 ? max * 140 / 100 : TARGET_SIZE == 2 ? max * 120 / 100 : max;
				double thrust = hit(attack, DEFENCE_ROLL) * bestOf(thrustMax, 2) / 5;
				double smash = hit(attack, DEFENCE_ROLL) * average(max) / 3;
				perTick = Math.max(thrust, smash);
			}
			else if (n.startsWith("dual macuahuitl"))
			{
				// Two hitsplats splitting the max; the second only lands if the first did. Blood moon: each landed
				// splat has a 33% chance to make the next attack come a tick early.
				double p = hit(attack, DEFENCE_ROLL);
				double early = wearsMoonSet(gear, "blood moon") ? p * (1 - p) * 0.33 + p * p * (1 - 0.67 * 0.67) : 0;
				perTick = (p * average(max / 2) + p * p * average(max - max / 2)) / (speed - early);
			}
			else
			{
				perTick = hit(attack, DEFENCE_ROLL) * average(max) / speed;
			}
			best = Math.max(best, perTick);
		}
		return best;
	}

	private static double ranged(Piece weapon, Map<Integer, Piece> gear, Levels levels)
	{
		String n = weapon.name;
		int speed = Math.max(1, speed(weapon.stats, 5) - 1); // rapid
		double accuracy = 1;
		double damage = 1;
		if (n.startsWith("twisted bow"))
		{
			// Scales with the target's Magic level (capped at 250 outside raids).
			int m = Math.min(TARGET_MAGIC, 250);
			accuracy = Math.min(140, 140 + (3 * m - 10) / 100.0 - Math.pow(0.3 * m - 100, 2) / 100) / 100;
			damage = Math.min(250, 250 + (3 * m - 14) / 100.0 - Math.pow(0.3 * m - 140, 2) / 100) / 100;
		}
		if (n.startsWith("bow of faerdhinen"))
		{
			// Crystal armour: helm +5%/+2.5%, body +15%/+7.5%, legs +10%/+5% accuracy/damage.
			if (startsWith(gear, HEAD, "crystal helm"))
			{
				accuracy += 0.05;
				damage += 0.025;
			}
			if (startsWith(gear, BODY, "crystal body"))
			{
				accuracy += 0.15;
				damage += 0.075;
			}
			if (startsWith(gear, LEGS, "crystal legs"))
			{
				accuracy += 0.10;
				damage += 0.05;
			}
		}
		boolean eliteVoid = wearsVoid(gear, "void ranger helm", true);
		boolean anyVoid = eliteVoid || wearsVoid(gear, "void ranger helm", false);
		// The eclipse atlatl hits with the Strength level and melee strength bonus; ranged prayers still apply.
		boolean atlatl = n.startsWith("eclipse atlatl");
		int damageLevel = atlatl ? levels.strength : levels.ranged;
		int effectiveStrength = (int) ((damageLevel * 123 / 100 + 8) * (eliteVoid ? 1.125 : anyVoid ? 1.1 : 1));
		int effectiveAttack = (levels.ranged * 120 / 100 + 8) * (anyVoid ? 110 : 100) / 100;
		int strength;
		if (atlatl)
		{
			strength = sum(gear, ItemEquipmentStats::getStr);
		}
		else
		{
			// The owned ammo in the outfit, else the best the launcher can fire.
			strength = sum(gear, ItemEquipmentStats::getRstr) + (gear.containsKey(AMMO) ? 0 : ammoStrength(n));
		}
		int max = (int) (((effectiveStrength * (strength + 64) + 320) / 640) * damage);
		long attack = (long) (effectiveAttack * (sum(gear, ItemEquipmentStats::getArange) + 64) * accuracy);
		int arrows = n.startsWith("dark bow") ? 2 : 1;
		double p = hit(attack, DEFENCE_ROLL);
		// Eclipse: a fifth of landed atlatl hits burn for 10 damage, about 2 per landed hit.
		double burn = atlatl && wearsMoonSet(gear, "eclipse moon") ? p * 2 : 0;
		return (arrows * p * average(max) + burn) / speed;
	}

	private static double magic(Piece weapon, Map<Integer, Piece> gear, Levels levels)
	{
		String n = weapon.name;
		int lvl = levels.magic;
		int base;
		int speed;
		boolean powered = true;
		double accuracyBonus = 0;
		double damageBonus = 0;
		if (n.startsWith("tumeken's shadow"))
		{
			base = lvl / 3 + 1;
			speed = 5;
		}
		else if (n.contains("sanguinesti staff"))
		{
			base = lvl / 3;
			speed = 4;
		}
		else if (n.startsWith("trident of the swamp"))
		{
			base = lvl / 3 - 2;
			speed = 4;
		}
		else if (n.startsWith("trident of the seas"))
		{
			base = lvl / 3 - 5;
			speed = 4;
		}
		else if (n.startsWith("eye of ayak"))
		{
			base = lvl / 3 - 6;
			speed = 3;
		}
		else if (n.startsWith("warped sceptre"))
		{
			base = (8 * lvl + 96) / 37;
			speed = 4;
		}
		else
		{
			// Autocasting: the best Ancient spell on staves that cast them, else the best fire spell.
			powered = false;
			boolean ancients = n.startsWith("kodai wand") || n.contains("nightmare staff") && !n.startsWith("harmonised")
				|| n.startsWith("ancient sceptre") || n.startsWith("ancient staff") || n.endsWith("ancient sceptre")
				|| n.startsWith("blue moon spear");
			base = ancients ? ancientSpell(lvl) : fireSpell(lvl);
			speed = n.startsWith("harmonised nightmare staff") ? 4 : n.startsWith("twinflame staff") ? 6 : 5;
			if (n.startsWith("twinflame staff"))
			{
				accuracyBonus = 0.10;
				damageBonus = 0.10;
			}
			if (n.contains("smoke") && n.contains("staff"))
			{
				damageBonus = 0.10;
			}
		}
		if (base <= 0)
		{
			return 0;
		}
		// The shadow triples every worn magic attack and damage bonus, its own included (damage capped at +100%).
		int shadow = n.startsWith("tumeken's shadow") ? 3 : 1;
		boolean eliteVoid = wearsVoid(gear, "void mage helm", true);
		boolean anyVoid = eliteVoid || wearsVoid(gear, "void mage helm", false);
		double magicDamage = Math.min(1.0, sum(gear, s -> Math.round(s.getMdmg() * 10)) / 1000.0 * shadow)
			+ 0.04 // Augury
			+ (eliteVoid ? 0.05 : 0) + damageBonus;
		int max = (int) (base * (1 + magicDamage));
		int effectiveMagic = (int) (lvl * 125 / 100 * (anyVoid ? 1.45 : 1)) + (powered ? 2 : 0) + 9;
		long attack = (long) (effectiveMagic * (long) (sum(gear, ItemEquipmentStats::getAmagic) * shadow + 64)
			* (1 + accuracyBonus));
		double p = startsWith(gear, GLOVES, "confliction gauntlets") && !weapon.stats.isTwoHanded()
			? confliction(attack, MAGIC_DEFENCE_ROLL) : hit(attack, MAGIC_DEFENCE_ROLL);
		// Sanguinesti: a fifth of hits deal 8 extra damage.
		double extra = n.contains("sanguinesti staff") ? p * 0.2 * 8 : 0;
		if (!powered && n.startsWith("blue moon spear") && wearsMoonSet(gear, "blue moon") && ancientSpell(lvl) > 0)
		{
			// Frostweaver: a fifth of Ice spell casts give a free melee hit with the spear (no melee prayer up).
			extra += 0.2 * spearHit(gear, levels);
		}
		return (p * average(max) + extra) / speed;
	}

	/** Expected damage of one stab with the worn gear, without a melee prayer. */
	private static double spearHit(Map<Integer, Piece> gear, Levels levels)
	{
		int effectiveStrength = levels.strength + 3 + 8;
		int effectiveAttack = levels.attack + 8;
		int max = (effectiveStrength * (sum(gear, ItemEquipmentStats::getStr) + 64) + 320) / 640;
		long attack = (long) effectiveAttack * (sum(gear, ItemEquipmentStats::getAstab) + 64);
		return hit(attack, DEFENCE_ROLL) * average(max);
	}

	private static int ancientSpell(int lvl)
	{
		return lvl >= 94 ? 30 : lvl >= 82 ? 26 : lvl >= 70 ? 22 : lvl >= 58 ? 18 : 0;
	}

	private static int fireSpell(int lvl)
	{
		return lvl >= 95 ? 24 : lvl >= 75 ? 20 : lvl >= 59 ? 16 : lvl >= 35 ? 12 : lvl >= 13 ? 8 : 0;
	}

	private static final String[] ARROW_TIERS = {"bronze", "iron", "steel", "mithril", "adamant", "rune", "amethyst",
		"dragon"};
	// Bolts by metal tier; gem-tipped ones by the metal they're made from: opal and jade bronze, pearl iron, topaz
	// steel, sapphire and emerald mithril, ruby and diamond adamant, dragonstone and onyx runite.
	private static final String[][] BOLT_TIERS = {
		{"bronze", "opal", "jade", "blurite", "barbed"}, {"iron", "pearl"}, {"steel", "topaz", "red topaz"},
		{"mithril", "sapphire", "emerald"}, {"adamant", "ruby", "diamond"}, {"runite", "dragonstone", "onyx", "broad"},
	};

	/**
	 * Whether the launcher fires this ammo: arrows and bolts up to the launcher's tier, and the special ammo of a
	 * few launchers.
	 */
	static boolean fires(String weaponName, String ammoName)
	{
		String w = weaponName.toLowerCase();
		String a = ammoName.toLowerCase();
		if (a.contains("javelin"))
		{
			return w.contains("ballista");
		}
		if (a.contains("atlatl dart"))
		{
			return w.startsWith("eclipse atlatl");
		}
		if (a.startsWith("bolt rack"))
		{
			return w.startsWith("karil's crossbow");
		}
		if (a.startsWith("ascension bolts"))
		{
			return w.startsWith("ascension crossbow");
		}
		if (a.contains("antler bolts"))
		{
			return w.startsWith("hunters' sunlight crossbow");
		}
		if (a.startsWith("kebbit bolts"))
		{
			return w.startsWith("hunters' crossbow");
		}
		if (a.startsWith("bone bolts"))
		{
			return w.startsWith("dorgeshuun crossbow");
		}
		if (a.contains("ogre arrow") || a.contains("brutal arrow"))
		{
			return w.contains("ogre bow");
		}
		if (a.startsWith("training arrows"))
		{
			return w.startsWith("training bow");
		}
		if (a.contains("bolts"))
		{
			int tier = boltTier(a);
			return tier > 0 && tier <= crossbowTier(w);
		}
		if (a.contains("arrow"))
		{
			int tier = arrowTier(a);
			return tier > 0 && tier <= bowTier(w);
		}
		return false;
	}

	private static int boltTier(String ammo)
	{
		if (ammo.contains("dragon bolts"))
		{
			return BOLT_TIERS.length + 1;
		}
		if (ammo.startsWith("amethyst broad"))
		{
			return BOLT_TIERS.length;
		}
		for (int i = 0; i < BOLT_TIERS.length; i++)
		{
			for (String prefix : BOLT_TIERS[i])
			{
				if (ammo.startsWith(prefix + " "))
				{
					return i + 1;
				}
			}
		}
		return 0;
	}

	private static int crossbowTier(String weapon)
	{
		if (!weapon.contains("crossbow") || weapon.startsWith("dorgeshuun") || weapon.startsWith("hunters'")
			|| weapon.startsWith("karil's") || weapon.startsWith("ascension"))
		{
			return 0;
		}
		String[] metals = {"bronze", "iron", "steel", "mithril", "adamant", "rune"};
		for (int i = 0; i < metals.length; i++)
		{
			if (weapon.startsWith(metals[i]))
			{
				return i + 1;
			}
		}
		if (weapon.startsWith("blurite") || weapon.equals("crossbow") || weapon.startsWith("phoenix"))
		{
			return 1;
		}
		return BOLT_TIERS.length + 1; // dragon, armadyl, zaryte, dragon hunter
	}

	private static int arrowTier(String ammo)
	{
		if (ammo.startsWith("broad arrows") || ammo.startsWith("amethyst broad"))
		{
			return 6;
		}
		if (ammo.startsWith("ice arrows"))
		{
			return 1;
		}
		for (int i = 0; i < ARROW_TIERS.length; i++)
		{
			if (ammo.startsWith(ARROW_TIERS[i] + " arrow"))
			{
				return i + 1;
			}
		}
		return 0;
	}

	private static int bowTier(String weapon)
	{
		if (weapon.contains("crossbow") || !weapon.contains("bow") || weapon.startsWith("craw's")
			|| weapon.startsWith("webweaver") || weapon.startsWith("bow of faerdhinen") || weapon.startsWith("crystal bow")
			|| weapon.startsWith("training bow") || weapon.contains("ogre bow"))
		{
			return 0;
		}
		String[][] woods = {{"magic", "7"}, {"seercull", "7"}, {"yew", "6"}, {"maple", "5"}, {"willow", "4"},
			{"oak", "3"}, {"shortbow", "2"}, {"longbow", "2"}};
		for (String[] wood : woods)
		{
			if (weapon.startsWith(wood[0]))
			{
				return Integer.parseInt(wood[1]);
			}
		}
		return ARROW_TIERS.length; // twisted, dark, venator, scorching, 3rd age...
	}

	// The best arrows and bolts each launcher tier can fire, by name prefix: ranged strength.
	private static final String[][] BOWS = {
		{"magic", "55"}, {"yew", "49"}, {"maple", "31"}, {"willow", "22"}, {"oak", "16"}, {"shortbow", "10"},
		{"longbow", "10"},
	};
	private static final String[][] CROSSBOWS = {
		{"rune", "115"}, {"adamant", "100"}, {"mithril", "82"}, {"steel", "64"}, {"iron", "46"}, {"bronze", "10"},
		{"blurite", "46"}, {"dorgeshuun", "49"}, {"hunters' sunlight", "60"}, {"crossbow", "10"},
	};

	private static int ammoStrength(String weapon)
	{
		if (weapon.contains("blowpipe"))
		{
			return DARTS;
		}
		if (weapon.matches(".*(knife|dart|thrownaxe|chinchompa|javelin|salamander|atlatl).*")
			|| weapon.startsWith("bow of faerdhinen") || weapon.startsWith("crystal bow")
			|| weapon.startsWith("ascension crossbows") || weapon.startsWith("craw's bow")
			|| weapon.startsWith("webweaver bow"))
		{
			return 0; // thrown, or no ammo
		}
		if (weapon.contains("ballista"))
		{
			return JAVELINS;
		}
		String[][] tiers = weapon.contains("crossbow") ? CROSSBOWS : BOWS;
		for (String[] tier : tiers)
		{
			if (weapon.startsWith(tier[0]))
			{
				return Integer.parseInt(tier[1]);
			}
		}
		// Dragon, armadyl, zaryte and dragon hunter crossbows; twisted, dark, venator, scorching and 3rd age bows.
		return weapon.contains("crossbow") ? BOLTS : ARROWS;
	}

	/** Full void: the style's helm plus top, robe and gloves; elite if the top and robe are elite. */
	private static boolean wearsVoid(Map<Integer, Piece> gear, String helm, boolean elite)
	{
		if (!startsWith(gear, HEAD, helm) || !startsWith(gear, GLOVES, "void knight gloves"))
		{
			return false;
		}
		boolean eliteTop = startsWith(gear, BODY, "elite void top");
		boolean eliteRobe = startsWith(gear, LEGS, "elite void robe");
		if (elite)
		{
			return eliteTop && eliteRobe;
		}
		return (eliteTop || startsWith(gear, BODY, "void knight top"))
			&& (eliteRobe || startsWith(gear, LEGS, "void knight robe"));
	}

	/** The helm, chestplate and tassets of a moon set (broken pieces count). */
	private static boolean wearsMoonSet(Map<Integer, Piece> gear, String set)
	{
		return startsWith(gear, HEAD, set + " helm") && startsWith(gear, BODY, set + " chestplate")
			&& startsWith(gear, LEGS, set + " tassets");
	}

	/** Inquisitor's armour on crush: +0.5% accuracy and damage per piece, 2.5% for all three. */
	private static int inquisitorPermille(Map<Integer, Piece> gear)
	{
		int pieces = (startsWith(gear, HEAD, "inquisitor's") ? 1 : 0) + (startsWith(gear, BODY, "inquisitor's") ? 1 : 0)
			+ (startsWith(gear, LEGS, "inquisitor's") ? 1 : 0);
		return pieces == 3 ? 25 : pieces * 5;
	}

	private static boolean startsWith(Map<Integer, Piece> gear, int slot, String prefix)
	{
		Piece p = gear.get(slot);
		return p != null && p.name.startsWith(prefix);
	}

	private interface Stat
	{
		long of(ItemEquipmentStats s);
	}

	private static int sum(Map<Integer, Piece> gear, Stat stat)
	{
		long total = 0;
		for (Piece p : gear.values())
		{
			if (p != null && p.stats != null)
			{
				total += stat.of(p.stats);
			}
		}
		return (int) total;
	}

	private static int speed(ItemEquipmentStats w, int fallback)
	{
		return w.getAspeed() > 0 ? w.getAspeed() : fallback;
	}

	static double hit(long attack, long defence)
	{
		attack = Math.max(attack, 0);
		return attack > defence ? 1 - (defence + 2) / (2.0 * (attack + 1)) : attack / (2.0 * (defence + 1));
	}

	/** Two accuracy rolls against one defence roll, either succeeding (Osmumten's fang). */
	static double twoRolls(long attack, long defence)
	{
		double a = Math.max(attack, 0);
		double d = defence;
		return a >= d ? 1 - (d + 2) * (2 * d + 3) / (6 * (a + 1) * (a + 1)) : a * (4 * a + 5) / (6 * (a + 1) * (d + 1));
	}

	/** Confliction gauntlets: a magic attack after a miss rolls accuracy twice; long-run hit chance. */
	private static double confliction(long attack, long defence)
	{
		double p = hit(attack, defence);
		double p2 = twoRolls(attack, defence);
		return p2 / (1 - p + p2);
	}

	private static double average(int max)
	{
		return Math.max(max, 0) / 2.0;
	}

	/** Mean of the highest of k damage rolls from 0 to max. */
	private static double bestOf(int max, int k)
	{
		double total = 0;
		for (int i = 1; i <= max; i++)
		{
			total += 1 - Math.pow((double) i / (max + 1), k);
		}
		return total;
	}
}
