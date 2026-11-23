package com.smartbanksorter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Set;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;
import org.junit.Test;

public class GearOrderTest
{
	private final Map<String, ItemEquipmentStats> stats = new HashMap<>();
	private final Map<String, Long> prices = new HashMap<>();
	private final Set<String> untradeable = new HashSet<>();
	private final Map<String, Integer> levels = new HashMap<>();
	private final Sets sets = new Sets(UserRules.parse("Blood moon: blood moon*\n"
		+ "Dharok's: Dharok's helm*, Dharok's platebody*, Dharok's platelegs*, Dharok's greataxe*\n"
		+ "Blue moon: Blue moon helm*, Blue moon chestplate*, Blue moon tassets*, Blue moon spear*"));

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

	private int upgradeRank(String name)
	{
		return GearOrder.upgradeRank(name, !untradeable.contains(name));
	}

	private final GearOrder.Gear<String> gear = new GearOrder.Gear<String>()
	{
		@Override
		public ItemEquipmentStats stats(String item)
		{
			return stats.get(item);
		}

		@Override
		public String name(String item)
		{
			return item;
		}

		@Override
		public long price(String item)
		{
			return prices.getOrDefault(item, 0L);
		}

		@Override
		public int upgrade(String item)
		{
			return upgradeRank(item);
		}

		@Override
		public int weaponRank(String item)
		{
			return levels.getOrDefault(item, 0);
		}

		@Override
		public int wieldLevel(String item)
		{
			return wieldLevels.getOrDefault(item, 0);
		}

		@Override
		public String set(String item)
		{
			return sets.match0(item);
		}
	};
	private final Map<String, Integer> wieldLevels = new HashMap<>();
	private boolean bestOnly;

	private List<List<String>> order(boolean columns, String... names)
	{
		return order(Categories.MELEE, columns, names);
	}

	private List<List<String>> order(String category, boolean columns, String... names)
	{
		return GearOrder.order(new ArrayList<>(Arrays.asList(names)), category, gear, DpsCalc.Levels.MAXED,
			Comparator.naturalOrder(), columns, false, bestOnly, 8);
	}

