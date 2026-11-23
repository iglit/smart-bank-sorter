package com.smartbanksorter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class RecentDepositsTest
{
	private static final long HOUR = TimeUnit.HOURS.toMillis(1);
	private static final long WINDOW = 6 * HOUR;
	private static final long T0 = 1_800_000_000_000L;
	private static final int SHARK = 385;
	private static final int WHIP = 4151;
	private static final int BONES = 536;

	private static Map<Integer, Long> bank(Object... idsAndQuantities)
	{
		Map<Integer, Long> bank = new HashMap<>();
		for (int i = 0; i < idsAndQuantities.length; i += 2)
		{
			bank.put((Integer) idsAndQuantities[i], ((Number) idsAndQuantities[i + 1]).longValue());
		}
		return bank;
	}

	@Test
	public void lootShowsButLeftoverSuppliesDoNot()
	{
		RecentDeposits r = new RecentDeposits(null);
		r.update(bank(SHARK, 100, BONES, 10), T0, WINDOW);
		assertTrue("the first look is the starting point", r.recent(T0, WINDOW, true).isEmpty());

		// Take 20 sharks on a trip, come back with 5 and a whip.
		r.update(bank(SHARK, 80), T0 + HOUR, WINDOW);
		r.update(bank(SHARK, 85, WHIP, 1), T0 + 2 * HOUR, WINDOW);
		assertEquals(Collections.singletonMap(WHIP, T0 + 2 * HOUR), r.recent(T0 + 2 * HOUR, WINDOW, true));
	}

	@Test
	public void everyDepositCountsWhenAskedEvenWithMoreTakenOut()
	{
		RecentDeposits r = new RecentDeposits(null);
		r.update(bank(SHARK, 100, BONES, 10), T0, WINDOW);
		// Deposit 200, withdraw 205.
		r.update(bank(SHARK, 300, BONES, 10), T0 + HOUR, WINDOW);
		r.update(bank(SHARK, 95, BONES, 10), T0 + 2 * HOUR, WINDOW);
		assertTrue(r.recent(T0 + 2 * HOUR, WINDOW, true).isEmpty());
		assertEquals(Collections.singletonMap(SHARK, T0 + HOUR), r.recent(T0 + 2 * HOUR, WINDOW, false));
		// A withdrawal alone isn't a deposit.
		r.update(bank(SHARK, 95, BONES, 10), T0 + 3 * HOUR, WINDOW);
		r.update(bank(SHARK, 95, BONES, 5), T0 + 4 * HOUR, WINDOW);
		assertEquals(Collections.singleton(SHARK), r.recent(T0 + 4 * HOUR, WINDOW, false).keySet());
	}

	@Test
	public void depositsDropOffAfterTheWindow()
	{
		RecentDeposits r = new RecentDeposits(null);
		r.update(bank(BONES, 10), T0, WINDOW);
		r.update(bank(BONES, 50), T0 + HOUR, WINDOW);
		assertEquals(Collections.singletonMap(BONES, T0 + HOUR), r.recent(T0 + 6 * HOUR, WINDOW, true));
		assertTrue(r.recent(T0 + 7 * HOUR, WINDOW, true).isEmpty());

		r.update(bank(BONES, 50), T0 + 8 * HOUR, WINDOW);
		assertEquals("nothing left to remember", "", r.save());
	}

	@Test
	public void theLatestDepositDatesTheItem()
	{
		RecentDeposits r = new RecentDeposits(null);
		r.update(bank(BONES, 10), T0, WINDOW);
		r.update(bank(BONES, 50), T0 + HOUR, WINDOW);
		r.update(bank(BONES, 40), T0 + 2 * HOUR, WINDOW);
		r.update(bank(BONES, 70), T0 + 3 * HOUR, WINDOW);
		assertEquals(Collections.singletonMap(BONES, T0 + 3 * HOUR), r.recent(T0 + 3 * HOUR, WINDOW, true));
		// Back to what it was: nothing gained.
		r.update(bank(BONES, 10), T0 + 4 * HOUR, WINDOW);
		assertTrue(r.recent(T0 + 4 * HOUR, WINDOW, true).isEmpty());
	}

	@Test
	public void survivesASaveAndCatchesChangesMadeWhileAway()
	{
		RecentDeposits r = new RecentDeposits(null);
		r.update(bank(BONES, 10, SHARK, 100), T0, WINDOW);
		r.update(bank(BONES, 50, SHARK, 100), T0 + HOUR, WINDOW);

		RecentDeposits loaded = new RecentDeposits(r.save());
		assertEquals(r.recent(T0 + HOUR, WINDOW, true), loaded.recent(T0 + HOUR, WINDOW, true));
		// Logged back in: more bones than last seen counts from now; sharks, with no history, are the new start.
		assertTrue(loaded.update(bank(BONES, 80, SHARK, 150), T0 + 2 * HOUR, WINDOW));
		assertEquals(Collections.singletonMap(BONES, T0 + 2 * HOUR), loaded.recent(T0 + 2 * HOUR, WINDOW, true));
		loaded.update(bank(BONES, 80, SHARK, 160), T0 + 3 * HOUR, WINDOW);
		assertEquals(T0 + 3 * HOUR, (long) loaded.recent(T0 + 3 * HOUR, WINDOW, true).get(SHARK));
	}

	@Test
	public void ignoresDamagedSaves()
	{
		RecentDeposits r = new RecentDeposits("536=0:10,x:50\n4151=0:0," + (T0 + HOUR) + ":1\njunk");
		assertEquals(Collections.singletonMap(WHIP, T0 + HOUR), r.recent(T0 + HOUR, WINDOW, true));
		assertFalse(r.save().contains("536"));
	}

	@Test
	public void longHistoriesAreCapped()
	{
		RecentDeposits r = new RecentDeposits(null);
		r.update(bank(BONES, 0), T0, WINDOW);
		for (int i = 1; i <= 200; i++)
		{
			r.update(bank(BONES, i), T0 + i, WINDOW);
		}
		assertTrue(r.save().split(",").length <= 51);
		assertEquals(T0 + 200, (long) r.recent(T0 + 200, WINDOW, true).get(BONES));
	}
}
