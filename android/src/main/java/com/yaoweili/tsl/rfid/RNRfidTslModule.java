
package com.yaoweili.tsl.rfid;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
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

		InitialThread();
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
	public void InitialThread() {
		scannerThread = new RNRfidTslThread(reactContext) {
			@Override
			public void dispatchEvent(String name, WritableMap data) {
				RNRfidTslModule.this.reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
						.emit(name, data);
			}

			@Override
			public void dispatchEvent(String name, String data) {
				RNRfidTslModule.this.reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
						.emit(name, data);
			}

			@Override
			public void dispatchEvent(String name, WritableArray data) {
				RNRfidTslModule.this.reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
						.emit(name, data);
			}

			@Override
			public void dispatchEvent(String name, boolean data) {
				RNRfidTslModule.this.reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
						.emit(name, data);
			}
		};

		scannerThread.start();
	}

	@ReactMethod
	public void Init(Promise promise) {
		if (scannerThread != null) {
			promise.resolve(scannerThread.Init());
		}
	}

	@ReactMethod
	public void ConnectDevice(Promise promise) {
		if (scannerThread != null) {
			try {
				promise.resolve(scannerThread.ConnectDevice());
			} catch (Exception ex) {
				promise.reject(ex);
			}

		}
	}

	@ReactMethod
	public void DisconnectDevice() {
		if (scannerThread != null) {
			scannerThread.DisconnectDevice();
		}
	}

	@ReactMethod
	public void IsConnected(Promise promise) {
		if (scannerThread != null) {
			promise.resolve(scannerThread.IsConnected());
		}
	}

	@ReactMethod
	public void AttemptToReconnect(Promise promise) {
		try {
			if (scannerThread != null) {
				promise.resolve(scannerThread.AttemptToReconnect());
			}
		} catch (Exception err) {
			promise.reject(err);
		}

	}

	@ReactMethod
	public void SaveCurrentRoute(String value) {
		if (scannerThread != null) {
			scannerThread.SaveCurrentRoute(value);
		}
	}

	@ReactMethod
	public void GetDeviceList(Promise promise) {
		if (scannerThread != null) {
			try {
				promise.resolve(scannerThread.GetDeviceList());
			} catch (Exception ex) {
				promise.reject(ex);
			}
		}

	}

	@ReactMethod
	public void SaveSelectedScanner(String name) {
		if (scannerThread != null) {
			scannerThread.SaveSelectedScanner(name);
		}
	}

	@ReactMethod
	public void GetBatteryLevel(Promise promise) {
		if (scannerThread != null) {
			try {
				promise.resolve(scannerThread.GetBatteryLevel());
			} catch (Exception ex) {
				promise.reject(ex);
			}

		}

	}

	@ReactMethod
	public void SetBuzzer(boolean value) {
		if (scannerThread != null) {
			scannerThread.SetBuzzer(value);
		}
	}

	@ReactMethod
	public void CleanCacheTags() {
		if (scannerThread != null) {
			scannerThread.CleanCacheTags();
		}
	}

	@ReactMethod
	public void ProgramTag(String oldTag, String newTag, Promise promise) {
		if (scannerThread != null) {
			try {
				promise.resolve(scannerThread.ProgramTag(oldTag, newTag));
			} catch (Exception err) {
				promise.reject(err);
			}
		}
	}

	@ReactMethod
	public void GetAntennaLevel(Promise promise) {
		if (scannerThread != null) {
			try {
				promise.resolve(scannerThread.GetAntennaLevel() + "");
			} catch (Exception err) {
				promise.reject(err);
			}

		}
	}

	@ReactMethod
	public void SetAntennaLevel(int level) {
		if (scannerThread != null) {
			scannerThread.SetAntennaLevel(level);
		}
	}

	@ReactMethod
	public void setEnabled(boolean value) {
		if (scannerThread != null) {
			scannerThread.setEnabled(value);
		}
	}

	@ReactMethod
	public void TagITReadBarcode(boolean value, Promise promise) {
		if (scannerThread != null) {
			scannerThread.TagITReadBarcode(value);
			promise.resolve("Done");
		}
	}

	@ReactMethod
	public void SaveTagID(String tag, Promise promise) {
		try {
			if (this.scannerThread != null) {
				promise.resolve(scannerThread.SaveTagID(tag));
			}
		} catch (Exception err) {
			promise.reject(err);
		}

	}

	@ReactMethod
	public void LocateMode(boolean value, Promise promise) {
		try {
			if (this.scannerThread != null) {
				promise.resolve(scannerThread.LocateMode(value));
			}
		} catch (Exception err) {
			promise.reject(err);
		}

	}
}