package neoe.dns;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

import neoe.dns.format.DNSMessage;
import neoe.dns.model.DnsRec;

/**
 *
 * @author neoe
 */
@Deprecated
public class DnsProxy1 {

    public static Server server;
    static final int MAX_TODO_SIZE = 1000 * 1000;
    static final int MAX_PACKET_SIZE = 1024;

    /**
     * @param args the command line arguments
     * @throws java.lang.Exception
     */
    public static void main(String[] args) throws Exception {
        server = new Server("127.0.0.1", U.DEFAULT_DNS_PORT);
        server.run();
//        Runtime.getRuntime().addShutdownHook(new Thread() {
//            public void run() {
//                try {
//                    Cache.save();
//                } catch (Throwable ex) {
//                }
//            }
//        });
    }

    static class Server {

        DatagramChannel channel;

        LinkedBlockingDeque<Event> todo = new LinkedBlockingDeque<>();
        LinkedBlockingDeque<Reply> reply = new LinkedBlockingDeque<>();
        LinkedBlockingDeque<Object[]> hitKey = new LinkedBlockingDeque<>();
        LinkedBlockingDeque<Object[]> notHitKey = new LinkedBlockingDeque<>();

        private Server(String bindIp, int port) throws Exception {
            InetSocketAddress addr = new InetSocketAddress(bindIp, port);
            channel = DatagramChannel.open(StandardProtocolFamily.INET);
            channel.bind(addr);
            channel.configureBlocking(false);
            System.out.println("binded " + addr);
            Log.app.log("binded " + addr);
        }

        private void run() {
            Log.app.log("server started");

            try {
                loadConfig();
                DnsResolver.init();
                Cache.load();
                new IoProcessRead().start();
                new IoProcessWrite().start();
                new Worker().start();
                new DbSaver().start();
                UI.addUI();
            } catch (Exception ex) {
                U.sendException(ex);
            }
        }
        private int notHit;
        private int hit;

        private void loadConfig() throws Exception {
            
        }

        class Worker extends Thread {

            @Override
            public void run() {
                while (true) {
                    try {
                        final Event evt = todo.take();
                        final DNSMessage msg = DNSMessage.parse(evt.buf);
                        msg.client = evt.client;
                        Log.resolve.log("in:" + evt.client + ", data:" + msg.toIdString());
                        if (msg.isResponse()) {
                            continue; // only requests are accepted
                        }

                        new Thread() {
                            public void run() {
                                try {
                                    dealWith(msg);
                                } catch (Exception ex) {
                                    U.sendException(ex);
                                }
                            }
                        }.start();
                    } catch (Throwable ex) {
                        U.sendException(ex);
                    }
                }
            }

            private void dealWith(DNSMessage msg) throws Exception {
                String key = msg.toIdString();
                if (Cache.isDisabledDomain(key)) {
                    Log.resolve.log("disabled domain:" + msg.toIdString());
                    reply.add(new Reply(msg.client, msg.getId(), msg.bs));
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
                    reply.add(new Reply(msg.client, msg.getId(), msgResp.bs));
                    notHitKey.add(new Object[]{key, msgResp.bs});
                } else {
                    hit++;
                    Log.resolve.log("HIT, " + getHitRateStr());
                    reply.add(new Reply(msg.client, msg.getId(), cachedBs));
                    hitKey.add(new Object[]{key, msg});
                }

                U.updateTooltip();
            }

        }

