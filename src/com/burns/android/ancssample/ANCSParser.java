package com.burns.android.ancssample;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class ANCSParser {
	// ANCS constants
	public final static int NotificationAttributeIDAppIdentifier = 0;
	public final static int NotificationAttributeIDTitle = 1; //, (Needs to be followed by a 2-bytes max length parameter)
	public final static int NotificationAttributeIDSubtitle = 2; //, (Needs to be followed by a 2-bytes max length parameter)
	public final static int NotificationAttributeIDMessage = 3; //, (Needs to be followed by a 2-bytes max length parameter)
	public final static int NotificationAttributeIDMessageSize = 4; //,
	public final static int NotificationAttributeIDDate = 5; //,
	public final static int AppAttributeIDDisplayName = 0;

	public final static int CommandIDGetNotificationAttributes = 0;
	public final static int CommandIDGetAppAttributes = 1;

	public final static int EventFlagSilent = (1 << 0);
	public final static int EventFlagImportant = (1 << 1);
	public final static int EventIDNotificationAdded = 0;
	public final static int EventIDNotificationModified = 1;
	public final static int EventIDNotificationRemoved = 2;

	public final static int CategoryIDOther = 0;
	public final static int CategoryIDIncomingCall = 1;
	public final static int CategoryIDMissedCall = 2;
	public final static int CategoryIDVoicemail = 3;
	public final static int CategoryIDSocial = 4;
	public final static int CategoryIDSchedule = 5;
	public final static int CategoryIDEmail = 6;
	public final static int CategoryIDNews = 7;
	public final static int CategoryIDHealthAndFitness = 8;
	public final static int CategoryIDBusinessAndFinance = 9;
	public final static int CategoryIDLocation = 10;
	public final static int CategoryIDEntertainment = 11;

	// !ANCS constants

	private final static int MSG_ADD_NOTIFICATION = 100;
	private final static int MSG_DO_NOTIFICATION = 101;
	private final static int MSG_RESET = 102;
	private final static int MSG_ERR = 103;
	private final static int MSG_CHECK_TIME = 104;
	private final static int MSG_FINISH = 105;
	private final static int FINISH_DELAY = 700;
	private final static int TIMEOUT = 15 * 1000;
	protected static final String TAG = "ANCSParser";

	private List<ANCSData> mPendingNotifcations = new LinkedList<ANCSData>();
	private Handler mHandler;

	private ANCSData mCurData;
	BluetoothGatt mGatt;

	BluetoothGattService mService;
	Context mContext;
	private static ANCSParser sInst;
	
	private ArrayList<onIOSNotification> mListeners=new ArrayList<onIOSNotification>(); 
	public interface onIOSNotification{
		void onIOSNotificationAdd(IOSNotification n);
		void onIOSNotificationRemove(int uid);
	}
	
	private ANCSParser(Context c) {
		mContext = c;
		mHandler = new Handler(c.getMainLooper()) {
			@Override
			public void handleMessage(Message msg) {
				int what = msg.what;
				if (MSG_CHECK_TIME == what) {
					if (mCurData == null) {
						return;
					}
					if (System.currentTimeMillis() >= mCurData.timeExpired) {
		
						Log.i(TAG,"msg timeout!");
					}
				} else if (MSG_ADD_NOTIFICATION == what) {
					mPendingNotifcations.add(new ANCSData((byte[]) msg.obj));
					mHandler.sendEmptyMessage(MSG_DO_NOTIFICATION);
				} else if (MSG_DO_NOTIFICATION == what) {
					processNotificationList();
				} else if (MSG_RESET == what) {
					mHandler.removeMessages(MSG_ADD_NOTIFICATION);
					mHandler.removeMessages(MSG_DO_NOTIFICATION);
					mHandler.removeMessages(MSG_RESET);
					mHandler.removeMessages(MSG_ERR);
					mPendingNotifcations.clear();
					mCurData = null;
		
					Log.i(TAG,"ANCSHandler reseted");
				} else if (MSG_ERR == what) {
	
					Log.i(TAG,"error,skip_cur_data");
					mCurData.clear();
					mCurData = null;
					mHandler.sendEmptyMessage(MSG_DO_NOTIFICATION);
				} else if (MSG_FINISH == what) {
					Log.i(TAG,"msg data.finish()");
					if(null!=mCurData)
					mCurData.finish();
				}
			}
		};
	}
	
	public void listenIOSNotification(onIOSNotification lis){
		if(!mListeners.contains(lis))
			mListeners.add(lis);
	}
	

	public void setService(BluetoothGattService bgs, BluetoothGatt bg) {
		mGatt = bg;
		mService = bgs;
	}

	public static ANCSParser getDefault(Context c) {
		if (sInst == null) {
			sInst = new ANCSParser(c);
		}
		return sInst;
	}

	public static ANCSParser get() {
		return sInst;
	}

	private void sendNotification(final IOSNotification noti) {
		Log.i(TAG,"[Add Notification] : "+noti.uid);
		for(onIOSNotification lis: mListeners){
			lis.onIOSNotificationAdd(noti);
		}
	}
	private void cancelNotification(int uid){
		Log.i(TAG,"[cancel Notification] : "+uid);
		for(onIOSNotification lis: mListeners){
			lis.onIOSNotificationRemove(uid);
		}
	}
	
	private class ANCSData {
		long timeExpired;
		int curStep = 0;

		final byte[] notifyData; // 8 bytes

		ByteArrayOutputStream bout;
		IOSNotification noti;

		ANCSData(byte[] data) {
			notifyData = data;
			curStep = 0;
			timeExpired = System.currentTimeMillis();
			noti=new  IOSNotification();
		}

		void clear() {
			if (bout != null) {
				bout.reset();
			}
			bout = null;
			curStep = 0;
		}

		int getUID() {
			return (0xff & notifyData[7] << 24) | (0xff & notifyData[6] << 16)
					| (0xff & notifyData[5] << 8) | (0xff & notifyData[4]);
		}

		void finish() {
			if (null == bout) {
				return;
			}
	
			final byte[] data = bout.toByteArray();
			logD(data);
			if (data.length < 5) {
				return; // 
			}
			// check if finished ?
			int cmdId = data[0]; // should be 0								//0 commandID
			if (cmdId != 0) {
				Log.i(TAG,"bad cmdId: " + cmdId);
				return;
			}
			int uid = ((0xff&data[4]) << 24) | ((0xff &data[3]) << 16)			
					| ((0xff & data[2]) << 8) | ((0xff &data[1]));
			if (uid != mCurData.getUID()) {
	
				Log.i(TAG,"bad uid: " + uid + "->" + mCurData.getUID());
				return;
			}

			// read attributes
			noti.uid = uid;
			int curIdx = 5; //hard code
			while (true) {
				if (noti.isAllInit()) {
					break; 
				}
				if (data.length < curIdx + 3) {
					return;
				}
				// attributes head
				int attrId = data[curIdx];
				int attrLen = ((data[curIdx + 1])&0xFF) | (0xFF&(data[curIdx + 2] << 8));
				curIdx += 3;
				if (data.length < curIdx + attrLen) {
					return;
				}
				String val = new String(data, curIdx, attrLen);//utf-8 encode
				if (attrId == NotificationAttributeIDTitle) { 
					noti.title = val;
				} else if (attrId == NotificationAttributeIDMessage) {
					noti.message = val;
				} else if (attrId == NotificationAttributeIDDate) { 
					noti.date = val;
				} else if (attrId == NotificationAttributeIDSubtitle) {
					noti.subtitle = val;
				} else if (attrId == NotificationAttributeIDMessageSize) {
					noti.messageSize = val;
				}
				curIdx += attrLen;
			}
			Log.i(TAG,"noti.title:"+noti.title);
			Log.i(TAG,"noti.message:"+noti.message);
			Log.i(TAG,"noti.date:"+noti.date);
			Log.i(TAG,"noti.subtitle:"+noti.subtitle);
			Log.i(TAG,"noti.messageSize:"+noti.messageSize);
			Log.i(TAG,"got a notification! data size = "+data.length);
			mCurData = null;
//			mHandler.sendEmptyMessage(MSG_DO_NOTIFICATION); // continue next!
			sendNotification(noti);
		}
	}


	private void processNotificationList() {
		mHandler.removeMessages(MSG_DO_NOTIFICATION);
		// handle curData!
		if (mCurData == null) {
			if (mPendingNotifcations.size() == 0) {
				return;
			}

			mCurData = mPendingNotifcations.remove(0);
			Log.i(TAG,"ANCS New CurData");
		} else if (mCurData.curStep == 0) { // parse notify data
	
			do {
				if (mCurData.notifyData == null
						|| mCurData.notifyData.length != 8) {
					mCurData = null; // ignore
			
					Log.i(TAG,"ANCS Bad Head!");
					break;
				}
				if(EventIDNotificationRemoved ==mCurData.notifyData[0]){
					
					int uid=(mCurData.notifyData[4]&0xff) |
							(mCurData.notifyData[5]&0xff<<8)|
							(mCurData.notifyData[6]&0xff<<16)|
							(mCurData.notifyData[7]&0xff<<24);
					cancelNotification(uid);
					mCurData = null;
					break;
				}
				if (EventIDNotificationAdded != mCurData.notifyData[0]) {
				
					mCurData = null; // ignore
					Log.i(TAG,"ANCS NOT Add!");
					break;
				}
				// get attribute if needed!
				BluetoothGattCharacteristic cha = mService	
						.getCharacteristic(GattConstant.Apple.sUUIDControl);
				if (null != cha ) {
					ByteArrayOutputStream bout = new ByteArrayOutputStream();
	
					bout.write((byte) 0); 
			
					bout.write(mCurData.notifyData[4]);
					bout.write(mCurData.notifyData[5]);
					bout.write(mCurData.notifyData[6]);
					bout.write(mCurData.notifyData[7]);

			
					bout.write(NotificationAttributeIDTitle);
					bout.write(50);	
					bout.write(0);	
					// subtitle
					bout.write(NotificationAttributeIDSubtitle);
					bout.write(100);
					bout.write(0);

					// message 
					bout.write(NotificationAttributeIDMessage);
					bout.write(500);
					bout.write(0);

					// message size
					bout.write(NotificationAttributeIDMessageSize);
					bout.write(10);
					bout.write(0);
					// date 
					bout.write(NotificationAttributeIDDate);
					bout.write(10);
					bout.write(0);

					byte[] data = bout.toByteArray();

					cha.setValue(data);
					mGatt.writeCharacteristic(cha);
					Log.i(TAG,"request ANCS(CP) the data of Notification. = ");
					mCurData.curStep = 1;	
					mCurData.bout = new ByteArrayOutputStream();
					mCurData.timeExpired = System.currentTimeMillis() + TIMEOUT;
//					mHandler.removeMessages(MSG_CHECK_TIME);
//					mHandler.sendEmptyMessageDelayed(MSG_CHECK_TIME, TIMEOUT);
					return;
				} else {
					Log.i(TAG,"ANCS has No Control Point !");
					// has no control!// just vibrate ...
					mCurData.bout = null;
					mCurData.curStep = 1;
				}

			} while (false);
		} else if (mCurData.curStep == 1) {
			// check if finished!	
//			mCurData.finish();
			return;
		} else {
			return;
		}
		mHandler.sendEmptyMessage(MSG_DO_NOTIFICATION); // do next step
	}


	public void onDSNotification(byte[] data) {
		if (mCurData == null) {
	
			Log.i(TAG,"got ds notify without cur data");
			return;
		}
		try {
			mHandler.removeMessages(MSG_FINISH);
			mCurData.bout.write(data);
			mHandler.sendEmptyMessageDelayed(MSG_FINISH, FINISH_DELAY);
		} catch (IOException e) {
			Log.i(TAG,e.toString());
		}
	}

	void onWrite(BluetoothGattCharacteristic characteristic, int status) {
		if (status != BluetoothGatt.GATT_SUCCESS) {
			Log.i(TAG,"write err: " + status);
			mHandler.sendEmptyMessage(MSG_ERR);
		} else {
			Log.i(TAG,"write OK");
			mHandler.sendEmptyMessage(MSG_DO_NOTIFICATION);
		}
	}

	public void onNotification(byte[] data) {
		if (data == null || data.length != 8) {
			Log.i(TAG,"bad ANCS notification data");
			return;
		}
		logD(data);
		Message msg = mHandler.obtainMessage(MSG_ADD_NOTIFICATION);
		msg.obj = data;
		msg.sendToTarget();
	}

	public void reset() {
		mHandler.sendEmptyMessage(MSG_RESET);
	}
	
	void logD(byte[] d){
		StringBuffer sb=new StringBuffer();
		int len = d.length;
		for(int i=0;i<len;i++){
			sb.append(d[i]+", ");
		}
		Log.i(TAG,"log Data size["+len+"] : "+sb);
	}
	
}
