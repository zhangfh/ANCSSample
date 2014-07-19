package com.burns.android.ancssample;

import java.util.ArrayList;
import java.util.UUID;

import android.app.NotificationManager;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class ANCSGattCallback extends BluetoothGattCallback {
	public static final int BleDisconnect = 0;//this is same to onConnectionStateChange()'s state
	public static final int BleAncsConnected = 10;// connected to iOS's ANCS
	public static final int BleBuildStart = 1;//after connectGatt(), before onConnectionStateChange()
	public static final int BleBuildConnectedGatt=2; 	//onConnectionStateChange() state==2
	public static final int BleBuildDiscoverService=3;//discoverServices()... this block
	public static final int BleBuildDiscoverOver=4;		//discoverServices() ok
	public static final int BleBuildSetingANCS=5;		//settingANCS	eg. need pwd...
	public static final int BleBuildNotify=6; //notify arrive	
	
	private static final String TAG = "ANCSGattCallback";
	private Context mContext;
	IOSNotification mnoti;
	public int mBleState;
	public static ANCSParser mANCSHandler;
	private BluetoothGatt mBluetoothGatt;
	BluetoothGattService mANCSservice;
	boolean mWritedNS,mWriteNS_DespOk;
	private ArrayList<StateListener> mStateListeners=new ArrayList<StateListener>();

	public interface StateListener{
		public void onStateChanged(int state);
	}
	
	public ANCSGattCallback(Context c,ANCSParser ancs){
		mContext = c;
		mANCSHandler = ancs;
	}

	public void addStateListen(StateListener sl){
		if(!mStateListeners.contains(sl)){
			mStateListeners.add(sl);
			sl.onStateChanged(mBleState);
		}
	}


	public void stop(){
		Log.i(TAG,"stop connectGatt..");
		mBleState = BleDisconnect;
		for(StateListener sl: mStateListeners){
			sl.onStateChanged(mBleState);
		}
		if(null != mBluetoothGatt){
			mBluetoothGatt.disconnect();
			mBluetoothGatt.close();
		}
		mBluetoothGatt = null;
		mANCSservice = null;
		mStateListeners.clear();
	}


	public void setBluetoothGatt(BluetoothGatt BluetoothGatt) {
		mBluetoothGatt = BluetoothGatt;
	}
	
	public void setStateStart(){
		mBleState = BleBuildStart;
		for (StateListener sl : mStateListeners) {
			sl.onStateChanged(mBleState);
		}
	}

	public String getState() {
		String state = "[unknown]" ;
		switch (mBleState) {
		case BleDisconnect: // 0
			state = "GATT [Disconnected]\n\n";
			break;
		case BleBuildStart: // 1
			state = "waiting state change after connectGatt()\n\n";
			break;
		case BleBuildConnectedGatt: // 2
			state = "GATT [Connected]\n\n";
			break;
		case BleBuildDiscoverService: // 3
			state = "GATT [Connected]\n"+"discoverServices...\n";
			break;
		case BleBuildDiscoverOver: // 4
			state = "GATT [Connected]\n"+"discoverServices OVER\n";
			break;
		case BleBuildSetingANCS: // 5
			state = "GATT [Connected]\n"+"discoverServices OVER\n"+"setting ANCS...password";
			break;
		case BleBuildNotify: // 6
			state = "ANCS notify arrive\n";
			break;
		case BleAncsConnected: // 10
			state = "GATT [Connected]\n"+"discoverServices OVER\n"+"ANCS[Connected] success !!";
			break;
		}
		return state;
	}


	@Override
	public void onCharacteristicChanged(BluetoothGatt gatt,
			BluetoothGattCharacteristic cha) {
		UUID uuid = cha.getUuid();
		if (uuid.equals(GattConstant.Apple.sUUIDChaNotify)) {

			Log.i(TAG,"Notify uuid");
			byte[] data = cha.getValue();
			mANCSHandler.onNotification(data);
			
			mBleState = BleBuildNotify;//6
			for (StateListener sl : mStateListeners) {
				sl.onStateChanged(mBleState);
			}
		} else if (uuid.equals(GattConstant.Apple.sUUIDDataSource)) {

			byte[] data = cha.getValue();
			mANCSHandler.onDSNotification(data);
			Log.i(TAG,"datasource uuid");
		} else {
		}
	}

	@Override
	public void onConnectionStateChange(BluetoothGatt gatt, int status,
			int newState) {

		Log.i(TAG,"onConnectionStateChange"+ "newState " + newState + "status:" + status);
		mBleState = newState;
		//below code is necessary?
		for (StateListener sl : mStateListeners) {
			sl.onStateChanged(mBleState);
		}
		if (newState == BluetoothProfile.STATE_CONNECTED
				&& status == BluetoothGatt.GATT_SUCCESS) {
			Log.i(TAG,"start discover service");
			mBleState = BleBuildDiscoverService;
			for(StateListener sl: mStateListeners){
				sl.onStateChanged(mBleState);
			}
			mBluetoothGatt.discoverServices();
			Log.i(TAG,"discovery service end");
			mBleState = BleBuildDiscoverOver;
			for(StateListener sl: mStateListeners){
				sl.onStateChanged(mBleState);
			}
		} else if (0 == newState/* && mDisconnectReq*/ && mBluetoothGatt != null) {
		}
	}

	@Override	// New services discovered
	public void onServicesDiscovered(BluetoothGatt gatt, int status) {

		Log.i(TAG,"onServicesDiscovered "+"status:" + status);
		if(status != 0 ) return;
		BluetoothGattService ancs = gatt.getService(GattConstant.Apple.sUUIDANCService);
		if (ancs == null) {
			Log.i(TAG,"cannot find ANCS uuid");
			return;
		}

		Log.i(TAG,"find ANCS service");

		BluetoothGattCharacteristic DScha = ancs.getCharacteristic(GattConstant.Apple.sUUIDDataSource);
		if (DScha == null) {
			Log.i(TAG,"cannot find DataSource(DS) characteristic");
			return;
		}
		boolean registerDS = mBluetoothGatt.setCharacteristicNotification(DScha,true);
		if (!registerDS) {
			Log.i(TAG," Enable (DS) notifications failed. ");
			return;
		}
		BluetoothGattDescriptor descriptor = DScha.getDescriptor(GattConstant.DESCRIPTOR_UUID);
		if (null != descriptor) {
			boolean r = descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			boolean rr = mBluetoothGatt.writeDescriptor(descriptor);

			Log.i(TAG,"Descriptoer setvalue " + r + "writeDescriptor() " + rr);
		} else {
			Log.i(TAG,"can not find descriptor from (DS)");
		}
		mWriteNS_DespOk = mWritedNS = false;
		DScha = ancs.getCharacteristic(GattConstant.Apple.sUUIDControl);
		if (DScha == null) {
			Log.i(TAG,"can not find ANCS's ControlPoint cha ");
		}
		
		mANCSservice = ancs;
		mANCSHandler.setService(ancs, mBluetoothGatt);
		ANCSParser.get().reset();
		Log.i(TAG,"found ANCS service & set DS character,descriptor OK !");

	}

	@Override//the result of a descriptor write operation.
	public void onDescriptorWrite(BluetoothGatt gatt,
			BluetoothGattDescriptor descriptor, int status) {
		Log.i(TAG,"onDescriptorWrite"+"status:" + status);

		if (15 == status || 5 == status) {
			mBleState = BleBuildSetingANCS;//5
			for (StateListener sl : mStateListeners) {
				sl.onStateChanged(mBleState);
			}
			return;
		}
		if (status != BluetoothGatt.GATT_SUCCESS)
			return;
		//for some ble device, writedescriptor on sUUIDDataSource will return 133. fixme.
		// status is 0, SUCCESS. 
		if (mWritedNS && mWriteNS_DespOk) {
			for (StateListener sl : mStateListeners) {
				mBleState = BleAncsConnected;
				sl.onStateChanged(mBleState);
			}
		}
		if (mANCSservice != null && !mWritedNS) {	// set NS
			mWritedNS = true;
			BluetoothGattCharacteristic cha = mANCSservice
					.getCharacteristic(GattConstant.Apple.sUUIDChaNotify);
			if (cha == null) {
				Log.i(TAG,"can not find ANCS's NS cha");
				return;
			}
			boolean registerNS = mBluetoothGatt.setCharacteristicNotification(
					cha, true);
			if (!registerNS) {
				Log.i(TAG," Enable (NS) notifications failed  ");
				return;
			}
			BluetoothGattDescriptor desp = cha.getDescriptor(GattConstant.DESCRIPTOR_UUID);
			if (null != desp) {
				boolean r=desp.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
				boolean rr = mBluetoothGatt.writeDescriptor(desp);
				mWriteNS_DespOk = rr;
				Log.i(TAG,"(NS)Descriptor.setValue(): " + r + ",writeDescriptor(): " + rr);
			} else {
				Log.i(TAG,"null descriptor");
			}
		}
		//add a notification
		mnoti=new  IOSNotification();
		mnoti.title="ANCS_Server";
		mnoti.message = "ANCS_run";
		mnoti.uid=0;
		NotificationCompat.Builder build = new NotificationCompat.Builder(mContext)
		.setSmallIcon(R.drawable.ic_launcher)
		.setContentTitle(mnoti.title)
		.setContentText(mnoti.message);
		((NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE)).notify(mnoti.uid, build.build());
		//
	}

}