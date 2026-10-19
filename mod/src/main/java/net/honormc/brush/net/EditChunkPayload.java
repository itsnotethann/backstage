package net.honormc.brush.net;

import net.honormc.brush.HonorBrush;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One slice of an edit. Serverbound custom payloads cap at 32 KiB, so the
 * client splits an edit into chunks of {@link #MAX_ENTRIES} that share an
 * {@code op} id; the server groups them into one undo step.
 *
 * @param op        client-chosen id shared by every chunk of one edit
 * @param positions {@code BlockPos.asLong()} per entry
 * @param states    {@code Block.getId(state)} per entry
 * @param last      true on the final chunk, so the server can report totals
 */
public record EditChunkPayload(int op, long[] positions, int[] states, boolean last) implements CustomPacketPayload {

    /** 8 bytes per position + up to 3 per varint state, comfortably under 32 KiB. */
    public static final int MAX_ENTRIES = 2000;

    public static final Type<EditChunkPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(HonorBrush.MOD_ID, "edit_chunk"));

    public static final StreamCodec<FriendlyByteBuf, EditChunkPayload> CODEC =
            CustomPacketPayload.codec(EditChunkPayload::write, EditChunkPayload::read);

    private static EditChunkPayload read(FriendlyByteBuf buf) {
        int op = buf.readVarInt();
        long[] positions = buf.readLongArray();
        int[] states = buf.readVarIntArray(MAX_ENTRIES);
        boolean last = buf.readBoolean();
        if (positions.length != states.length || positions.length > MAX_ENTRIES) {
            throw new IllegalArgumentException("malformed edit chunk");
        }
        return new EditChunkPayload(op, positions, states, last);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(op);
        buf.writeLongArray(positions);
        buf.writeVarIntArray(states);
        buf.writeBoolean(last);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
