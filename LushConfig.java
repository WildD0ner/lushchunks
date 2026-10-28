package dev.lushchunks;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Einfache Properties-Konfiguration (config/lushchunks.properties).
 * Farben sind ARGB-Hexwerte, z. B. FF7CFC00.
 */
public final class LushConfig {

	public enum Style {
		/** Nur Kasten-Umriss ueber die Hoehe des Lush-Bioms. */
		BOX,
		/** Nur gefuellte Flaeche auf Spielerhoehe. */
		PLANE,
		/** Beides. */
		BOTH
	}

	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("lushchunks.properties");

	public boolean enabled = true;
	/** Radius (in Chunks) um den Spieler, in dem gescannt wird. */
	public int scanRadius = 16;
	/** Radius (in Chunks), in dem das Overlay gezeichnet wird. */
	public int overlayRadius = 12;
	/** Mindestanzahl Biom-Zellen (je 4x4x4 Bloecke) mit Lush Caves, damit ein Chunk zaehlt. */
	public int minLushCells = 3;
	/** Wie viele Chunks pro Tick hoechstens gescannt werden. */
	public int maxScansPerTick = 6;
	public Style style = Style.BOTH;
	/** Overlay auch durch Waende sichtbar. */
	public boolean alwaysOnTop = true;
	public float strokeWidth = 2.0f;
	public int strokeColor = 0xFF7CFC00;
	public int fillColor = 0x3355FF77;

	public static LushConfig load() {
		LushConfig cfg = new LushConfig();
		if (Files.exists(FILE)) {
			Properties p = new Properties();
			try (Reader in = Files.newBufferedReader(FILE)) {
				p.load(in);
				cfg.enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
				cfg.scanRadius = parseInt(p, "scanRadius", cfg.scanRadius);
				cfg.overlayRadius = parseInt(p, "overlayRadius", cfg.overlayRadius);
				cfg.minLushCells = parseInt(p, "minLushCells", cfg.minLushCells);
				cfg.maxScansPerTick = parseInt(p, "maxScansPerTick", cfg.maxScansPerTick);
				cfg.alwaysOnTop = Boolean.parseBoolean(p.getProperty("alwaysOnTop", "true"));
				cfg.strokeWidth = parseFloat(p, "strokeWidth", cfg.strokeWidth);
				cfg.strokeColor = parseColor(p, "strokeColor", cfg.strokeColor);
				cfg.fillColor = parseColor(p, "fillColor", cfg.fillColor);
				try {
					cfg.style = Style.valueOf(p.getProperty("style", "BOTH").trim().toUpperCase(Locale.ROOT));
				} catch (IllegalArgumentException ignored) {
					cfg.style = Style.BOTH;
				}
			} catch (IOException e) {
				LushChunksClient.LOGGER.warn("Konfiguration konnte nicht gelesen werden, nutze Standardwerte", e);
			}
		}
		cfg.clamp();
		cfg.save();
		return cfg;
	}

	public void clamp() {
		scanRadius = Math.max(2, Math.min(32, scanRadius));
		overlayRadius = Math.max(1, Math.min(scanRadius, overlayRadius));
		minLushCells = Math.max(1, Math.min(256, minLushCells));
		maxScansPerTick = Math.max(1, Math.min(64, maxScansPerTick));
		strokeWidth = Math.max(1.0f, Math.min(8.0f, strokeWidth));
	}

	public void save() {
		Properties p = new Properties();
		p.setProperty("enabled", Boolean.toString(enabled));
		p.setProperty("scanRadius", Integer.toString(scanRadius));
		p.setProperty("overlayRadius", Integer.toString(overlayRadius));
		p.setProperty("minLushCells", Integer.toString(minLushCells));
		p.setProperty("maxScansPerTick", Integer.toString(maxScansPerTick));
		p.setProperty("style", style.name());
		p.setProperty("alwaysOnTop", Boolean.toString(alwaysOnTop));
		p.setProperty("strokeWidth", Float.toString(strokeWidth));
		p.setProperty("strokeColor", String.format("%08X", strokeColor));
		p.setProperty("fillColor", String.format("%08X", fillColor));
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer out = Files.newBufferedWriter(FILE)) {
				p.store(out, "Lush Chunks - Farben als ARGB-Hex (AARRGGBB)");
			}
		} catch (IOException e) {
			LushChunksClient.LOGGER.warn("Konfiguration konnte nicht gespeichert werden", e);
		}
	}

	private static int parseInt(Properties p, String key, int def) {
		try {
			return Integer.parseInt(p.getProperty(key, Integer.toString(def)).trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static float parseFloat(Properties p, String key, float def) {
		try {
			return Float.parseFloat(p.getProperty(key, Float.toString(def)).trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static int parseColor(Properties p, String key, int def) {
		try {
			String s = p.getProperty(key, "").trim();
			if (s.startsWith("#")) {
				s = s.substring(1);
			}
			if (s.isEmpty()) {
				return def;
			}
			return (int) Long.parseLong(s, 16);
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
