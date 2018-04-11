package fi.elidor.expose;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class WebServerService extends Service {

    private SensorPublisherServer server = null;

    @Override
    public void onCreate() {
        Log.i("SENSPUB", "Creating and starting SensorPublisherService");
        super.onCreate();
        server = new SensorPublisherServer(this);
        server.start();
    }

    @Override
    public void onDestroy() {
        Log.i("SENSPUB", "Destroying SensorPublisherService");
        server.close();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}