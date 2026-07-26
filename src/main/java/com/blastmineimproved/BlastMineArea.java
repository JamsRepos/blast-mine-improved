package com.blastmineimproved;

import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

final class BlastMineArea
{
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
		return world.getX() >= 1465 && world.getX() <= 1515
			&& world.getY() >= 3840 && world.getY() <= 3890;
	}
}
