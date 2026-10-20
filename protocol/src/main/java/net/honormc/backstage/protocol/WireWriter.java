package net.honormc.backstage.protocol;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

final class WireWriter {
    private byte[] buffer = new byte[64];
    private int size;

    void writeByte(int value) {
        ensure(1);
        buffer[size++] = (byte) value;
    }

    void writeVarInt(int value) {
        while ((value & ~0x7F) != 0) {
            writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        writeByte(value);
    }

    void writeVarLong(long value) {
        while ((value & ~0x7FL) != 0) {
            writeByte((int) (value & 0x7F) | 0x80);
            value >>>= 7;
        }
        writeByte((int) value);
    }

    void writeBool(boolean value) {
        writeByte(value ? 1 : 0);
    }

    void writeInt(int value) {
        ensure(4);
        for (int shift = 24; shift >= 0; shift -= 8) {
            buffer[size++] = (byte) (value >>> shift);
        }
    }

    void writeLong(long value) {
        ensure(8);
        for (int shift = 56; shift >= 0; shift -= 8) {
            buffer[size++] = (byte) (value >>> shift);
        }
    }

    void writeFloat(float value) {
        writeInt(Float.floatToRawIntBits(value));
    }

    void writeDouble(double value) {
        writeLong(Double.doubleToRawLongBits(value));
    }

    void writeUuid(UUID value) {
        writeLong(value.getMostSignificantBits());
        writeLong(value.getLeastSignificantBits());
    }

    void writeString(String value, int maxBytes) {
        byte[] bytes;
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(value));
            bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
        } catch (CharacterCodingException e) {
            throw new ProtocolException("string is not valid unicode");
        }
        if (bytes.length > maxBytes) {
            throw new ProtocolException("string of " + bytes.length + " bytes exceeds " + maxBytes);
        }
        writeVarInt(bytes.length);
        ensure(bytes.length);
        System.arraycopy(bytes, 0, buffer, size, bytes.length);
        size += bytes.length;
    }

    void writeCount(int count, int max) {
        if (count > max) {
            throw new ProtocolException("list of " + count + " entries exceeds " + max);
        }
        writeVarInt(count);
    }

    int size() {
        return size;
    }

    byte[] toByteArray() {
        return Arrays.copyOf(buffer, size);
    }

    private void ensure(int extra) {
        if (size + extra > buffer.length) {
            buffer = Arrays.copyOf(buffer, Math.max(buffer.length * 2, size + extra));
        }
    }
}
