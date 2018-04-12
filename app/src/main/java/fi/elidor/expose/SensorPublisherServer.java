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
import android.os.PowerManager;
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
import java.util.concurrent.LinkedBlockingQueue;


public class SensorPublisherServer extends Thread{
    private static final String TAG = "SENSOR_PUB_SRV";
    private final String IP = "192.168.43.1";
    private final int PORT = 3451;

    private ServerSocket serverSocket;
    private LinkedList<Socket> clients = new LinkedList<>();
    private LinkedBlockingQueue<Object> sendQueue = new LinkedBlockingQueue<>();

    private SensorManager sensorService;
    private LocationManager locationService;

    private Sensor accelerometer;
    private Sensor magnetometer;

    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];

    private final float[] mRotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    SensorPublisherServer(Context ctx) {
        PowerManager powerManager = (PowerManager) ctx.getSystemService(ctx.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyWakelockTag");
        wakeLock.acquire();

        sensorService = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);

        accelerometer = sensorService.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorService.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        locationService = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);

        // mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, 1000*500);
        // mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL, 1000*500);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void run() {
        Sender sender = new Sender();
        sender.start();

        try {
            serverSocket = new ServerSocket(PORT, 5, InetAddress.getByName(IP));

            while(true) {
                Socket socket = serverSocket.accept();
                if(clients.isEmpty()) {
                    sensorService.registerListener(sender, accelerometer, 1000*250, 1000*100);
                    sensorService.registerListener(sender, magnetometer, 1000*250, 1000*100);
                    locationService.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 1, sender);
                }
                clients.add(socket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void close() {
        try {
            serverSocket.close();
        } catch (IOException ignored) {}

        try {
            sendQueue.put("END");
            this.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private class Sender extends Thread implements SensorEventListener, LocationListener {
        private LowPassFilter
                lpAzimuth = new LowPassFilter(),
                lpPitch = new LowPassFilter(),
                lpRoll = new LowPassFilter(),
                lpSpeed = new LowPassFilter(0.5),
                lpSpeedAccuracy = new LowPassFilter(),
                lpAltitude = new LowPassFilter(),
                lpVerticalAccuracy = new LowPassFilter(),
                lpLatitude = new LowPassFilter(),
                lpLongitude = new LowPassFilter(),
                lpHorizontalAccuracy = new LowPassFilter(),
                lpBearing = new LowPassFilter(),
                lpBearingAccuracy = new LowPassFilter();

        @Override
        public void run() {
            try {
                while(true) {
                    Object data = sendQueue.take();

                    if(data instanceof String && data.equals("END")) {
                        shutdown();
                        return;
                    }

                    transmitToAllClients(data);
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

            if(clients.isEmpty()) {
                sensorService.unregisterListener(this);
                locationService.removeUpdates(this);
            }
        }

        private void shutdown() {
            for (Socket socket : clients) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void publish() {
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

            try {
                sendQueue.put(root);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor == accelerometer) {
                System.arraycopy(event.values, 0, accelerometerReading,0, accelerometerReading.length);
            }
            if (event.sensor == magnetometer) {
                System.arraycopy(event.values, 0, magnetometerReading,0, magnetometerReading.length);
            }

            SensorManager.getRotationMatrix(mRotationMatrix, null, accelerometerReading, magnetometerReading);
            SensorManager.getOrientation(mRotationMatrix, orientationAngles);

            lpAzimuth.update(orientationAngles[0]);
            lpPitch.update(orientationAngles[1]);
            lpRoll.update(orientationAngles[2]);

            publish();
        }

        @Override
        public void onLocationChanged(Location location) {
            lpSpeed.update(location.getSpeed());
            //lpSpeedAccuracy.update(location.getSpeedAccuracyMetersPerSecond());
            lpAltitude.update(location.getAltitude());
            //lpVerticalAccuracy.update(location.getVerticalAccuracyMeters());
            lpLatitude.update(location.getLatitude());
            lpLongitude.update(location.getLongitude());
            lpHorizontalAccuracy.update(location.getAccuracy());
            lpBearing.update(location.getBearing());
            //lpBearingAccuracy.update(location.getBearingAccuracyDegrees());

            publish();
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
