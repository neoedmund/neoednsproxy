package neoe.dns;

import static neoe.dns.U.DEFAULT_DNS_PORT;

import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.List;

import neoe.dns.format.DNSMessage;
import neoe.util.Log;

/**
 * *
 * 
 * @author neoe
 */
public class DnsResolver {

	static List dnsHostList;
	static final int MAX_PACKET_SIZE = 1024;

	static DNSMessage resolve(DNSMessage msg) {
		Log.log("resolving " + msg);
		if (dnsHostList == null || dnsHostList.isEmpty()) {
			return null;
		}
		DNSMessage[] res = new DNSMessage[1];
		Thread[] ts = new Thread[dnsHostList.size()];
		int ti = 0;
		for (Object host : dnsHostList) {
			ts[ti++] = resolveConcurrent((String) host, res, msg);
		}

		for (int i = 0; i < U.timeoutUnitCnt; i++) { // bug: dont be too small
			U.sleep(U.timeoutUnitInMs);
			if (res[0] != null) {
				U.stopThreads(ts);
				return res[0];
			}
		}
		// timeout!
		Log.log("resolve timeout! data=" + msg);
		U.stopThreads(ts);
		return null;
	}

	private static Thread resolveConcurrent(final String host, final DNSMessage[] res, final DNSMessage msg) {
		Thread t = new Thread() {
			public void run() {
				try {
					DNSMessage resp = resolve(host, msg);
					if (resp != null && res[0] == null) {
						res[0] = resp;
						Log.log("first reply from " + resp.host + ", data=" + resp + "[" + resp.bs.length + "]");
					}
				} catch (Exception ex) {
					Log.log("resolve err:" + ex);
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
