package fi.elidor.expose;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity {

    private Handler handler = new Handler();
/*
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

            } finally {
                handler.postDelayed(sensorPublisher, 1000);
            }
        }
    };
*/
    private TextView
            viewAzimuth, viewPitch, viewRoll, viewSpeed, viewAltitude, viewBearing,
            viewLatitude, viewLongitude, viewHorizontalAccuracy, viewBearingAccuracy, viewSpeedAccuracy,
            viewVerticalAccuracy;

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

        Intent webServerService = new Intent(this, WebServerService.class);
        startService(webServerService);
    }
}
