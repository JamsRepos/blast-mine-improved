package com.blastmineimproved;

import java.awt.Color;
import java.util.List;
import lombok.Getter;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

@Value
public class HelperAction
{
	/**
	 * Policy lives on the kind so icons, menu strip, and left-click prefer cannot drift.
	 * Icon hide and menu strip are separate: COLLECT_ORE hides rock icons but must not
	 * strip Light (that was the 21st-dynamite bug).
	 */
	public enum Kind
	{
		IDLE("Idle", false, false, false),
		EXCAVATE("Excavate", true, true, true, "Excavate"),
		PLACE_DYNAMITE("Place dynamite", true, true, true, "Place", "Use"),
		LIGHT("Light", true, true, true, "Light"),
		COLLECT_ORE("Pick up blasted ore", true, false, false, "Take", "Pick-up"),
		DEPOSIT_SACK("Deposit ore sack", true, false, false, "Deposit"),
		BANK_DYNAMITE("Use dynamite on bank chest", true, false, false, "Use"),
		PREP_INVENTORY("Prepare inventory", true, false, false, "Use", "Deposit"),
		COLLECT_OPERATOR("Collect from operator", true, false, false, "Talk-to", "Collect"),
		WEAR_PROSPECTORS("Wear prospectors before collecting", true, false, false, "Talk-to", "Collect");

		@Getter
		private final String label;
		private final boolean hideOffPathIcons;
		private final boolean iconsOnFocusOnly;
		private final boolean stripOffPathRockMenus;
		private final String[] preferOptions;

		Kind(String label, boolean hideOffPathIcons, boolean iconsOnFocusOnly, boolean stripOffPathRockMenus, String... preferOptions)
		{
			this.label = label;
			this.hideOffPathIcons = hideOffPathIcons;
			this.iconsOnFocusOnly = iconsOnFocusOnly;
			this.stripOffPathRockMenus = stripOffPathRockMenus;
			this.preferOptions = preferOptions;
		}

		public boolean hidesOffPathIcons()
		{
			return hideOffPathIcons;
		}

		public boolean iconsOnFocusOnly()
		{
			return iconsOnFocusOnly;
		}

		public boolean stripsOffPathRockMenus()
		{
			return stripOffPathRockMenus;
		}

		public boolean matchesPrefer(String option)
		{
			if (option == null)
			{
				return false;
			}
			for (String prefer : preferOptions)
			{
				if (prefer.equals(option))
				{
					return true;
				}
			}
			return this == DEPOSIT_SACK && option.contains("Deposit");
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
