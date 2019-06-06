package neoe.dns;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import neoe.dns.format.DNSMessage;
import neoe.dns.format.DNSResourceRecord;
import neoe.dns.model.DnsRec;
import neoe.util.Log;

/**
 *
 * @author neoe
 */
public class Cache {

	final static int capacity = 1000 * 100;

	public static Map<String, byte[]> m;
	public static Map<String, Long> updated;
	public static Map<String, Integer> access;
	final static String CACHE_FN = "cache.save";
	final static String ASCII = "iso8859-1";

	static {
		m = Collections.synchronizedMap(new LinkedHashMap(capacity + 1, 1.1f, true) {
			protected boolean removeEldestEntry(Entry eldest) {
				return size() > capacity;
			}
		});
		updated = Collections.synchronizedMap(new LinkedHashMap(capacity + 1, 1.1f, true) {
			protected boolean removeEldestEntry(Entry eldest) {
				return size() > capacity;
			}
		});
		access = Collections.synchronizedMap(new LinkedHashMap(capacity + 1, 1.1f, true) {
			protected boolean removeEldestEntry(Entry eldest) {
				return size() > capacity;
			}
		});
	}

	static int clearWild(String s) {
		s = s.toLowerCase();
		int n = 0;
		for (String key : new HashSet<>(m.keySet())) {
			byte[] value = m.get(key);
			if (value == null) {
				continue;
			}
			try {
				DNSMessage msg = DNSMessage.parse(U.bs2buf(value));
				DNSResourceRecord[] ans = msg.getAnswers();
				if (ans != null) {
					for (DNSResourceRecord rec : ans) {
						if (rec.getName().toLowerCase().indexOf(s) >= 0) {
							m.remove(key);
							n++;
							break;
						}
					}
				}
			} catch (Exception ex) {
				U.sendException(ex);
				m.remove(key);
				n++;
			}
		}
		return n;
	}

	static int clearExact(String s) {
		s = s.trim().toLowerCase();
		int n = 0;
		for (String key : new HashSet<>(m.keySet())) {
			byte[] value = m.get(key);
			if (value == null) {
				continue;
			}
			try {
				DNSMessage msg = DNSMessage.parse(U.bs2buf(value));
				DNSResourceRecord[] ans = msg.getAnswers();
				if (ans != null) {
					for (DNSResourceRecord rec : ans) {
						if (rec.getName().toLowerCase().equals(s)) {
							m.remove(key);
							n++;
							break;
						}
					}
				}
			} catch (Exception ex) {
				U.sendException(ex);
				m.remove(key);
				n++;
			}
		}
		return n;
	}

	static int save() throws IOException {
		int n = 0;
		DataOutputStream out = new DataOutputStream(new FileOutputStream(CACHE_FN));
		for (String key : new HashSet<>(m.keySet())) {
			byte[] value = m.get(key);
			if (value == null) {
				continue;
			}
			out.writeUTF(key);
			out.writeInt(value.length);
			out.write(value);
			n++;
		}
		Log.log("saved cache size:" + n);
		return n;
	}

	static int load() throws Exception {
		return // loadFromFile() +
		loadFromeDb();
	}

	static int loadFromFile() {
		if (!new File(CACHE_FN).exists()) {
			return 0;
		}
		// for upgrade
		final List<DnsRec> recs = new ArrayList<>();
		int n = 0;
		try {
			DataInputStream in = null;
			m.clear();
			in = new DataInputStream(new FileInputStream(CACHE_FN));
			Date now = new Date();
			while (true) {
				try {
					String s = in.readUTF();
					if (s == null) {
						break;
					}
					int size = in.readInt();
					byte[] bs = new byte[size];
					in.read(bs);
					n++;
					m.put(s, bs);
					DnsRec rec = new DnsRec();
					rec.domain = s;
					rec.reply = bs;
					if (rec.canFitToDB()) {
						rec.updated = now;
						recs.add(rec);
					}
				} catch (IOException ex) {
					break;
				}
			}
			in.close();

		} catch (Exception ex) {
			U.sendException(ex);
		}

		return n;
	}

	public static long getUpdated(String key) {
		Long t = updated.get(key);
		if (t == null)
			return 0;
		return t;
	}

	private static int loadFromeDb() throws Exception {

		return 0;
	}

	static void put(String key, byte[] bs) {
		m.put(key, bs);
		updated.put(key, System.currentTimeMillis());
	}

	static byte[] get(String key) {
		return m.get(key);
	}

	static Object accessLock = new Object();

	public static void incAccess(String name) {
		synchronized (accessLock) {
			Integer v = access.get(name);
			if (v == null)
				v = 1;
			else {
				v += 1;
			}
			access.put(name, v);
		}
	}
	
	public static void dumpAccess(OutputStream out) throws IOException{
		List<Entry<String, Integer>> ens;
		synchronized (accessLock) {
			ens = new ArrayList<Entry<String, Integer>>(access.entrySet());
		}
		Collections.sort(ens, new Comparator() {
			@Override
			public int compare(Object o1, Object o2) {
				Entry<String, Integer> e1=(Entry<String, Integer>) o1;
				Entry<String, Integer> e2=(Entry<String, Integer>) o2;
				return e1.getValue().compareTo(e2.getValue());
			}
		});
		for (Entry<String, Integer> e:ens){
			out.write(e.getKey().getBytes("utf8"));
			out.write('\t');
			out.write(e.getValue().toString().getBytes("utf8"));
			out.write('\n');
		}
		out.flush();
	}

}
