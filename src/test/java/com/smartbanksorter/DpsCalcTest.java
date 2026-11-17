package com.smartbanksorter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;
import org.junit.Test;

/**
 * Best outfits for top weapons, with OSRS Wiki item stats, checked against the wiki's DPS calculator for the same
 * outfit, 99s and the style's best prayer against DpsCalc's reference boss (200 Defence and Magic, +100 defence
 * bonuses, size 3). Ammo is the dragon ammo DpsCalc assumes.
 */
public class DpsCalcTest
{
	private static final class Worn
	{
		final int slot;
		final DpsCalc.Piece piece;

		Worn(int slot, DpsCalc.Piece piece)
		{
			this.slot = slot;
			this.piece = piece;
		}
	}

	private static Worn piece(EquipmentInventorySlot slot, String name, ItemEquipmentStats stats)
	{
		return new Worn(slot.getSlotIdx(), new DpsCalc.Piece(name, stats));
	}

	private static Map<Integer, DpsCalc.Piece> gear(Worn... pieces)
	{
		Map<Integer, DpsCalc.Piece> gear = new HashMap<>();
		for (Worn w : pieces)
		{
			gear.put(w.slot, w.piece);
		}
		return gear;
	}

	private static void assertWithin3Percent(double wikiDps, String style, Map<Integer, DpsCalc.Piece> gear)
	{
		double ours = DpsCalc.perTick(style, gear, DpsCalc.Levels.MAXED) / 0.6;
		assertEquals(wikiDps, ours, wikiDps * 0.03);
	}

	@Test
	public void scytheOfViturMatchesTheWikiCalculator()
	{
		// Wiki DPS calculator: 6.48 DPS for this outfit against the reference boss.
		assertWithin3Percent(6.4778, Categories.MELEE, gear(
			piece(EquipmentInventorySlot.WEAPON, "scythe of vitur", ItemEquipmentStats.builder().astab(70).aslash(125).acrush(30).amagic(-6).str(75).aspeed(5).isTwoHanded(true).build()),
			piece(EquipmentInventorySlot.HEAD, "oathplate helm", ItemEquipmentStats.builder().aslash(10).arange(-7).amagic(-2).str(6).build()),
			piece(EquipmentInventorySlot.CAPE, "infernal cape", ItemEquipmentStats.builder().astab(4).aslash(4).acrush(4).arange(1).amagic(1).str(8).prayer(2).build()),
			piece(EquipmentInventorySlot.AMULET, "amulet of rancour", ItemEquipmentStats.builder().astab(25).aslash(25).acrush(25).arange(-8).amagic(-6).str(12).prayer(2).build()),
			piece(EquipmentInventorySlot.BODY, "oathplate chest", ItemEquipmentStats.builder().aslash(16).arange(-18).amagic(-16).str(4).build()),
			piece(EquipmentInventorySlot.LEGS, "oathplate legs", ItemEquipmentStats.builder().aslash(12).arange(-14).amagic(-12).str(2).build()),
			piece(EquipmentInventorySlot.GLOVES, "ferocious gloves", ItemEquipmentStats.builder().astab(16).aslash(16).acrush(16).arange(-16).amagic(-16).str(14).build()),
			piece(EquipmentInventorySlot.BOOTS, "avernic treads (max)", ItemEquipmentStats.builder().astab(5).aslash(5).acrush(5).arange(15).amagic(11).str(6).rstr(3).mdmg(2.0f).build()),
			piece(EquipmentInventorySlot.RING, "bellator ring", ItemEquipmentStats.builder().aslash(20).str(6).build())));
	}

