package com.github.smashyalts.udpproxy.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class HAProxyUtilTest {

    @Test
    void testEncodeAndDecodeIPv4() {
        InetSocketAddress source = new InetSocketAddress("192.168.1.100", 12345);
        InetSocketAddress dest = new InetSocketAddress("10.0.0.1", 19132);

        ByteBuf encoded = HAProxyUtil.encodeV2Header(source, dest);
        assertNotNull(encoded);
        assertTrue(encoded.readableBytes() > HAProxyUtil.MIN_HEADER_LENGTH);

        HAProxyUtil.ProxyProtocolResult result = HAProxyUtil.decodeV2Header(encoded);
        assertNotNull(result);
        assertEquals(source, result.getSourceAddress());
        assertEquals(dest, result.getDestAddress());
        assertEquals(0, result.getPayload().readableBytes());

        encoded.release();
    }

    @Test
    void testEncodeAndDecodeIPv6() {
        InetSocketAddress source = new InetSocketAddress("::1", 54321);
        InetSocketAddress dest = new InetSocketAddress("::1", 25565);

        ByteBuf encoded = HAProxyUtil.encodeV2Header(source, dest);
        assertNotNull(encoded);

        HAProxyUtil.ProxyProtocolResult result = HAProxyUtil.decodeV2Header(encoded);
        assertNotNull(result);
        assertEquals(source.getPort(), result.getSourceAddress().getPort());
        assertEquals(dest.getPort(), result.getDestAddress().getPort());

        encoded.release();
    }

    @Test
    void testEncodeWithPayloadAndDecode() {
        InetSocketAddress source = new InetSocketAddress("192.168.1.100", 12345);
        InetSocketAddress dest = new InetSocketAddress("10.0.0.1", 19132);

        ByteBuf header = HAProxyUtil.encodeV2Header(source, dest);
        byte[] testPayload = "Hello, World!".getBytes();
        ByteBuf combined = Unpooled.wrappedBuffer(header, Unpooled.wrappedBuffer(testPayload));

        HAProxyUtil.ProxyProtocolResult result = HAProxyUtil.decodeV2Header(combined);
        assertNotNull(result);
        assertEquals(source, result.getSourceAddress());

        ByteBuf payload = result.getPayload();
        assertEquals(testPayload.length, payload.readableBytes());
        byte[] readPayload = new byte[payload.readableBytes()];
        payload.readBytes(readPayload);
        assertArrayEquals(testPayload, readPayload);

        combined.release();
    }

    @Test
    void testDecodeInvalidData() {
        ByteBuf invalidData = Unpooled.wrappedBuffer("Not a proxy header".getBytes());
        HAProxyUtil.ProxyProtocolResult result = HAProxyUtil.decodeV2Header(invalidData);
        assertNull(result);
        // Reader index should be reset
        assertEquals(0, invalidData.readerIndex());
        invalidData.release();
    }

    @Test
    void testDecodeTooShort() {
        ByteBuf shortData = Unpooled.wrappedBuffer(new byte[]{0x0D, 0x0A});
        HAProxyUtil.ProxyProtocolResult result = HAProxyUtil.decodeV2Header(shortData);
        assertNull(result);
        shortData.release();
    }

    @Test
    void testIsProxyProtocolV2() {
        InetSocketAddress source = new InetSocketAddress("192.168.1.1", 1234);
        InetSocketAddress dest = new InetSocketAddress("10.0.0.1", 5678);

        ByteBuf header = HAProxyUtil.encodeV2Header(source, dest);
        assertTrue(HAProxyUtil.isProxyProtocolV2(header));
        header.release();

        ByteBuf notProxy = Unpooled.wrappedBuffer("not proxy".getBytes());
        assertFalse(HAProxyUtil.isProxyProtocolV2(notProxy));
        notProxy.release();
    }

    @Test
    void testIsProxyProtocolV2EmptyBuffer() {
        ByteBuf empty = Unpooled.EMPTY_BUFFER;
        assertFalse(HAProxyUtil.isProxyProtocolV2(empty));
    }

    @Test
    void testSignatureBytes() {
        assertEquals(12, HAProxyUtil.SIGNATURE.length);
        assertEquals(16, HAProxyUtil.MIN_HEADER_LENGTH);
    }

    @Test
    void testMultipleEncodeDecodeRoundTrips() {
        // Ensure no state leaks between encode/decode operations
        for (int i = 0; i < 10; i++) {
            InetSocketAddress source = new InetSocketAddress("10.0.0." + (i + 1), 10000 + i);
            InetSocketAddress dest = new InetSocketAddress("172.16.0.1", 19132);

            ByteBuf encoded = HAProxyUtil.encodeV2Header(source, dest);
            HAProxyUtil.ProxyProtocolResult result = HAProxyUtil.decodeV2Header(encoded);

            assertNotNull(result, "Round trip " + i + " failed");
            assertEquals(source, result.getSourceAddress());
            assertEquals(dest, result.getDestAddress());

            encoded.release();
        }
    }
}
