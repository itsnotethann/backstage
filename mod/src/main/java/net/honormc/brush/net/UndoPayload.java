package net.honormc.brush.net;

import net.honormc.brush.HonorBrush;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Asks the server to revert the sender's most recent edit. */
public record UndoPayload() implements CustomPacketPayload {

    public static final UndoPayload INSTANCE = new UndoPayload();

    public static final Type<UndoPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(HonorBrush.MOD_ID, "undo"));

    public static final StreamCodec<FriendlyByteBuf, UndoPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
