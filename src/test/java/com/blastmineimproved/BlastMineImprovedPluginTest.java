package com.blastmineimproved;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BlastMineImprovedPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BlastMineImprovedPlugin.class);
		RuneLite.main(args);
	}
}
