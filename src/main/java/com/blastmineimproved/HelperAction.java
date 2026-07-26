package com.blastmineimproved;

import java.awt.Color;
import java.util.List;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

@Value
public class HelperAction
{
	public enum Kind
	{
		IDLE("Idle"),
		EXCAVATE("Excavate"),
		PLACE_DYNAMITE("Place dynamite"),
		LIGHT("Light"),
		COLLECT_ORE("Pick up blasted ore"),
		DEPOSIT_SACK("Deposit ore sack"),
		BANK_DYNAMITE("Use dynamite on bank chest"),
		PREP_INVENTORY("Prepare inventory"),
		COLLECT_OPERATOR("Collect from operator"),
		WEAR_PROSPECTORS("Wear prospectors before collecting");

		private final String label;

		Kind(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	Kind kind;
	String detail;
	List<WorldPoint> highlightTiles;
	Color color;

	public static HelperAction idle()
	{
		return new HelperAction(Kind.IDLE, "Waiting…", List.of(), Color.GRAY);
	}
}
