package neoe.dns.format;

import java.io.UnsupportedEncodingException;
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

	public DNSResourceRecord(String name, short rtype, short rclass, int ttl, byte[] rdata) {
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
			return name.equals(other.name) && rtype == other.rtype && rclass == other.rclass && ttl == other.ttl
					&& Arrays.equals(rdata, other.rdata);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return (((name.hashCode() * 31 + rtype) * 31 + rclass) * 31 + ttl) * 31 + rdata.hashCode();
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("\n\tResourceRecord { name: ");
		builder.append(name);
		builder.append(", rtype: ");
		builder.append(rtype);
		builder.append(", rclass: ");
		builder.append(rclass);
		builder.append(", ttl: ");
		builder.append(ttl);
		builder.append(", rdata: ");
		boolean rdataPrinted = false;
		if (rtype == 5) { // CNAME : the canonical name for an alias
			try {
				builder.append(new String(rdata, "UTF8"));
				rdataPrinted = true;
			} catch (UnsupportedEncodingException e) {
				builder.append("ErrorUTF8String,fallback:");
			}
		}
		if (!rdataPrinted)
			for (int i = 0; i < rdata.length; ++i) {

				final byte b = rdata[i];
				if (i > 0) {
					builder.append('.');
				}
				builder.append((int) (b & 0xff));
			}
		builder.append(" }");
		return builder.toString();
	}
}
