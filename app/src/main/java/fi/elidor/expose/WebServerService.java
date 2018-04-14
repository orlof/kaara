package fi.elidor.expose;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class WebServerService extends Service {

    private SensorPublisherServer server;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        Log.i("SENSPUB", "Creating and starting SensorPublisherService");
        super.onCreate();

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakelockTag");
        wakeLock.acquire();

        Intent notificationIntent = new Intent(this, MainActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, "mci01")
//                .setSmallIcon(R.mipmap.sym_def_app_icon)
                .setContentTitle("My Awesome App")
                .setContentText("Doing some work...")
                .setContentIntent(pendingIntent).build();

        startForeground(1337, notification);

        server = new SensorPublisherServer(this);
        server.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i("SENSPUB", "Destroying SensorPublisherService");
        //server.close();
        //super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}