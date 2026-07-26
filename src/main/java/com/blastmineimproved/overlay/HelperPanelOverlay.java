package com.blastmineimproved.overlay;

import com.blastmineimproved.BlastMineImprovedConfig;
import com.blastmineimproved.BlastMineImprovedPlugin;
import com.blastmineimproved.HelperAction;
import com.blastmineimproved.HelperService;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

public class HelperPanelOverlay extends OverlayPanel
{
	private final BlastMineImprovedConfig config;
	private final HelperService helperService;

	@Inject
	private HelperPanelOverlay(
		BlastMineImprovedPlugin plugin,
		BlastMineImprovedConfig config,
		HelperService helperService)
	{
		super(plugin);
		setPosition(OverlayPosition.TOP_LEFT);
		this.config = config;
		this.helperService = helperService;
		addMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Blast Mine helper");
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.enableHelper() || !config.showHelperPanel())
		{
			return null;
		}

		HelperAction action = helperService.getCurrentAction();
		if (action == null || action.getKind() == HelperAction.Kind.IDLE && "Waiting…".equals(action.getDetail()))
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Blast Mine Helper")
			.color(Color.CYAN)
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left(action.getKind().getLabel())
			.leftColor(action.getColor())
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left(action.getDetail())
			.build());

		return super.render(graphics);
	}
}
