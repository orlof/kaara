package fi.elidor.expose;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    private Handler handler = new Handler();

    private Runnable sensorPublisher = new Runnable() {
        private String str(double val) {
            return String.format("%s", Math.round(val));
        }

        @Override
        public void run() {
            try {
                viewAzimuth.setText(str(Math.toDegrees(lpAzimuth.value)));
                viewPitch.setText(str(Math.toDegrees(lpPitch.value)));
                viewRoll.setText(str(Math.toDegrees(lpRoll.value)));

                viewSpeed.setText(str(lpSpeed.value));
                viewSpeedAccuracy.setText(str(lpSpeedAccuracy.value));

                viewAltitude.setText(str(lpAltitude.value));
                viewVerticalAccuracy.setText(str(lpVerticalAccuracy.value));

                viewBearing.setText(str(lpBearing.value));
                viewBearingAccuracy.setText(str(lpBearingAccuracy.value));

                viewLatitude.setText(String.format("%s", lpLatitude.value));
                viewLongitude.setText(String.format("%s", lpLongitude.value));
                viewHorizontalAccuracy.setText(str(lpHorizontalAccuracy.value));

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

                networkServer.publish(root);

            } finally {
                handler.postDelayed(sensorPublisher, 1000);
            }
        }
    };

    private SensorManager mSensorManager;
    private Sensor accelerometer, magnetometer;
    private LocationManager locationManager;

    private final float[] mRotationMatrix = new float[9];

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

    private final float[] orientationAngles = new float[3];
    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];

    private TextView
            viewAzimuth, viewPitch, viewRoll, viewSpeed, viewAltitude, viewBearing,
            viewLatitude, viewLongitude, viewHorizontalAccuracy, viewBearingAccuracy, viewSpeedAccuracy,
            viewVerticalAccuracy;

    private NetworkServer networkServer;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(
                this,
                new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.INTERNET
                }, 0);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        viewAzimuth = findViewById(R.id.azimuth);
        viewPitch = findViewById(R.id.pitch);
        viewRoll = findViewById(R.id.roll);
        viewSpeed = findViewById(R.id.speed);
        viewSpeedAccuracy = findViewById(R.id.speed_accuracy);
        viewAltitude = findViewById(R.id.altitude);
        viewVerticalAccuracy = findViewById(R.id.vertical_accuracy);
        viewBearing = findViewById(R.id.bearing);
        viewBearingAccuracy = findViewById(R.id.bearing_accuracy);

        viewLatitude = findViewById(R.id.latitude);
        viewLongitude = findViewById(R.id.longitude);
        viewHorizontalAccuracy = findViewById(R.id.horizontal_accuracy);

        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while(networkInterfaces.hasMoreElements()) {
                NetworkInterface ni = networkInterfaces.nextElement();

                Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
                while(inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();

                    if(inetAddress instanceof Inet4Address && !inetAddress.isLoopbackAddress())
                        Log.d("NIC", ni.getDisplayName() + inetAddress.getHostAddress());
                }
            }
        }
        catch (SocketException ex)
        {
            Log.e("ServerActivity", ex.toString());
        }

        networkServer = new NetworkServer("192.168.43.1", 3451);
        networkServer.start();

        mSensorManager.registerListener(this, accelerometer, 1000*250, 1000*100);
        mSensorManager.registerListener(this, magnetometer, 1000*250, 1000*100);
//        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, 1000*500);
//        mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL, 1000*500);

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 1, this);

        handler.postDelayed(sensorPublisher, 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mSensorManager.unregisterListener(this);
        locationManager.removeUpdates(this);

        handler.removeCallbacks(sensorPublisher);
        networkServer.close();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
        // You must implement this callback in your code.
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
