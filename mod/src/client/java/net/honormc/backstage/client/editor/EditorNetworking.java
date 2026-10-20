package net.honormc.backstage.client.editor;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ServerboundPlayChannelEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.honormc.backstage.Backstage;
import net.honormc.backstage.editor.EditorPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class EditorNetworking {
    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId();

    private EditorNetworking() {
    }

    public static EditorClient install() {
        String version = FabricLoader.getInstance().getModContainer(Backstage.MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        EditorClient client = new EditorClient(new EditorClient.Output() {
            @Override
            public void send(byte[] body) {
                ClientPlayNetworking.send(new EditorPayload(body));
            }

            @Override
            public void toast(String title, String message) {
                Minecraft mc = Minecraft.getInstance();
                SystemToast.add(mc.gui.toastManager(), TOAST, Component.literal(title), Component.literal(message));
            }

            @Override
            public void chat(String line) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal(line));
                }
            }
        }, version);

        ServerboundPlayChannelEvents.REGISTER.register((listener, sender, mc, channels) -> {
            var names = channels.stream().map(Identifier::toString).toList();
            mc.execute(() -> client.channelsRegistered(names));
        });
        ClientPlayNetworking.registerGlobalReceiver(EditorPayload.TYPE, (payload, context) -> client.receive(payload.body()));
        ClientPlayConnectionEvents.DISCONNECT.register((listener, mc) -> client.disconnected());
        return client;
    }
}
