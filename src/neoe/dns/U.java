package neoe.dns;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JOptionPane;

import neoe.dns.format.DNSMessage;
import neoe.dns.format.DNSResourceRecord;
import neoe.util.FileUtil;

/**
 * 
 * @author neoe
 */
public class U {

	public static final int DEFAULT_DNS_PORT = 53;
	static long DNS_UPDATE_IN_MS = 1000 * 60 * 2;
	static Set<String> inserted = new HashSet<>();

	public static InputStream getIns(String fn) {
		return getIns(fn, false);
	}

	static byte[] ZERO_BA = new byte[10];

	public static void reply(byte[] bs, short id, SocketAddress client, DatagramChannel socketChannel)
			throws IOException {
		if (bs == null) {
			bs = ZERO_BA;
		}
		try {
			ByteBuffer buf = U.bs2buf(bs);
			buf.putShort(id);
			buf.rewind();
			int bytesSent = socketChannel.send(buf, client);
			buf.rewind();
			if (bs == ZERO_BA) {
				Log.resolve.log("disabled record replied (bug?)");
			} else {
				Log.resolve.log("replied to " + client + " id=" + id + " bytes sent:" + bytesSent + " "
						+ DNSMessage.parse(buf).toIdString());
			}
		} catch (Exception ex) {
			U.sendException(ex);
		}
	}

	public static void reply(byte[] bs, short id, DatagramSocket so, DatagramPacket packet) {

		if (bs == null) {
			bs = ZERO_BA;
		}
		try {
			ByteBuffer buf = U.bs2buf(bs);
			buf.putShort(id);
			buf.rewind();
			byte[] bs2 = buf.array();
			InetAddress address = packet.getAddress();
			int port = packet.getPort();
			packet = new DatagramPacket(bs2, bs2.length, address, port);
			so.send(packet);

			if (bs == ZERO_BA) {
				Log.resolve.log("disabled record replied (bug?)");
			} else {
				Log.resolve.log("replied to " + address + " id=" + id + " bytes sent:" + bs.length + " "
						+ DNSMessage.parse(buf).toIdString());
			}
		} catch (Exception ex) {
			U.sendException(ex);
		}
	}

	public static InputStream getIns(String fn, boolean debug) {
		InputStream ins;
		File localf = new File(".", fn);
		if (localf.exists()) {
			try {
				ins = new FileInputStream(localf);
			} catch (FileNotFoundException e) {
				U.sendException(e);
				return null;
			}
			if (debug) {
				System.out.println("load in filesystem " + fn);
			}
			return ins;
		} else {
			ins = ClassLoader.getSystemResourceAsStream(fn);
			if (ins != null) {
				if (debug) {
					System.out.println("load in system classpath " + fn);
				}
				return ins;
			}
			ins = U.class.getClassLoader().getResourceAsStream(fn);
			if (ins != null) {
				if (debug) {
					System.out.println("load in current classpath " + fn);
				}
				return ins;
			}
		}

		if (fn.startsWith("/")) {
			return getIns(fn.substring(1));
		} else {
			if (debug) {
				System.out.println("cannot find " + fn);
			}
			return null;
		}
	}

	public static ByteBuffer bs2buf(byte[] bs) {
		ByteBuffer bb = ByteBuffer.allocate(bs.length);
		bb.clear();
		bb.put(bs);
		bb.flip();
		return bb;
	}

	public static byte[] buf2bs(ByteBuffer buf) {
		buf.rewind();
		byte[] bs = new byte[buf.remaining()];
		buf.get(bs);
		buf.rewind();
		return bs;
	}

	static void sleep(long i) {
		try {
			Thread.sleep(i);
		} catch (InterruptedException ex) {
			ex.printStackTrace();
		}
	}

	public static void sendException(Throwable e) {
		if (e instanceof ClosedByInterruptException) {
			// ignore
		} else {
			Log.app.log("err:" + except2Str(e));
		}
	}

	static byte[] readBytes(InputStream ins) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		FileUtil.copy(ins, out);
		return out.toByteArray();
	}

	static void showMsg(String string) {
		JOptionPane.showMessageDialog(null, string);
	}

	static synchronized void updateTooltip() {
		if (Cache.m == null || DnsProxy2.server == null || UI.trayIcon == null)
			return;
		UI.trayIcon.setToolTip("Cached " + Cache.m.size() + ", " + DnsProxy2.Quest.getHitRateStr());
	}

	static void stopThreads(Thread[] ts) {
		try {
			for (Thread t : ts) {
				t.interrupt();
			}
		} catch (Exception ex) {
			sendException(ex);
		}
	}

	static boolean isSameWithoutID(byte[] a, byte[] b) {
		// ignore first 2 byte
		if (a == null || b == null) {
			return false;
		}
		int al = a.length;
		int bl = b.length;
		if (al != bl || al < 2 || bl < 2) {
			return false;
		}

		try {
			DNSMessage m1 = DNSMessage.parse(ByteBuffer.wrap(a));
			DNSMessage m2 = DNSMessage.parse(ByteBuffer.wrap(b));
			DNSResourceRecord[] ans1 = m1.getAnswers();
			DNSResourceRecord[] ans2 = m2.getAnswers();
			if (ans1 == null || ans2 == null || ans1.length != ans2.length) {
				return false;
			}
			for (int i = 0; i < ans1.length; i++) {
				DNSResourceRecord rr1 = ans1[i];
				DNSResourceRecord rr2 = ans2[i];
				if (!Arrays.equals(rr1.getRData(), rr2.getRData())) {
					return false;
				}
			}
			return true;
		} catch (Exception ex) {
			for (int i = 2; i < al; i++) {
				if (a[i] != b[i]) {
					return false;
				}
			}
			return true;
		}
	}

	public static String except2Str(Throwable e) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps;
		e.printStackTrace(ps = new PrintStream(baos));
		ps.close();
		String s;
		try {
			s = baos.toString("utf8");
		} catch (UnsupportedEncodingException ex) {
			return e + "," + ex;
		}
		return s;
	}

}
