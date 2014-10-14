package neoe.dns.format;

import java.util.Arrays;

/**
 * A single RR in a DNS message
 *
 * @author Alexey Borzenkov
 *
 */
public final class DNSResourceRecord {

    private final static String HEX = "0123456789abcdef";

    private final String name;
    private final short rtype;
    private final short rclass;
    private final int ttl;
    private final byte[] rdata;

    public DNSResourceRecord(String name, short rtype, short rclass, int ttl,
            byte[] rdata) {
        if (name == null) {
            throw new NullPointerException();
        }
        if (rdata == null) {
            throw new NullPointerException();
        }
        this.name = name;
        this.rtype = rtype;
        this.rclass = rclass;
        this.ttl = ttl;
        this.rdata = rdata;
    }

    public final String getName() {
        return name;
    }

    public final short getRType() {
        return rtype;
    }

    public final short getRClass() {
        return rclass;
    }

    public final int getTtl() {
        return ttl;
    }

    public final byte[] getRData() {
        return rdata;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof DNSResourceRecord) {
            final DNSResourceRecord other = (DNSResourceRecord) obj;
            return name.equals(other.name) && rtype == other.rtype
                    && rclass == other.rclass && ttl == other.ttl
                    && Arrays.equals(rdata, other.rdata);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (((name.hashCode() * 31 + rtype) * 31 + rclass) * 31 + ttl) * 31
                + rdata.hashCode();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("ResourceRecord { name: ");
        builder.append(name);
        builder.append(", rtype: ");
        builder.append(rtype);
        builder.append(", rclass: ");
        builder.append(rclass);
        builder.append(", ttl: ");
        builder.append(ttl);
        builder.append(", rdata: ");
        for (int i = 0; i < rdata.length; ++i) {
            final byte b = rdata[i];
            builder.append(HEX.charAt((b & 0xff) >> 4));
            builder.append(HEX.charAt((b & 0x0f)));
        }
        builder.append(" }");
        return builder.toString();
    }
}
