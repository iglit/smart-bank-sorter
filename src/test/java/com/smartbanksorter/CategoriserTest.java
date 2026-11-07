package com.smartbanksorter;

import static com.smartbanksorter.Categories.*;
import static org.junit.Assert.assertEquals;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;
import org.junit.Test;

public class CategoriserTest
{
	private static final String[] NONE = new String[5];

	private static ItemEquipmentStats.ItemEquipmentStatsBuilder slot(EquipmentInventorySlot slot)
	{
		return ItemEquipmentStats.builder().slot(slot.getSlotIdx());
	}

	private static String gear(ItemEquipmentStats.ItemEquipmentStatsBuilder stats)
	{
		return Categoriser.builtIn("x", NONE, stats.build());
	}

	@Test
	public void weaponsByStyle()
	{
		// Abyssal whip
		assertEquals(MELEE, gear(slot(EquipmentInventorySlot.WEAPON).aslash(82).str(82)));
		// Magic shortbow
		assertEquals(RANGED, gear(slot(EquipmentInventorySlot.WEAPON).arange(69)));
		// Staff of fire: crush bonus, but a staff
		assertEquals(MAGIC, gear(slot(EquipmentInventorySlot.WEAPON).astab(2).acrush(10).amagic(10).str(7)));
		// Trident: magic damage only
		assertEquals(MAGIC, gear(slot(EquipmentInventorySlot.WEAPON).amagic(25).mdmg(15)));
	}

	@Test
	public void armourByStyle()
	{
		// Rune platebody: defence only, magic penalty
		assertEquals(MELEE, gear(slot(EquipmentInventorySlot.BODY).amagic(-30).arange(-10).dstab(82).dslash(80).dcrush(72)));
		// Black d'hide body
		assertEquals(RANGED, gear(slot(EquipmentInventorySlot.BODY).amagic(-15).arange(30).dstab(55).dmagic(50)));
		// Mystic robe top
		assertEquals(MAGIC, gear(slot(EquipmentInventorySlot.BODY).amagic(20).dmagic(20)));
		// Fire cape
		assertEquals(MELEE, gear(slot(EquipmentInventorySlot.CAPE).str(4).dstab(11)));
		// Ava's assembler
		assertEquals(RANGED, gear(slot(EquipmentInventorySlot.CAPE).arange(8).rstr(2)));
		// Cosmetic, no stats
		assertEquals(OTHER_GEAR, gear(slot(EquipmentInventorySlot.HEAD)));
	}

	@Test
	public void slotsWithFixedCategories()
	{
		// Amulet of torture / anguish / occult
		assertEquals(MELEE, gear(slot(EquipmentInventorySlot.AMULET).astab(15).aslash(15).acrush(15).str(10)));
		assertEquals(RANGED, gear(slot(EquipmentInventorySlot.AMULET).arange(15).rstr(5)));
		assertEquals(MAGIC, gear(slot(EquipmentInventorySlot.AMULET).amagic(12).mdmg(5)));
		// Amulet of fury: hybrid, melee by its strength bonus
		assertEquals(MELEE, gear(slot(EquipmentInventorySlot.AMULET).astab(10).arange(10).amagic(10).str(8).dstab(15)));
		// Ring of suffering: defence only; games necklace: nothing
		assertEquals(JEWELLERY, gear(slot(EquipmentInventorySlot.RING).dstab(20).dmagic(20)));
		assertEquals(JEWELLERY, gear(slot(EquipmentInventorySlot.AMULET)));
		assertEquals(AMMO, gear(slot(EquipmentInventorySlot.AMMO).rstr(49)));
		assertEquals(MELEE, Categoriser.gear("honourable blessing", slot(EquipmentInventorySlot.AMMO).prayer(1).build()));
		assertEquals(AMMO, Categoriser.gear("atlatl dart", slot(EquipmentInventorySlot.AMMO).build()));
	}

