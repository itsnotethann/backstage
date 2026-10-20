package net.honormc.backstage.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PrimitivesTest {
    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static void varInt(int value, byte[] expected) {
        WireWriter out = new WireWriter();
        out.writeVarInt(value);
        assertArrayEquals(expected, out.toByteArray());
        WireReader in = new WireReader(expected);
        assertEquals(value, in.readVarInt());
        in.end();
    }

    private static void varLong(long value, byte[] expected) {
        WireWriter out = new WireWriter();
        out.writeVarLong(value);
        assertArrayEquals(expected, out.toByteArray());
        WireReader in = new WireReader(expected);
        assertEquals(value, in.readVarLong());
        in.end();
    }

    @Test
    void varIntBoundaries() {
        varInt(0, bytes(0x00));
        varInt(-1, bytes(0xFF, 0xFF, 0xFF, 0xFF, 0x0F));
        varInt(127, bytes(0x7F));
        varInt(128, bytes(0x80, 0x01));
        varInt(Integer.MAX_VALUE, bytes(0xFF, 0xFF, 0xFF, 0xFF, 0x07));
        varInt(Integer.MIN_VALUE, bytes(0x80, 0x80, 0x80, 0x80, 0x08));
    }

    @Test
    void varLongBoundaries() {
        varLong(0, bytes(0x00));
        varLong(-1, bytes(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x01));
        varLong(127, bytes(0x7F));
        varLong(128, bytes(0x80, 0x01));
        varLong(Integer.MAX_VALUE, bytes(0xFF, 0xFF, 0xFF, 0xFF, 0x07));
        varLong(Integer.MIN_VALUE, bytes(0x80, 0x80, 0x80, 0x80, 0xF8, 0xFF, 0xFF, 0xFF, 0xFF, 0x01));
        varLong(Long.MAX_VALUE, bytes(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x7F));
        varLong(Long.MIN_VALUE, bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x01));
    }

    @Test
    void varIntTooLongRejected() {
        assertThrows(ProtocolException.class, () -> new WireReader(bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x01)).readVarInt());
        assertThrows(ProtocolException.class, () -> new WireReader(bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80,
                0x80, 0x80, 0x80, 0x01)).readVarLong());
        assertThrows(ProtocolException.class, () -> new WireReader(bytes(0x80)).readVarInt());
    }

    @Test
    void fixedWidthIsBigEndian() {
        WireWriter out = new WireWriter();
        out.writeLong(0x0102030405060708L);
        out.writeFloat(1.0f);
        out.writeDouble(-2.0);
        out.writeUuid(new UUID(0x1112131415161718L, 0x2122232425262728L));
        assertArrayEquals(bytes(
                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                0x3F, 0x80, 0x00, 0x00,
                0xC0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
                0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28), out.toByteArray());
        WireReader in = new WireReader(out.toByteArray());
        assertEquals(0x0102030405060708L, in.readLong());
        assertEquals(1.0f, in.readFloat());
        assertEquals(-2.0, in.readDouble());
        assertEquals(new UUID(0x1112131415161718L, 0x2122232425262728L), in.readUuid());
        in.end();
    }

    @Test
    void boolIsStrict() {
        WireReader in = new WireReader(bytes(0, 1, 2));
        assertEquals(false, in.readBool());
        assertEquals(true, in.readBool());
        assertThrows(ProtocolException.class, in::readBool);
    }

    @Test
    void stringIsUtf8ByteLength() {
        WireWriter out = new WireWriter();
        out.writeString("é", EditorProtocol.MAX_STRING_BYTES);
        assertArrayEquals(bytes(0x02, 0xC3, 0xA9), out.toByteArray());
        out = new WireWriter();
        out.writeString(Samples.repeat('a', EditorProtocol.MAX_STRING_BYTES), EditorProtocol.MAX_STRING_BYTES);
        WireReader in = new WireReader(out.toByteArray());
        assertEquals(EditorProtocol.MAX_STRING_BYTES, in.readString(EditorProtocol.MAX_STRING_BYTES).length());
        in.end();
    }

    @Test
    void stringLimitsRejected() {
        assertThrows(ProtocolException.class, () -> new WireWriter()
                .writeString(Samples.repeat('a', EditorProtocol.MAX_STRING_BYTES + 1), EditorProtocol.MAX_STRING_BYTES));
        assertThrows(ProtocolException.class, () -> new WireWriter()
                .writeString(Samples.repeat('é', 16_384), EditorProtocol.MAX_STRING_BYTES));
        assertThrows(ProtocolException.class, () -> new WireWriter().writeString("\ud800", EditorProtocol.MAX_STRING_BYTES));
        assertThrows(ProtocolException.class, () -> new WireReader(bytes(0x02, 0xC3, 0x28)).readString(100));
        assertThrows(ProtocolException.class, () -> new WireReader(bytes(0x05, 'a', 'b')).readString(100));
        assertThrows(ProtocolException.class, () -> new WireReader(bytes(0xFF, 0xFF, 0xFF, 0xFF, 0x0F)).readString(100));
    }

    @Test
    void trailingBytesRejected() {
        WireReader in = new WireReader(bytes(0x01, 0x02));
        in.readByte();
        assertThrows(ProtocolException.class, in::end);
    }
}
