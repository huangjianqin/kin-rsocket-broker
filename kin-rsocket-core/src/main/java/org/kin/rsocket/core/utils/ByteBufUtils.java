package org.kin.rsocket.core.utils;

import io.netty.buffer.ByteBuf;

/**
 * @author huangjianqin
 * @date 2021/7/31
 */
public final class ByteBufUtils {
    private ByteBufUtils() {
    }

    //------------------------------------------var int/long reader 算法来自于protocolbuf------------------------------------------
    public static int readRawVarInt32(ByteBuf byteBuf) {
        return decodeZigZag32(_readRawVarInt32(byteBuf));
    }

    /**
     * Decode a ZigZag-encoded 32-bit value. ZigZag encodes signed integers into values that can be
     * efficiently encoded with varint. (Otherwise, negative values must be sign-extended to 64 bits
     * to be varint encoded, thus always taking 10 bytes on the wire.)
     *
     * @param n An unsigned 32-bit integer, stored in a signed int because Java has no explicit
     *          unsigned support.
     * @return A signed 32-bit integer.
     */
    private static int decodeZigZag32(int n) {
        return (n >>> 1) ^ -(n & 1);
    }

    /**
     * read 变长 32位int
     */
    private static int _readRawVarInt32(ByteBuf byteBuf) {
        fastpath:
        {
            int readerIndex = byteBuf.readerIndex();

            if (byteBuf.readableBytes() <= 0) {
                break fastpath;
            }

            int x;
            if ((x = byteBuf.readByte()) >= 0) {
                return x;
            } else if (byteBuf.readableBytes() < 9) {
                //reset reader index
                byteBuf.readerIndex(readerIndex);
                break fastpath;
            } else if ((x ^= (byteBuf.readByte() << 7)) < 0) {
                x ^= (~0 << 7);
            } else if ((x ^= (byteBuf.readByte() << 14)) >= 0) {
                x ^= (~0 << 7) ^ (~0 << 14);
            } else if ((x ^= (byteBuf.readByte() << 21)) < 0) {
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
            } else {
                int y = byteBuf.readByte();
                x ^= y << 28;
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
                if (y < 0
                        && byteBuf.readByte() < 0
                        && byteBuf.readByte() < 0
                        && byteBuf.readByte() < 0
                        && byteBuf.readByte() < 0
                        && byteBuf.readByte() < 0) {
                    //reset reader index
                    byteBuf.readerIndex(readerIndex);
                    break fastpath; // Will throw malformedVarint()
                }
            }
            return x;
        }
        return (int) readRawVarint64SlowPath(byteBuf);
    }

    private static long readRawVarint64SlowPath(ByteBuf byteBuf) {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            final byte b = readRawByte(byteBuf);
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new IllegalArgumentException("encountered a malformed varint");
    }

    public static long readRawVarLong64(ByteBuf byteBuf) {
        return decodeZigZag64(_readRawVarLong64(byteBuf));
    }

    /**
     * Decode a ZigZag-encoded 64-bit value. ZigZag encodes signed integers into values that can be
     * efficiently encoded with varint. (Otherwise, negative values must be sign-extended to 64 bits
     * to be varint encoded, thus always taking 10 bytes on the wire.)
     *
     * @param n An unsigned 64-bit integer, stored in a signed int because Java has no explicit
     *          unsigned support.
     * @return A signed 64-bit integer.
     */
    public static long decodeZigZag64(long n) {
        return (n >>> 1) ^ -(n & 1);
    }

