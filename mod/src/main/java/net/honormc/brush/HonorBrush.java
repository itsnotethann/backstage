package net.honormc.brush;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.honormc.brush.net.EditChunkPayload;
import net.honormc.brush.net.UndoPayload;
import net.honormc.brush.server.ServerEdits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint. Runs on the dedicated server and on the singleplayer
 * integrated server: the client computes edits from Lua and ships them here,
 * the server checks the player may build and applies them with undo.
 */
public final class HonorBrush implements ModInitializer {

    public static final String MOD_ID = "honorbrush";
    public static final Logger LOG = LoggerFactory.getLogger("HonorBrush");

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(EditChunkPayload.TYPE, EditChunkPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UndoPayload.TYPE, UndoPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(EditChunkPayload.TYPE,
                (payload, ctx) -> ServerEdits.apply(ctx.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(UndoPayload.TYPE,
                (payload, ctx) -> ServerEdits.undo(ctx.player()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ServerEdits.forget(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerEdits.clear());
    }
}
