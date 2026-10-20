package net.honormc.backstage;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.honormc.backstage.editor.EditorPayload;
import net.honormc.backstage.net.EditChunkPayload;
import net.honormc.backstage.net.UndoPayload;
import net.honormc.backstage.server.ServerEdits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Backstage implements ModInitializer {
    public static final String MOD_ID = "backstage";
    public static final Logger LOG = LoggerFactory.getLogger("Backstage");

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(EditChunkPayload.TYPE, EditChunkPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UndoPayload.TYPE, UndoPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EditorPayload.TYPE, EditorPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EditorPayload.TYPE, EditorPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(EditChunkPayload.TYPE,
                (payload, ctx) -> ServerEdits.apply(ctx.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(UndoPayload.TYPE,
                (payload, ctx) -> ServerEdits.undo(ctx.player()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ServerEdits.forget(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerEdits.clear());
    }
}
