package neoe.dns;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 * @author neoe
 */
public class Log {
    public static Log app = new Log("app", "log-app.log");
    public static Log resolve = new Log("resolve", "log-resolve.log");
//    BufferedWriter out;
    private SimpleDateFormat time;
    Date now = new Date();

    private Log(String name, String fn) {
        try {
        	// for performance reason, DO NOT log to file
            //File f = new File(fn);
            //System.out.println("Log " + name + ":" + f.getAbsolutePath());
            //out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true), "utf8"));
            time = new SimpleDateFormat("yyMMdd H:m:s:S");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public synchronized void log(Object o) {
        if (o == null) {
            return;
        }
        try {
            now.setTime(System.currentTimeMillis());
            StringBuilder sb = new StringBuilder();
            sb.append(time.format(now)).append(" ").append(o).append("\r\n");
            //out.write(sb.toString());
            //out.flush();
            System.out.print(sb.toString());
            System.out.flush();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
