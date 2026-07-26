package com.blastmineimproved.overlay;

import com.blastmineimproved.BlastMineImprovedConfig;
import com.blastmineimproved.BlastedOreTracker;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.time.Duration;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.components.ProgressPieComponent;
import net.runelite.api.gameval.ItemID;

public class InventoryOreTimerOverlay extends WidgetItemOverlay
{
	private final BlastMineImprovedConfig config;
	private final BlastedOreTracker oreTracker;

	@Inject
	private InventoryOreTimerOverlay(BlastMineImprovedConfig config, BlastedOreTracker oreTracker)
	{
		this.config = config;
		this.oreTracker = oreTracker;
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.showInventoryOreTimers() || itemId != ItemID.LOVAKENGJ_BLASTED_ORE)
		{
			return;
		}

		BlastedOreTracker.OreTimer matched = null;
		for (BlastedOreTracker.OreTimer timer : oreTracker.timersForInventorySlots())
		{
			if (timer.getSlot() == widgetItem.getWidget().getIndex())
			{
				matched = timer;
				break;
			}
		}

		if (matched == null)
		{
			return;
		}

		Duration remaining = matched.remaining();
		Rectangle bounds = widgetItem.getCanvasBounds();
		ProgressPieComponent pie = new ProgressPieComponent();
		pie.setPosition(new net.runelite.api.Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2));
		pie.setProgress(matched.remainingRelative());

		Color color = remaining.compareTo(Duration.ofSeconds(45)) < 0
			? Color.RED
			: remaining.compareTo(Duration.ofSeconds(90)) < 0
			? Color.ORANGE
			: Color.CYAN;
		pie.setFill(color);
		pie.setBorderColor(color);
		pie.render(graphics);
	}
}