	@Test
	public void osmumtenSFangMatchesTheWikiCalculator()
	{
		// Wiki DPS calculator: 5.10 DPS for this outfit against the reference boss.
		assertWithin3Percent(5.1020, Categories.MELEE, gear(
			piece(EquipmentInventorySlot.WEAPON, "osmumten's fang", ItemEquipmentStats.builder().astab(105).aslash(75).str(103).aspeed(5).build()),
			piece(EquipmentInventorySlot.HEAD, "sanguine torva full helm", ItemEquipmentStats.builder().arange(-5).amagic(-5).str(8).prayer(1).build()),
			piece(EquipmentInventorySlot.CAPE, "infernal cape", ItemEquipmentStats.builder().astab(4).aslash(4).acrush(4).arange(1).amagic(1).str(8).prayer(2).build()),
			piece(EquipmentInventorySlot.AMULET, "amulet of rancour", ItemEquipmentStats.builder().astab(25).aslash(25).acrush(25).arange(-8).amagic(-6).str(12).prayer(2).build()),
			piece(EquipmentInventorySlot.BODY, "oathplate chest", ItemEquipmentStats.builder().aslash(16).arange(-18).amagic(-16).str(4).build()),
			piece(EquipmentInventorySlot.SHIELD, "avernic defender", ItemEquipmentStats.builder().astab(30).aslash(29).acrush(28).arange(-4).amagic(-5).str(8).build()),
			piece(EquipmentInventorySlot.LEGS, "oathplate legs", ItemEquipmentStats.builder().aslash(12).arange(-14).amagic(-12).str(2).build()),
			piece(EquipmentInventorySlot.GLOVES, "ferocious gloves", ItemEquipmentStats.builder().astab(16).aslash(16).acrush(16).arange(-16).amagic(-16).str(14).build()),
			piece(EquipmentInventorySlot.BOOTS, "avernic treads (max)", ItemEquipmentStats.builder().astab(5).aslash(5).acrush(5).arange(15).amagic(11).str(6).rstr(3).mdmg(2.0f).build()),
			piece(EquipmentInventorySlot.RING, "ultor ring", ItemEquipmentStats.builder().str(12).build())));
	}

	@Test
	public void obligatorMatchesTheWikiCalculator()
	{
		// Wiki DPS calculator: 5.60 DPS for this outfit against the reference boss.
		assertWithin3Percent(5.6043, Categories.MELEE, gear(
			piece(EquipmentInventorySlot.WEAPON, "obligator", ItemEquipmentStats.builder().astab(22).aslash(-8).acrush(120).str(40).prayer(3).aspeed(5).isTwoHanded(true).build()),
			piece(EquipmentInventorySlot.HEAD, "inquisitor's great helm", ItemEquipmentStats.builder().acrush(10).arange(-5).amagic(-5).str(6).prayer(1).build()),
			piece(EquipmentInventorySlot.CAPE, "infernal cape", ItemEquipmentStats.builder().astab(4).aslash(4).acrush(4).arange(1).amagic(1).str(8).prayer(2).build()),
			piece(EquipmentInventorySlot.AMULET, "amulet of rancour", ItemEquipmentStats.builder().astab(25).aslash(25).acrush(25).arange(-8).amagic(-6).str(12).prayer(2).build()),
			piece(EquipmentInventorySlot.BODY, "inquisitor's hauberk", ItemEquipmentStats.builder().acrush(16).arange(-10).amagic(-11).str(4).prayer(2).build()),
			piece(EquipmentInventorySlot.LEGS, "inquisitor's plateskirt", ItemEquipmentStats.builder().acrush(12).arange(-5).amagic(-9).str(2).prayer(2).build()),
			piece(EquipmentInventorySlot.GLOVES, "ferocious gloves", ItemEquipmentStats.builder().astab(16).aslash(16).acrush(16).arange(-16).amagic(-16).str(14).build()),
			piece(EquipmentInventorySlot.BOOTS, "avernic treads (max)", ItemEquipmentStats.builder().astab(5).aslash(5).acrush(5).arange(15).amagic(11).str(6).rstr(3).mdmg(2.0f).build()),
			piece(EquipmentInventorySlot.RING, "ultor ring", ItemEquipmentStats.builder().str(12).build())));
	}

