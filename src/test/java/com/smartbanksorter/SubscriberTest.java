package com.smartbanksorter;

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
		SmartBankSorterPlugin plugin = new SmartBankSorterPlugin();
		bus.register(plugin);
		bus.register(new SmartBankSorterPlugin.AfterBankTags(plugin));
	}
}
