package com.blastmineimproved.overlay;

import com.blastmineimproved.BlastMineImprovedConfig;
import com.blastmineimproved.BlastMineImprovedPlugin;
import com.blastmineimproved.BlastMineRock;
import com.google.common.collect.ImmutableSet;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ProgressPieComponent;
import net.runelite.client.util.ColorUtil;

public class RockOverlay extends Overlay
{
	private static final int MAX_DISTANCE = 16;
	private static final int WARNING_DISTANCE = 1;
	private static final ImmutableSet<Integer> WALL_OBJECTS = ImmutableSet.of(
		ObjectID.BLAST_MINING_WALL_TYPE_TWO, ObjectID.BLAST_MINING_WALL_TYPE_THREE, ObjectID.BLAST_MINING_WALL_TYPE_FIVE,
		ObjectID.BLAST_MINING_ROOF_START_01, ObjectID.BLAST_MINING_ROOF_START_02,
		ObjectID.BLAST_MINING_ROOF_FOR_TYPE_2_01, ObjectID.BLAST_MINING_ROOF_FOR_TYPE_2_02, ObjectID.BLAST_MINING_ROOF_FOR_TYPE_2_03,
		ObjectID.BLAST_MINING_ROOF_FOR_TYPE_3_01,
		ObjectID.BLAST_MINING_WALL_01, ObjectID.BLAST_MINING_WALL_02,
		ObjectID.BLAST_MINING_WALL_CHISELED_01, ObjectID.BLAST_MINING_WALL_CHISELED_02,
		ObjectID.BLAST_MINING_WALL_POT_01, ObjectID.BLAST_MINING_WALL_POT_02,
		ObjectID.BLAST_MINING_WALL_BURNING_01, ObjectID.BLAST_MINING_WALL_BURNING_02,
		ObjectID.BLAST_MINING_WALL_DESTROYED_01, ObjectID.BLAST_MINING_WALL_DESTROYED_02);

	private final Client client;
	private final BlastMineImprovedPlugin plugin;
	private final BlastMineImprovedConfig config;
	private final BufferedImage chiselIcon;
	private final BufferedImage dynamiteIcon;
	private final BufferedImage tinderboxIcon;

	@Inject
	private RockOverlay(Client client, BlastMineImprovedPlugin plugin, BlastMineImprovedConfig config, ItemManager itemManager)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		chiselIcon = itemManager.getImage(ItemID.CHISEL);
		dynamiteIcon = itemManager.getImage(ItemID.LOVAKENGJ_DYNAMITE_FUSED);
		tinderboxIcon = itemManager.getImage(ItemID.TINDERBOX);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Map<net.runelite.api.coords.WorldPoint, BlastMineRock> rocks = plugin.getRocks();
		if (rocks.isEmpty() || client.getLocalPlayer() == null)
		{
			return null;
		}

		final Tile[][][] tiles = client.getScene().getTiles();

		for (final BlastMineRock rock : rocks.values())
		{
			if (rock.getGameObject().getCanvasLocation() == null
				|| rock.getGameObject().getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation()) > MAX_DISTANCE)
			{
				continue;
			}

			switch (rock.getType())
			{
				case NORMAL:
					drawIconOnRock(graphics, rock, chiselIcon);
					break;
				case CHISELED:
					drawIconOnRock(graphics, rock, dynamiteIcon);
					break;
				case LOADED:
					drawIconOnRock(graphics, rock, tinderboxIcon);
					break;
				case LIT:
					drawTimerOnRock(graphics, rock, config.getTimerColor());
					drawAreaWarning(graphics, rock, config.getWarningColor(), tiles);
					break;
				default:
					break;
			}
		}

		return null;
	}

	private void drawIconOnRock(Graphics2D graphics, BlastMineRock rock, BufferedImage icon)
	{
		if (!config.showRockIconOverlay())
		{
			return;
		}

		Point loc = Perspective.getCanvasImageLocation(client, rock.getGameObject().getLocalLocation(), icon, 150);
		if (loc != null)
		{
			graphics.drawImage(icon, loc.getX(), loc.getY(), null);
		}
	}

	private void drawTimerOnRock(Graphics2D graphics, BlastMineRock rock, Color color)
	{
		if (!config.showTimerOverlay())
		{
			return;
		}

		Point loc = Perspective.localToCanvas(client, rock.getGameObject().getLocalLocation(), rock.getGameObject().getPlane(), 150);
		if (loc != null)
		{
			final double timeLeft = 1 - rock.getRemainingFuseTimeRelative();
			final ProgressPieComponent pie = new ProgressPieComponent();
			pie.setFill(color);
			pie.setBorderColor(color);
			pie.setPosition(loc);
			pie.setProgress(timeLeft);
			pie.render(graphics);
		}
	}

	private void drawAreaWarning(Graphics2D graphics, BlastMineRock rock, Color color, Tile[][][] tiles)
	{
		if (!config.showWarningOverlay())
		{
			return;
		}

		final int z = client.getPlane();
		int x = rock.getGameObject().getLocalLocation().getX() / Perspective.LOCAL_TILE_SIZE;
		int y = rock.getGameObject().getLocalLocation().getY() / Perspective.LOCAL_TILE_SIZE;

		if (tiles[z][x][y] == null || tiles[z][x][y].getWallObject() == null)
		{
			return;
		}

		final int orientation = tiles[z][x][y].getWallObject().getOrientationA();

		switch (orientation)
		{
			case 1:
				x--;
				break;
			case 4:
				x++;
				break;
			case 8:
				y--;
				break;
			default:
				y++;
		}

		for (int i = -WARNING_DISTANCE; i <= WARNING_DISTANCE; i++)
		{
			for (int j = -WARNING_DISTANCE; j <= WARNING_DISTANCE; j++)
			{
				if (x + i < 0 || y + j < 0 || x + i >= tiles[z].length || y + j >= tiles[z][x + i].length)
				{
					continue;
				}

				final GameObject gameObject = tiles[z][x + i][y + j].getGameObjects()[0];
				if (gameObject == null || !WALL_OBJECTS.contains(gameObject.getId()))
				{
					final LocalPoint localTile = new LocalPoint(
						(x + i) * Perspective.LOCAL_TILE_SIZE + Perspective.LOCAL_TILE_SIZE / 2,
						(y + j) * Perspective.LOCAL_TILE_SIZE + Perspective.LOCAL_TILE_SIZE / 2);
					final Polygon poly = Perspective.getCanvasTilePoly(client, localTile);
					if (poly != null)
					{
						graphics.setColor(ColorUtil.colorWithAlpha(color, (int) (color.getAlpha() / 2.5)));
						graphics.fillPolygon(poly);
					}
				}
			}
		}
	}
}
