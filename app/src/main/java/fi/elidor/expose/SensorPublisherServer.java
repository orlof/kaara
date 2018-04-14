package fi.elidor.expose;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.HandlerThread;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;


public class SensorPublisherServer extends Thread{
    private final Object lock = new Object();
    private static final String TAG = "SENSOR_PUBLISHER_SERVER";
    private final String IP = "192.168.43.1";
    private final int PORT = 3451;

    private Sender sender;

    private ServerSocket serverSocket;
    private LinkedList<Socket> clients = new LinkedList<>();

    private SensorManager sensorService;
    private LocationManager locationService;

    private Sensor accelerometer;
    private Sensor magnetometer;

    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];

    private final float[] mRotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    SensorPublisherServer(Context ctx) {
        sensorService = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);

        accelerometer = sensorService.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorService.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        locationService = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void run() {
        sender = new Sender();

        HandlerThread ht = new HandlerThread("ExposeLocationUpdateHandlerThread");
        ht.start();

        try {
            serverSocket = new ServerSocket(PORT, 5, InetAddress.getByName(IP));

            while(true) {
                Socket socket = serverSocket.accept();
                synchronized(lock) {
                    if (clients.isEmpty()) {
                        sensorService.registerListener(sender, accelerometer, 1000 * 500, 1000 * 500);
                        sensorService.registerListener(sender, magnetometer, 1000 * 500, 1000 * 500);
                        locationService.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 1, sender, ht.getLooper());
                        new Thread(sender).start();
                    }
                    clients.add(socket);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            serverSocket.close();
        } catch (IOException ignored) {}

        sender.shutdown();
    }

    private class Sender implements Runnable, SensorEventListener, LocationListener {

        private LowPassFilter
                lpAzimuth = new LowPassFilter(),
                lpPitch = new LowPassFilter(),
                lpRoll = new LowPassFilter(),
                lpSpeed = new LowPassFilter(0.5),
                lpSpeedAccuracy = new LowPassFilter(),
                lpAltitude = new LowPassFilter(),
                lpVerticalAccuracy = new LowPassFilter(),
                lpLatitude = new LowPassFilter(0.5),
                lpLongitude = new LowPassFilter(0.5),
                lpHorizontalAccuracy = new LowPassFilter(),
                lpBearing = new LowPassFilter(0.5),
                lpBearingAccuracy = new LowPassFilter();

        void shutdown() {
            for (Socket socket : clients) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void run() {
            try {
                while(true) {
                    transmitToAllClients(getData());

                    synchronized (lock) {
                        if (clients.isEmpty()) {
                            sensorService.unregisterListener(this);
                            locationService.removeUpdates(this);

                            return;
                        }
                    }

                    Thread.sleep(500);
                }
            } catch(InterruptedException ignored) {}
        }

        private void transmitToAllClients(Object data) {
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
                    Log.d("NETWORK", "Cannot write " + e.toString());
                    try {
                        socket.close();
                    } catch (IOException e1) {
                        Log.d("NETWORK", "Cannot close socket " + e1.toString());
                    }
                    it.remove();
                }
            }
        }

        private HashMap<String, Object> getData() {
            SensorManager.getRotationMatrix(mRotationMatrix, null, accelerometerReading, magnetometerReading);
            SensorManager.getOrientation(mRotationMatrix, orientationAngles);

            lpAzimuth.update(orientationAngles[0]);
            lpPitch.update(orientationAngles[1]);
            lpRoll.update(orientationAngles[2]);

            HashMap<String, Object> root = new HashMap<>();

            HashMap<String, Object> data = new HashMap<>();
            data.put("azimuth", Math.round(Math.toDegrees(lpAzimuth.value)));
            data.put("pitch", Math.round(Math.toDegrees(lpPitch.value)));
            data.put("roll", Math.round(Math.toDegrees(lpRoll.value)));
            root.put("orientation_angles", data);

            data = new HashMap<>();
            data.put("speed", Math.round(lpSpeed.value));
            data.put("speed_accuracy", Math.round(lpSpeedAccuracy.value));
            data.put("altitude", Math.round(lpAltitude.value));
            data.put("vertical_accuracy", Math.round(lpVerticalAccuracy.value));
            data.put("bearing", Math.round(lpBearing.value));
            data.put("bearing_accuracy", Math.round(lpBearingAccuracy.value));
            data.put("latitude", lpLatitude.value);
            data.put("longitude", lpLongitude.value);
            data.put("horizontal_accuracy", Math.round(lpHorizontalAccuracy.value));
            root.put("location", data);

            return root;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor == magnetometer) {
                System.arraycopy(event.values, 0, magnetometerReading,0, magnetometerReading.length);
            }
            if (event.sensor == accelerometer) {
                System.arraycopy(event.values, 0, accelerometerReading,0, accelerometerReading.length);
            }
        }

        @Override
        public void onLocationChanged(Location location) {
            Log.d("SNSRPUBSRV", "Loc: " + location);
            lpSpeed.update(location.getSpeed());
            //lpSpeedAccuracy.update(location.getSpeedAccuracyMetersPerSecond());
            lpAltitude.update(location.getAltitude());
            //lpVerticalAccuracy.update(location.getVerticalAccuracyMeters());
            lpLatitude.update(location.getLatitude());
            lpLongitude.update(location.getLongitude());
            lpHorizontalAccuracy.update(location.getAccuracy());
            lpBearing.update(location.getBearing());
            //lpBearingAccuracy.update(location.getBearingAccuracyDegrees());
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {
        }

        @Override
        public void onProviderEnabled(String s) {
        }

        @Override
        public void onProviderDisabled(String s) {
        }

    }

    private static String toJson(Object obj) {
        if(obj instanceof Integer) {
            return Integer.toString((int) obj);
        } else if(obj instanceof Long) {
            return Long.toString((long) obj);
        } else if(obj instanceof Float) {
            return Float.toString((float) obj);
        } else if(obj instanceof Double) {
            return Double.toString((double) obj);
        } else if(obj instanceof String) {
            return "\"" + obj + "\"";
        } else if(obj instanceof HashMap) {
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
        } else if(obj.getClass().isArray()) {
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
}
