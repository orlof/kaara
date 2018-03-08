package fi.elidor.expose;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by teppo on 7.3.2018.
 */

public class NetworkServer extends Thread {
    private final String ip;
    private final int port;

    private ServerSocket serverSocket;
    private LinkedList<Socket> clients = new LinkedList<>();
    private LinkedBlockingQueue<Object> sendQueue = new LinkedBlockingQueue<>();

    public NetworkServer(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public void close() {
        try {
            serverSocket.close();
        } catch (IOException e) {}
    }

    public void publish(HashMap data) {
        try {
            sendQueue.put(data);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static String toJson(Object obj) {
        if(obj instanceof Integer) {
            return Integer.toString((int) obj);
        }
        if(obj instanceof Long) {
            return Long.toString((long) obj);
        }
        if(obj instanceof Float) {
            return Float.toString((float) obj);
        }
        if(obj instanceof Double) {
            return Double.toString((double) obj);
        }
        if(obj instanceof String) {
            return "\"" + obj + "\"";
        }
        if(obj instanceof HashMap) {
            StringBuilder sb = new StringBuilder(200);
            sb.append("{");
            for (Map.Entry<String, Object> entry : ((HashMap<String, Object>) obj).entrySet()) {
                if(sb.length() > 1) {
                    sb.append(",");
                }
                String key = entry.getKey();
                Object value = entry.getValue();
                sb.append("\"").append(key).append("\"").append(":").append(toJson(value));
            }
            sb.append("}");
            return sb.toString();
        }
        if(obj.getClass().isArray()) {
            StringBuilder sb = new StringBuilder(200);
            sb.append("[");

            int arrlength = Array.getLength(obj);
            for(int i = 0; i < arrlength; ++i){
                if(sb.length() > 1) {
                    sb.append(",");
                }
                sb.append(toJson(Array.get(obj, i)));
            }

            sb.append("]");
            return sb.toString();
        }

        throw new RuntimeException("Illegal JSON serialization type: " + obj.getClass().getName());
    }

    @Override
    public void run() {
        Sender sender = new Sender();
        sender.start();

        try {
            serverSocket = new ServerSocket(port, 5, InetAddress.getByName(ip));

            while(true) {
                Socket socket = serverSocket.accept();
                clients.add(socket);
            }
        } catch (IOException e) {}
    }

    private class Sender extends Thread {
        @Override
        public void run() {
            try {
                while(true) {
                    Object data = sendQueue.take();
                    String json = toJson(data);

                    for (Iterator<Socket> it = clients.iterator(); it.hasNext(); ) {
                        Socket socket = it.next();
                        try {
                            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                            dos.writeByte(json.length() / 256);
                            dos.writeByte(json.length() % 256);
                            dos.writeBytes(json);
                            dos.flush();
                        } catch (IOException e) {
                            try {
                                socket.close();
                            } catch (IOException e1) {
                            }
                            it.remove();
                        }
                    }
                }
            } catch(InterruptedException ex) {}
        }
    }
}