	@Test
	public void wieldableToolsAreTools()
	{
		assertEquals(TOOLS, Categoriser.builtIn("dragon pickaxe", NONE,
			slot(EquipmentInventorySlot.WEAPON).astab(38).acrush(32).str(42).build()));
		assertEquals(TOOLS, Categoriser.builtIn("rune axe", NONE, null));
		assertEquals(MELEE, Categoriser.builtIn("dragon battleaxe", NONE,
			slot(EquipmentInventorySlot.WEAPON).aslash(70).str(85).build()));
	}

	@Test
	public void consumablesByAction()
	{
		assertEquals(FOOD, Categoriser.builtIn("shark", new String[]{"Eat", null, null, null, "Drop"}, null));
		assertEquals(POTIONS, Categoriser.builtIn("prayer potion(4)", new String[]{"Drink", null, null, null, "Drop"}, null));
		assertEquals(PRAYER, Categoriser.builtIn("dragon bones", new String[]{"Bury", null, null, null, "Drop"}, null));
		assertEquals(TELEPORTS, Categoriser.builtIn("varrock teleport", new String[]{"Break", null, null, null, "Drop"}, null));
	}

	@Test
	public void resourcesByName()
	{
		assertEquals(RUNES, Categoriser.builtIn("blood rune", NONE, null));
		assertEquals(RUNES, Categoriser.builtIn("divine rune pouch", NONE, null));
		assertEquals(RUNES, Categoriser.builtIn("blighted teleport spell sack", NONE, null));
		assertEquals(HERBLORE, Categoriser.builtIn("grimy ranarr weed", NONE, null));
		assertEquals(HERBLORE, Categoriser.builtIn("ranarr potion (unf)", NONE, null));
		assertEquals(FARMING, Categoriser.builtIn("ranarr seed", NONE, null));
		assertEquals(MISC, Categoriser.builtIn("crystal armour seed", NONE, null));
		assertEquals(MISC, Categoriser.builtIn("enhanced crystal weapon seed", NONE, null));
		assertEquals(TELEPORTS, Categoriser.builtIn("royal seed pod", NONE, null));
		String[] rub = {"Wear", "Rub", null, null, "Drop"};
		assertEquals(TELEPORTS, Categoriser.builtIn("amulet of glory(4)", rub,
			slot(EquipmentInventorySlot.AMULET).astab(10).str(6).build()));
		assertEquals(MELEE, Categoriser.builtIn("amulet of glory", new String[]{"Wear", null, null, null, "Drop"},
			slot(EquipmentInventorySlot.AMULET).astab(10).str(6).build()));
		assertEquals(MISC, Categoriser.builtIn("antique lamp", new String[]{"Rub", null, null, null, "Drop"}, null));
		assertEquals(GEMS, Categoriser.builtIn("uncut diamond", NONE, null));
		assertEquals(ORES_BARS, Categoriser.builtIn("runite ore", NONE, null));
		assertEquals(ORES_BARS, Categoriser.builtIn("coal", NONE, null));
		assertEquals(LOGS_PLANKS, Categoriser.builtIn("magic logs", NONE, null));
		assertEquals(CURRENCY, Categoriser.builtIn("coins", NONE, null));
		assertEquals(MISC, Categoriser.builtIn("feather", NONE, null));
		assertEquals(RANGED, Categoriser.builtIn("cannon barrels", NONE, null));
		assertEquals(RANGED, Categoriser.builtIn("granite cannonball", NONE, null));
	}

	@Test
	public void bundledOverridesApply()
	{
		Categoriser c = new Categoriser(UserRules.parse(""));
		assertEquals(MELEE, c.categorise(25975, "Lightbearer", NONE, slot(EquipmentInventorySlot.RING).build()));
	}

	@Test
	public void userRulesOverrideBuiltIns()
	{
		Categoriser c = new Categoriser(UserRules.parse("Slayer: slayer helm*"));
		assertEquals("Slayer", c.categorise(1, "Slayer helmet (i)", NONE,
			slot(EquipmentInventorySlot.HEAD).dstab(30).build()));
	}
}
