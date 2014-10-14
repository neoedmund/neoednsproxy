package neoe.dns.format;

import java.io.UnsupportedEncodingException;
import java.net.SocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import neoe.dns.Log;

/**
 * Parsed DNS message
 *
 * @author Alexey Borzenkov
 *
 */
public final class DNSMessage {

    private static final short RESPONSE_MASK = (short) 0x8000;
    private static final short OPCODE_MASK = (short) 0x7800;
    private static final int OPCODE_SHIFT = 11;
    private static final short AA_MASK = (short) 0x0400;
    private static final short TC_MASK = (short) 0x0200;
    private static final short RD_MASK = (short) 0x0100;
    private static final short RA_MASK = (short) 0x0080;
    private static final short RCODE_MASK = (short) 0x000f;

    private final short id;
    private final short flags;
    private final DNSQuestion[] questions;
    private final DNSResourceRecord[] answers;
    private final DNSResourceRecord[] nameservers;
    private final DNSResourceRecord[] additionalrecords;
    public byte[] bs;
    public SocketAddress client;
    public String host;

    public DNSMessage(short id, short flags, short qdcount, short ancount,
            short nscount, short arcount) {
        this.id = id;
        this.flags = flags;
        this.questions = new DNSQuestion[qdcount];
        this.answers = new DNSResourceRecord[ancount];
        this.nameservers = new DNSResourceRecord[nscount];
        this.additionalrecords = new DNSResourceRecord[arcount];
    }

    public final short getId() {
        return id;
    }

    public final short getFlags() {
        return flags;
    }

    public final boolean isResponse() {
        return (flags & RESPONSE_MASK) != 0;
    }

    public final int getOpcode() {
        return (flags & OPCODE_MASK) >> OPCODE_SHIFT;
    }

    public final boolean isAuthoritativeAnswer() {
        return (flags & AA_MASK) != 0;
    }

    public final boolean isTruncated() {
        return (flags & TC_MASK) != 0;
    }

    public final boolean isRecursionDesired() {
        return (flags & RD_MASK) != 0;
    }

    public final boolean isRecursionAvailable() {
        return (flags & RA_MASK) != 0;
    }

    public final int getRcode() {
        return (flags & RCODE_MASK);
    }

    public final DNSQuestion[] getQuestions() {
        return questions;
    }

    public final DNSResourceRecord[] getAnswers() {
        return answers;
    }

    public final DNSResourceRecord[] getNameServers() {
        return nameservers;
    }

    public final DNSResourceRecord[] getAdditionalRecords() {
        return additionalrecords;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("Message { id: ");
        builder.append(id);
        builder.append(", flags: ");
        builder.append(flags);
        if (questions.length > 0) {
            builder.append(", questions: { ");
            for (int i = 0; i < questions.length; ++i) {
                if (i != 0) {
                    builder.append(", ");
                }
                if (questions[i] != null) {
                    builder.append(questions[i].toString());
                } else {
                    builder.append("null");
                }
            }
            builder.append(" }");
        }
        if (answers.length > 0) {
            builder.append("}, answers: { ");
            for (int i = 0; i < answers.length; ++i) {
                if (i != 0) {
                    builder.append(", ");
                }
                if (answers[i] != null) {
                    builder.append(answers[i].toString());
                } else {
                    builder.append("null");
                }
            }
            builder.append(" }");
        }
        builder.append(" }");
        return builder.toString();
    }

    public String toIdString() {
        final StringBuilder builder = new StringBuilder();
        if (questions.length > 0) {
            builder.append("[");
            for (int i = 0; i < questions.length; ++i) {
                if (i != 0) {
                    builder.append(",");
                }
                if (questions[i] != null) {
                    builder.append(questions[i].toIdString());
                } else {
                    builder.append("null");
                }
            }
            builder.append("]");
        }
        return builder.toString();
    }