	@Test
	public void inquisitorSMaceMatchesTheWikiCalculator()
	{
		// Wiki DPS calculator: 5.41 DPS for this outfit against the reference boss.
		assertWithin3Percent(5.4089, Categories.MELEE, gear(
			piece(EquipmentInventorySlot.WEAPON, "inquisitor's mace", ItemEquipmentStats.builder().astab(52).aslash(-4).acrush(102).str(96).prayer(2).aspeed(4).build()),
			piece(EquipmentInventorySlot.HEAD, "inquisitor's great helm", ItemEquipmentStats.builder().acrush(10).arange(-5).amagic(-5).str(6).prayer(1).build()),
			piece(EquipmentInventorySlot.CAPE, "infernal cape", ItemEquipmentStats.builder().astab(4).aslash(4).acrush(4).arange(1).amagic(1).str(8).prayer(2).build()),
			piece(EquipmentInventorySlot.AMULET, "amulet of rancour", ItemEquipmentStats.builder().astab(25).aslash(25).acrush(25).arange(-8).amagic(-6).str(12).prayer(2).build()),
			piece(EquipmentInventorySlot.BODY, "inquisitor's hauberk", ItemEquipmentStats.builder().acrush(16).arange(-10).amagic(-11).str(4).prayer(2).build()),
			piece(EquipmentInventorySlot.SHIELD, "avernic defender", ItemEquipmentStats.builder().astab(30).aslash(29).acrush(28).arange(-4).amagic(-5).str(8).build()),
			piece(EquipmentInventorySlot.LEGS, "inquisitor's plateskirt", ItemEquipmentStats.builder().acrush(12).arange(-5).amagic(-9).str(2).prayer(2).build()),
			piece(EquipmentInventorySlot.GLOVES, "ferocious gloves", ItemEquipmentStats.builder().astab(16).aslash(16).acrush(16).arange(-16).amagic(-16).str(14).build()),
			piece(EquipmentInventorySlot.BOOTS, "avernic treads (max)", ItemEquipmentStats.builder().astab(5).aslash(5).acrush(5).arange(15).amagic(11).str(6).rstr(3).mdmg(2.0f).build()),
			piece(EquipmentInventorySlot.RING, "ultor ring", ItemEquipmentStats.builder().str(12).build())));
	}

	@Test
	public void abyssalWhipMatchesTheWikiCalculator()
	{
		// Wiki DPS calculator: 4.76 DPS for this outfit against the reference boss.
		assertWithin3Percent(4.7578, Categories.MELEE, gear(
			piece(EquipmentInventorySlot.WEAPON, "abyssal whip", ItemEquipmentStats.builder().aslash(82).str(82).aspeed(4).build()),
			piece(EquipmentInventorySlot.HEAD, "oathplate helm", ItemEquipmentStats.builder().aslash(10).arange(-7).amagic(-2).str(6).build()),
			piece(EquipmentInventorySlot.CAPE, "infernal cape", ItemEquipmentStats.builder().astab(4).aslash(4).acrush(4).arange(1).amagic(1).str(8).prayer(2).build()),
			piece(EquipmentInventorySlot.AMULET, "amulet of rancour", ItemEquipmentStats.builder().astab(25).aslash(25).acrush(25).arange(-8).amagic(-6).str(12).prayer(2).build()),
			piece(EquipmentInventorySlot.BODY, "oathplate chest", ItemEquipmentStats.builder().aslash(16).arange(-18).amagic(-16).str(4).build()),
			piece(EquipmentInventorySlot.SHIELD, "avernic defender", ItemEquipmentStats.builder().astab(30).aslash(29).acrush(28).arange(-4).amagic(-5).str(8).build()),
			piece(EquipmentInventorySlot.LEGS, "oathplate legs", ItemEquipmentStats.builder().aslash(12).arange(-14).amagic(-12).str(2).build()),
			piece(EquipmentInventorySlot.GLOVES, "ferocious gloves", ItemEquipmentStats.builder().astab(16).aslash(16).acrush(16).arange(-16).amagic(-16).str(14).build()),
			piece(EquipmentInventorySlot.BOOTS, "avernic treads (max)", ItemEquipmentStats.builder().astab(5).aslash(5).acrush(5).arange(15).amagic(11).str(6).rstr(3).mdmg(2.0f).build()),
			piece(EquipmentInventorySlot.RING, "bellator ring", ItemEquipmentStats.builder().aslash(20).str(6).build())));
	}