    /**
     * read 变长 64位long
     */
    public static long _readRawVarLong64(ByteBuf byteBuf) {
        // Implementation notes:
        //
        // Optimized for one-byte values, expected to be common.
        // The particular code below was selected from various candidates
        // empirically, by winning VarintBenchmark.
        //
        // Sign extension of (signed) Java bytes is usually a nuisance, but
        // we exploit it here to more easily obtain the sign of bytes read.
        // Instead of cleaning up the sign extension bits by masking eagerly,
        // we delay until we find the final (positive) byte, when we clear all
        // accumulated bits with one xor.  We depend on javac to constant fold.
        fastpath:
        {
            int readerIndex = byteBuf.readerIndex();

            if (byteBuf.readableBytes() <= 0) {
                break fastpath;
            }

            long x;
            int y;
            if ((y = byteBuf.readByte()) >= 0) {
                return y;
            } else if (byteBuf.readableBytes() < 9) {
                //reset reader index
                byteBuf.readerIndex(readerIndex);
                break fastpath;
            } else if ((y ^= (byteBuf.readByte() << 7)) < 0) {
                x = y ^ (~0 << 7);
            } else if ((y ^= (byteBuf.readByte() << 14)) >= 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14));
            } else if ((y ^= (byteBuf.readByte() << 21)) < 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
            } else if ((x = y ^ ((long) byteBuf.readByte() << 28)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
            } else if ((x ^= ((long) byteBuf.readByte() << 35)) < 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
            } else if ((x ^= ((long) byteBuf.readByte() << 42)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
            } else if ((x ^= ((long) byteBuf.readByte() << 49)) < 0L) {
                x ^=
                        (~0L << 7)
                                ^ (~0L << 14)
                                ^ (~0L << 21)
                                ^ (~0L << 28)
                                ^ (~0L << 35)
                                ^ (~0L << 42)
                                ^ (~0L << 49);
            } else {
                x ^= ((long) byteBuf.readByte() << 56);
                x ^=
                        (~0L << 7)
                                ^ (~0L << 14)
                                ^ (~0L << 21)
                                ^ (~0L << 28)
                                ^ (~0L << 35)
                                ^ (~0L << 42)
                                ^ (~0L << 49)
                                ^ (~0L << 56);
                if (x < 0L) {
                    if (byteBuf.readByte() < 0L) {
                        //reset reader index
                        byteBuf.readerIndex(readerIndex);
                        break fastpath; // Will throw malformedVarint()
                    }
                }
            }
            return x;
        }
        return readRawVarint64SlowPath(byteBuf);
    }

    private static byte readRawByte(ByteBuf byteBuf) {
        if (byteBuf.readableBytes() <= 0) {
            throw new IllegalStateException("While parsing a protocol, the input ended unexpectedly "
                    + "in the middle of a field.  This could mean either that the "
                    + "input has been truncated or that an embedded message "
                    + "misreported its own length.");
        }
        return byteBuf.readByte();
    }

    //------------------------------------------var int/long writer 算法来自于protocolbuf------------------------------------------
    public static void writeRawVarInt32(ByteBuf byteBuf, int value) {
        _writeRawVarInt32(byteBuf, encodeZigZag32(value));
    }

    /**
     * Encode a ZigZag-encoded 32-bit value. ZigZag encodes signed integers into values that can be
     * efficiently encoded with varint. (Otherwise, negative values must be sign-extended to 64 bits
     * to be varint encoded, thus always taking 10 bytes on the wire.)
     *
     * @param n A signed 32-bit integer.
     * @return An unsigned 32-bit integer, stored in a signed int because Java has no explicit
     * unsigned support.
     */
    private static int encodeZigZag32(int n) {
        // Note:  the right-shift must be arithmetic
        return (n << 1) ^ (n >> 31);
    }

    private static void _writeRawVarInt32(ByteBuf byteBuf, int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                byteBuf.writeByte(value);
                return;
            } else {
                byteBuf.writeByte((value & 0x7F) | 0x80);
                value >>>= 7;
            }
        }
    }

    public static void writeRawVarlong64(ByteBuf byteBuf, long value) {
        _writRawVarLong64(byteBuf, encodeZigZag64(value));
    }

    /**
     * Encode a ZigZag-encoded 64-bit value. ZigZag encodes signed integers into values that can be
     * efficiently encoded with varint. (Otherwise, negative values must be sign-extended to 64 bits
     * to be varint encoded, thus always taking 10 bytes on the wire.)
     *
     * @param n A signed 64-bit integer.
     * @return An unsigned 64-bit integer, stored in a signed int because Java has no explicit
     * unsigned support.
     */
    private static long encodeZigZag64(long n) {
        // Note:  the right-shift must be arithmetic
        return (n << 1) ^ (n >> 63);
    }

    private static void _writRawVarLong64(ByteBuf byteBuf, long value) {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                byteBuf.writeByte((int) value);
                return;
            } else {
                byteBuf.writeByte(((int) value & 0x7F) | 0x80);
                value >>>= 7;
            }
        }
    }

    //-----------------------------------------------------------------------------------------------------------------------------
}
