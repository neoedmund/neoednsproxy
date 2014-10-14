package neoe.dns.format;

/**
 * A single question in a DNS message
 *
 * @author Alexey Borzenkov
 *
 */
public final class DNSQuestion {

    private final String name;
    private final short qtype;
    private final short qclass;

    public DNSQuestion(String name, short qtype, short qclass) {
        this.name = name;
        this.qtype = qtype;
        this.qclass = qclass;
    }

    public final String getName() {
        return name;
    }

    public final int getQType() {
        return qtype;
    }

    public final int getQClass() {
        return qclass;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof DNSQuestion) {
            final DNSQuestion other = (DNSQuestion) obj;
            return name.equals(other.name) && qtype == other.qtype
                    && qclass == other.qclass;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (name.hashCode() * 31 + qtype) * 31 + qclass;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("Question { name: ");
        builder.append(name);
        builder.append(", qtype: ");
        builder.append(qtype);
        builder.append(", qclass: ");
        builder.append(qclass);
        builder.append(" }");
        return builder.toString();
    }

    public String toIdString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(name);
        builder.append(":");
        builder.append(qtype);
        builder.append(":");
        builder.append(qclass);
        return builder.toString();
    }
}
