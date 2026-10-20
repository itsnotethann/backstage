package net.honormc.backstage.net;

import net.honormc.backstage.Backstage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record UndoPayload() implements CustomPacketPayload {
    public static final UndoPayload INSTANCE = new UndoPayload();

    public static final Type<UndoPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Backstage.MOD_ID, "undo"));

    public static final StreamCodec<FriendlyByteBuf, UndoPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
