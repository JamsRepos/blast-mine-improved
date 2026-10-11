package com.blastmineimproved.overlay;

import com.blastmineimproved.BlastMineImprovedPlugin;
import com.blastmineimproved.HelperService;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Explains why Collect is missing from the operator: shown under the same condition
 * MenuSafetyService uses to strip it, so the label and the hidden option never disagree.
 */
public class OperatorOverlay extends Overlay
{
	private static final String LABEL = "Wear prospector kit to collect";
	private static final Color LABEL_COLOR = new Color(255, 170, 0);

	private final Client client;
	private final BlastMineImprovedPlugin plugin;
	private final HelperService helperService;

	@Inject
	private OperatorOverlay(Client client, BlastMineImprovedPlugin plugin, HelperService helperService)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		this.client = client;
		this.plugin = plugin;
		this.helperService = helperService;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		NPC operator = plugin.getOperator();
		if (operator == null || !helperService.needsProspectorKit())
		{
			return null;
		}

		Point text = Perspective.getCanvasTextLocation(
			client, graphics, operator.getLocalLocation(), LABEL, operator.getLogicalHeight() + 40);
		if (text != null)
		{
			OverlayUtil.renderTextLocation(graphics, text, LABEL, LABEL_COLOR);
		}

		return null;
	}
}
