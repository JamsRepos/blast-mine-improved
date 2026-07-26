package com.blastmineimproved;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("blastmineimproved")
public interface BlastMineImprovedConfig extends Config
{
	@ConfigSection(
		name = "Overlays",
		description = "Visual overlays for Blast Mine",
		position = 0
	)
	String overlaysSection = "overlays";

	@ConfigSection(
		name = "Helper",
		description = "North-east rotation helper (Easy Blast Furnace style)",
		position = 1
	)
	String helperSection = "helper";

	@ConfigSection(
		name = "Menu safety",
		description = "Mistake-prevention menu changes",
		position = 2
	)
	String menuSection = "menu";

	@ConfigSection(
		name = "Alerts",
		description = "Chat and sound alerts for dynamite",
		position = 3
	)
	String alertsSection = "alerts";

	@ConfigItem(
		keyName = "showOreOverlay",
		name = "Show ore sack overlay",
		description = "Show ore counts and estimated Mining XP currently in the sack",
		section = overlaysSection,
		position = 0
	)
	default boolean showOreOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRockIconOverlay",
		name = "Show rock icons",
		description = "Show chisel/dynamite/tinderbox icons on rocks",
		section = overlaysSection,
		position = 1
	)
	default boolean showRockIconOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTimerOverlay",
		name = "Show fuse timers",
		description = "Show fuse countdown on lit dynamite",
		section = overlaysSection,
		position = 2
	)
	default boolean showTimerOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showWarningOverlay",
		name = "Show explosion warning",
		description = "Highlight tiles in the explosion radius",
		section = overlaysSection,
		position = 3
	)
	default boolean showWarningOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showInventoryOreTimers",
		name = "Inventory ore timers",
		description = "Show remaining time before blasted ore in your inventory disintegrates",
		section = overlaysSection,
		position = 4
	)
	default boolean showInventoryOreTimers()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "timerColor",
		name = "Timer color",
		description = "Color of the fuse timer overlay",
		section = overlaysSection,
		position = 5
	)
	default Color getTimerColor()
	{
		return Color.CYAN;
	}

	@Alpha
	@ConfigItem(
		keyName = "warningColor",
		name = "Warning color",
		description = "Color of the explosion radius warning",
		section = overlaysSection,
		position = 6
	)
	default Color getWarningColor()
	{
		return Color.ORANGE;
	}

	@ConfigItem(
		keyName = "enableHelper",
		name = "Enable rotation helper",
		description = "Guide the north-east pair rotation with next-click highlights and prompts",
		section = helperSection,
		position = 0
	)
	default boolean enableHelper()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightNextClick",
		name = "Highlight next click",
		description = "Highlight the tile/object for the recommended next action",
		section = helperSection,
		position = 1
	)
	default boolean highlightNextClick()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showHelperPanel",
		name = "Show helper panel",
		description = "Show a panel with the current recommended action",
		section = helperSection,
		position = 2
	)
	default boolean showHelperPanel()
	{
		return true;
	}

	@Range(min = 1, max = 50)
	@ConfigItem(
		keyName = "lowDynamiteThreshold",
		name = "Low dynamite threshold",
		description = "Prompt banking when unnoted dynamite drops to this amount",
		section = helperSection,
		position = 3
	)
	default int lowDynamiteThreshold()
	{
		return 4;
	}

	@ConfigItem(
		keyName = "assumeProspectors",
		name = "Estimate with prospectors",
		description = "Apply full prospector kit (+2.5%) to estimated sack XP",
		section = helperSection,
		position = 4
	)
	default boolean assumeProspectors()
	{
		return true;
	}

	@ConfigItem(
		keyName = "deprioritizeWithoutDynamite",
		name = "Deprioritize excavate without dynamite",
		description = "Make Excavate right-click only when you have no unnoted dynamite",
		section = menuSection,
		position = 0
	)
	default boolean deprioritizeWithoutDynamite()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pairLightSafety",
		name = "Paired Light safety",
		description = "Hide Light on a pot unless its pair partner is also ready to light",
		section = menuSection,
		position = 1
	)
	default boolean pairLightSafety()
	{
		return true;
	}

	@ConfigItem(
		keyName = "prioritizeHelperOptions",
		name = "Left-click helper options",
		description = "Prefer the recommended action as the left-click option when available",
		section = menuSection,
		position = 2
	)
	default boolean prioritizeHelperOptions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatMessages",
		name = "Dynamite chat messages",
		description = "Chat messages when running out of or replenishing dynamite",
		section = alertsSection,
		position = 0
	)
	default MessagesEnabledType chatMessages()
	{
		return MessagesEnabledType.ALL_MESSAGES;
	}

	@ConfigItem(
		keyName = "soundFx",
		name = "Out of dynamite sound",
		description = "Play a sound when you run out of unnoted dynamite",
		section = alertsSection,
		position = 1
	)
	default boolean soundFx()
	{
		return true;
	}

	@Range(max = 4996)
	@ConfigItem(
		keyName = "soundId",
		name = "Sound ID",
		description = "Sound effect ID for out-of-dynamite alert",
		section = alertsSection,
		position = 2
	)
	default int soundId()
	{
		return 2277;
	}

	@Range(max = 127)
	@ConfigItem(
		keyName = "soundVolume",
		name = "Sound volume",
		description = "Volume for the out-of-dynamite sound",
		section = alertsSection,
		position = 3
	)
	default int soundVolume()
	{
		return 64;
	}

	enum MessagesEnabledType
	{
		ALL_MESSAGES("All messages"),
		OUT_OF_DYNAMITE("Out of dynamite only"),
		REPLENISHED_DYNAMITE("Replenished dynamite only"),
		OFF("Off");

		private final String name;

		MessagesEnabledType(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
