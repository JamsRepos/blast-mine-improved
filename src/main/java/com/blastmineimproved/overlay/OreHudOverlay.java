package com.blastmineimproved.overlay;

import com.blastmineimproved.BlastMineImprovedConfig;
import com.blastmineimproved.BlastMineImprovedPlugin;
import com.blastmineimproved.HelperService;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

public class OreHudOverlay extends OverlayPanel
{
	private final Client client;
	private final BlastMineImprovedConfig config;
	private final HelperService helperService;
	private final ItemManager itemManager;
	private final PanelComponent oreRow = new PanelComponent();

	@Inject
	private OreHudOverlay(
		BlastMineImprovedPlugin plugin,
		Client client,
		BlastMineImprovedConfig config,
		HelperService helperService,
		ItemManager itemManager)
	{
		super(plugin);
		setPosition(OverlayPosition.TOP_LEFT);
		this.client = client;
		this.config = config;
		this.helperService = helperService;
		this.itemManager = itemManager;
		oreRow.setBackgroundColor(null);
		oreRow.setOrientation(ComponentOrientation.HORIZONTAL);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Blast Mine Improved overlay");
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final Widget blastMineWidget = client.getWidget(InterfaceID.LovakengjBlastMiningHud.DATA);
		if (blastMineWidget == null)
		{
			return null;
		}

		if (!config.showOreOverlay())
		{
			blastMineWidget.setHidden(false);
			return null;
		}

		blastMineWidget.setHidden(true);
		panelComponent.getChildren().clear();
		oreRow.getChildren().clear();

		panelComponent.getChildren().add(TitleComponent.builder().text("Ore sack").build());

		oreRow.getChildren().add(new ImageComponent(getImage(ItemID.COAL, client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_COAL_BIGGER))));
		oreRow.getChildren().add(new ImageComponent(getImage(ItemID.GOLD_ORE, client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_GOLD_BIGGER))));
		oreRow.getChildren().add(new ImageComponent(getImage(ItemID.MITHRIL_ORE, client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_MITHRIL_BIGGER))));
		oreRow.getChildren().add(new ImageComponent(getImage(ItemID.ADAMANTITE_ORE, client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_ADAMANTITE_BIGGER))));
		oreRow.getChildren().add(new ImageComponent(getImage(ItemID.RUNITE_ORE, client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_RUNITE_BIGGER))));
		panelComponent.getChildren().add(oreRow);

		int total = helperService.totalSackOres();
		int xp = helperService.estimateSackXp();
		String xpSuffix = config.assumeProspectors() ? " (w/ prospectors)" : "";

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Ores")
			.right(Integer.toString(total))
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Est. XP" + xpSuffix)
			.right(Integer.toString(xp))
			.rightColor(helperService.isSackFull() ? Color.ORANGE : Color.GREEN)
			.build());

		if (helperService.isSackFull())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Sack full — collect")
				.leftColor(Color.ORANGE)
				.build());
		}

		return super.render(graphics);
	}

	private BufferedImage getImage(int itemID, int amount)
	{
		return itemManager.getImage(itemID, amount, true);
	}
}
