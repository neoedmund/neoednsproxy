package neoe.dns;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.List;
import static neoe.dns.DnsProxy1.MAX_PACKET_SIZE;
import static neoe.dns.U.DEFAULT_DNS_PORT;
import neoe.dns.format.DNSMessage;

/**
 *
 * @author neoe
 */
public class DnsResolver {

	static boolean inited = false;
	static List<String> dnsHostList;
	static final String CONF = "dnsservers.cfg";

	static {
		init();
	}

	static DNSMessage resolve(DNSMessage msg) {
		if (!inited) {
			init();
		}
		if (dnsHostList == null || dnsHostList.isEmpty()) {
			return null;
		}
		Log.app.log("resolving " + msg);
		DNSMessage[] res = new DNSMessage[1];
		Thread[] ts = new Thread[dnsHostList.size()];
		int ti = 0;
		for (String host : dnsHostList) {
			ts[ti++] = resolveConcurrent(host, res, msg);
		}
		
		for (int i = 0; i < 120; i++) { // bug: dont be too small
			U.sleep(10);
			if (res[0] != null) {
				U.stopThreads(ts);
				return res[0];
			}
		}
		// timeout!
		Log.app.log("resolve timeout! data=" + msg);
		U.stopThreads(ts);
		return null;
	}

	public static void init() {
		inited = true;
		if (!new File(CONF).exists()) {
			U.showMsg(CONF + " not exists! Please find it.\nProgram will exit now.");
			System.exit(1);
			return;
		}
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(CONF), "utf8"));
			dnsHostList = new ArrayList<>();
			String line;
			while ((line = in.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("#") || line.length() == 0) {
					continue;
				}
				dnsHostList.add(line);
			}
			in.close();
			Log.app.log("read dns list size:" + dnsHostList.size());
		} catch (Exception ex) {
			ex.printStackTrace();
			Log.app.log("dns list init err:" + ex);
		}
	}

	private static Thread resolveConcurrent(final String host, final DNSMessage[] res, final DNSMessage msg) {
		Thread t = new Thread() {
			public void run() {
				try {
					DNSMessage resp = resolve(host, msg);
					if (resp != null && res[0] == null) {
						res[0] = resp;
						Log.resolve
								.log("first reply from " + resp.host + ", data=" + resp + "[" + resp.bs.length + "]");
					}
				} catch (Exception ex) {
					Log.app.log("resolve err:" + ex);
				}
			}
		};
		t.start();
		return t;
	}

	public static DNSMessage resolve(String host, DNSMessage msg) throws Exception {
		int port = DEFAULT_DNS_PORT;
		{
			int p1 = host.indexOf(":");
			if (p1 > 0) {
				port = Integer.parseInt(host, p1 + 1);
				host = host.substring(0, p1);
			}
		}
		InetSocketAddress addr = new InetSocketAddress(host, port);
		DatagramChannel channel = DatagramChannel.open(StandardProtocolFamily.INET);
		try {
			channel.connect(addr);
			{
				ByteBuffer buf = U.bs2buf(msg.bs);
				int bytesSent = channel.send(buf, addr);
			}
			ByteBuffer buf2 = ByteBuffer.allocate(MAX_PACKET_SIZE);
			buf2.clear();
			int bytesRead = channel.read(buf2);
			buf2.flip();
			DNSMessage msgResp = DNSMessage.parse(buf2);
			msgResp.host = host;
			return msgResp;
		} catch (Exception ex) {
			U.sendException(ex);
		} finally {
			channel.close();
		}
		return null;
	}
}