	@Test
	public void twistedBowMatchesTheWikiCalculator()
	{
		// Wiki DPS calculator: 7.01 DPS for this outfit against the reference boss.
		assertWithin3Percent(7.0064, Categories.RANGED, gear(
			piece(EquipmentInventorySlot.WEAPON, "twisted bow", ItemEquipmentStats.builder().arange(70).rstr(20).aspeed(6).isTwoHanded(true).build()),
			piece(EquipmentInventorySlot.HEAD, "masori mask (f)", ItemEquipmentStats.builder().arange(12).amagic(-1).rstr(2).prayer(1).build()),
			piece(EquipmentInventorySlot.CAPE, "blessed dizana's quiver", ItemEquipmentStats.builder().arange(18).rstr(3).build()),
			piece(EquipmentInventorySlot.AMULET, "necklace of rupture", ItemEquipmentStats.builder().arange(20).rstr(8).prayer(3).build()),
			piece(EquipmentInventorySlot.BODY, "masori body (f)", ItemEquipmentStats.builder().arange(43).amagic(-4).rstr(4).prayer(1).build()),
			piece(EquipmentInventorySlot.LEGS, "masori chaps (f)", ItemEquipmentStats.builder().arange(27).amagic(-2).rstr(2).prayer(1).build()),
			piece(EquipmentInventorySlot.GLOVES, "zaryte vambraces", ItemEquipmentStats.builder().astab(-8).aslash(-8).acrush(-8).arange(18).rstr(2).prayer(1).build()),
			piece(EquipmentInventorySlot.BOOTS, "avernic treads (pe)", ItemEquipmentStats.builder().astab(5).aslash(5).acrush(5).arange(15).amagic(11).str(4).rstr(3).mdmg(1.0f).build()),
			piece(EquipmentInventorySlot.RING, "venator ring", ItemEquipmentStats.builder().arange(10).rstr(2).build())));
	}

	@Test
	public void bowOfFaerdhinenCMatchesTheWikiCalculator()
	{
		// Wiki DPS calculator: 6.13 DPS for this outfit against the reference boss.
		assertWithin3Percent(6.1330, Categories.RANGED, gear(
			piece(EquipmentInventorySlot.WEAPON, "bow of faerdhinen (c)", ItemEquipmentStats.builder().arange(128).rstr(106).aspeed(5).isTwoHanded(true).build()),
			piece(EquipmentInventorySlot.HEAD, "crystal helm", ItemEquipmentStats.builder().arange(9).amagic(-10).prayer(2).build()),
			piece(EquipmentInventorySlot.CAPE, "blessed dizana's quiver", ItemEquipmentStats.builder().arange(18).rstr(3).build()),
			piece(EquipmentInventorySlot.AMULET, "necklace of rupture", ItemEquipmentStats.builder().arange(20).rstr(8).prayer(3).build()),
			piece(EquipmentInventorySlot.BODY, "crystal body", ItemEquipmentStats.builder().arange(31).amagic(-18).prayer(3).build()),
			piece(EquipmentInventorySlot.LEGS, "crystal legs", ItemEquipmentStats.builder().arange(18).amagic(-12).prayer(2).build()),
			piece(EquipmentInventorySlot.GLOVES, "zaryte vambraces", ItemEquipmentStats.builder().astab(-8).aslash(-8).acrush(-8).arange(18).rstr(2).prayer(1).build()),
			piece(EquipmentInventorySlot.BOOTS, "avernic treads (pe)", ItemEquipmentStats.builder().astab(5).aslash(5).acrush(5).arange(15).amagic(11).str(4).rstr(3).mdmg(1.0f).build()),
			piece(EquipmentInventorySlot.RING, "venator ring", ItemEquipmentStats.builder().arange(10).rstr(2).build())));
	}

