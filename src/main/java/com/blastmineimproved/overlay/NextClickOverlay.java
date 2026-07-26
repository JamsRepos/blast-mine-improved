package com.blastmineimproved.overlay;

import com.blastmineimproved.BlastMineImprovedConfig;
import com.blastmineimproved.HelperAction;
import com.blastmineimproved.HelperService;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

public class NextClickOverlay extends Overlay
{
	private final Client client;
	private final BlastMineImprovedConfig config;
	private final HelperService helperService;

	@Inject
	private NextClickOverlay(Client client, BlastMineImprovedConfig config, HelperService helperService)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		this.client = client;
		this.config = config;
		this.helperService = helperService;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.enableHelper() || !config.highlightNextClick())
		{
			return null;
		}

		HelperAction action = helperService.getCurrentAction();
		if (action == null || action.getHighlightTiles() == null || action.getHighlightTiles().isEmpty())
		{
			return null;
		}

		Color color = action.getColor() != null ? action.getColor() : Color.CYAN;
		for (WorldPoint tile : action.getHighlightTiles())
		{
			LocalPoint local = LocalPoint.fromWorld(client, tile);
			if (local == null)
			{
				continue;
			}

			Polygon poly = Perspective.getCanvasTilePoly(client, local);
			if (poly != null)
			{
				OverlayUtil.renderPolygon(graphics, poly, color);
				graphics.setStroke(new BasicStroke(2));
			}
		}

		return null;
	}
}
