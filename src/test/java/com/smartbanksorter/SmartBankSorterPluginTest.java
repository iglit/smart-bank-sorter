package com.smartbanksorter;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SmartBankSorterPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SmartBankSorterPlugin.class);
		RuneLite.main(args);
	}
}
