//neoe(c)
package neoe.util;

import java.io.*;
import java.util.*;

/**
 *
 * @author neoe
 */
public class Config {

	private static Map<String, Long> confTime = new HashMap<String, Long>();
	private static Map<String, Map> confMap = new HashMap<String, Map>();

	private static long getConfTime(String cfgName) {
		Long t = confTime.get(cfgName);
		return t == null ? 0 : t;
	}

	public static Map getConfig(String cfgName) throws Exception {
		return getConfig(cfgName, true);
	}

	public static Map getConfig(String cfgName, boolean needComma) throws Exception {
		Map config = confMap.get(cfgName);
		if (config == null) {
			config = initInFileSystem(cfgName, needComma);
		}
		Long conf1time = getConfTime(cfgName);
		if (conf1time > 0) {
			File f = new File(cfgName);
			if (f.exists()) {
				long time = f.lastModified();
				if (time > conf1time + 200) {
					Log.log("Log file changed:" + f.getAbsolutePath());
					config = initInFileSystem(cfgName, needComma);
				}
			}
		}
		return config;
	}

	/**
	 * xxx.[2].yyy.[0]
	 */
	public static Object get(Map config, String name) {
		String[] ss = name.split("\\.");
		Object node = config;
		Object o = null;
		for (int i = 0; i < ss.length; i++) {
			if (node == null)
				return null;
			String s = ss[i];
			if (s.startsWith("[") && s.endsWith("]")) {
				int p = Integer.parseInt(s.substring(1, s.length() - 1));
				if (node instanceof Map) {
					o = ((Map) node).values().toArray()[p];
				} else {
					o = ((List) node).get(p);
				}
			} else {
				o = ((Map) node).get(s);
			}
			node = o;
		}
		// Log.log("config["+name+"]="+o);
		return o;
	}

	/**
	 * default
	 * 
	 * @param cfgName
	 * @param needComma
	 */
	private static synchronized Map initInFileSystem(String cfgName, boolean needComma) throws Exception {
		File f1 = new File(cfgName);
		Log.log("load config file:" + f1.getAbsolutePath());
		FileInputStream in;
		Map config = (Map) PyData.parseAll(FileUtil.readString(in = new FileInputStream(f1), "UTF8"), needComma);
		in.close();
		long conf1time = f1.lastModified();
		confMap.put(cfgName, config);
		confTime.put(cfgName, conf1time);
		Log.log("config:" + config.size());
		return config;
	}
}
