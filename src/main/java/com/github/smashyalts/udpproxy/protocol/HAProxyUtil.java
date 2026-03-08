package com.github.smashyalts.udpproxy.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Utility class for encoding and decoding HAProxy PROXY protocol v2 headers for UDP.
 * <p>
 * The PROXY protocol v2 binary header format:
 * <pre>
 *   Bytes 0-11:  Signature (\x0D\x0A\x0D\x0A\x00\x0D\x0A\x51\x55\x49\x54\x0A)
 *   Byte 12:     Version (upper 4 bits = 0x2) | Command (lower 4 bits: 0x0=LOCAL, 0x1=PROXY)
 *   Byte 13:     Address Family (upper 4 bits) | Transport Protocol (lower 4 bits: 0x2=DGRAM/UDP)
 *   Bytes 14-15: Length of address data (big-endian)
 *   Remaining:   Address data (varies by address family)
 * </pre>
 * <p>
 * This follows the same approach used by Geyser for handling PROXY protocol
 * with UDP (RakNet) connections.
 */
public final class HAProxyUtil {

    /** PROXY protocol v2 signature (12 bytes). */
    public static final byte[] SIGNATURE = new byte[]{
            0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
    };

    /** Minimum header size: 12 (signature) + 4 (version/command/family/length) = 16 bytes. */
    public static final int MIN_HEADER_LENGTH = 16;

    private static final byte VERSION_COMMAND_PROXY = 0x21; // Version 2, PROXY command
    private static final byte FAMILY_IPV4_UDP = 0x12; // AF_INET, DGRAM
    private static final byte FAMILY_IPV6_UDP = 0x22; // AF_INET6, DGRAM

    private HAProxyUtil() {
    }

    /**
     * Encode a PROXY protocol v2 header for a UDP connection.
     *
     * @param sourceAddress the real client address
     * @param destAddress   the destination (proxy listen) address
     * @return a ByteBuf containing the PROXY protocol v2 header
     */
    public static ByteBuf encodeV2Header(InetSocketAddress sourceAddress, InetSocketAddress destAddress) {
        InetAddress srcAddr = sourceAddress.getAddress();
        InetAddress dstAddr = destAddress.getAddress();

        boolean isIPv4 = srcAddr instanceof Inet4Address && dstAddr instanceof Inet4Address;
        boolean isIPv6 = srcAddr instanceof Inet6Address || dstAddr instanceof Inet6Address;

        byte familyByte;
        int addressLen;

        if (isIPv6) {
            familyByte = FAMILY_IPV6_UDP;
            addressLen = 36; // 16 + 16 + 2 + 2
        } else if (isIPv4) {
            familyByte = FAMILY_IPV4_UDP;
            addressLen = 12; // 4 + 4 + 2 + 2
        } else {
            throw new IllegalArgumentException("Unsupported address type: " + srcAddr.getClass().getName());
        }

        ByteBuf buf = Unpooled.buffer(MIN_HEADER_LENGTH + addressLen);

        // Signature
        buf.writeBytes(SIGNATURE);

        // Version and command
        buf.writeByte(VERSION_COMMAND_PROXY);

        // Address family and transport
        buf.writeByte(familyByte);

        // Length of address data
        buf.writeShort(addressLen);

        if (isIPv6) {
            // Convert IPv4 to IPv4-mapped IPv6 if needed
            byte[] srcBytes = toIPv6Bytes(srcAddr);
            byte[] dstBytes = toIPv6Bytes(dstAddr);
            buf.writeBytes(srcBytes);
            buf.writeBytes(dstBytes);
        } else {
            buf.writeBytes(srcAddr.getAddress());
            buf.writeBytes(dstAddr.getAddress());
        }

        buf.writeShort(sourceAddress.getPort());
        buf.writeShort(destAddress.getPort());

        return buf;
    }