	@Test
	public void toxicBlowpipeMatchesTheWikiCalculator()
	{
		// Wiki DPS calculator: 5.72 DPS for this outfit against the reference boss.
		assertWithin3Percent(5.7208, Categories.RANGED, gear(
			piece(EquipmentInventorySlot.WEAPON, "toxic blowpipe", ItemEquipmentStats.builder().arange(30).rstr(20).aspeed(3).isTwoHanded(true).build()),
			piece(EquipmentInventorySlot.HEAD, "masori mask (f)", ItemEquipmentStats.builder().arange(12).amagic(-1).rstr(2).prayer(1).build()),
			piece(EquipmentInventorySlot.CAPE, "blessed dizana's quiver", ItemEquipmentStats.builder().arange(18).rstr(3).build()),
			piece(EquipmentInventorySlot.AMULET, "necklace of rupture", ItemEquipmentStats.builder().arange(20).rstr(8).prayer(3).build()),
			piece(EquipmentInventorySlot.BODY, "masori body (f)", ItemEquipmentStats.builder().arange(43).amagic(-4).rstr(4).prayer(1).build()),
			piece(EquipmentInventorySlot.LEGS, "masori chaps (f)", ItemEquipmentStats.builder().arange(27).amagic(-2).rstr(2).prayer(1).build()),
			piece(EquipmentInventorySlot.GLOVES, "zaryte vambraces", ItemEquipmentStats.builder().astab(-8).aslash(-8).acrush(-8).arange(18).rstr(2).prayer(1).build()),
			piece(EquipmentInventorySlot.BOOTS, "avernic treads (pe)", ItemEquipmentStats.builder().astab(5).aslash(5).acrush(5).arange(15).amagic(11).str(4).rstr(3).mdmg(1.0f).build()),
			piece(EquipmentInventorySlot.RING, "venator ring", ItemEquipmentStats.builder().arange(10).rstr(2).build())));
	}

	@Test
	public void zaryteCrossbowMatchesTheWikiCalculator()
	{
		// Wiki DPS calculator: 4.46 DPS for this outfit against the reference boss.
		assertWithin3Percent(4.4560, Categories.RANGED, gear(
			piece(EquipmentInventorySlot.WEAPON, "zaryte crossbow", ItemEquipmentStats.builder().arange(110).prayer(1).aspeed(6).build()),
			piece(EquipmentInventorySlot.HEAD, "masori mask (f)", ItemEquipmentStats.builder().arange(12).amagic(-1).rstr(2).prayer(1).build()),
			piece(EquipmentInventorySlot.CAPE, "blessed dizana's quiver", ItemEquipmentStats.builder().arange(18).rstr(3).build()),
			piece(EquipmentInventorySlot.AMULET, "necklace of rupture", ItemEquipmentStats.builder().arange(20).rstr(8).prayer(3).build()),
			piece(EquipmentInventorySlot.BODY, "masori body (f)", ItemEquipmentStats.builder().arange(43).amagic(-4).rstr(4).prayer(1).build()),
			piece(EquipmentInventorySlot.SHIELD, "aquanite hopper", ItemEquipmentStats.builder().astab(-5).aslash(-5).acrush(-3).arange(12).amagic(-13).rstr(1).build()),
			piece(EquipmentInventorySlot.LEGS, "masori chaps (f)", ItemEquipmentStats.builder().arange(27).amagic(-2).rstr(2).prayer(1).build()),
			piece(EquipmentInventorySlot.GLOVES, "zaryte vambraces", ItemEquipmentStats.builder().astab(-8).aslash(-8).acrush(-8).arange(18).rstr(2).prayer(1).build()),
			piece(EquipmentInventorySlot.BOOTS, "avernic treads (pe)", ItemEquipmentStats.builder().astab(5).aslash(5).acrush(5).arange(15).amagic(11).str(4).rstr(3).mdmg(1.0f).build()),
			piece(EquipmentInventorySlot.RING, "venator ring", ItemEquipmentStats.builder().arange(10).rstr(2).build())));
	}

