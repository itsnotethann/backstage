package net.honormc.backstage.editor;

import net.honormc.backstage.protocol.EditorProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record EditorPayload(byte[] body) implements CustomPacketPayload {
    public static final Type<EditorPayload> TYPE = new Type<>(Identifier.parse(EditorProtocol.CHANNEL));

    public static final StreamCodec<FriendlyByteBuf, EditorPayload> CODEC =
            CustomPacketPayload.codec(EditorPayload::write, EditorPayload::read);

    private static EditorPayload read(FriendlyByteBuf buf) {
        byte[] body = new byte[buf.readableBytes()];
        buf.readBytes(body);
        return new EditorPayload(body);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBytes(body);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
