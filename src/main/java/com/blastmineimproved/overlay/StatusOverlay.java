package com.blastmineimproved.overlay;

import com.blastmineimproved.BlastMineImprovedConfig;
import com.blastmineimproved.BlastMineImprovedPlugin;
import com.blastmineimproved.HelperAction;
import com.blastmineimproved.HelperService;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;

import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

/**
 * One movable top-left panel: helper text, then ore icons, then a single stats line.
 */
public class StatusOverlay extends Overlay
{
	private static final int PAD = 6;
	private static final int LINE_GAP = 2;
	private static final int SECTION_GAP = 6;
	private static final int ICON_GAP = 2;
	private static final Color BG = ComponentConstants.STANDARD_BACKGROUND_COLOR;
	private static final Color XP_COLOR = new Color(140, 220, 140);
	private static final int[] ORE_ITEM_IDS = {
		ItemID.COAL,
		ItemID.GOLD_ORE,
		ItemID.MITHRIL_ORE,
		ItemID.ADAMANTITE_ORE,
		ItemID.RUNITE_ORE
	};
	private static final int[] ORE_VARBITS = {
		VarbitID.LOVAKENGJ_ORE_COAL_BIGGER,
		VarbitID.LOVAKENGJ_ORE_GOLD_BIGGER,
		VarbitID.LOVAKENGJ_ORE_MITHRIL_BIGGER,
		VarbitID.LOVAKENGJ_ORE_ADAMANTITE_BIGGER,
		VarbitID.LOVAKENGJ_ORE_RUNITE_BIGGER
	};

	private final Client client;
	private final BlastMineImprovedConfig config;
	private final HelperService helperService;
	private final ItemManager itemManager;

	private final int[] cachedOreQty = new int[ORE_ITEM_IDS.length];
	private final BufferedImage[] cachedOreIcons = new BufferedImage[ORE_ITEM_IDS.length];
	private boolean hudHidden;
	private Boolean lastHudHidden;

	@Inject
	private StatusOverlay(
		BlastMineImprovedPlugin plugin,
		Client client,
		BlastMineImprovedConfig config,
		HelperService helperService,
		ItemManager itemManager)
	{
		super(plugin);
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(PRIORITY_MED);
		this.client = client;
		this.config = config;
		this.helperService = helperService;
		this.itemManager = itemManager;
		Arrays.fill(cachedOreQty, Integer.MIN_VALUE);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Blast Mine Improved");
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		final Widget blastMineWidget = client.getWidget(InterfaceID.LovakengjBlastMiningHud.DATA);
		final boolean hudPresent = blastMineWidget != null;
		final boolean showOre = config.showOreOverlay() && hudPresent;
		hudHidden = showOre;
		if (hudPresent && (lastHudHidden == null || lastHudHidden != hudHidden))
		{
			blastMineWidget.setHidden(hudHidden);
			lastHudHidden = hudHidden;
		}

		HelperAction action = helperService.getCurrentAction();
		boolean showHelper = config.enableHelper()
			&& config.showHelperPanel()
			&& action != null
			&& !(action.getKind() == HelperAction.Kind.IDLE && "Waiting…".equals(action.getDetail()));

		if (!showHelper && !showOre)
		{
			if (hudPresent && lastHudHidden != null && lastHudHidden)
			{
				blastMineWidget.setHidden(false);
				lastHudHidden = false;
			}
			return null;
		}

		FontMetrics fm = g.getFontMetrics();
		int lineHeight = fm.getHeight();

		String title = showHelper ? "Blast Mine Helper" : null;
		String actionLabel = showHelper ? action.getKind().getLabel() : null;
		String detail = showHelper ? action.getDetail() : null;

		BufferedImage[] icons = null;
		String stats = null;
		boolean sackFull = false;
		if (showOre)
		{
			icons = refreshOreIcons();
			sackFull = helperService.isSackFull();
			stats = helperService.totalSackOres() + " ores · " + formatXp(helperService.estimateSackXp())
				+ (config.assumeProspectors() ? "*" : "")
				+ (sackFull ? " · FULL" : "");
		}

		int contentWidth = 0;
		int contentHeight = 0;

		if (showHelper)
		{
			contentWidth = Math.max(contentWidth, fm.stringWidth(title));
			contentWidth = Math.max(contentWidth, fm.stringWidth(actionLabel));
			contentWidth = Math.max(contentWidth, fm.stringWidth(detail));
			contentHeight += lineHeight * 3 + LINE_GAP * 2;
		}

		int iconRowWidth = 0;
		int iconRowHeight = 0;
		if (showOre)
		{
			if (showHelper)
			{
				contentHeight += SECTION_GAP;
			}
			for (int i = 0; i < icons.length; i++)
			{
				iconRowWidth += icons[i].getWidth();
				if (i < icons.length - 1)
				{
					iconRowWidth += ICON_GAP;
				}
				iconRowHeight = Math.max(iconRowHeight, icons[i].getHeight());
			}
			contentWidth = Math.max(contentWidth, iconRowWidth);
			contentWidth = Math.max(contentWidth, fm.stringWidth(stats));
			contentHeight += iconRowHeight + LINE_GAP + lineHeight;
		}

		int boxW = contentWidth + PAD * 2;
		int boxH = contentHeight + PAD * 2;

		g.setColor(BG);
		g.fillRect(0, 0, boxW, boxH);

		int y = PAD + fm.getAscent();
		int x = PAD;

		if (showHelper)
		{
			g.setColor(Color.CYAN);
			g.drawString(title, x, y);
			y += lineHeight + LINE_GAP;

			Color actionColor = action.getColor() != null ? action.getColor() : Color.CYAN;
			g.setColor(actionColor);
			g.drawString(actionLabel, x, y);
			y += lineHeight + LINE_GAP;

			g.setColor(Color.WHITE);
			g.drawString(detail, x, y);
			y += lineHeight;
		}

		if (showOre)
		{
			if (showHelper)
			{
				y += SECTION_GAP;
			}

			int iconY = y - fm.getAscent() + 2;
			int iconX = x;
			for (int i = 0; i < icons.length; i++)
			{
				g.drawImage(icons[i], iconX, iconY, null);
				iconX += icons[i].getWidth() + ICON_GAP;
			}
			y = iconY + iconRowHeight + LINE_GAP + fm.getAscent();

			g.setColor(sackFull ? Color.ORANGE : Color.WHITE);
			g.drawString(stats, x, y);

			if (!sackFull)
			{
				String left = helperService.totalSackOres() + " ores · ";
				g.setColor(XP_COLOR);
				g.drawString(formatXp(helperService.estimateSackXp()) + (config.assumeProspectors() ? "*" : ""),
					x + fm.stringWidth(left), y);
			}
		}

		return new Dimension(boxW, boxH);
	}

	/** Only rebuild ore images when sack quantities change. */
	private BufferedImage[] refreshOreIcons()
	{
		for (int i = 0; i < ORE_VARBITS.length; i++)
		{
			int qty = client.getVarbitValue(ORE_VARBITS[i]);
			if (qty != cachedOreQty[i] || cachedOreIcons[i] == null)
			{
				cachedOreQty[i] = qty;
				cachedOreIcons[i] = itemManager.getImage(ORE_ITEM_IDS[i], qty, true);
			}
		}
		return cachedOreIcons;
	}

	private static String formatXp(int xp)
	{
		if (xp >= 100_000)
		{
			return String.format("%.0fk XP", xp / 1000.0);
		}
		if (xp >= 10_000)
		{
			return String.format("%.1fk XP", xp / 1000.0);
		}
		return String.format("%,d XP", xp);
	}
}