	@Test
	public void tumekenSShadowMatchesTheWikiCalculator()
	{
		// Wiki DPS calculator: 8.12 DPS for this outfit against the reference boss.
		assertWithin3Percent(8.1162, Categories.MAGIC, gear(
			piece(EquipmentInventorySlot.WEAPON, "tumeken's shadow", ItemEquipmentStats.builder().amagic(35).prayer(1).aspeed(5).isTwoHanded(true).build()),
			piece(EquipmentInventorySlot.HEAD, "ancestral hat", ItemEquipmentStats.builder().arange(-2).amagic(8).mdmg(3.0f).build()),
			piece(EquipmentInventorySlot.CAPE, "imbued guthix max cape", ItemEquipmentStats.builder().amagic(15).mdmg(2.0f).build()),
			piece(EquipmentInventorySlot.AMULET, "occult necklace", ItemEquipmentStats.builder().amagic(12).prayer(2).mdmg(5.0f).build()),
			piece(EquipmentInventorySlot.BODY, "ancestral robe top", ItemEquipmentStats.builder().arange(-8).amagic(35).mdmg(3.0f).build()),
			piece(EquipmentInventorySlot.LEGS, "twisted ancestral robe bottom", ItemEquipmentStats.builder().arange(-7).amagic(26).mdmg(3.0f).build()),
			piece(EquipmentInventorySlot.GLOVES, "confliction gauntlets", ItemEquipmentStats.builder().arange(-4).amagic(20).prayer(2).mdmg(7.0f).build()),
			piece(EquipmentInventorySlot.BOOTS, "avernic treads (et)", ItemEquipmentStats.builder().astab(5).aslash(5).acrush(5).arange(15).amagic(11).str(4).rstr(2).mdmg(2.0f).build()),
			piece(EquipmentInventorySlot.RING, "magus ring", ItemEquipmentStats.builder().amagic(15).mdmg(2.0f).build())));
	}

	@Test
	public void sanguinestiStaffMatchesTheWikiCalculator()
	{
		// Wiki DPS calculator: 5.65 DPS for this outfit against the reference boss.
		assertWithin3Percent(5.6497, Categories.MAGIC, gear(
			piece(EquipmentInventorySlot.WEAPON, "sanguinesti staff", ItemEquipmentStats.builder().arange(-4).amagic(25).aspeed(4).build()),
			piece(EquipmentInventorySlot.HEAD, "ancestral hat", ItemEquipmentStats.builder().arange(-2).amagic(8).mdmg(3.0f).build()),
			piece(EquipmentInventorySlot.CAPE, "imbued guthix max cape", ItemEquipmentStats.builder().amagic(15).mdmg(2.0f).build()),
			piece(EquipmentInventorySlot.AMULET, "occult necklace", ItemEquipmentStats.builder().amagic(12).prayer(2).mdmg(5.0f).build()),
			piece(EquipmentInventorySlot.BODY, "ancestral robe top", ItemEquipmentStats.builder().arange(-8).amagic(35).mdmg(3.0f).build()),
			piece(EquipmentInventorySlot.SHIELD, "devil's element", ItemEquipmentStats.builder().amagic(20).prayer(3).mdmg(6.0f).build()),
			piece(EquipmentInventorySlot.LEGS, "twisted ancestral robe bottom", ItemEquipmentStats.builder().arange(-7).amagic(26).mdmg(3.0f).build()),
			piece(EquipmentInventorySlot.GLOVES, "confliction gauntlets", ItemEquipmentStats.builder().arange(-4).amagic(20).prayer(2).mdmg(7.0f).build()),
			piece(EquipmentInventorySlot.BOOTS, "avernic treads (et)", ItemEquipmentStats.builder().astab(5).aslash(5).acrush(5).arange(15).amagic(11).str(4).rstr(2).mdmg(2.0f).build()),
			piece(EquipmentInventorySlot.RING, "magus ring", ItemEquipmentStats.builder().amagic(15).mdmg(2.0f).build())));
	}

