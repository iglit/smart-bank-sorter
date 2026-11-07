package com.banksorter;

import net.runelite.client.eventbus.EventBus;
import org.junit.Test;

/**
 * RuneLite's event bus rejects badly named subscribers only when the plugin starts in the client; registering
 * here catches that at build time.
 */
public class SubscriberTest
{
	@Test
	public void subscribersRegister()
	{
		EventBus bus = new EventBus();
		BankSorterPlugin plugin = new BankSorterPlugin();
		bus.register(plugin);
		bus.register(new BankSorterPlugin.AfterBankTags(plugin));
	}
}