    private static final String parseName(ByteBuffer buffer)
            throws DNSParseException {
        int jumps = 0;
        int lastpos = -1;
        CharBuffer dst = CharBuffer.allocate(255);
        while (true) {
            final byte b = buffer.get();
            if (b == 0) {
                break;
            }
            switch (b & 0xC0) {
                case 0x00:
                    // length that follows
                    if (dst.position() != 0) {
                        if (dst.remaining() < b + 1) {
                            throw new DNSParseException("DNS name too long");
                        }
                        dst.put('.');
                    } else {
                        if (dst.remaining() < b) {
                            throw new DNSParseException("DNS name too long");
                        }
                    }
                    for (int i = 0; i < b; ++i) {
                        dst.put((char) (buffer.get() & 0xff));
                    }
                    break;
                case 0xc0:
                    // offset of the new position
                    if (++jumps >= 16) {
                        throw new DNSParseException("Too many DNS name jumps");
                    }
                    final int offset = ((b & 0x3f) << 8) | (buffer.get() & 0xff);
                    if (lastpos == -1) {
                        lastpos = buffer.position();
                    }
                    buffer.position(offset);
                    break;
                default:
                    throw new DNSParseException("Unsupported DNS name byte");
            }
        }
        if (lastpos != -1) {
            buffer.position(lastpos);
        }
        return dst.flip().toString();
    }

    public static final DNSQuestion parseQuestion(ByteBuffer buffer)
            throws DNSParseException {
        final String name = parseName(buffer);
        final short qtype = buffer.getShort();
        final short qclass = buffer.getShort();
        return new DNSQuestion(name, qtype, qclass);
    }

    public static final DNSResourceRecord parseResourceRecord(ByteBuffer buffer)
            throws DNSParseException {
        final String name = parseName(buffer);
        final short rtype = buffer.getShort();
        final short rclass = buffer.getShort();
        final int ttl = buffer.getInt();
        final int rdlength = buffer.getShort() & 0xffff;
        final byte[] rdata = new byte[rdlength];
        buffer.get(rdata);
        return new DNSResourceRecord(name, rtype, rclass, ttl, rdata);
    }

    public static final DNSMessage parse(ByteBuffer buffer)
            throws DNSParseException {
        buffer = buffer.order(ByteOrder.BIG_ENDIAN);
        return parse(buffer, true);
    }

    private static final DNSMessage parse(ByteBuffer buffer, boolean complete)
            throws DNSParseException {
        //assert buffer.order() == ByteOrder.BIG_ENDIAN;
        final short id = buffer.getShort();
        final short flags = buffer.getShort();
        final short qdcount = buffer.getShort();
        final short ancount = buffer.getShort();
        final short nscount = buffer.getShort();
        final short arcount = buffer.getShort();

        final DNSMessage msg = new DNSMessage(id, flags, qdcount,
                complete ? ancount : 0, complete ? nscount : 0,
                complete ? arcount : 0);
        for (int i = 0; i < qdcount; ++i) {
            msg.questions[i] = parseQuestion(buffer);
        }
        if (complete) {
            try {
                for (int i = 0; i < ancount; ++i) {
                    msg.answers[i] = parseResourceRecord(buffer);
                }
                for (int i = 0; i < nscount; ++i) {
                    msg.nameservers[i] = parseResourceRecord(buffer);
                }
                for (int i = 0; i < arcount; ++i) {
                    msg.additionalrecords[i] = parseResourceRecord(buffer);
                }
            } catch (BufferUnderflowException e) {
                // Failure to read answers is not fatal in our case
            }
        }
        buffer.rewind();
        byte[] bs = new byte[buffer.limit()];
        buffer.get(bs);
        msg.bs = bs;
        return msg;
    }
    String key;

    public String strKey() {
        if (key == null) {
            try {
                key = new String(bs, 2, bs.length - 2, "iso8859-1");
            } catch (UnsupportedEncodingException ex) {
                ex.printStackTrace();
            }
        }
        return key;
    }

    static class DNSParseException extends Exception {

        public DNSParseException(String message) {
            super(message);
        }

    }
}
