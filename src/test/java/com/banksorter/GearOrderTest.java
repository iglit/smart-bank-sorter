package com.banksorter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;
import org.junit.Test;

public class GearOrderTest
{
	private final Map<String, ItemEquipmentStats> stats = new HashMap<>();
	private final Map<String, Long> prices = new HashMap<>();
	private final Sets sets = new Sets(UserRules.parse("Blood moon: blood moon*"));

	private static final class Sets
	{
		final UserRules rules;

		Sets(UserRules rules)
		{
			this.rules = rules;
		}

		String match0(String name)
		{
			return rules.match(-1, name);
		}
	}

	private void add(String name, EquipmentInventorySlot slot, int str, int def, long price)
	{
		stats.put(name, ItemEquipmentStats.builder().slot(slot.getSlotIdx()).str(str)
			.dstab(def).dslash(def).dcrush(def).build());
		prices.put(name, price);
	}

	private List<List<String>> order(boolean columns, String... names)
	{
		return GearOrder.order(new ArrayList<>(Arrays.asList(names)), Categories.MELEE, stats::get,
			n -> prices.getOrDefault(n, 0L), sets::match0, Comparator.naturalOrder(), columns, 8);
	}

	private void addBank()
	{
		add("Abyssal whip", EquipmentInventorySlot.WEAPON, 82, 0, 1_500_000);
		add("Dragon scimitar", EquipmentInventorySlot.WEAPON, 66, 0, 60_000);
		add("Blood moon helm", EquipmentInventorySlot.HEAD, 3, 40, 3_000_000);
		add("Blood moon chestplate", EquipmentInventorySlot.BODY, 4, 80, 3_000_000);
		add("Blood moon tassets", EquipmentInventorySlot.LEGS, 3, 60, 3_000_000);
		add("Rune full helm", EquipmentInventorySlot.HEAD, 0, 30, 20_000);
		add("Rune platebody", EquipmentInventorySlot.BODY, 0, 80, 38_000);
		add("Rune platelegs", EquipmentInventorySlot.LEGS, 0, 50, 38_000);
		add("Rune kiteshield", EquipmentInventorySlot.SHIELD, 0, 50, 30_000);
		add("Fire cape", EquipmentInventorySlot.CAPE, 4, 11, 0);
		add("Barrows gloves", EquipmentInventorySlot.GLOVES, 12, 12, 0);
		stats.put("Cannonball", null);
		stats.put("Honourable blessing", ItemEquipmentStats.builder().slot(EquipmentInventorySlot.AMMO.getSlotIdx())
			.prayer(1).build());
	}

	private static final String[] BANK = {"Honourable blessing", "Rune platelegs", "Fire cape", "Rune kiteshield", "Blood moon chestplate",
		"Dragon scimitar", "Cannonball", "Rune full helm", "Barrows gloves", "Blood moon helm", "Abyssal whip",
		"Rune platebody", "Blood moon tassets"};

	@Test
	public void columnsAreOutfitsHeadToFeet()
	{
		addBank();
		assertEquals(Arrays.asList(
			Arrays.asList("Abyssal whip", "Dragon scimitar", "Honourable blessing"),
			Arrays.asList("Blood moon helm", "Rune full helm"),
			Arrays.asList("Fire cape"),
			Arrays.asList("Blood moon chestplate", "Rune platebody"),
			Arrays.asList("Rune kiteshield"),
			Arrays.asList("Blood moon tassets", "Rune platelegs"),
			Arrays.asList("Barrows gloves"),
			Arrays.asList("Cannonball")),
			order(true, BANK));
	}

	@Test
	public void rowsAreOutfitsBestOnTop()
	{
		addBank();

		assertEquals(Arrays.asList(
			Arrays.asList("Abyssal whip", "Dragon scimitar", "Honourable blessing", "Rune kiteshield"),
			Arrays.asList("Blood moon helm", "Fire cape", "Blood moon chestplate", "Blood moon tassets",
				"Barrows gloves"),
			Arrays.asList("Rune full helm", "Rune platebody", "Rune platelegs"),
			Arrays.asList("Cannonball")),
			order(false, BANK));
	}

	@Test
	public void setPiecesShareTheirBestPiecesTier()
	{
		add("Helm of neitiznot", EquipmentInventorySlot.HEAD, 3, 40, 50_000);
		add("Blood moon helm", EquipmentInventorySlot.HEAD, 2, 35, 3_000_000);
		add("Blood moon chestplate", EquipmentInventorySlot.BODY, 4, 80, 3_000_000);
		add("Blood moon tassets", EquipmentInventorySlot.LEGS, 3, 60, 3_000_000);
		add("Rune platebody", EquipmentInventorySlot.BODY, 0, 70, 38_000);
		add("Fire cape", EquipmentInventorySlot.CAPE, 4, 11, 0);

		assertEquals(Arrays.asList(
			Arrays.asList("Blood moon helm", "Helm of neitiznot"),
			Arrays.asList("Fire cape"),
			Arrays.asList("Blood moon chestplate", "Rune platebody"),
			Arrays.asList("Blood moon tassets")),
			order(true, "Helm of neitiznot", "Rune platebody", "Blood moon tassets", "Fire cape",
				"Blood moon helm", "Blood moon chestplate"));
	}

	@Test
	public void lonePieceOfASetRanksNormally()
	{
		add("Helm of neitiznot", EquipmentInventorySlot.HEAD, 3, 40, 50_000);
		add("Blood moon helm", EquipmentInventorySlot.HEAD, 2, 35, 3_000_000);
		assertEquals(Arrays.asList(Arrays.asList("Helm of neitiznot", "Blood moon helm")),
			order(true, "Blood moon helm", "Helm of neitiznot"));
	}

	@Test
	public void smallDefenceDifferencesCount()
	{
		ItemEquipmentStats lunar = ItemEquipmentStats.builder().slot(EquipmentInventorySlot.RING.getSlotIdx())
			.amagic(2).dmagic(2).build();
		ItemEquipmentStats beacon = ItemEquipmentStats.builder().slot(EquipmentInventorySlot.RING.getSlotIdx())
			.amagic(2).dmagic(1).build();
		assertTrue(GearOrder.score(Categories.MAGIC, lunar) > GearOrder.score(Categories.MAGIC, beacon));
	}

	@Test
	public void offenceIsPerStyle()
	{
		ItemEquipmentStats barrowsGloves = ItemEquipmentStats.builder().slot(EquipmentInventorySlot.GLOVES.getSlotIdx())
			.astab(12).aslash(12).acrush(12).amagic(6).arange(12).str(12).build();
		ItemEquipmentStats runeKite = ItemEquipmentStats.builder().slot(EquipmentInventorySlot.SHIELD.getSlotIdx())
			.amagic(-8).arange(-2).dstab(35).build();
		assertEquals(6, GearOrder.offence(Categories.MAGIC, barrowsGloves));
		assertEquals(12, GearOrder.offence(Categories.RANGED, barrowsGloves));
		assertEquals(-8, GearOrder.offence(Categories.MAGIC, runeKite));
	}
}
