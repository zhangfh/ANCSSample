package com.burns.android.ancssample;


import com.burns.android.ancssample.ANCSGattCallback.StateListener;
import com.burns.android.ancssample.R;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class BLEservice extends Service implements ANCSParser.onIOSNotification
		, ANCSGattCallback.StateListener{
	private static final String TAG="BLEservice";
	private final IBinder mBinder = new MyBinder();
	private ANCSParser mANCSHandler;
	private ANCSGattCallback mANCScb;
	BluetoothGatt mBluetoothGatt;
	BroadcastReceiver mBtOnOffReceiver;
	boolean mAuto;
	String addr;
	int mBleANCS_state = 0;
    public class MyBinder extends Binder {
    	BLEservice getService() {
            // Return this instance  so clients can call public methods
            return BLEservice.this;
        }
    }
    @SuppressLint("HandlerLeak")
	private Handler mHandler = new Handler(){
    	@Override
    	public void handleMessage(Message msg){
			switch (msg.what) {
			case 11:	//bt off, stopSelf()
				stopSelf();
				startActivityMsg();
				break;
			}
    	}
    };
    // when bt off,  show a Message to notify user that ble need re_connect
    private void startActivityMsg(){
    	Intent i = new Intent(this,Notice.class);
    	i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	startActivity(i);
    }
    
	@Override
	public void onCreate() {
		super.onCreate();
		Log.i(TAG, "onCreate");
		mANCSHandler = ANCSParser.getDefault(this);
		mANCScb = new ANCSGattCallback(this, mANCSHandler);
		mBtOnOffReceiver = new BroadcastReceiver() {
			public void onReceive(Context arg0, Intent i) {
				// action must be bt on/off .
				int state = i.getIntExtra(BluetoothAdapter.EXTRA_STATE,
						BluetoothAdapter.ERROR);
				if (state == BluetoothAdapter.STATE_OFF) {
					Log.i(TAG,"bluetooth OFF !");
					mHandler.sendEmptyMessageDelayed(11, 500);
				}
			}
		};
		IntentFilter filter= new IntentFilter();
		filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);// bt on/off
		registerReceiver(mBtOnOffReceiver, filter);
		Log.i(TAG,"onCreate()");
	}
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			mAuto = intent.getBooleanExtra("auto", true);
			addr = intent.getStringExtra("addr");
		}
		Log.i(TAG,"onStartCommand() flags="+flags+",stardId="+startId);
		return START_STICKY_COMPATIBILITY;
		//return startId;
	}

	@Override
	public void onDestroy() {
		Log.i(TAG," onDestroy()");
		mANCScb.stop();
		unregisterReceiver(mBtOnOffReceiver);
		Editor e =getSharedPreferences(MainActivity.PREFS_NAME, 0).edit();
		e.putInt(MainActivity.BleStateKey, ANCSGattCallback.BleDisconnect);
		e.commit();
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent i) {
		Log.i(TAG," onBind()thread id ="+android.os.Process.myTid());
		return mBinder;
	}

	//** when ios notification changed
	@Override
	public void onIOSNotificationAdd(IOSNotification noti) {
		NotificationCompat.Builder build = new
		NotificationCompat.Builder(this)
		.setSmallIcon(R.drawable.ic_launcher)
		.setContentTitle(noti.title)
		.setContentText(noti.message);		
		((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(noti.uid, build.build());
	}

	@Override
	public void onIOSNotificationRemove(int uid) {
		((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).cancel(uid);
	}
	
	//** public method , for client to call
	public void startBleConnect(String addr, boolean auto) {
		Log.i(TAG,"startBleConnect");
		if (mBleANCS_state != 0) {
			Log.i(TAG,"stop ancs,then restart it");
			mANCScb.stop();
		}
		mAuto = auto;
		this.addr = addr;
		BluetoothDevice dev = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(addr);
		mANCSHandler.listenIOSNotification(this);
		mBluetoothGatt = dev.connectGatt(this, auto, mANCScb);
		mANCScb.setBluetoothGatt(mBluetoothGatt);
		mANCScb.setStateStart();
	}

	public void registerStateChanged(StateListener sl) {
		Log.i(TAG,"registerStateChanged");
		if (null != sl)
			mANCScb.addStateListen(sl);
		mANCScb.addStateListen(this);
	}
	public void connect(){
		if (!mAuto)
			mBluetoothGatt.connect();
	}
	
	public String getStateDes(){
		return mANCScb.getState();
	}
	
	public int getmBleANCS_state() {
		return mBleANCS_state;
	}

	@Override
	public void onStateChanged(int state) {
		mBleANCS_state = state;
	}
	
}