	@Test
	public void eyeOfAyakMatchesTheWikiCalculator()
	{
		// Wiki DPS calculator: 5.82 DPS for this outfit against the reference boss.
		assertWithin3Percent(5.8199, Categories.MAGIC, gear(
			piece(EquipmentInventorySlot.WEAPON, "eye of ayak", ItemEquipmentStats.builder().amagic(30).prayer(2).aspeed(3).build()),
			piece(EquipmentInventorySlot.HEAD, "ancestral hat", ItemEquipmentStats.builder().arange(-2).amagic(8).mdmg(3.0f).build()),
			piece(EquipmentInventorySlot.CAPE, "imbued guthix max cape", ItemEquipmentStats.builder().amagic(15).mdmg(2.0f).build()),
			piece(EquipmentInventorySlot.AMULET, "occult necklace", ItemEquipmentStats.builder().amagic(12).prayer(2).mdmg(5.0f).build()),
			piece(EquipmentInventorySlot.BODY, "ancestral robe top", ItemEquipmentStats.builder().arange(-8).amagic(35).mdmg(3.0f).build()),
			piece(EquipmentInventorySlot.SHIELD, "elidinis' ward (f)", ItemEquipmentStats.builder().amagic(25).prayer(4).mdmg(5.0f).build()),
			piece(EquipmentInventorySlot.LEGS, "twisted ancestral robe bottom", ItemEquipmentStats.builder().arange(-7).amagic(26).mdmg(3.0f).build()),
			piece(EquipmentInventorySlot.GLOVES, "confliction gauntlets", ItemEquipmentStats.builder().arange(-4).amagic(20).prayer(2).mdmg(7.0f).build()),
			piece(EquipmentInventorySlot.BOOTS, "avernic treads (et)", ItemEquipmentStats.builder().astab(5).aslash(5).acrush(5).arange(15).amagic(11).str(4).rstr(2).mdmg(2.0f).build()),
			piece(EquipmentInventorySlot.RING, "magus ring", ItemEquipmentStats.builder().amagic(15).mdmg(2.0f).build())));
	}

	@Test
	public void kodaiWandMatchesTheWikiCalculator()
	{
		// Wiki DPS calculator: 4.28 DPS for this outfit against the reference boss.
		assertWithin3Percent(4.2803, Categories.MAGIC, gear(
			piece(EquipmentInventorySlot.WEAPON, "kodai wand", ItemEquipmentStats.builder().amagic(28).mdmg(15.0f).aspeed(4).build()),
			piece(EquipmentInventorySlot.HEAD, "ancestral hat", ItemEquipmentStats.builder().arange(-2).amagic(8).mdmg(3.0f).build()),
			piece(EquipmentInventorySlot.CAPE, "imbued guthix max cape", ItemEquipmentStats.builder().amagic(15).mdmg(2.0f).build()),
			piece(EquipmentInventorySlot.AMULET, "occult necklace", ItemEquipmentStats.builder().amagic(12).prayer(2).mdmg(5.0f).build()),
			piece(EquipmentInventorySlot.BODY, "ancestral robe top", ItemEquipmentStats.builder().arange(-8).amagic(35).mdmg(3.0f).build()),
			piece(EquipmentInventorySlot.SHIELD, "elidinis' ward (f)", ItemEquipmentStats.builder().amagic(25).prayer(4).mdmg(5.0f).build()),
			piece(EquipmentInventorySlot.LEGS, "twisted ancestral robe bottom", ItemEquipmentStats.builder().arange(-7).amagic(26).mdmg(3.0f).build()),
			piece(EquipmentInventorySlot.GLOVES, "confliction gauntlets", ItemEquipmentStats.builder().arange(-4).amagic(20).prayer(2).mdmg(7.0f).build()),
			piece(EquipmentInventorySlot.BOOTS, "avernic treads (et)", ItemEquipmentStats.builder().astab(5).aslash(5).acrush(5).arange(15).amagic(11).str(4).rstr(2).mdmg(2.0f).build()),
			piece(EquipmentInventorySlot.RING, "magus ring", ItemEquipmentStats.builder().amagic(15).mdmg(2.0f).build())));
	}

