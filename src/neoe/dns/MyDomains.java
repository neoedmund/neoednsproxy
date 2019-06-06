package neoe.dns;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.List;
import java.util.Map;

import neoe.dns.DnsProxy2.Reply;
import neoe.dns.format.DNSMessage;
import neoe.dns.format.DNSQuestion;
import neoe.util.Log;

public class MyDomains {

	public static boolean serve(DNSMessage msg, DatagramSocket so, DatagramPacket packet, String key) throws Exception {
		if (serverIPV4(msg, so, packet, key))
			return true;
		if (serverIPV6(msg, so, packet, key))
			return true;
		return false;
	}

	private static boolean serverIPV4(DNSMessage msg, DatagramSocket so, DatagramPacket packet, String key)
			throws Exception {
		DNSQuestion[] qs = msg.getQuestions();
		if (qs.length != 1)
			return false;
		DNSQuestion q = qs[0];
		if (q.getQType() != 1) {// A 1 ipv4 address
			return false;
		}
		String domain = q.getName();
		domain = domain.toLowerCase();// some naughty uPcaSe gAMe

		String sub = "@";
		String main = domain;
		{
			int p1 = domain.lastIndexOf('.');
			if (p1 > 0) {
				int p2 = domain.lastIndexOf('.', p1 - 1);
				if (p2 > 0) {
					sub = domain.substring(0, p2);
					main = domain.substring(p2 + 1);
				}
			}
		}
		Map m2 = (Map) U.conf("ipv4");
		if (m2 == null)
			return false;
		Map staticSub = (Map) m2.get(main);
		if (staticSub == null) {
			return false;
		}
		Log.log("[my4]: " + domain);
		List ips = (List) staticSub.get(sub);
		if (ips == null || ips.isEmpty()) {
			Reply reply = new Reply(msg.client, msg.getId(), msg.bs);
			U.reply(reply.data, reply.id, so, packet);
			return true;
		}
		byte[] bs = makeAns(msg, ips, domain);
		Reply reply = new Reply(msg.client, msg.getId(), bs);
		U.reply(reply.data, reply.id, so, packet);
		Cache.put(key, bs);
		return true;
	}

	private static boolean serverIPV6(DNSMessage msg, DatagramSocket so, DatagramPacket packet, String key)
			throws Exception {
		DNSQuestion[] qs = msg.getQuestions();
		if (qs.length != 1)
			return false;
		DNSQuestion q = qs[0];
		if (q.getQType() != 28) {// AAAA 28 ipv6 address
			return false;
		}
		String domain = q.getName();
		domain = domain.toLowerCase();// some naughty uPcaSe gAMe

		String sub = "@";
		String main = domain;
		{
			int p1 = domain.lastIndexOf('.');
			if (p1 > 0) {
				int p2 = domain.lastIndexOf('.', p1 - 1);
				if (p2 > 0) {
					sub = domain.substring(0, p2);
					main = domain.substring(p2 + 1);
				}
			}
		}
		Map m2 = (Map) U.conf("ipv6");
		if (m2 == null)
			return false;
		Map staticSub = (Map) m2.get(main);
		if (staticSub == null) {
			return false;
		}
		Log.log("[my6]: " + domain);
		// no data
		Reply reply = new Reply(msg.client, msg.getId(), msg.bs);
		U.reply(reply.data, reply.id, so, packet);
		Cache.put(key, msg.bs);
		return true;

	}

	public static byte[] makeAns(DNSMessage msg, List ips, String domain) throws IOException {
		ByteArrayOutputStream ba = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(ba);
		out.writeShort(msg.getId());
		out.writeShort(DNSMessage.AA_MASK | DNSMessage.RESPONSE_MASK);
		out.writeShort(1);
		out.writeShort(ips.size());
		out.writeShort(0);
		out.writeShort(0);
		msg.getQuestions()[0].dump(out);
		//
		for (int i = 0; i < ips.size(); i++) {
			// name, 1,1, // //
			if (false) {
				msg.getQuestions()[0].dump(out);// some naughty
			} else {
				// uPcaSe gAMe
				writeName(out, domain);
				out.writeShort(1);
				out.writeShort(1);
			}
			// ttl
			out.writeInt(800);
			out.writeShort(4);
			String ip = (String) ips.get(i);
			String[] ss = ip.split("\\.");
			if (ss.length != 4) {
				throw new RuntimeException("bad ip in conf:" + ip);
			}
			for (String s : ss) {
				out.writeByte(Integer.parseInt(s.trim()));
			}
		}
		out.close();
		return ba.toByteArray();
	}

	/* seems wrong */
	private static void writeName(DataOutputStream out, String domain) throws IOException {
		String[] ss = domain.split("\\.");
		for (String s : ss) {
			out.writeByte(s.length());
			for (char c : s.toCharArray()) {
				out.writeByte(c);
			}
		}
		out.writeByte(0);
	}

}