        public String getHitRateStr() {
            int all = hit + notHit;
            if (all == 0) {
                return "HitRate:NA";
            }
            return String.format("HitRate:%.2f", (hit / (float) all));
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

        class IoProcessRead extends Thread {

            @Override
            public void run() {
                try {

                    Selector selector;
                    selector = Selector.open();
                    channel.register(selector, SelectionKey.OP_READ);

                    while (true) {
                        try {
                            //Log.resolve.log("select");
                            int ready = selector.select();
                            //Log.resolve.log("select="+ready);
                            if (ready > 0) {
                                Iterator selectedKeys = selector.selectedKeys().iterator();
                                //Log.app.log("keys[");
                                while (selectedKeys.hasNext()) {
                                    SelectionKey key = (SelectionKey) selectedKeys.next();
                                    selectedKeys.remove();

                                    if (!key.isValid()) {
                                        continue;
                                    }

                                    // Check what event is available and deal with it
                                    if (key.isAcceptable()) {
                                        Log.app.log("found accept");
                                    } else if (key.isReadable()) {
                                        //Log.app.log("  readable");
                                        // read
                                        DatagramChannel socketChannel = (DatagramChannel) key.channel();
                                        final ByteBuffer buf = ByteBuffer
                                                .allocateDirect(MAX_PACKET_SIZE);
                                        buf.clear();
                                        final SocketAddress client = socketChannel.receive(buf);
                                        buf.flip();
                                        addTodo(new Event(client, buf));
                                    } else if (key.isWritable()) {
                                        Log.app.log("found writable in read()");
                                    } else {
                                        Log.app.log("  etc");
                                    }
//          TODO                      key.interestOps(SelectionKey.OP_WRITE|SelectionKey.OP_READ);
                                    //                         if (!reply.isEmpty()) {
//                                    key.interestOps(SelectionKey.OP_WRITE);
//                                }else{
//                                    key.interestOps(SelectionKey.OP_READ);
//                                }
                                }
                                //Log.app.log("]keys");
                            } else {
                                //Thread.sleep(100);
                            }
                        } catch (Throwable ex) {
                            U.sendException(ex);
                        }

                    }
                } catch (Throwable ex) {
                    U.sendException(ex);
                }
            }

            private void addTodo(Event event) {
                if (todo.size() < MAX_TODO_SIZE) {
                    todo.add(event);
                } else {
                    Log.app.log("todo list size too big, discard");
                }
            }
        }

        class IoProcessWrite extends Thread {

            @Override
            public void run() {
                try {

                    Selector selector;
                    selector = Selector.open();
                    channel.register(selector, SelectionKey.OP_WRITE);

                    while (true) {
                        try {
                            //Log.resolve.log("select");
                            int ready = selector.select();
                            //Log.resolve.log("select="+ready);
                            if (ready > 0) {
                                Iterator selectedKeys = selector.selectedKeys().iterator();
                                //Log.app.log("keys[");
                                while (selectedKeys.hasNext()) {
                                    SelectionKey key = (SelectionKey) selectedKeys.next();
                                    selectedKeys.remove();

                                    if (!key.isValid()) {
                                        continue;
                                    }

                                    // Check what event is available and deal with it
                                    if (key.isAcceptable()) {
                                        Log.app.log("found accept");
                                    } else if (key.isReadable()) {
                                        Log.app.log("found readable in write()");
                                    } else if (key.isWritable()) {
                                        //Log.app.log("  writable");
                                        while (true) {
                                            Reply rep = reply.take();
                                            if (rep != null) {
                                                DatagramChannel socketChannel = (DatagramChannel) key.channel();
                                                U.reply(rep.data, rep.id, rep.client, socketChannel);
                                            } else {
                                                //Log.app.log("write but no ready data");
                                                //serious bug:key.interestOps(SelectionKey.OP_READ);
                                                //Thread.sleep(100);
                                                break;
                                            }
                                        }
                                    } else {
                                        Log.app.log("  etc");
                                    }
//          TODO                      key.interestOps(SelectionKey.OP_WRITE|SelectionKey.OP_READ);
                                    //                         if (!reply.isEmpty()) {
//                                    key.interestOps(SelectionKey.OP_WRITE);
//                                }else{
//                                    key.interestOps(SelectionKey.OP_READ);
//                                }
                                }
                                //Log.app.log("]keys");
                            } else {
                                //Thread.sleep(100);
                            }
                        } catch (Throwable ex) {
                            U.sendException(ex);
                        }

                    }
                } catch (Throwable ex) {
                    U.sendException(ex);
                }
            }

            private void addTodo(Event event) {
                if (todo.size() < MAX_TODO_SIZE) {
                    todo.add(event);
                } else {
                    Log.app.log("todo list size too big, discard");
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