	@Test
	public void eclipseAtlatlHitsWithStrength()
	{
		// Wiki DPS calculator: 3.44 DPS. The atlatl's max hit uses Strength and melee strength bonus.
		assertWithin3Percent(3.4350, Categories.RANGED, gear(
			piece(EquipmentInventorySlot.WEAPON, "eclipse atlatl", ItemEquipmentStats.builder().arange(87).str(40).aspeed(4).isTwoHanded(true).build()),
			piece(EquipmentInventorySlot.CAPE, "dizana's quiver", ItemEquipmentStats.builder().arange(18).rstr(3).prayer(1).build()),
			piece(EquipmentInventorySlot.AMULET, "necklace of anguish", ItemEquipmentStats.builder().arange(15).rstr(5).prayer(2).build()),
			piece(EquipmentInventorySlot.GLOVES, "zaryte vambraces", ItemEquipmentStats.builder().astab(-8).aslash(-8).acrush(-8).amagic(-10).arange(18).rstr(2).prayer(1).build()),
			piece(EquipmentInventorySlot.BOOTS, "avernic treads (pe)", ItemEquipmentStats.builder().astab(5).aslash(5).acrush(5).arange(15).amagic(11).str(4).rstr(3).mdmg(1.0f).build()),
			piece(EquipmentInventorySlot.RING, "venator ring", ItemEquipmentStats.builder().arange(10).rstr(2).build()),
			piece(EquipmentInventorySlot.HEAD, "masori mask (f)", ItemEquipmentStats.builder().arange(12).amagic(-1).rstr(2).prayer(1).build()),
			piece(EquipmentInventorySlot.BODY, "masori body (f)", ItemEquipmentStats.builder().arange(43).amagic(-4).rstr(4).prayer(1).build()),
			piece(EquipmentInventorySlot.LEGS, "masori chaps (f)", ItemEquipmentStats.builder().arange(27).amagic(-2).rstr(2).prayer(1).build())));
	}

	@Test
	public void eclipseSetAddsItsBurn()
	{
		// Wiki DPS calculator: 3.33 DPS without the set effect (hit chance 0.5194, rapid 3 ticks); the burn adds about
		// 2 damage per landed hit (wiki), 2 * 0.5194 / 1.8 s.
		assertWithin3Percent(3.33 + 2 * 0.5194 / 1.8, Categories.RANGED, gear(
			piece(EquipmentInventorySlot.WEAPON, "eclipse atlatl", ItemEquipmentStats.builder().arange(87).str(40).aspeed(4).isTwoHanded(true).build()),
			piece(EquipmentInventorySlot.CAPE, "dizana's quiver", ItemEquipmentStats.builder().arange(18).rstr(3).prayer(1).build()),
			piece(EquipmentInventorySlot.AMULET, "necklace of anguish", ItemEquipmentStats.builder().arange(15).rstr(5).prayer(2).build()),
			piece(EquipmentInventorySlot.GLOVES, "zaryte vambraces", ItemEquipmentStats.builder().astab(-8).aslash(-8).acrush(-8).amagic(-10).arange(18).rstr(2).prayer(1).build()),
			piece(EquipmentInventorySlot.BOOTS, "avernic treads (pe)", ItemEquipmentStats.builder().astab(5).aslash(5).acrush(5).arange(15).amagic(11).str(4).rstr(3).mdmg(1.0f).build()),
			piece(EquipmentInventorySlot.RING, "venator ring", ItemEquipmentStats.builder().arange(10).rstr(2).build()),
			piece(EquipmentInventorySlot.HEAD, "eclipse moon helm", ItemEquipmentStats.builder().arange(8).amagic(-6).str(3).build()),
			piece(EquipmentInventorySlot.BODY, "eclipse moon chestplate", ItemEquipmentStats.builder().arange(31).amagic(-15).str(3).build()),
			piece(EquipmentInventorySlot.LEGS, "eclipse moon tassets", ItemEquipmentStats.builder().arange(17).amagic(-15).str(1).build())));
	}

	@Test
	public void launchersFireAmmoUpToTheirTier()
	{
		assertTrue(DpsCalc.fires("Twisted bow", "Dragon arrow"));
		assertTrue(DpsCalc.fires("Magic shortbow (i)", "Amethyst arrow"));
		assertFalse(DpsCalc.fires("Magic shortbow (i)", "Dragon arrow"));
		assertTrue(DpsCalc.fires("Rune crossbow", "Runite bolts"));
		assertTrue(DpsCalc.fires("Rune crossbow", "Onyx bolts (e)"));
		assertFalse(DpsCalc.fires("Rune crossbow", "Dragon bolts"));
		assertTrue(DpsCalc.fires("Zaryte crossbow", "Ruby dragon bolts (e)"));
		assertTrue(DpsCalc.fires("Heavy ballista", "Dragon javelin"));
		assertTrue(DpsCalc.fires("Karil's crossbow", "Bolt rack"));
		assertFalse(DpsCalc.fires("Twisted bow", "Runite bolts"));
		assertFalse(DpsCalc.fires("Bow of faerdhinen (c)", "Dragon arrow"));
	}
}
