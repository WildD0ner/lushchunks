package dev.lushchunks;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Zeichnet das Chunk-Overlay ueber vanillas Gizmo-System.
 *
 * Gizmos werden von Minecraft selbst gerendert. Dadurch brauchen wir keine eigene
 * RenderPipeline und es gibt keine Konflikte mit dem Render-Pass (auf 26.3 erlaubt der
 * Command-Encoder nur einen offenen Pass).
 */
public final class LushOverlay {

	private static final double INSET = 0.05;

	private final LushConfig config;
	private final LushScanner scanner;

	public LushOverlay(LushConfig config, LushScanner scanner) {
		this.config = config;
		this.scanner = scanner;
	}

	public void render() {
		Minecraft mc = Minecraft.getInstance();
		if (!config.enabled || mc.player == null || mc.level == null) {
			return;
		}

		int pcx = Mth.floor(mc.player.getX()) >> 4;
		int pcz = Mth.floor(mc.player.getZ()) >> 4;
		int radius = config.overlayRadius;
		double planeY = Mth.floor(mc.player.getY()) + 0.05;

		GizmoStyle outline = GizmoStyle.stroke(config.strokeColor, config.strokeWidth);
		GizmoStyle plane = GizmoStyle.strokeAndFill(config.strokeColor, config.strokeWidth, config.fillColor);

		boolean drawBox = config.style == LushConfig.Style.BOX || config.style == LushConfig.Style.BOTH;
		boolean drawPlane = config.style == LushConfig.Style.PLANE || config.style == LushConfig.Style.BOTH;

		for (Map.Entry<Long, LushScanner.ChunkInfo> e : scanner.lushChunks().entrySet()) {
			int cx = LushScanner.keyX(e.getKey());
			int cz = LushScanner.keyZ(e.getKey());
			if (Math.abs(cx - pcx) > radius || Math.abs(cz - pcz) > radius) {
				continue;
			}
			LushScanner.ChunkInfo info = e.getValue();

			double x0 = (cx << 4) + INSET;
			double z0 = (cz << 4) + INSET;
			double x1 = (cx << 4) + 16 - INSET;
			double z1 = (cz << 4) + 16 - INSET;

			if (drawBox) {
				AABB box = new AABB(x0, info.minY(), z0, x1, info.maxY(), z1);
				var props = Gizmos.cuboid(box, outline);
				if (config.alwaysOnTop) {
					props.setAlwaysOnTop();
				}
			}

			if (drawPlane) {
				var props = Gizmos.rect(
						new Vec3(x0, planeY, z0),
						new Vec3(x1, planeY, z0),
						new Vec3(x1, planeY, z1),
						new Vec3(x0, planeY, z1),
						plane);
				if (config.alwaysOnTop) {
					props.setAlwaysOnTop();
				}
			}
		}
	}
}
