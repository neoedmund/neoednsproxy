//neoe(c)
package neoe.util;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import neoe.dns.U;

/**
 *
 * @author neoe
 */
public class Log {

	static public String DEFAULT = "neoe";

	final static Map<String, Log> cache = new HashMap<String, Log>();

	public static boolean stdout = false;

	public static boolean debug = true;

	public static boolean logToFile;

	public synchronized static Log getLog(String name) {
		Log log = cache.get(name);
		if (log == null) {
			log = new Log(name, "log-" + name + ".log");
			cache.put(name, log);
		}
		return log;
	}

	private PrintWriter out;
	private SimpleDateFormat time;
	private Date now = new Date();

	private Log(String name, String fn) {
		try {
			File f = new File(fn);
			System.out.println("Log " + name + ":" + f.getAbsolutePath());
			out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f, true), "utf8"), true);
			time = new SimpleDateFormat("yyMMdd H:m:s:S");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static void logTo(String name, Object msg) {
		Log.getLog(name).log0(msg);
	}

	public static void logTo(String name, Object msg, Throwable t) {
		Log.getLog(name).log0(msg, t);
	}

	public static void log(Object msg) {
		Log.getLog(DEFAULT).log0(msg);
	}

	public static void log(Object msg, Throwable t) {
		Log.getLog(DEFAULT).log0(msg, t);
	}

	public synchronized void log0(Object o, Throwable t) {
		if (!logToFile && !stdout && !U.debug)
			return;
		if (out == null || o == null) {
			return;
		}
		String s0 = o.toString();
		if (!debug && s0.startsWith("[D]"))
			return;
		try {
			now.setTime(System.currentTimeMillis());
			StringBuilder sb = new StringBuilder();
			sb.append(time.format(now)).append(" ").append(s0);
			if (t == null)
				sb.append("\r\n");
			else
				sb.append(", Error:\r\n");
			if (logToFile || U.debug) {
				out.write(sb.toString());
				if (t != null) {
					t.printStackTrace(out);
				}
				out.flush();
			}
			if (stdout || U.debug)
				System.out.print(sb.toString());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public synchronized void log0(Object o) {
		log0(o, null);
	}
}
