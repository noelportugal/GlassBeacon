package com.noelportugal.glassbeacon;

import java.util.ArrayList;
import java.util.List;
import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.estimote.sdk.Utils;
import com.google.android.glass.timeline.LiveCard;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.widget.RemoteViews;

public class BeaconService extends Service implements SensorEventListener{
	private Handler handler;
	private SensorManager sensorManager;
	private Sensor stepSensor;
	private BeaconManager beaconManager;
	private Region houseRegion;
	private Beacon officeBeacon;
	private Beacon kitchenBeacon;
	private Beacon bedroomBeacon;

	private enum BeaconState {INSIDE, OUTSIDE};
	private BeaconState officeState;
	private BeaconState kitchenState;
	private BeaconState bedroomState;
	private LiveCard liveCard;
	
	private static final String TAG = "BeaconService";
	
	private static final String ESTIMOTE_PROXIMITY_UUID = "B9407F30-F5F8-466E-AFF9-25556B57FE6D";

	private static final int officeMajor = 65501;
	private static final int officeMinor = 26880;

	private static final int kitchenMajor = 32789;
	private static final int kitchenMinor = 44173;

	private static final int bedroomMajor = 54060;
	private static final int bedroomMinor = 38916;

	private static final double enterThreshold = 1.5;
	private static final double exitThreshold = 2.5;



	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	private void runOnUiThread(Runnable runnable) {
		handler.post(runnable);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		handler = new Handler();


		// TODO add sensor data to stop/start beacon scanning
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
		sensorManager.registerListener(this, stepSensor,SensorManager.SENSOR_DELAY_NORMAL);

		officeState = BeaconState.OUTSIDE;
		kitchenState = BeaconState.OUTSIDE;
		bedroomState = BeaconState.OUTSIDE;

		houseRegion = new Region("regionId", ESTIMOTE_PROXIMITY_UUID, null, null);
		beaconManager = new BeaconManager(getApplicationContext());

		// Default values are 5s of scanning and 25s of waiting time to save CPU cycles.
		// In order for this demo to be more responsive and immediate we lower down those values.
		//beaconManager.setBackgroundScanPeriod(TimeUnit.SECONDS.toMillis(5), TimeUnit.SECONDS.toMillis(25));
		//beaconManager.setForegroundScanPeriod(TimeUnit.SECONDS.toMillis(5), TimeUnit.SECONDS.toMillis(10));
		beaconManager.setRangingListener(new BeaconManager.RangingListener() {
			@Override
			public void onBeaconsDiscovered(Region region, final List<Beacon> beacons) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						for (Beacon beacon : beacons) {
							//Log.d(TAG, "MAC = " + beacon.getMacAddress() + ", RSSI = " + -beacon.getRssi());
							if (beacon.getMajor() == officeMajor && beacon.getMinor() == officeMinor ){
								officeBeacon = beacon;
							}
							if (beacon.getMajor() == kitchenMajor && beacon.getMinor() == kitchenMinor){
								kitchenBeacon = beacon;
							}
							if (beacon.getMajor() == bedroomMajor && beacon.getMinor() == bedroomMinor){
								bedroomBeacon = beacon;
							}
						}

						
						if (officeBeacon != null){
							double officeDistance = Utils.computeAccuracy(officeBeacon);
							Log.d(TAG, "officeDistance: " + officeDistance);
							if (officeDistance < enterThreshold && officeState == BeaconState.OUTSIDE){
								officeState = BeaconState.INSIDE;
								showNotification("You are in the office");
							}else if (officeDistance > exitThreshold && officeState == BeaconState.INSIDE){
								officeState = BeaconState.OUTSIDE;
								showNotification("You left the office");
							}
						}
						
						if (kitchenBeacon != null){
							double kitchenDistance = Utils.computeAccuracy(kitchenBeacon);
							Log.d(TAG, "kitchenDistance: " + kitchenDistance);
							if (kitchenDistance < enterThreshold && kitchenState == BeaconState.OUTSIDE){
								kitchenState = BeaconState.INSIDE;
								showNotification("You are in the kitchen");
							}else if (kitchenDistance > exitThreshold && kitchenState == BeaconState.INSIDE){
								kitchenState = BeaconState.OUTSIDE;
								showNotification("You left the kitchen");
							}
						}
						
						if (bedroomBeacon != null){
							double bedroomDistance = Utils.computeAccuracy(bedroomBeacon);
							Log.d(TAG, "bedroomDistance: " + bedroomDistance);
							if (bedroomDistance < enterThreshold && bedroomState == BeaconState.OUTSIDE){
								bedroomState = BeaconState.INSIDE;
								showNotification("You are in the bedroom");
							}else if (bedroomDistance > exitThreshold && bedroomState == BeaconState.INSIDE){
								bedroomState = BeaconState.OUTSIDE;
								showNotification("You left the bedroom");
							}
						}


					}
				});
			}
		});
	}

	private void startScanning(){
		beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
			@Override
			public void onServiceReady() {
				try {
					//beaconManager.startMonitoring(houseRegion);
					beaconManager.startRanging(houseRegion);
				} catch (RemoteException e) {
					Log.d(TAG, "Error while starting Ranging");
				}
			}
		});
	}

	private void stopScanning(){
		try {
			//beaconManager.stopMonitoring(houseRegion);
			beaconManager.stopRanging(houseRegion);
		} catch (RemoteException e) {
			Log.e(TAG, "Cannot stop but it does not matter now", e);
		}
	}

	private void showNotification(String msg) {
		Log.d(TAG, msg);
		RemoteViews views = new RemoteViews(getPackageName(), R.layout.livecard_beacon);
		views.setTextViewText(R.id.livecard_content,msg);
		liveCard = new LiveCard(getApplication(),"beacon");
		liveCard.setViews(views);
		Intent intent = new Intent(getApplication(), BeaconService.class);
		liveCard.setAction(PendingIntent.getActivity(getApplication(), 0, intent, 0));
		liveCard.publish(LiveCard.PublishMode.REVEAL);
	}



	@Override
	public void onDestroy() {
		super.onDestroy();
		beaconManager.disconnect();
	}
	
	

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		ArrayList<String> voiceResults = intent.getExtras().getStringArrayList(RecognizerIntent.EXTRA_RESULTS);
		for (String voice : voiceResults) {
			Log.d(TAG,"voiceResults:voice = " + voice);
			if (voice.contains("stop")){
				Log.d(TAG,"stopScanning");
				stopScanning();
			}else if (voice.contains("start")){
				Log.d(TAG,"startScanning");
				startScanning();
			}
		}
		
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub

	}

}
