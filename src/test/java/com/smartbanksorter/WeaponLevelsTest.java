package com.smartbanksorter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WeaponLevelsTest
{
	@Test
	public void levelIsForTheStylesSkill()
	{
		assertEquals(75, WeaponLevels.level("Staff of the Dead", Categories.MELEE));
		assertEquals(75, WeaponLevels.level("Staff of the Dead", Categories.MAGIC));
		assertEquals(0, WeaponLevels.level("Staff of the Dead", Categories.RANGED));
		assertEquals(85, WeaponLevels.level("Twisted bow", Categories.RANGED));
	}

	@Test
	public void variantsUseTheBaseWeapon()
	{
		assertEquals(70, WeaponLevels.level("Abyssal whip (or)", Categories.MELEE));
		assertEquals(75, WeaponLevels.level("Toxic blowpipe (empty)", Categories.RANGED));
		assertEquals(80, WeaponLevels.level("Sanguine Scythe of Vitur", Categories.MELEE));
	}

	@Test
	public void guideTierBeatsWieldLevel()
	{
		// Scythe (80 Attack) is the wiki guides' melee megarare; Obligator (85) and the fang (82) are endgame.
		assertTrue(WeaponLevels.rank("Scythe of Vitur", Categories.MELEE)
			> WeaponLevels.rank("Obligator", Categories.MELEE));
		assertTrue(WeaponLevels.rank("Obligator", Categories.MELEE)
			> WeaponLevels.rank("Osmumten's fang", Categories.MELEE));
		assertTrue(WeaponLevels.rank("Sanguine Scythe of Vitur", Categories.MELEE)
			> WeaponLevels.rank("Obligator", Categories.MELEE));
		// The blowpipe is an endgame weapon in the guides; the salamander only needs more levels.
		assertTrue(WeaponLevels.rank("Toxic blowpipe", Categories.RANGED)
			> WeaponLevels.rank("Tecu salamander", Categories.RANGED));
	}

	@Test
	public void unlistedWeaponNeedsNothing()
	{
		assertEquals(0, WeaponLevels.level("Rubber chicken", Categories.MELEE));
	}
}
