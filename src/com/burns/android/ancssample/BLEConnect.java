package com.burns.android.ancssample;


import com.burns.android.ancssample.ANCSGattCallback.StateListener;
import com.burns.android.ancssample.BLEservice.MyBinder;
import com.burns.android.ancssample.R;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;


public class BLEConnect extends Activity implements StateListener{
	private static String TAG="BLEConnect";
	SharedPreferences mSharedP;
	String addr;
	boolean mAuto;	// whether connectGatt(,auto,)
	boolean mBond;
	TextView mViewState;
	CheckBox mExitService;
	BLEservice mBLEservice;
	Intent mIntent;
	int mCachedState;
	BroadcastReceiver mBtOnOffReceiver;
	@Override
	public void onCreate(Bundle b) {
		super.onCreate(b);
		Log.i(TAG,"onCreate");
		
		setContentView(R.layout.ble_connect);
		mViewState = (TextView)findViewById(R.id.ble_state);
		mExitService= (CheckBox)findViewById(R.id.exit_service);
		
		addr = getIntent().getStringExtra("addr");
		mAuto = getIntent().getBooleanExtra("auto", true);
		
		mSharedP = getSharedPreferences(MainActivity.PREFS_NAME, 0);
		
		Log.e(TAG,"mAuto:"+ mAuto);
		
		if(!mAuto){
			mViewState.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View arg0) {
					if (null != mBLEservice) {
						mBLEservice.connect();
						Toast.makeText(BLEConnect.this,
								R.string.connect_notice, Toast.LENGTH_SHORT)
								.show();
					}
				}
			});
		}
		
		mCachedState = getIntent().getIntExtra("state", 0);
		mIntent = new Intent(this, BLEservice.class);
		mIntent.putExtra("addr", addr);
		mIntent.putExtra("auto", mAuto);
		startService(mIntent);

//		if (!BluetoothAdapter.checkBluetoothAddress(addr)) {
//			finish();
//			return;
//		}
		mBtOnOffReceiver = new BroadcastReceiver() {
			public void onReceive(Context arg0, Intent i) {
				// action must be bt on/off .
				String act = i.getAction();
				if (act.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
					int state = i.getIntExtra(BluetoothAdapter.EXTRA_STATE,
							BluetoothAdapter.ERROR);
					if (state != BluetoothAdapter.STATE_ON) {
						finish();
					}
				}
			}
		};
	}
	@Override
	public void onStart(){
		
		super.onStart();
		Log.i(TAG,"onStart");
		IntentFilter filter = new IntentFilter();
		filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);// bt on/off

		registerReceiver(mBtOnOffReceiver, filter);
	}
	@Override
	public void onResume(){
		super.onResume();
		Log.i(TAG,"onResume");
		bindService(mIntent, conn, 1);
	}
	@Override
	public void onStop() {
		Log.i(TAG,"onStop");
		unregisterReceiver(mBtOnOffReceiver);
		unbindService(conn);
		if ( mExitService.isChecked()) {
			stopService(mIntent);
		}
		super.onStop();
	}

	ServiceConnection conn = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName cn, IBinder binder) {
			Log.i(TAG,"onServiceConnected");
			MyBinder b = (MyBinder) binder;
			mBLEservice = b.getService();
			mBond = true;
			startConnectGatt();//now not connect ,

		}

		@Override
		public void onServiceDisconnected(ComponentName cn) {
			mBond = false;
			Log.i(TAG,"onServiceDisconnected");
		}
	};

	private void startConnectGatt() {
		//FIXME: there is a bug in here.
		Log.i(TAG,"startConnectGatt " + "mCachedState:" + mCachedState + "getmBleANCS_state:" + mBLEservice.getmBleANCS_state());
		if(mBLEservice.getmBleANCS_state() !=ANCSGattCallback.BleDisconnect)
		{
			final String str = mBLEservice.getStateDes();
			mViewState.setText(str);
		}
		else if (ANCSGattCallback.BleDisconnect == mCachedState) {
			Log.i(TAG, "connect ble");
			mBLEservice.startBleConnect(addr, mAuto);
			mBLEservice.registerStateChanged(this);
		} else { // just display current state
			
			final String str = mBLEservice.getStateDes();
			mViewState.setText(str);
		}
	}
	
	@Override
	public void onStateChanged( final int state) {
		SharedPreferences.Editor edit=mSharedP.edit();
		edit.putInt(MainActivity.BleStateKey, state);
		edit.putString(MainActivity.BleAddrKey, addr);
		edit.putBoolean(MainActivity.BleAutoKey, mAuto);
//		edit.commit();
//		log("put state : "+state);
		runOnUiThread(new Runnable() {
			public void run() {
				mViewState.setText(mBLEservice.getStateDes() );
			}
		});
	}

}
