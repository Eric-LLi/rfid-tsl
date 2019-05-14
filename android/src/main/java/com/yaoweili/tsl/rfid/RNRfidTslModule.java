
package com.yaoweili.tsl.rfid;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class RNRfidTslModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

	private final ReactApplicationContext reactContext;
	private RNRfidTslThread scannerThread = null;

	public RNRfidTslModule(ReactApplicationContext reactContext) {
		super(reactContext);
		this.reactContext = reactContext;
		this.reactContext.addLifecycleEventListener(this);

		scannerThread = new RNRfidTslThread(reactContext) {
			@Override
			public void dispatchEvent(String name, WritableMap data) {
				RNRfidTslModule.this.reactContext
						.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(name, data);
			}

			@Override
			public void dispatchEvent(String name, String data) {
				RNRfidTslModule.this.reactContext
						.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(name, data);
			}

			@Override
			public void dispatchEvent(String name, WritableArray data) {
				RNRfidTslModule.this.reactContext
						.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(name, data);
			}
		};

		scannerThread.start();
	}

	@Override
	public String getName() {
		return "RNRfidTsl";
	}

	@Override
	public void onHostResume() {
		if (scannerThread != null) {
			scannerThread.onHostResume();
		}
	}

	@Override
	public void onHostPause() {
		if (scannerThread != null) {
			scannerThread.onHostPause();
		}
	}

	@Override
	public void onHostDestroy() {
		if (scannerThread != null) {
			scannerThread.onHostDestroy();
		}
	}

	@ReactMethod
	public void ConnectDevice() {
		if (scannerThread != null) {
			scannerThread.ConnectDevice();
		}
	}
}