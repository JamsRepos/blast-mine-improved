package com.blastmineimproved;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import lombok.Getter;
import net.runelite.api.gameval.ObjectID;

public enum BlastMineRockType
{
	NORMAL(ObjectID.BLAST_MINING_WALL_01, ObjectID.BLAST_MINING_WALL_02),
	CHISELED(ObjectID.BLAST_MINING_WALL_CHISELED_01, ObjectID.BLAST_MINING_WALL_CHISELED_02),
	LOADED(ObjectID.BLAST_MINING_WALL_POT_01, ObjectID.BLAST_MINING_WALL_POT_02),
	LIT(ObjectID.BLAST_MINING_WALL_BURNING_01, ObjectID.BLAST_MINING_WALL_BURNING_02),
	EXPLODED(ObjectID.BLAST_MINING_WALL_DESTROYED_01, ObjectID.BLAST_MINING_WALL_DESTROYED_02);

	private static final Map<Integer, BlastMineRockType> ROCK_TYPES;

	static
	{
		ImmutableMap.Builder<Integer, BlastMineRockType> builder = new ImmutableMap.Builder<>();
		for (BlastMineRockType type : values())
		{
			for (int spotId : type.getObjectIds())
			{
				builder.put(spotId, type);
			}
		}
		ROCK_TYPES = builder.build();
	}

	@Getter
	private final int[] objectIds;

	BlastMineRockType(int... objectIds)
	{
		this.objectIds = objectIds;
	}

	public static BlastMineRockType getRockType(int objectId)
	{
		return ROCK_TYPES.get(objectId);
	}
}
