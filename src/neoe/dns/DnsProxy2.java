package neoe.dns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingDeque;

import neoe.dns.format.DNSMessage;
import neoe.dns.format.DNSQuestion;
import neoe.util.Log;

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
				DNSMessage msg = DNSMessage
						.parse(ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength()));

				dealWith(msg);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}

		}

		static int notHit, hit;

		DoH1111 doh1111 = new DoH1111();

		private void dealWith(DNSMessage msg) throws Exception {

			{
				DNSQuestion[] qs = msg.getQuestions();
				if (qs != null && qs.length > 0) {
					String name = msg.getQuestions()[0].getName();
					Cache.incAccess(name);
					if (U.blacklist().contains(name)) {
						Log.log("[bl]" + name);
						reply(new Reply(msg.client, msg.getId(), msg.bs));
						return;
					}
				}
			}
			// if (msg.hasExtraData) {
			// Log.log("[t]hasExtraData");
			// }
			if (msg.hasExtraData /* || AntiVirus.isBadQuestion(msg) */) {
				Log.log("[AV]bad " + AntiVirus.getSecurityString(msg));
				msg = AntiVirus.clearifyQuestion(msg);
			}
			String key = msg.toIdString();
			Log.log("[Q]" + key);
			key = key.toLowerCase();

			byte[] cachedBs = Cache.get(key);
			if (cachedBs == null) {
				notHit++;
				Log.log("nohit, " + getHitRateStr());
				if (U.useDoh) {
					System.out.println("{ doh1111");
					doh1111.serverIPV4(msg, so, packet, key);
					System.out.println("doh1111 } ");
					return;
				}

				if (MyDomains.serve(msg, so, packet, key)) {
					return;
				}
				DNSMessage msgResp = DnsResolver.resolve(msg);
				if (msgResp == null) {
					Log.log("cannot resolve:" + msg);
					reply(new Reply(msg.client, msg.getId(), msg.bs));
					return;
				}
				// Log.log("[d]resp=" + msgResp.toString());
				if (msgResp.getAnswers() != null && msgResp.getAnswers().length > 0) {
					Cache.put(key, msgResp.bs);
				}
				reply(new Reply(msg.client, msg.getId(), msgResp.bs));
			} else {
				hit++;
				Log.log("HIT, " + getHitRateStr());
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
	 * @param args the command line arguments
	 * @throws java.lang.Exception
	 */
	public static void main(String[] args) throws Exception {
		System.out.println("NeoeDnsProxy " + U.VER);
		if (args.length > 0 && "log".equals(args[0]))
			Log.logToFile = true;
		else {
			Log.logToFile = false;
			Log.stdout = true;

		}
		server = new Server("0.0.0.0", U.DEFAULT_DNS_PORT);
		server.run();
	}

	static class Server {
		LinkedBlockingDeque<Object[]> hitKey = new LinkedBlockingDeque<>();
		LinkedBlockingDeque<Object[]> notHitKey = new LinkedBlockingDeque<>();

		DatagramSocket sso;

		private Server(String bindIp, int port) throws Exception {
			InetSocketAddress addr = new InetSocketAddress(bindIp, port);
			System.out.println("binding  " + addr);
			sso = new DatagramSocket(port);
			Log.log("binded " + addr);
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

		Object lock = new Object();

		private void run() {
			Log.log("server started");

			try {
				// loadConfig();
				U.loadConf();
				// Cache.load();
				UI.addUI();
				//
				autoRefresh(U.autoRefreshInSec);
				//
				dumpSites();

				while (true) {
					byte[] buf = new byte[MAX_PACKET_SIZE];
					final DatagramPacket packet = new DatagramPacket(buf, buf.length);
					sso.receive(packet);
					synchronized (lock) {
						running++;
					}

					final long startTime = System.currentTimeMillis();
					Log.log("accept(" + running + ")" + packet.getPort() + "/" + packet.getLength());
					new Thread() {
						public void run() {
							new Quest(sso, packet).run();
							synchronized (lock) {
								running--;
							}
							long t2 = (System.currentTimeMillis() - startTime);
							long s1 = (long) (avgTime * cnt + t2);
							cnt++;
							avgTime = s1 / (double) cnt;
							Log.log("finish(" + running + ")[" + t2 + " / " + (int) avgTime + " ms]" + packet.getPort()
									+ "/" + packet.getLength());
						}
					}.start();
				}

			} catch (Exception ex) {
				U.sendException(ex);
			}
		}
		/*-
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
												Log.log("cannot resolve:" + msg);
												continue;
											}
											Cache.put(domain, msgResp.bs);
		
										} else {
		
										}
									}
		
								}
								long t2 = System.currentTimeMillis() - t1;
								if (t2 > 10) {
									Log.log("db update loop in " + t2 + "ms");
								}
								Thread.sleep(4000);
							} catch (Throwable ex) {
								U.sendException(ex);
							}
						}
					}
				}
		*/

		private void dumpSites() {

			new Thread() {
				public void run() {
					while (true) {
						try { // clear cache
							if (U.dumpfile != null && new File(U.dumpfile).exists()) {
								FileOutputStream out = new FileOutputStream(U.dumpfile);
								Cache.dumpAccess(out);
								out.close();
							}
						} catch (Exception ex) {
							System.out.println("error dumpSites:" + ex);
						}
						U.sleep(U.dumpSleep);
					}
				}
			}.start();

		}

		protected void dumpSitesToFile(File file) {
			// TODO Auto-generated method stub

		}
	}

	/*-
		static class Event {
	
			Event(SocketAddress client, ByteBuffer buf) {
				this.client = client;
				this.buf = buf;
			}
	
			SocketAddress client;
			ByteBuffer buf;
		}
	*/
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
