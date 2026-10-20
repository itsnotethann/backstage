package net.honormc.backstage.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class WireReader {
    private final byte[] data;
    private int position;

    WireReader(byte[] data) {
        this.data = data;
    }

    int readByte() {
        need(1);
        return data[position++] & 0xFF;
    }

    int readVarInt() {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            int b = readByte();
            value |= (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new ProtocolException("varint longer than 5 bytes");
    }

    long readVarLong() {
        long value = 0;
        for (int i = 0; i < 10; i++) {
            int b = readByte();
            value |= (long) (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new ProtocolException("varlong longer than 10 bytes");
    }

    boolean readBool() {
        int b = readByte();
        if (b > 1) {
            throw new ProtocolException("bool byte " + b + " is not 0 or 1");
        }
        return b == 1;
    }

    int readInt() {
        need(4);
        int value = 0;
        for (int i = 0; i < 4; i++) {
            value = (value << 8) | (data[position++] & 0xFF);
        }
        return value;
    }

    long readLong() {
        need(8);
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (data[position++] & 0xFF);
        }
        return value;
    }

    float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    UUID readUuid() {
        long most = readLong();
        long least = readLong();
        return new UUID(most, least);
    }

    String readString(int maxBytes) {
        int length = readVarInt();
        if (length < 0 || length > maxBytes) {
            throw new ProtocolException("string of " + length + " bytes exceeds " + maxBytes);
        }
        need(length);
        try {
            String value = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data, position, length)).toString();
            position += length;
            return value;
        } catch (CharacterCodingException e) {
            throw new ProtocolException("string is not valid UTF-8");
        }
    }

    int readCount(int max) {
        int count = readVarInt();
        if (count < 0 || count > max) {
            throw new ProtocolException("list of " + count + " entries exceeds " + max);
        }
        return count;
    }

    void end() {
        if (position != data.length) {
            throw new ProtocolException((data.length - position) + " trailing bytes");
        }
    }

    private void need(int bytes) {
        if (bytes > data.length - position) {
            throw new ProtocolException("body ends early");
        }
    }
}