	private void addBank()
	{
		add("Abyssal whip", EquipmentInventorySlot.WEAPON, 82, 0, 1_500_000);
		add("Dragon scimitar", EquipmentInventorySlot.WEAPON, 66, 0, 60_000);
		levels.put("Abyssal whip", 70);
		levels.put("Dragon scimitar", 60);
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
	public void columnsAreOutfitsHeadToFeetUnderTheirWeapon()
	{
		addBank();
		// The scimitar's outfit would only be three pieces of nine, so it goes after the outfits, its pieces packed.
		assertEquals(Arrays.asList(
			Arrays.asList("Abyssal whip"),
			Arrays.asList("Honourable blessing"),
			Arrays.asList("Blood moon helm"),
			Arrays.asList("Fire cape"),
			Arrays.asList("Blood moon chestplate"),
			Arrays.asList("Rune kiteshield"),
			Arrays.asList("Blood moon tassets"),
			Arrays.asList("Barrows gloves"),
			Arrays.asList("Dragon scimitar"),
			Arrays.asList("Rune full helm", "Rune platebody", "Rune platelegs"),
			Arrays.asList("Cannonball")),
			order(true, BANK));
	}

	@Test
	public void aHalfFullOutfitKeepsItsColumn()
	{
		stats.put("Abyssal whip", melee(EquipmentInventorySlot.WEAPON, 0, 82, 0, 82, 4, false));
		stats.put("Dragon scimitar", melee(EquipmentInventorySlot.WEAPON, 8, 67, -2, 66, 4, false));
		stats.put("Torva full helm", melee(EquipmentInventorySlot.HEAD, 0, 0, 0, 8, 0, false));
		stats.put("Helm of neitiznot", melee(EquipmentInventorySlot.HEAD, 0, 0, 0, 3, 0, false));
		stats.put("Infernal cape", melee(EquipmentInventorySlot.CAPE, 4, 4, 4, 8, 0, false));
		stats.put("Fire cape", melee(EquipmentInventorySlot.CAPE, 1, 1, 1, 4, 0, false));
		stats.put("Amulet of torture", melee(EquipmentInventorySlot.AMULET, 15, 15, 15, 10, 0, false));
		stats.put("Amulet of fury", melee(EquipmentInventorySlot.AMULET, 10, 10, 10, 8, 0, false));
		stats.put("Torva platebody", melee(EquipmentInventorySlot.BODY, 0, 0, 0, 6, 0, false));
		stats.put("Fighter torso", melee(EquipmentInventorySlot.BODY, 0, 0, 0, 4, 0, false));
		stats.put("Torva platelegs", melee(EquipmentInventorySlot.LEGS, 0, 0, 0, 4, 0, false));
		stats.put("Rune platelegs", melee(EquipmentInventorySlot.LEGS, 0, 0, 0, 2, 0, false));
		// Five pieces of nine add damage: the scimitar keeps its column.
		assertEquals(Arrays.asList(
			Arrays.asList("Abyssal whip", "Dragon scimitar"),
			Arrays.asList("Torva full helm", "Helm of neitiznot"),
			Arrays.asList("Infernal cape", "Fire cape"),
			Arrays.asList("Amulet of torture", "Amulet of fury"),
			Arrays.asList("Torva platebody", "Fighter torso"),
			Arrays.asList("Torva platelegs", "Rune platelegs")),
			order(true, "Rune platelegs", "Fire cape", "Dragon scimitar", "Torva platebody", "Amulet of fury",
				"Helm of neitiznot", "Abyssal whip", "Infernal cape", "Torva full helm", "Fighter torso", "Torva platelegs",
				"Amulet of torture"));
	}

	@Test
	public void withBestOutfitsOnlyPiecesThatAddNoDamageDontKeepAColumn()
	{
		bestOnly = true;
		stats.put("Abyssal whip", melee(EquipmentInventorySlot.WEAPON, 0, 82, 0, 82, 4, false));
		stats.put("Dragon scimitar", melee(EquipmentInventorySlot.WEAPON, 8, 67, -2, 66, 4, false));
		stats.put("Torva full helm", melee(EquipmentInventorySlot.HEAD, 0, 0, 0, 8, 0, false));
		stats.put("Helm of neitiznot", melee(EquipmentInventorySlot.HEAD, 0, 0, 0, 3, 0, false));
		stats.put("Infernal cape", melee(EquipmentInventorySlot.CAPE, 4, 4, 4, 8, 0, false));
		stats.put("Fire cape", melee(EquipmentInventorySlot.CAPE, 1, 1, 1, 4, 0, false));
		stats.put("Amulet of torture", melee(EquipmentInventorySlot.AMULET, 15, 15, 15, 10, 0, false));
		stats.put("Amulet of fury", melee(EquipmentInventorySlot.AMULET, 10, 10, 10, 8, 0, false));
		stats.put("Torva platebody", melee(EquipmentInventorySlot.BODY, 0, 0, 0, 6, 0, false));
		stats.put("Fighter torso", melee(EquipmentInventorySlot.BODY, 0, 0, 0, 4, 0, false));
		stats.put("Torva platelegs", melee(EquipmentInventorySlot.LEGS, 0, 0, 0, 4, 0, false));
		stats.put("Rune platelegs", melee(EquipmentInventorySlot.LEGS, 0, 0, 0, 0, 0, false));
		// Five pieces of nine, but the platelegs add no damage: four isn't half, so the scimitar goes after the
		// outfits and its pieces join the spare gear.
		assertEquals(Arrays.asList(
			Arrays.asList("Abyssal whip"),
			Arrays.asList("Torva full helm"),
			Arrays.asList("Infernal cape"),
			Arrays.asList("Amulet of torture"),
			Arrays.asList("Torva platebody"),
			Arrays.asList("Torva platelegs"),
			Arrays.asList("Dragon scimitar"),
			Arrays.asList("Helm of neitiznot", "Fire cape", "Amulet of fury", "Fighter torso", "Rune platelegs")),
			order(true, "Rune platelegs", "Fire cape", "Dragon scimitar", "Torva platebody", "Amulet of fury",
				"Helm of neitiznot", "Abyssal whip", "Infernal cape", "Torva full helm", "Fighter torso", "Torva platelegs",
				"Amulet of torture"));
	}

	@Test
	public void rowsAreOutfitsHeadedByTheirWeapon()
	{
		addBank();

		assertEquals(Arrays.asList(
			Arrays.asList("Abyssal whip", "Blood moon helm", "Fire cape", "Blood moon chestplate", "Rune kiteshield",
				"Blood moon tassets", "Barrows gloves"),
			Arrays.asList("Dragon scimitar", "Rune full helm", "Rune platebody", "Rune platelegs", "Honourable blessing",
				"Cannonball")),
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
	public void guideTierBreaksDamageTies()
	{
		// Same stats, so the same damage: the weapon the wiki guides rank higher goes first.
		add("Rune scimitar", EquipmentInventorySlot.WEAPON, 45, 0, 15_000);
		add("Gilded scimitar", EquipmentInventorySlot.WEAPON, 45, 0, 300_000);
		levels.put("Rune scimitar", 40_040);
		levels.put("Gilded scimitar", 40_000);
		assertEquals(Arrays.asList(Arrays.asList("Rune scimitar", "Gilded scimitar")),
			order(true, "Gilded scimitar", "Rune scimitar"));
	}

	private static ItemEquipmentStats melee(EquipmentInventorySlot slot, int stab, int slash, int crush, int str, int speed,
		boolean twoHanded)
	{
		return ItemEquipmentStats.builder().slot(slot.getSlotIdx()).astab(stab).aslash(slash).acrush(crush).str(str)
			.aspeed(speed).isTwoHanded(twoHanded).build();
	}

	@Test
	public void strengthPiecesThatOnlyHelpTogetherAreFound()
	{
		// Wiki stats. Alone, neither Fire cape for Ardougne cloak nor Berserker ring for Brimstone raises the fang's
		// max hit, so each loses on accuracy; together they do, and hit a little harder.
		stats.put("Osmumten's fang", melee(EquipmentInventorySlot.WEAPON, 105, 75, 0, 103, 5, false));
		stats.put("Blood moon helm", melee(EquipmentInventorySlot.HEAD, 0, 0, 0, 4, 0, false));
		stats.put("Blood moon chestplate", melee(EquipmentInventorySlot.BODY, 0, 0, 0, 4, 0, false));
		stats.put("Blood moon tassets", melee(EquipmentInventorySlot.LEGS, 0, 0, 0, 2, 0, false));
		stats.put("Amulet of fury", melee(EquipmentInventorySlot.AMULET, 10, 10, 10, 8, 0, false));
		stats.put("Dragon defender", melee(EquipmentInventorySlot.SHIELD, 25, 24, 23, 6, 0, false));
		stats.put("Barrows gloves", melee(EquipmentInventorySlot.GLOVES, 12, 12, 12, 12, 0, false));
		stats.put("Dragon boots", melee(EquipmentInventorySlot.BOOTS, 0, 0, 0, 4, 0, false));
		stats.put("Ardougne cloak 1", melee(EquipmentInventorySlot.CAPE, 2, 0, 0, 0, 0, false));
		stats.put("Fire cape", melee(EquipmentInventorySlot.CAPE, 1, 1, 1, 4, 0, false));
		stats.put("Brimstone ring", melee(EquipmentInventorySlot.RING, 4, 4, 4, 4, 0, false));
		stats.put("Berserker ring (i)", melee(EquipmentInventorySlot.RING, 0, 0, 0, 8, 0, false));
		assertEquals(Arrays.asList(
			Arrays.asList("Osmumten's fang"),
			Arrays.asList("Blood moon helm"),
			Arrays.asList("Fire cape"),
			Arrays.asList("Amulet of fury"),
			Arrays.asList("Blood moon chestplate"),
			Arrays.asList("Dragon defender"),
			Arrays.asList("Blood moon tassets"),
			Arrays.asList("Barrows gloves"),
			Arrays.asList("Dragon boots"),
			Arrays.asList("Berserker ring (i)"),
			Arrays.asList("Ardougne cloak 1", "Brimstone ring")),
			order(true, "Osmumten's fang", "Blood moon helm", "Blood moon chestplate", "Blood moon tassets",
				"Amulet of fury", "Dragon defender", "Barrows gloves", "Dragon boots", "Ardougne cloak 1", "Fire cape",
				"Brimstone ring", "Berserker ring (i)"));
	}

	@Test
	public void weaponsRankByTheDamageOfTheirOutfit()
	{
		// Wiki stats. The scythe's three hits on large targets beat the fang's double accuracy roll and the
		// Obligator's thrust, despite the Obligator needing 85 Attack to the scythe's 80.
		stats.put("Osmumten's fang", melee(EquipmentInventorySlot.WEAPON, 105, 75, 0, 103, 5, false));
		stats.put("Scythe of vitur", melee(EquipmentInventorySlot.WEAPON, 70, 125, 30, 75, 5, true));
		stats.put("Obligator", melee(EquipmentInventorySlot.WEAPON, 22, -8, 120, 40, 5, true));
		stats.put("Abyssal whip", melee(EquipmentInventorySlot.WEAPON, 0, 82, 0, 82, 4, false));
		assertEquals(Arrays.asList(Arrays.asList("Scythe of vitur", "Osmumten's fang", "Obligator", "Abyssal whip")),
			order(true, "Abyssal whip", "Obligator", "Osmumten's fang", "Scythe of vitur"));
	}

	@Test
	public void eachOutfitSuitsItsWeapon()
	{
		// Bellator (+20 slash) suits the scythe; Ultor (+12 strength) suits the crush weapon. The mace's outfit is
		// just the ring, so the mace and the ring go after the outfits.
		stats.put("Scythe of vitur", melee(EquipmentInventorySlot.WEAPON, 70, 125, 30, 75, 5, true));
		stats.put("Inquisitor's mace", melee(EquipmentInventorySlot.WEAPON, 52, -4, 102, 96, 4, false));
		stats.put("Bellator ring", melee(EquipmentInventorySlot.RING, 0, 20, 0, 6, 0, false));
		stats.put("Ultor ring", melee(EquipmentInventorySlot.RING, 0, 0, 0, 12, 0, false));
		assertEquals(Arrays.asList(
			Arrays.asList("Scythe of vitur"),
			Arrays.asList("Bellator ring"),
			Arrays.asList("Inquisitor's mace"),
			Arrays.asList("Ultor ring")),
			order(true, "Ultor ring", "Inquisitor's mace", "Bellator ring", "Scythe of vitur"));
	}

	@Test
	public void aSetStaysWholeUnderItsOwnWeapon()
	{
		// Dharok's helm alone would suit the scythe (more strength than the neitiznot), but the set stays together
		// under the greataxe.
		stats.put("Scythe of vitur", melee(EquipmentInventorySlot.WEAPON, 70, 125, 30, 75, 5, true));
		stats.put("Dharok's greataxe", melee(EquipmentInventorySlot.WEAPON, -4, 103, 95, 105, 7, true));
		stats.put("Dharok's helm", melee(EquipmentInventorySlot.HEAD, 0, 0, 0, 3, 0, false));
		stats.put("Dharok's platebody", melee(EquipmentInventorySlot.BODY, 0, 0, 0, 0, 0, false));
		stats.put("Dharok's platelegs", melee(EquipmentInventorySlot.LEGS, 0, 0, 0, 0, 0, false));
		stats.put("Helm of neitiznot", melee(EquipmentInventorySlot.HEAD, 0, 0, 0, 3, 0, false));
		stats.put("Fighter torso", melee(EquipmentInventorySlot.BODY, 0, 0, 0, 4, 0, false));
		assertEquals(Arrays.asList(
			Arrays.asList("Scythe of vitur", "Dharok's greataxe"),
			Arrays.asList("Helm of neitiznot", "Dharok's helm"),
			Arrays.asList("Fighter torso", "Dharok's platebody"),
			Arrays.asList(null, "Dharok's platelegs")),
			order(true, "Dharok's platelegs", "Fighter torso", "Dharok's helm", "Scythe of vitur", "Helm of neitiznot",
				"Dharok's platebody", "Dharok's greataxe"));
	}

	@Test
	public void aSetWithNoBonusIsJustPieces()
	{
		// Without its weapon, Blood moon has no bonus to keep it together: its chestplate beats the rune platebody on
		// its own, while the helm loses to the torva.
		stats.put("Scythe of vitur", melee(EquipmentInventorySlot.WEAPON, 70, 125, 30, 75, 5, true));
		stats.put("Blood moon helm", melee(EquipmentInventorySlot.HEAD, 0, 0, 0, 1, 0, false));
		stats.put("Blood moon chestplate", melee(EquipmentInventorySlot.BODY, 0, 0, 0, 4, 0, false));
		stats.put("Torva full helm", melee(EquipmentInventorySlot.HEAD, 0, 0, 0, 8, 0, false));
		stats.put("Rune platebody", melee(EquipmentInventorySlot.BODY, 0, 0, 0, 3, 0, false));
		List<List<String>> order = order(true, "Blood moon chestplate", "Rune platebody", "Scythe of vitur",
			"Torva full helm", "Blood moon helm");
		assertEquals(Arrays.asList(
			Arrays.asList("Scythe of vitur"),
			Arrays.asList("Torva full helm"),
			Arrays.asList("Blood moon chestplate"),
			Arrays.asList("Blood moon helm", "Rune platebody")),
			order);
	}

	@Test
	public void piecesThatDoTheSameDamageGoByStats()
	{
		// With the whip, Blood moon's extra strength doesn't raise the max hit: the same damage, so its better
		// strength wins each slot.
		stats.put("Abyssal whip", melee(EquipmentInventorySlot.WEAPON, 0, 82, 0, 82, 4, false));
		stats.put("Helm of neitiznot", melee(EquipmentInventorySlot.HEAD, 0, 0, 0, 3, 0, false));
		stats.put("Fighter torso", melee(EquipmentInventorySlot.BODY, 0, 0, 0, 4, 0, false));
		stats.put("Dragon platelegs", melee(EquipmentInventorySlot.LEGS, 0, 0, 0, 0, 0, false));
		stats.put("Blood moon helm", melee(EquipmentInventorySlot.HEAD, 0, 0, 0, 4, 0, false));
		stats.put("Blood moon chestplate", melee(EquipmentInventorySlot.BODY, 0, 0, 0, 4, 0, false));
		stats.put("Blood moon tassets", melee(EquipmentInventorySlot.LEGS, 0, 0, 0, 2, 0, false));
		List<List<String>> order = order(true, "Abyssal whip", "Helm of neitiznot", "Fighter torso", "Dragon platelegs",
			"Blood moon helm", "Blood moon chestplate", "Blood moon tassets");
		assertEquals(Arrays.asList("Abyssal whip", "Blood moon helm", "Blood moon chestplate", "Blood moon tassets"),
			order.subList(0, 4).stream().map(row -> row.get(0)).collect(Collectors.toList()));
	}

	@Test
	public void ammoGoesUnderTheLauncherThatFiresIt()
	{
		stats.put("Twisted bow", ItemEquipmentStats.builder().slot(EquipmentInventorySlot.WEAPON.getSlotIdx()).arange(70)
			.rstr(20).aspeed(6).isTwoHanded(true).build());
		stats.put("Rune crossbow", ItemEquipmentStats.builder().slot(EquipmentInventorySlot.WEAPON.getSlotIdx()).arange(90)
			.aspeed(6).build());
		for (String[] ammo : new String[][]{{"Dragon arrow", "60"}, {"Rune arrow", "49"}, {"Runite bolts", "115"}})
		{
			stats.put(ammo[0], ItemEquipmentStats.builder().slot(EquipmentInventorySlot.AMMO.getSlotIdx())
				.rstr(Integer.parseInt(ammo[1])).build());
		}
		// The crossbow's outfit would be only its bolts, so it goes after the outfits and the bolts join the spare ammo.
		assertEquals(Arrays.asList(
			Arrays.asList("Twisted bow"),
			Arrays.asList("Dragon arrow"),
			Arrays.asList("Rune crossbow"),
			Arrays.asList("Runite bolts", "Rune arrow")),
			order(Categories.RANGED, true, "Rune arrow", "Runite bolts", "Rune crossbow", "Dragon arrow", "Twisted bow"));
	}

	@Test
	public void aStrongerWeaponCanWearAnotherWeaponsSet()
	{
		// Blue moon armour (+58 magic attack, +3% damage) beats mystic on the eye of Ayak, which out-damages the
		// spear even with its set, so the set goes to the eye. Three pieces of mystic don't make the spear an outfit.
		stats.put("Eye of ayak", ItemEquipmentStats.builder().slot(EquipmentInventorySlot.WEAPON.getSlotIdx()).amagic(30)
			.aspeed(3).build());
		stats.put("Blue moon spear", ItemEquipmentStats.builder().slot(EquipmentInventorySlot.WEAPON.getSlotIdx())
			.astab(70).aslash(62).acrush(62).amagic(30).str(71).mdmg(5).aspeed(5).isTwoHanded(true).build());
		stats.put("Blue moon helm", ItemEquipmentStats.builder().slot(EquipmentInventorySlot.HEAD.getSlotIdx()).amagic(6)
			.str(3).mdmg(1).build());
		stats.put("Blue moon chestplate", ItemEquipmentStats.builder().slot(EquipmentInventorySlot.BODY.getSlotIdx())
			.amagic(30).str(2).mdmg(1).build());
		stats.put("Blue moon tassets", ItemEquipmentStats.builder().slot(EquipmentInventorySlot.LEGS.getSlotIdx())
			.amagic(22).str(1).mdmg(1).build());
		stats.put("Mystic hat", ItemEquipmentStats.builder().slot(EquipmentInventorySlot.HEAD.getSlotIdx()).amagic(4).build());
		stats.put("Mystic robe top", ItemEquipmentStats.builder().slot(EquipmentInventorySlot.BODY.getSlotIdx()).amagic(20)
			.build());
		stats.put("Mystic robe bottom", ItemEquipmentStats.builder().slot(EquipmentInventorySlot.LEGS.getSlotIdx())
			.amagic(15).build());
		assertEquals(Arrays.asList(
			Arrays.asList("Eye of ayak"),
			Arrays.asList("Blue moon helm"),
			Arrays.asList("Blue moon chestplate"),
			Arrays.asList("Blue moon tassets"),
			Arrays.asList("Blue moon spear"),
			Arrays.asList("Mystic hat", "Mystic robe top", "Mystic robe bottom")),
			order(Categories.MAGIC, true, "Mystic hat", "Blue moon spear", "Blue moon tassets", "Mystic robe top",
				"Eye of ayak", "Blue moon helm", "Mystic robe bottom", "Blue moon chestplate"));
	}

	private List<List<String>> compact(boolean pack)
	{
		// Scythe (2h, no shield) gets the helm; the whip gets the defender; the dart gets nothing.
		stats.put("Scythe of vitur", melee(EquipmentInventorySlot.WEAPON, 70, 125, 30, 75, 5, true));
		stats.put("Abyssal whip", melee(EquipmentInventorySlot.WEAPON, 0, 82, 0, 82, 4, false));
		stats.put("Rune dart", melee(EquipmentInventorySlot.WEAPON, 0, 0, 0, 0, 3, false));
		stats.put("Torva full helm", melee(EquipmentInventorySlot.HEAD, 0, 0, 0, 8, 0, false));
		stats.put("Dragon defender", melee(EquipmentInventorySlot.SHIELD, 25, 24, 23, 6, 0, false));
		return GearOrder.order(new ArrayList<>(Arrays.asList("Rune dart", "Dragon defender", "Abyssal whip",
			"Torva full helm", "Scythe of vitur")), Categories.MELEE, gear, DpsCalc.Levels.MAXED, Comparator.naturalOrder(),
			true, pack, false, 8);
	}

	@Test
	public void sparseOutfitsGoAfterTheOutfits()
	{
		// The whip would head just the defender and the dart nothing, so both follow the outfits, the defender with
		// the spare gear.
		List<List<String>> expected = Arrays.asList(
			Arrays.asList("Scythe of vitur"),
			Arrays.asList("Torva full helm"),
			Arrays.asList("Abyssal whip", "Rune dart"),
			Arrays.asList("Dragon defender"));
		assertEquals(expected, compact(false));
		assertEquals(expected, compact(true));
	}

	@Test
	public void spareAmmoFollowsWeaponsThatHaveNoOutfit()
	{
		stats.put("Rune crossbow", ItemEquipmentStats.builder().slot(EquipmentInventorySlot.WEAPON.getSlotIdx()).arange(90)
			.aspeed(6).build());
		// A crossbow can't fire arrows.
		stats.put("Rune arrow", ItemEquipmentStats.builder().slot(EquipmentInventorySlot.AMMO.getSlotIdx()).rstr(49).build());
		List<List<String>> order = GearOrder.order(new ArrayList<>(Arrays.asList("Rune arrow", "Rune crossbow")),
			Categories.RANGED, gear, DpsCalc.Levels.MAXED, Comparator.naturalOrder(), true, false, false, 8);
		assertEquals(Arrays.asList(Arrays.asList("Rune crossbow"), Arrays.asList("Rune arrow")), order);
	}

	@Test
	public void weaponsThePlayerCantWieldGoLast()
	{
		stats.put("Scythe of vitur", melee(EquipmentInventorySlot.WEAPON, 70, 125, 30, 75, 5, true));
		stats.put("Abyssal whip", melee(EquipmentInventorySlot.WEAPON, 0, 82, 0, 82, 4, false));
		wieldLevels.put("Scythe of vitur", 80);
		wieldLevels.put("Abyssal whip", 70);
		List<List<String>> order = GearOrder.order(new ArrayList<>(Arrays.asList("Scythe of vitur", "Abyssal whip")),
			Categories.MELEE, gear, new DpsCalc.Levels(75, 99, 99, 99), Comparator.naturalOrder(), true, false, false, 8);
		assertEquals(Arrays.asList(Arrays.asList("Abyssal whip", "Scythe of vitur")), order);
	}

	@Test
	public void untradeableUpgradeBeatsItsBase()
	{
		// Blood fury has fury's stats plus its effect. RuneLite prices it as a fury; without that mapping it's 0.
		add("Amulet of fury", EquipmentInventorySlot.AMULET, 8, 15, 2_000_000);
		add("Amulet of blood fury", EquipmentInventorySlot.AMULET, 8, 15, 2_000_000);
		untradeable.add("Amulet of blood fury");
		assertEquals(Arrays.asList(Arrays.asList("Amulet of blood fury", "Amulet of fury")),
			order(true, "Amulet of fury", "Amulet of blood fury"));
		prices.put("Amulet of blood fury", 0L);
		assertEquals(Arrays.asList(Arrays.asList("Amulet of blood fury", "Amulet of fury")),
			order(true, "Amulet of fury", "Amulet of blood fury"));
	}

	@Test
	public void maxCapeVersionBeatsItsCape()
	{
		add("Infernal cape", EquipmentInventorySlot.CAPE, 8, 12, 0);
		add("Infernal max cape", EquipmentInventorySlot.CAPE, 8, 12, 0);
		untradeable.add("Infernal cape");
		untradeable.add("Infernal max cape");
		assertEquals(Arrays.asList(Arrays.asList("Infernal max cape", "Infernal cape")),
			order(true, "Infernal cape", "Infernal max cape"));
	}

	@Test
	public void equalScoresGoToTheHarderHitter()
	{
		// Both score 300 for Ranged; the hopper's ranged strength beats the book's prayer.
		stats.put("Book of Law", ItemEquipmentStats.builder().slot(EquipmentInventorySlot.SHIELD.getSlotIdx())
			.arange(10).prayer(5).build());
		stats.put("Aquanite hopper", ItemEquipmentStats.builder().slot(EquipmentInventorySlot.SHIELD.getSlotIdx())
			.arange(12).rstr(1).build());
		untradeable.add("Book of Law");
		assertEquals(Arrays.asList(Arrays.asList("Aquanite hopper", "Book of Law")),
			order(Categories.RANGED, true, "Book of Law", "Aquanite hopper"));
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
