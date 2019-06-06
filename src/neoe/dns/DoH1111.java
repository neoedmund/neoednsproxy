package neoe.dns;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;
import java.util.Map;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import neoe.dns.DnsProxy2.Reply;
import neoe.dns.format.DNSMessage;
import neoe.dns.format.DNSQuestion;
import neoe.util.BAOS;
import neoe.util.Config;
import neoe.util.Log;
import neoe.util.PyData;

public class DoH1111 {

	private static final String UTF8 = "utf8";

	public static void main(String[] args) throws Exception {
		System.out.println(new DoH1111().resolve("n101n.xyz", 1));
	}

	final int MAX_SIZE = 1000 * 1000;

	public static class LoopStringBuffer {

		private int[] cs;
		private int p;
		private int size;

		LoopStringBuffer(int size) {
			this.size = size;
			p = 0;
			cs = new int[size];
		}

		void add(int c) {
			cs[p++] = (char) c;
			if (p >= size) {
				p = 0;
			}
		}

		public String get() {
			int q = p;
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < size; i++) {
				sb.append((char) cs[q++]);
				if (q >= size) {
					q = 0;
				}
			}
			return sb.toString();
		}
	}

	String RN = "\r\n";

	private String readUntil(InputStream in, String end) {
		BAOS ba = new BAOS(0, MAX_SIZE);
		int b;
		int total = 0;
		try {
			LoopStringBuffer lsb = new LoopStringBuffer(end.length());
			while (true) {
				if ((b = in.read()) == -1) {
					// System.out.println("last=" + ba.toString());
					return ba.toString();
				}
				total++;
				if (total > MAX_SIZE) {
					throw new RuntimeException("NeoeHttpd: Total Size Exceed");
				}
				ba.write(b);
				lsb.add(b);
				if (lsb.get().equals(end)) {
					break;
				}
			}
			String s = ba.toString(UTF8);
			return s.substring(0, s.length() - end.length());
		} catch (IOException e) {
			throw new RuntimeException("read error", e);
		}

	}

	SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();

	public String resolve(String domain, int type) throws Exception {
		SSLSocket sslsocket = (SSLSocket) factory.createSocket("1.1.1.1", 443);
		System.out.println(sslsocket);
		OutputStream os = (sslsocket.getOutputStream());
		String host = "cloudflare-dns.com";
		os.write(String
				.format("GET /dns-query?name=%s&type=%s HTTP/1.1\r\nHost: %s\r\nAccept: application/dns-json\r\n\r\n",
						domain, type, host)
				.getBytes());
		os.flush();
		System.out.println("sent");
		BufferedInputStream is = new BufferedInputStream(sslsocket.getInputStream());
		String s = readUntil(is, RN);
		if (s.startsWith("GET ") || s.startsWith("POST ") || s.startsWith("HEAD ")) {
//			int p1 = s.indexOf(' ');
//			int p2 = s.indexOf(' ', p1 + 1);
//			if (p1 > 0 && p2 > 0) {
//				method = s.substring(0, p1);
//				path = s.substring(p1 + 1, p2);
//			}
		}
		int ctlen = 0;
		while ((s = readUntil(is, RN)).length() > 0) {
			int p1 = s.indexOf(':');
			if (p1 > 0) {
				String s1 = s.substring(0, p1).toLowerCase();
				String s2 = s.substring(p1 + 1).trim();
				if (s1.equalsIgnoreCase("Content-Length")) {
					ctlen = Integer.parseInt(s2);
				}
			} else {
				Log.log("bad header:" + p1);
			}
		}
		System.out.println("ctlen=" + ctlen);
		byte[] bs = is.readNBytes(ctlen);
		String ret = new String(bs);
		System.out.println(ret);
		Map m = (Map) PyData.parseAll(ret);
		String data = (String) Config.get(m, "Answer.[0].data");
//		System.out.println(data);
		return data;
	}

	public boolean serverIPV4(DNSMessage msg, DatagramSocket so, DatagramPacket packet, String key) throws Exception {
		DNSQuestion[] qs = msg.getQuestions();
		if (qs.length != 1)
			return false;
		DNSQuestion q = qs[0];
		int type = q.getQType();
		String domain = q.getName();
		domain = domain.toLowerCase();// some naughty uPcaSe gAMe

		String ip = resolve(domain, type);
		System.out.printf("doh:%s for %s\n", ip, domain);

		byte[] bs = MyDomains.makeAns(msg, Arrays.asList(new String[] { ip }), domain);
		Reply reply = new Reply(msg.client, msg.getId(), bs);
		U.reply(reply.data, reply.id, so, packet);
		Cache.put(key, bs);
		return true;
	}

}
