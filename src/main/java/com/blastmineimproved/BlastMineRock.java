package com.blastmineimproved;

import java.time.Duration;
import java.time.Instant;
import lombok.Getter;
import net.runelite.api.GameObject;

@Getter
public class BlastMineRock
{
	private static final Duration PLANT_TIME = Duration.ofSeconds(30);
	private static final Duration FUSE_TIME = Duration.ofMillis(4200);

	private final GameObject gameObject;
	private final BlastMineRockType type;
	private final Instant creationTime = Instant.now();

	BlastMineRock(final GameObject gameObject, BlastMineRockType blastMineRockType)
	{
		this.gameObject = gameObject;
		this.type = blastMineRockType;
	}

	public double getRemainingFuseTimeRelative()
	{
		Duration duration = Duration.between(creationTime, Instant.now());
		return duration.compareTo(FUSE_TIME) < 0
			? (double) duration.toMillis() / FUSE_TIME.toMillis()
			: 1;
	}

	public double getRemainingTimeRelative()
	{
		Duration duration = Duration.between(creationTime, Instant.now());
		return duration.compareTo(PLANT_TIME) < 0
			? (double) duration.toMillis() / PLANT_TIME.toMillis()
			: 1;
	}
}
