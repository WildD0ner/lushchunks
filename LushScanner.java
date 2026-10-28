package dev.lushchunks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biomes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Erkennt Chunks mit Lush-Caves-Biom.
 *
 * Der Client kennt die Biome aller geladenen Chunks (sie stecken im Chunk-Paket).
 * Biome liegen in einem 4x4x4-Raster ("Zellen"), deshalb tasten wir jeden Chunk
 * in 4er-Schritten ab: 4 x 4 Zellen pro Ebene, ueber die gesamte Welthoehe.
 */
public final class LushScanner {

	/** Ergebnis fuer einen Lush-Chunk. minY/maxY sind Block-Hoehen der Lush-Zellen. */
	public record ChunkInfo(int cells, int minY, int maxY) {
	}

	private final LushConfig config;
	/** Nur Chunks, die die Mindestanzahl Lush-Zellen erreichen. */
	private final Map<Long, ChunkInfo> lush = new HashMap<>();
	/** Alle bereits gescannten Chunks (auch ohne Lush). */
	private final Set<Long> scanned = new HashSet<>();
	private final List<Long> queue = new ArrayList<>();
	private ClientLevel lastLevel;
	private int tickCounter;

	public LushScanner(LushConfig config) {
		this.config = config;
	}

	public static long key(int cx, int cz) {
		return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
	}

	public static int keyX(long key) {
		return (int) (key >> 32);
	}

	public static int keyZ(long key) {
		return (int) key;
	}

	public Map<Long, ChunkInfo> lushChunks() {
		return lush;
	}

	public int scannedCount() {
		return scanned.size();
	}

	public void clear() {
		lush.clear();
		scanned.clear();
		queue.clear();
	}

	public void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			if (lastLevel != null) {
				clear();
				lastLevel = null;
			}
			return;
		}
		if (level != lastLevel) {
			clear();
			lastLevel = level;
		}
		if (!config.enabled) {
			return;
		}

		int pcx = Mth.floor(mc.player.getX()) >> 4;
		int pcz = Mth.floor(mc.player.getZ()) >> 4;
		int radius = config.scanRadius;

		// Warteschlange nur gelegentlich neu aufbauen
		if (tickCounter++ % 5 == 0) {
			evictFarChunks(pcx, pcz, radius + 4);
			rebuildQueue(level, pcx, pcz, radius);
		}

		int budget = config.maxScansPerTick;
		while (budget > 0 && !queue.isEmpty()) {
			long k = queue.remove(queue.size() - 1);
			int cx = keyX(k);
			int cz = keyZ(k);
			if (scanned.contains(k) || !level.hasChunk(cx, cz)) {
				continue;
			}
			scanChunk(level, cx, cz);
			budget--;
		}
	}

	private void evictFarChunks(int pcx, int pcz, int maxDist) {
		Iterator<Long> it = scanned.iterator();
		while (it.hasNext()) {
			long k = it.next();
			if (Math.abs(keyX(k) - pcx) > maxDist || Math.abs(keyZ(k) - pcz) > maxDist) {
				it.remove();
				lush.remove(k);
			}
		}
	}

	private void rebuildQueue(ClientLevel level, int pcx, int pcz, int radius) {
		queue.clear();
		List<long[]> candidates = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int cx = pcx + dx;
				int cz = pcz + dz;
				long k = key(cx, cz);
				if (!scanned.contains(k) && level.hasChunk(cx, cz)) {
					candidates.add(new long[] {k, (long) dx * dx + (long) dz * dz});
				}
			}
		}
		// Naechste Chunks zuletzt in die Liste, weil von hinten abgearbeitet wird.
		candidates.sort((a, b) -> Long.compare(b[1], a[1]));
		for (long[] c : candidates) {
			queue.add(c[0]);
		}
	}

	private void scanChunk(ClientLevel level, int cx, int cz) {
		int baseX = cx << 4;
		int baseZ = cz << 4;
		int bottom = level.getMinY();
		int top = bottom + level.getHeight();

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int cells = 0;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;

		for (int qx = 0; qx < 4; qx++) {
			for (int qz = 0; qz < 4; qz++) {
				for (int y = bottom; y < top; y += 4) {
					pos.set(baseX + qx * 4 + 2, y + 2, baseZ + qz * 4 + 2);
					if (level.getBiome(pos).is(Biomes.LUSH_CAVES)) {
						cells++;
						if (y < minY) {
							minY = y;
						}
						if (y + 4 > maxY) {
							maxY = y + 4;
						}
					}
				}
			}
		}

		long k = key(cx, cz);
		scanned.add(k);
		if (cells >= config.minLushCells) {
			lush.put(k, new ChunkInfo(cells, minY, maxY));
		} else {
			lush.remove(k);
		}
	}
}
