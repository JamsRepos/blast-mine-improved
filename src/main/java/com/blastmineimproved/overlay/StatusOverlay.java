package com.blastmineimproved.overlay;

import com.blastmineimproved.BlastMineImprovedConfig;
import com.blastmineimproved.BlastMineImprovedPlugin;
import com.blastmineimproved.HelperAction;
import com.blastmineimproved.HelperService;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
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
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;

import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

/**
 * The Status Panel: a single movable OverlayPanel showing helper guidance, then the live
 * ore-sack contents. Hides entirely when there's nothing actionable to show.
 */
public class StatusOverlay extends OverlayPanel
{
	private static final int LINE_GAP = 2;
	private static final int ICON_GAP = 2;
	private static final Color DEFAULT_ACTION_COLOR = Color.CYAN;
	private static final Color LABEL_COLOR = Color.WHITE;
	private static final Color DETAIL_COLOR = new Color(170, 170, 170);
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
		panelComponent.setGap(new Point(0, LINE_GAP));
		this.client = client;
		this.config = config;
		this.helperService = helperService;
		this.itemManager = itemManager;
		Arrays.fill(cachedOreQty, Integer.MIN_VALUE);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Jam's Blast Mine");
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final Widget blastMineWidget = client.getWidget(InterfaceID.LovakengjBlastMiningHud.DATA);
		final boolean hudPresent = blastMineWidget != null;
		final boolean showOre = config.showOreOverlay() && hudPresent;
		// Re-apply every frame: the client recreates/unhides this HUD when entering the
		// mine or enabling the plugin, so a one-shot setHidden is not enough.
		if (hudPresent)
		{
			blastMineWidget.setHidden(config.showOreOverlay());
		}

		HelperAction action = helperService.getCurrentAction();
		boolean showHelper = config.enableHelper()
			&& config.showHelperPanel()
			&& action != null
			&& !(action.getKind() == HelperAction.Kind.IDLE && "Waiting…".equals(action.getDetail()));

		if (!showHelper && !showOre)
		{
			return null;
		}

		panelComponent.getChildren().clear();

		if (showHelper)
		{
			Color actionColor = action.getColor() != null ? action.getColor() : DEFAULT_ACTION_COLOR;
			panelComponent.getChildren().add(LineComponent.builder()
				.left(action.getKind().getLabel())
				.leftColor(actionColor)
				.build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left(action.getDetail())
				.leftColor(DETAIL_COLOR)
				.leftFont(FontManager.getRunescapeSmallFont())
				.build());
		}

		if (showOre)
		{
			if (showHelper)
			{
				panelComponent.getChildren().add(LineComponent.builder().left("").build());
			}

			panelComponent.getChildren().add(new OreIconRow(refreshOreIcons()));

			boolean sackFull = helperService.isCachedSackFull();
			int totalOres = helperService.getCachedTotalSackOres();
			String xp = formatXp(helperService.getCachedSackXp()) + (config.requireProspectors() ? "*" : "");

			if (sackFull)
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left(totalOres + " ores · " + xp + " · FULL")
					.leftColor(config.getWarningColor())
					.build());
			}
			else
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left(totalOres + " ores")
					.leftColor(LABEL_COLOR)
					.right(xp)
					.rightColor(XP_COLOR)
					.build());
			}
		}

		return super.render(graphics);
	}

	/** A row of ore icons, laid out as a single panel line. */
	private static final class OreIconRow implements LayoutableRenderableEntity
	{
		private final BufferedImage[] icons;
		private final Rectangle bounds = new Rectangle();
		private Point preferredLocation = new Point(0, 0);

		private OreIconRow(BufferedImage[] icons)
		{
			this.icons = icons;
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			int startX = preferredLocation.x;
			int x = startX;
			int height = 0;
			for (BufferedImage icon : icons)
			{
				graphics.drawImage(icon, x, preferredLocation.y, null);
				x += icon.getWidth() + ICON_GAP;
				height = Math.max(height, icon.getHeight());
			}
			Dimension dimension = new Dimension(Math.max(0, x - startX - ICON_GAP), height);
			bounds.setLocation(preferredLocation);
			bounds.setSize(dimension);
			return dimension;
		}

		@Override
		public Rectangle getBounds()
		{
			return bounds;
		}

		@Override
		public void setPreferredLocation(Point preferredLocation)
		{
			this.preferredLocation = preferredLocation;
		}

		@Override
		public void setPreferredSize(Dimension preferredSize)
		{
			// fixed-size row of live item sprites; external sizing requests are ignored
		}
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
