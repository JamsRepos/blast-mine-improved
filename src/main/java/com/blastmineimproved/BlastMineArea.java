package com.blastmineimproved;

import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

final class BlastMineArea
{
	/** Lovakengj Blast Mine. Shared with {@link NortheastSite} markers. */
	static final int REGION_ID = 5948;

	private BlastMineArea()
	{
	}

	static boolean isInBlastMine(Client client)
	{
		if (client.getLocalPlayer() == null)
		{
			return false;
		}

		LocalPoint local = client.getLocalPlayer().getLocalLocation();
		WorldPoint world = WorldPoint.fromLocalInstance(client, local);
		return world.getRegionID() == REGION_ID;
	}
}