    /**
     * Attempt to decode a PROXY protocol v2 header from the beginning of a datagram.
     * Returns null if the data does not contain a valid PROXY protocol v2 header.
     *
     * @param data the datagram payload
     * @return decoded result containing addresses and remaining payload, or null if not a PROXY protocol header
     */
    public static ProxyProtocolResult decodeV2Header(ByteBuf data) {
        if (data.readableBytes() < MIN_HEADER_LENGTH) {
            return null;
        }

        int readerIndex = data.readerIndex();

        // Check signature
        for (byte b : SIGNATURE) {
            if (data.readByte() != b) {
                data.readerIndex(readerIndex);
                return null;
            }
        }

        byte versionCommand = data.readByte();
        if (versionCommand != VERSION_COMMAND_PROXY) {
            data.readerIndex(readerIndex);
            return null;
        }

        byte familyTransport = data.readByte();
        int addressLen = data.readUnsignedShort();

        if (data.readableBytes() < addressLen) {
            data.readerIndex(readerIndex);
            return null;
        }

        InetSocketAddress sourceAddress;
        InetSocketAddress destAddress;

        try {
            if (familyTransport == FAMILY_IPV4_UDP) {
                byte[] srcAddr = new byte[4];
                byte[] dstAddr = new byte[4];
                data.readBytes(srcAddr);
                data.readBytes(dstAddr);
                int srcPort = data.readUnsignedShort();
                int dstPort = data.readUnsignedShort();
                sourceAddress = new InetSocketAddress(InetAddress.getByAddress(srcAddr), srcPort);
                destAddress = new InetSocketAddress(InetAddress.getByAddress(dstAddr), dstPort);
            } else if (familyTransport == FAMILY_IPV6_UDP) {
                byte[] srcAddr = new byte[16];
                byte[] dstAddr = new byte[16];
                data.readBytes(srcAddr);
                data.readBytes(dstAddr);
                int srcPort = data.readUnsignedShort();
                int dstPort = data.readUnsignedShort();
                sourceAddress = new InetSocketAddress(InetAddress.getByAddress(srcAddr), srcPort);
                destAddress = new InetSocketAddress(InetAddress.getByAddress(dstAddr), dstPort);
            } else {
                // Skip unknown address data
                data.skipBytes(addressLen);
                sourceAddress = null;
                destAddress = null;
            }
        } catch (Exception e) {
            data.readerIndex(readerIndex);
            return null;
        }

        // Remaining data after the header is the actual payload
        ByteBuf payload = data.slice();

        return new ProxyProtocolResult(sourceAddress, destAddress, payload);
    }

    /**
     * Check if a ByteBuf starts with the PROXY protocol v2 signature.
     */
    public static boolean isProxyProtocolV2(ByteBuf data) {
        if (data.readableBytes() < SIGNATURE.length) {
            return false;
        }
        int readerIndex = data.readerIndex();
        for (byte b : SIGNATURE) {
            if (data.getByte(readerIndex++) != b) {
                return false;
            }
        }
        return true;
    }

    private static byte[] toIPv6Bytes(InetAddress addr) {
        if (addr instanceof Inet6Address) {
            return addr.getAddress();
        }
        // Map IPv4 to IPv6
        byte[] ipv4 = addr.getAddress();
        byte[] ipv6 = new byte[16];
        ipv6[10] = (byte) 0xFF;
        ipv6[11] = (byte) 0xFF;
        System.arraycopy(ipv4, 0, ipv6, 12, 4);
        return ipv6;
    }

    /**
     * Result of decoding a PROXY protocol v2 header.
     */
    public static class ProxyProtocolResult {
        private final InetSocketAddress sourceAddress;
        private final InetSocketAddress destAddress;
        private final ByteBuf payload;

        public ProxyProtocolResult(InetSocketAddress sourceAddress, InetSocketAddress destAddress, ByteBuf payload) {
            this.sourceAddress = sourceAddress;
            this.destAddress = destAddress;
            this.payload = payload;
        }

        public InetSocketAddress getSourceAddress() {
            return sourceAddress;
        }

        public InetSocketAddress getDestAddress() {
            return destAddress;
        }

        public ByteBuf getPayload() {
            return payload;
        }
    }
}
