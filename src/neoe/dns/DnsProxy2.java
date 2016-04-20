package neoe.dns;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

import neoe.dns.format.DNSMessage;
import neoe.dns.model.DnsRec;

/**
 * 
 * @author neoe
 */
public class DnsProxy2 {

	public static class Quest {

		DatagramPacket packet;
		DatagramSocket so;

		public Quest(DatagramSocket so, DatagramPacket packet) {
			this.so = so;
			this.packet = packet;
		}

		public void run() {
			try {
				final DNSMessage msg = DNSMessage.parse(ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength()));
				final DNSMessage msg2 = AntiVirus.clearifyQuestion(msg);
				if (msg2 == null) {
					Log.app.log("[AV]drop " + AntiVirus.getSecurityString(msg));
				} else {
					dealWith(msg2);
				}
			} catch (Throwable ex) {
				ex.printStackTrace();
			}

		}

		static int notHit, hit;

		private void dealWith(DNSMessage msg) throws Exception {
			String key = msg.toIdString();
			Log.resolve.log("[Q]" + key);
			if (Cache.isDisabledDomain(key)) {
				Log.resolve.log("disabled domain:" + msg.toIdString());
				reply(new Reply(msg.client, msg.getId(), msg.bs));
				return;
			}
			byte[] cachedBs = Cache.get(key);
			if (cachedBs == null) {
				notHit++;
				Log.resolve.log("nohit, " + getHitRateStr());
				DNSMessage msgResp = DnsResolver.resolve(msg);
				if (msgResp == null) {
					Log.resolve.log("cannot resolve:" + msg);
					return;
				}
				Cache.put(key, msgResp.bs);
				reply(new Reply(msg.client, msg.getId(), msgResp.bs));
			} else {
				hit++;
				Log.resolve.log("HIT, " + getHitRateStr());
				reply(new Reply(msg.client, msg.getId(), cachedBs));
			}

			U.updateTooltip();
		}

		private void reply(Reply reply) throws IOException {
			U.reply(reply.data, reply.id, so, packet);

		}

		public static String getHitRateStr() {
			int all = hit + notHit;
			if (all == 0) {
				return "HitRate:NA";
			}
			return String.format("HitRate:%d%%", (hit * 100 / all));
		}

	}

	public static Server server;
	static final int MAX_PACKET_SIZE = 1024;

	/**
	 * @param args
	 *            the command line arguments
	 * @throws java.lang.Exception
	 */
	public static void main(String[] args) throws Exception {
		System.out.println("NeoeDnsProxy v1.0.1510");
		server = new Server("127.0.0.1", U.DEFAULT_DNS_PORT);
		server.run();
		// Runtime.getRuntime().addShutdownHook(new Thread() {
		// public void run() {
		// try {
		// Cache.save();
		// } catch (Throwable ex) {
		// }
		// }
		// });
	}

	static class Server {
		LinkedBlockingDeque<Object[]> hitKey = new LinkedBlockingDeque<>();
		LinkedBlockingDeque<Object[]> notHitKey = new LinkedBlockingDeque<>();

		DatagramSocket sso;

		private Server(String bindIp, int port) throws Exception {
			InetSocketAddress addr = new InetSocketAddress(bindIp, port);
			sso = new DatagramSocket(port);
			System.out.println("binded " + addr);
			Log.app.log("binded " + addr);
		}

		int running = 0;
		double avgTime = 0;
		int cnt = 0;

		/** auto clear cache */
		void autoRefresh(int sec) {
			System.out.println("cache clears every " + sec + " seconds");
			final long ms = sec * 1000;
			new Thread() {
				public void run() {
					while (true) {
						try { // clear cache
							int n = Cache.m.size();
							Cache.m.clear();
							Cache.updated.clear();
							U.inserted.clear(); // useless
							System.out.println("auto cleared cache:" + n);
						} catch (Exception ex) {
							System.out.println("error when clear cache:" + ex);
						}
						U.sleep(ms);
					}
				}
			}.start();

		}

		private void run() {
			Log.app.log("server started");

			try {
				// loadConfig();
				DnsResolver.init();
				// Cache.load();
				UI.addUI();

				//
				autoRefresh(60 * 60);

				while (true) {
					byte[] buf = new byte[MAX_PACKET_SIZE];
					final DatagramPacket packet = new DatagramPacket(buf, buf.length);
					sso.receive(packet);
					running++;
					final long startTime = System.currentTimeMillis();
					Log.app.log("accept(" + running + ")" + packet.getPort() + "/" + packet.getLength());
					new Thread() {
						public void run() {
							new Quest(sso, packet).run();
							running--;
							long t2 = (System.currentTimeMillis() - startTime);
							long s1 = (long) (avgTime * cnt + t2);
							cnt++;
							avgTime = s1 / (double) cnt;

							Log.app.log("finish(" + running + ")[" + t2 + " / " + (int) avgTime + " ms]" + packet.getPort() + "/" + packet.getLength());
						}
					}.start();
				}

			} catch (Exception ex) {
				U.sendException(ex);
			}
		}

		class DbSaver extends Thread {

			@Override
			public void run() {
				final List<DnsRec> rs = new ArrayList<>();
				while (true) {
					try {
						long t1 = System.currentTimeMillis();
						{
							int size = notHitKey.size();
							rs.clear();
							for (int i = 0; i < size; i++) {
								Object[] o = notHitKey.take();
								DnsRec rec = new DnsRec();
								rec.domain = (String) o[0];
								rec.reply = (byte[]) o[1];
								if (rec.canFitToDB() && !U.inserted.contains(rec.domain)) {
									U.inserted.add(rec.domain);
									rec.updated = new Date();
									rs.add(rec);
								}
							}

						}
						{
							int size = hitKey.size();
							final long now = System.currentTimeMillis();
							for (int i = 0; i < size; i++) {
								Object[] o = hitKey.take();
								final String domain = (String) o[0];
								final DNSMessage msg = (DNSMessage) o[1];
								long updated = Cache.getUpdated(domain);
								boolean needUpdate = now - updated > U.DNS_UPDATE_IN_MS;
								if (needUpdate) {
									final DNSMessage msgResp = DnsResolver.resolve(msg);
									if (msgResp == null) {
										Log.resolve.log("cannot resolve:" + msg);
										continue;
									}
									Cache.put(domain, msgResp.bs);

								} else {

								}
							}

						}
						long t2 = System.currentTimeMillis() - t1;
						if (t2 > 10) {
							Log.app.log("db update loop in " + t2 + "ms");
						}
						Thread.sleep(4000);
					} catch (Throwable ex) {
						U.sendException(ex);
					}
				}
			}
		}

	}

	static class Event {

		Event(SocketAddress client, ByteBuffer buf) {
			this.client = client;
			this.buf = buf;
		}

		SocketAddress client;
		ByteBuffer buf;
	}

	static class Reply {

		Reply(SocketAddress client, short id, byte[] data) {
			this.client = client;
			this.id = id;
			this.data = data;
		}

		SocketAddress client;
		short id;
		byte[] data;
	}
}
