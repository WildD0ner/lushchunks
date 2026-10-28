package dev.lushchunks;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class LushChunksClient implements ClientModInitializer {

	public static final String MOD_ID = "lushchunks";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private LushConfig config;
	private LushScanner scanner;
	private LushOverlay overlay;
	private KeyMapping toggleKey;

	@Override
	public void onInitializeClient() {
		config = LushConfig.load();
		scanner = new LushScanner(config);
		overlay = new LushOverlay(config, scanner);

		// Taste L (aenderbar unter Steuerung -> Sonstiges)
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key." + MOD_ID + ".toggle",
				InputConstants.KEY_L,
				KeyMapping.Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

		// Overlay: Gizmos werden direkt vor dem Gizmo-Rendering von Vanilla eingereicht.
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> overlay.render());

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommands.literal("lushchunks")
						.executes(ctx -> {
							say(ctx.getSource().getClient(), statusLine());
							return 1;
						})
						.then(ClientCommands.literal("toggle").executes(ctx -> {
							toggle(ctx.getSource().getClient());
							return 1;
						}))
						.then(ClientCommands.literal("rescan").executes(ctx -> {
							scanner.clear();
							say(ctx.getSource().getClient(), "Lush-Scan wird neu gestartet.");
							return 1;
						}))
						.then(ClientCommands.literal("nearest").executes(ctx -> {
							nearest(ctx.getSource().getClient());
							return 1;
						}))
						.then(ClientCommands.literal("ontop").executes(ctx -> {
							config.alwaysOnTop = !config.alwaysOnTop;
							config.save();
							say(ctx.getSource().getClient(),
									"Durch Waende sichtbar: " + (config.alwaysOnTop ? "an" : "aus"));
							return 1;
						}))
						.then(ClientCommands.literal("style")
								.then(ClientCommands.literal("box").executes(ctx -> setStyle(ctx.getSource().getClient(), LushConfig.Style.BOX)))
								.then(ClientCommands.literal("plane").executes(ctx -> setStyle(ctx.getSource().getClient(), LushConfig.Style.PLANE)))
								.then(ClientCommands.literal("both").executes(ctx -> setStyle(ctx.getSource().getClient(), LushConfig.Style.BOTH))))
						.then(ClientCommands.literal("radius")
								.then(ClientCommands.argument("chunks", IntegerArgumentType.integer(1, 32))
										.executes(ctx -> {
											config.overlayRadius = IntegerArgumentType.getInteger(ctx, "chunks");
											if (config.scanRadius < config.overlayRadius) {
												config.scanRadius = config.overlayRadius;
											}
											config.clamp();
											config.save();
											say(ctx.getSource().getClient(),
													"Overlay-Radius: " + config.overlayRadius + " Chunks (Scan: " + config.scanRadius + ")");
											return 1;
										})))));

		LOGGER.info("Lush Chunks geladen.");
	}

	private void onClientTick(Minecraft client) {
		while (toggleKey.consumeClick()) {
			toggle(client);
		}
		scanner.tick(client);
	}

	private void toggle(Minecraft client) {
		config.enabled = !config.enabled;
		config.save();
		if (!config.enabled) {
			scanner.clear();
		}
		say(client, "Lush-Chunk-Overlay: " + (config.enabled ? "an" : "aus"));
	}

	private int setStyle(Minecraft client, LushConfig.Style style) {
		config.style = style;
		config.save();
		say(client, "Overlay-Stil: " + style.name().toLowerCase());
		return 1;
	}

	private void nearest(Minecraft client) {
		if (client.player == null) {
			return;
		}
		int pcx = Mth.floor(client.player.getX()) >> 4;
		int pcz = Mth.floor(client.player.getZ()) >> 4;

		long best = 0;
		LushScanner.ChunkInfo bestInfo = null;
		long bestDist = Long.MAX_VALUE;
		for (Map.Entry<Long, LushScanner.ChunkInfo> e : scanner.lushChunks().entrySet()) {
			long dx = LushScanner.keyX(e.getKey()) - pcx;
			long dz = LushScanner.keyZ(e.getKey()) - pcz;
			long d = dx * dx + dz * dz;
			if (d < bestDist) {
				bestDist = d;
				best = e.getKey();
				bestInfo = e.getValue();
			}
		}

		if (bestInfo == null) {
			say(client, "Kein Lush-Cave-Chunk im Scan-Radius gefunden (" + scanner.scannedCount() + " Chunks gescannt).");
			return;
		}
		int cx = LushScanner.keyX(best);
		int cz = LushScanner.keyZ(best);
		say(client, String.format("Naechster Lush-Chunk: Chunk [%d, %d], Mitte ca. X %d / Z %d, Y %d bis %d, %d Chunks entfernt.",
				cx, cz, (cx << 4) + 8, (cz << 4) + 8, bestInfo.minY(), bestInfo.maxY(), (int) Math.round(Math.sqrt(bestDist))));
	}

	private String statusLine() {
		return String.format("Lush Chunks: %s | Lush-Chunks: %d | gescannt: %d | Stil: %s | Overlay-Radius: %d | Scan-Radius: %d",
				config.enabled ? "an" : "aus",
				scanner.lushChunks().size(),
				scanner.scannedCount(),
				config.style.name().toLowerCase(),
				config.overlayRadius,
				config.scanRadius);
	}

	private static void say(Minecraft client, String text) {
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal("[LushChunks] " + text));
		}
	}
}
