package com.yaoweili.tsl.rfid;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
// import android.support.v4.content.LocalBroadcastManager;
import android.media.MediaPlayer;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import android.content.Context;

import com.uk.tsl.rfid.asciiprotocol.AsciiCommander;
import com.uk.tsl.rfid.asciiprotocol.commands.AlertCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.BatteryStatusCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.FactoryDefaultsCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.InventoryCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.SwitchActionCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.WriteTransponderCommand;
import com.uk.tsl.rfid.asciiprotocol.device.ConnectionState;
import com.uk.tsl.rfid.asciiprotocol.device.IAsciiTransport;
import com.uk.tsl.rfid.asciiprotocol.device.ObservableReaderList;
import com.uk.tsl.rfid.asciiprotocol.device.Reader;
import com.uk.tsl.rfid.asciiprotocol.device.ReaderManager;
import com.uk.tsl.rfid.asciiprotocol.device.TransportType;
import com.uk.tsl.rfid.asciiprotocol.enumerations.Databank;
import com.uk.tsl.rfid.asciiprotocol.enumerations.QuerySelect;
import com.uk.tsl.rfid.asciiprotocol.enumerations.QuerySession;
import com.uk.tsl.rfid.asciiprotocol.enumerations.QueryTarget;
import com.uk.tsl.rfid.asciiprotocol.enumerations.SelectAction;
import com.uk.tsl.rfid.asciiprotocol.enumerations.SelectTarget;
import com.uk.tsl.rfid.asciiprotocol.enumerations.SwitchState;
import com.uk.tsl.rfid.asciiprotocol.enumerations.TriState;
import com.uk.tsl.rfid.asciiprotocol.responders.ICommandResponseLifecycleDelegate;
import com.uk.tsl.rfid.asciiprotocol.responders.ISwitchStateReceivedDelegate;
import com.uk.tsl.rfid.asciiprotocol.responders.ITransponderReceivedDelegate;
import com.uk.tsl.rfid.asciiprotocol.responders.LoggerResponder;
import com.uk.tsl.rfid.asciiprotocol.responders.SwitchResponder;
import com.uk.tsl.rfid.asciiprotocol.responders.TransponderData;
import com.uk.tsl.utils.HexEncoding;
import com.uk.tsl.utils.Observable;

import java.util.ArrayList;

public abstract class RNRfidTslThread extends Thread {
	private ReactApplicationContext context;

	private static String currentRoute = null;
	// The Reader currently in use
	private static Reader mReader = null;
	// Available reader list
	private static ArrayList<Reader> mReaders = null;
	// User selected reader
	private static String selectedReader = null;

	//Indicate to read barcode
	private static boolean isReadBarcode = false;

	//Inventory
	private static InventoryCommand mInventoryCommand = null;
	private static InventoryCommand mInventoryResponder = null;
	private static boolean mAnyTagSeen = false;
	private static ArrayList<String> cacheTags = null;

	//Play Sound
	private static MediaPlayer mp = null;
	private static Thread soundThread = null;
	private static boolean isPlaying = false;
	private static int soundRange = -1;

	//Find IT
	private static String tagID = null;
	private static boolean isLocateMode = false;

	//Program tag
	private static WriteTransponderCommand mWriteCommand = null;

	private SignalPercentageConverter mPercentageConverter = new SignalPercentageConverter();

	RNRfidTslThread(ReactApplicationContext context) {
		this.context = context;

		mp = MediaPlayer.create(this.context, R.raw.beeper);
		Init();
	}

	private void PlaySound(long value) {
		if (value > 0 && value <= 30) {
			soundRange = 1000;
		} else if (value > 31 && value <= 75) {
			soundRange = 600;
		} else {
			soundRange = 100;
		}

		if (soundThread == null) {
			soundThread = new Thread(new Runnable() {
				@Override
				public void run() {
					while (isPlaying) {
						if (soundRange > 0) {
							Log.e("LOOP", soundRange + "");
							try {
								Thread.sleep(soundRange);
							} catch (InterruptedException e) {
								e.getMessage();
							}
							mp.start();
						}

					}
				}
			});
			soundThread.start();
		}
	}

	public abstract void dispatchEvent(String name, WritableMap data);

	public abstract void dispatchEvent(String name, String data);

	public abstract void dispatchEvent(String name, WritableArray data);

	public abstract void dispatchEvent(String name, boolean data);

	void onHostResume() {
//		if (mReader != null && ReaderManager.sharedInstance() != null) {
//			// setEnabled(true);
//			// Remember if the pause/resume was caused by ReaderManager - this will be
//			// cleared when ReaderManager.onResume() is called
//			// boolean readerManagerDidCauseOnPause =
//			// ReaderManager.sharedInstance().didCauseOnPause();
//
//			// The ReaderManager needs to know about Activity lifecycle changes
//			ReaderManager.sharedInstance().onResume();
//
//			// The Activity may start with a reader already connected (perhaps by another
//			// App)
//			// Update the ReaderList which will add any unknown reader, firing events
//			// appropriately
//			ReaderManager.sharedInstance().updateList();
//
//			// Locate a Reader to use when necessary
//			AutoSelectReader(true);
//
//		}
	}

	void onHostPause() {
//		if (mReader != null && ReaderManager.sharedInstance() != null) {
//			setEnabled(false);
//			// Disconnect from the reader to allow other Apps to use it
//			// unless pausing when USB device attached or using the DeviceListActivity to
//			// select a Reader
//			if (!ReaderManager.sharedInstance().didCauseOnPause() && mReader != null) {
//				mReader.disconnect();
//			}
//
//			ReaderManager.sharedInstance().onPause();
//		}
	}

	void onHostDestroy() {
		DisconnectDevice();

		LocalBroadcastManager.getInstance(this.context).unregisterReceiver(mCommanderMessageReceiver);
	}

	private void Init() {
		try {
			if (getCommander() != null && mReader.isConnected()) {
				DisconnectDevice();
			}

			// Ensure the shared instance of AsciiCommander exists
			AsciiCommander.createSharedInstance(this.context);

			final AsciiCommander commander = getCommander();

			// Ensure that all existing responders are removed
			commander.clearResponders();

			//Logger
			commander.addResponder(new LoggerResponder());

			// Add responder to enable the synchronous commands
			commander.addSynchronousResponder();

			// Configure the ReaderManager when necessary
			ReaderManager.create(this.context);

			// Add observers for changes
			ReaderManager.sharedInstance().getReaderList().readerAddedEvent().addObserver(mAddedObserver);
			ReaderManager.sharedInstance().getReaderList().readerUpdatedEvent().addObserver(mUpdatedObserver);
			ReaderManager.sharedInstance().getReaderList().readerRemovedEvent().addObserver(mRemovedObserver);

			// Register to receive notifications from the AsciiCommander
			LocalBroadcastManager.getInstance(this.context).registerReceiver(mCommanderMessageReceiver,
					new IntentFilter(AsciiCommander.STATE_CHANGED_NOTIFICATION));
		} catch (Exception ex) {
			HandleError(ex.getMessage(), "Init");
		}
	}

	private void InitInventory() {

		// Initiate tags array for saving scanned tags, and prevent duplicate tags.
		cacheTags = new ArrayList<>();

		// This is the command that will be used to perform configuration changes and
		// inventories
		mInventoryCommand = new InventoryCommand();
		mInventoryCommand.setResetParameters(TriState.YES);

		// Configure the type of inventory
		mInventoryCommand.setIncludeTransponderRssi(TriState.YES);
		mInventoryCommand.setIncludeChecksum(TriState.YES);
		mInventoryCommand.setIncludePC(TriState.YES);
		mInventoryCommand.setIncludeDateTime(TriState.YES);

		// Use an InventoryCommand as a responder to capture all incoming inventory
		// responses
		mInventoryResponder = new InventoryCommand();
		// Also capture the responses that were not from App commands
		mInventoryResponder.setCaptureNonLibraryResponses(true);

		// Notify when each transponder is seen
		mInventoryResponder.setTransponderReceivedDelegate(mInventoryDelegate);

		mInventoryResponder.setResponseLifecycleDelegate(new ICommandResponseLifecycleDelegate() {
			@Override
			public void responseEnded() {
				if (!mAnyTagSeen && mInventoryCommand.getTakeNoAction() != TriState.YES) {
					// sendMessageNotification("No transponders seen");
					Log.i("No transponders seen", "No transponders seen");
					if (isLocateMode) {
//						isPlaying = false;
						soundRange = -1;
//						soundThread = null;
						WritableMap map = Arguments.createMap();
						map.putInt("distance", 0);
						dispatchEvent("locateTag", map);
					}
				}
				mInventoryCommand.setTakeNoAction(TriState.NO);
			}

			@Override
			public void responseBegan() {
				mAnyTagSeen = false;
			}
		});
	}

	private void InitTrigger() {
		//Trigger
		SwitchResponder mSwitchResponder = new SwitchResponder();
		mSwitchResponder.setSwitchStateReceivedDelegate(mSwitchDelegate);
		getCommander().addResponder(mSwitchResponder);

		// Configure the switch actions
		SwitchActionCommand switchActionCommand = SwitchActionCommand.synchronousCommand();
		switchActionCommand.setResetParameters(TriState.YES);
		// Enable asynchronous switch state reporting
		switchActionCommand.setAsynchronousReportingEnabled(TriState.YES);

		getCommander().executeCommand(switchActionCommand);
	}

	private void InitProgramTag() {
		mWriteCommand = WriteTransponderCommand.synchronousCommand();

		mWriteCommand.setResetParameters(TriState.YES);

		mWriteCommand.setSelectOffset(0x20);
		mWriteCommand.setBank(Databank.ELECTRONIC_PRODUCT_CODE);
		mWriteCommand.setOffset(2);

		mWriteCommand.setSelectAction(SelectAction.DEASSERT_SET_B_NOT_ASSERT_SET_A);
		mWriteCommand.setSelectTarget(SelectTarget.SESSION_2);

		mWriteCommand.setQuerySelect(QuerySelect.ALL);
		mWriteCommand.setQuerySession(QuerySession.SESSION_2);
		mWriteCommand.setQueryTarget(QueryTarget.TARGET_B);

		mWriteCommand.setTransponderReceivedDelegate(mProgramTagDelegate);
	}

	private void InitLocateTag() throws Exception {
		if (getCommander() != null && getCommander().isConnected() && tagID != null) {
			mInventoryCommand = InventoryCommand.synchronousCommand();
			mInventoryCommand.setResetParameters(TriState.YES);
			mInventoryCommand.setTakeNoAction(TriState.YES);

			mInventoryCommand.setIncludeTransponderRssi(TriState.YES);

			mInventoryCommand.setInventoryOnly(TriState.NO);

			mInventoryCommand.setQuerySession(QuerySession.SESSION_0);
			mInventoryCommand.setQueryTarget(QueryTarget.TARGET_B);

			mInventoryCommand.setSelectAction(SelectAction.DEASSERT_SET_B_NOT_ASSERT_SET_A);
			mInventoryCommand.setSelectTarget(SelectTarget.SESSION_0);
			mInventoryCommand.setSelectBank(Databank.ELECTRONIC_PRODUCT_CODE);

			mInventoryCommand.setSelectData(tagID);
			mInventoryCommand.setSelectLength(tagID.length() * 4);
			mInventoryCommand.setSelectOffset(0x20);

			mInventoryCommand.setUseAlert(TriState.NO);

			getCommander().executeCommand(mInventoryCommand);
			if (!mInventoryCommand.isSuccessful()) {
				String errorMsg = String.format(
						"%s failed!\nError code: %s\n",
						mInventoryCommand.getClass().getSimpleName(), mInventoryCommand.getErrorCode());
				Log.e("LocateTag", errorMsg);
				throw new Exception(errorMsg);
			}
		} else {
			throw new Exception("Initialize locate tag fail");
		}
	}

	boolean SaveTagID(String tag) {
		if (getCommander() != null && getCommander().isConnected()) {
			tagID = tag;
			return true;
		}
		return false;
	}

	boolean ProgramTag(String oldTag, String newTag) {
		if (getCommander() != null && getCommander().isConnected()) {
			if (oldTag != null && newTag != null) {
				byte[] data;
				data = HexEncoding.stringToBytes(newTag);
				mWriteCommand.setData(data);
				mWriteCommand.setLength(data.length / 2);
				mWriteCommand.setSelectData(oldTag);
				mWriteCommand.setSelectLength(oldTag.length() * 4);
				getCommander().executeCommand(mWriteCommand);

				if (!mWriteCommand.isSuccessful()) {
					String errorMsg = String.format(
							"%s failed!\nError code: %s\n",
							mWriteCommand.getClass().getSimpleName(), mWriteCommand.getErrorCode());
					dispatchEvent("writeTag", errorMsg);
					return false;
				}
				return true;
			}
		}
		return false;
	}

	//Trigger Handler
	private final ISwitchStateReceivedDelegate mSwitchDelegate = new ISwitchStateReceivedDelegate() {

		@Override
		public void switchStateReceived(SwitchState state) {
			// Use the alert command to indicate the type of asynchronous switch press
			// No vibration just vary the tone & duration
			if (currentRoute != null) {
				WritableMap map = Arguments.createMap();
				if (SwitchState.OFF.equals(state)) {
					//Trigger Release
					if (isLocateMode) {
						isPlaying = false;
						soundRange = -1;
						soundThread = null;
						map.putInt("distance", 0);
						dispatchEvent("locateTag", map);
					} else if (isReadBarcode) {
						dispatchEvent("BarcodeTrigger", false);
					} else if (currentRoute.equalsIgnoreCase("tagit") ||
							currentRoute.equalsIgnoreCase("lookup")) {
						cacheTags = new ArrayList<>();
					} else if (currentRoute.equalsIgnoreCase("locateTag")) {
						map.putString("RFIDStatusEvent", "inventoryStop");
						dispatchEvent("triggerAction", map);
					}
				} else {
					//Trigger Pull
					if (isLocateMode) {
						isPlaying = true;
					} else if (isReadBarcode) {
						dispatchEvent("BarcodeTrigger", true);
					} else if (currentRoute.equalsIgnoreCase("lookup") ||
							currentRoute.equalsIgnoreCase("locatetag")) {
						map.putString("RFIDStatusEvent", "inventoryStart");
						dispatchEvent("triggerAction", map);
					}
				}
			}
		}
	};

	//Inventory Delegate Handler
	private final ITransponderReceivedDelegate mInventoryDelegate =
			new ITransponderReceivedDelegate() {
				@Override
				public void transponderReceived(TransponderData transponder, boolean moreAvailable) {
					//Inventory received tags
					mAnyTagSeen = true;
					String EPC = transponder.getEpc();
					int rssi = transponder.getRssi();

					if (isLocateMode && isPlaying) {
						int distance = mPercentageConverter.asPercentage(rssi);
						PlaySound(distance);
						Log.e("distance", distance + "");
						WritableMap map = Arguments.createMap();
						map.putInt("distance", distance);
						dispatchEvent("locateTag", map);
					} else if (!isLocateMode && !isReadBarcode) {
						if (currentRoute != null && currentRoute.equalsIgnoreCase("tagit")) {
							if (rssi > -50) {
								if (addTagToList(EPC) && cacheTags.size() == 1) {
									dispatchEvent("TagEvent", EPC);
								}
							}
						} else {
							if (addTagToList(EPC)) {
								dispatchEvent("TagEvent", EPC);
							}
						}
					}
				}
			};

	//Program tag Delegate Handler
	private final ITransponderReceivedDelegate mProgramTagDelegate =
			new ITransponderReceivedDelegate() {
				@Override
				public void transponderReceived(TransponderData transponderData, boolean b) {
					String eaMsg = transponderData.getAccessErrorCode() == null ? "" : transponderData.getAccessErrorCode().getDescription() + " (EA)";
					String ebMsg = transponderData.getBackscatterErrorCode() == null ? "" : transponderData.getBackscatterErrorCode().getDescription() + " (EB)";
					String errorMsg = eaMsg + ebMsg;
					if (errorMsg.length() > 0) {
						dispatchEvent("writeTag", errorMsg);
					} else {
						dispatchEvent("writeTag", "success");
					}
				}
				//
			};

	void DisconnectDevice() {
		if (mReader != null && getCommander() != null) {
			setEnabled(false);

			// Remove observers for changes
			ReaderManager.sharedInstance().getReaderList().readerAddedEvent().removeObserver(mAddedObserver);
			ReaderManager.sharedInstance().getReaderList().readerUpdatedEvent().removeObserver(mUpdatedObserver);
			ReaderManager.sharedInstance().getReaderList().readerRemovedEvent().removeObserver(mRemovedObserver);

			// Unregister to receive notifications from the AsciiCommander
			// LocalBroadcastManager.getInstance(this.context).unregisterReceiver
			// (mCommanderMessageReceiver);

			mReader.disconnect();
			mReader = null;
			currentRoute = null;
			// Available reader list
			mReaders = null;
			// User selected reader
			selectedReader = null;

			//Indicate to read barcode
			isReadBarcode = false;

			//Inventory
			mInventoryCommand = null;
			mInventoryResponder = null;
			mAnyTagSeen = false;
			cacheTags = null;

			//Play Sound
			mp = null;
			soundThread = null;
			isPlaying = false;
			soundRange = -1;

			//Find IT
			tagID = null;
			isLocateMode = false;

			//Program tag
			mWriteCommand = null;
		}
	}

	boolean ConnectDevice() {
		if (selectedReader != null && mReaders != null) {
			for (Reader reader : mReaders) {
				if (reader.getDisplayName().equals(selectedReader)) {
					mReader = reader;
					mReader.connect();
					getCommander().setReader(mReader);
					break;
				}
			}
			return true;
		}
		return false;
	}

	boolean AttemptToReconnect() {
		if (selectedReader != null) {
			AutoSelectReader(true);
			return true;
		}
		return false;
	}

	boolean IsConnected() {
		if (getCommander() != null) {
			return getCommander().isConnected();
		}
		return false;
	}

	void CleanCacheTags() {
		if (getCommander() != null && getCommander().isConnected()) {
			cacheTags = new ArrayList<>();
		}
	}

	private void setEnabled(boolean state) {
		// Update the commander for state changes
		if (state) {
			// Listen for transponders
			getCommander().addResponder(mInventoryResponder);
//			getCommander().addResponder(mSwitchResponder);

			// Listen for barcodes
			// getCommander().addResponder(mBarcodeResponder);
		} else {
			// Stop listening for transponders
			getCommander().removeResponder(mInventoryResponder);
//			getCommander().removeResponder(mSwitchResponder);

			// Stop listening for barcodes
			// getCommander().removeResponder(mBarcodeResponder);
		}
	}

//	private void testForAntenna() {
//		if (getCommander().isConnected()) {
//			InventoryCommand testCommand = InventoryCommand.synchronousCommand();
//			testCommand.setTakeNoAction(TriState.YES);
//			getCommander().executeCommand(testCommand);
//			if (!testCommand.isSuccessful()) {
//				Log.e("Error",
//						"ER:Error! Code: " + testCommand.getErrorCode() + " " + testCommand.getMessages().toString());
//			}
//		}
//	}

	void SaveCurrentRoute(String value) throws Exception {
		currentRoute = value;
		if (currentRoute != null) {
			setEnabled(true);
			if (isLocateMode && !currentRoute.equalsIgnoreCase("locateTag")) {
				InitInventory();
				isLocateMode = false;
			} else if (currentRoute.equalsIgnoreCase("locateTag")) {
				InitLocateTag();
				isLocateMode = true;
			}
		} else {
			setEnabled(false);
			isReadBarcode = false;
		}
	}

	WritableArray GetDeviceList() {
		WritableArray deviceList = Arguments.createArray();
		ReaderManager.sharedInstance().updateList();
		mReaders = ReaderManager.sharedInstance().getReaderList().list();
		for (Reader reader : mReaders) {
			WritableMap map = Arguments.createMap();
			map.putString("name", reader.getDisplayName());
			map.putString("address", reader.getDisplayInfoLine());
			deviceList.pushMap(map);
		}
		return deviceList;
	}

	private String GetBatteryLevel() {
		String level = null;
		if (getCommander() != null && getCommander().isConnected()) {
			BatteryStatusCommand bCommand = BatteryStatusCommand.synchronousCommand();
			getCommander().executeCommand(bCommand);
			int batteryLevel = bCommand.getBatteryLevel();
			Log.e("BatteryLevel", batteryLevel + "");
			level = String.valueOf(batteryLevel);
		}
		return level;
	}

	private void SetBuzzer(boolean value) {
		if (getCommander() != null && getCommander().isConnected()) {
			AlertCommand aCommand = AlertCommand.synchronousCommand();
			aCommand.setEnableBuzzer(value ? TriState.YES : TriState.NO);
			aCommand.setEnableVibrator(value ? TriState.YES : TriState.NO);
			getCommander().executeCommand(aCommand);
		}
	}
//
//	private int GetAntennaLevel() {
//		if (getCommander() != null && getCommander().isConnected()) {
//			getCommander().executeCommand(getInventoryCommand());
//			int level = getInventoryCommand().getOutputPower();
//			if (level > 0)
//				return level;
//		}
//		return 0;
//	}

	void SetAntennaLevel(int level) {
		if (getCommander() != null && getCommander().isConnected()) {
			mInventoryCommand.setOutputPower(level);
			mInventoryCommand.setTakeNoAction(TriState.YES);
			getCommander().executeCommand(mInventoryCommand);
		}
	}

	private void updateConfiguration() {
		if (getCommander() != null && getCommander().isConnected()) {
			mInventoryCommand.setTakeNoAction(TriState.YES);
			getCommander().executeCommand(mInventoryCommand);
			boolean isSuccess = mInventoryCommand.isSuccessful();
			Log.i("updateConfiguration", isSuccess + "");
		}
	}

	void SaveSelectedScanner(String name) {
		selectedReader = name;
	}

	String GetConnectedReader() {
		return selectedReader;
	}

	//
	// Select the Reader to use and reconnect to it as needed
	//
	private void AutoSelectReader(boolean attemptReconnect) {
		ObservableReaderList readerList = ReaderManager.sharedInstance().getReaderList();
		Reader usbReader = null;
		if (readerList.list().size() >= 1) {
			// Currently only support a single USB connected device so we can safely take
			// the
			// first CONNECTED reader if there is one
			for (Reader reader : readerList.list()) {
				IAsciiTransport transport = reader.getActiveTransport();
				if (reader.hasTransportOfType(TransportType.USB)) {
					usbReader = reader;
					break;
				}
			}
		}

		if (mReader == null) {
			if (usbReader != null) {
				// Use the Reader found, if any
				mReader = usbReader;
				getCommander().setReader(mReader);
			}
		} else {
			// If already connected to a Reader by anything other than USB then
			// switch to the USB Reader
			IAsciiTransport activeTransport = mReader.getActiveTransport();
			if (activeTransport != null && activeTransport.type() != TransportType.USB && usbReader != null) {
				// appendMessage("Disconnecting from: " + mReader.getDisplayName());
				mReader.disconnect();

				mReader = usbReader;

				// Use the Reader found, if any
				getCommander().setReader(mReader);
			}
		}

		// Reconnect to the chosen Reader
		if (mReader != null && (mReader.getActiveTransport() == null
				|| mReader.getActiveTransport().connectionStatus().value() == ConnectionState.DISCONNECTED)) {
			// Attempt to reconnect on the last used transport unless the ReaderManager is
			// cause of OnPause (USB device connecting)
			if (attemptReconnect) {
				if (mReader.allowMultipleTransports() || mReader.getLastTransportType() == null) {
					// appendMessage("Connecting to: " + mReader.getDisplayName());
					// Reader allows multiple transports or has not yet been connected so connect to
					// it over any available transport
					mReader.connect();
				} else {
					// appendMessage("Connecting (over last transport) to: " +
					// mReader.getDisplayName());
					// Reader supports only a single active transport so connect to it over the
					// transport that was last in use
					mReader.connect(mReader.getLastTransportType());
				}
			}
		}
	}

	// ReaderList Observers
	private Observable.Observer<Reader> mAddedObserver = new Observable.Observer<Reader>() {
		@Override
		public void update(Observable<? extends Reader> observable, Reader reader) {
			// Log.e("mAddedObserver", "mAddedObserver");
			// See if this newly added Reader should be used
			// AutoSelectReader(true);
		}
	};

	private Observable.Observer<Reader> mUpdatedObserver = new Observable.Observer<Reader>() {
		@Override
		public void update(Observable<? extends Reader> observable, Reader reader) {
			// Log.e("mUpdatedObserver", "mUpdatedObserver");
		}
	};

	private Observable.Observer<Reader> mRemovedObserver = new Observable.Observer<Reader>() {
		@Override
		public void update(Observable<? extends Reader> observable, Reader reader) {
			// Log.e("mRemovedObserver", "mRemovedObserver");
			// Was the current Reader removed
			if (reader == mReader) {
				mReader = null;

				// Stop using the old Reader
				getCommander().setReader(mReader);
			}
		}
	};

	/**
	 * @return the current AsciiCommander
	 */
	private AsciiCommander getCommander() {
		return AsciiCommander.sharedInstance();
	}

	//
	// Handle the messages broadcast from the AsciiCommander
	//
	private BroadcastReceiver mCommanderMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {

			try {
				// String connectionStateMsg = intent.getStringExtra(AsciiCommander.REASON_KEY);
				WritableMap map = Arguments.createMap();
				if (getCommander().getConnectionState().equals(ConnectionState.CONNECTED)) {
					mPercentageConverter = new SignalPercentageConverter();
					resetDevice();
					InitInventory();
					InitTrigger();
					InitProgramTag();
					SetBuzzer(false);
					updateConfiguration();
					SetAntennaLevel(getCommander().getDeviceProperties().getMaximumCarrierPower());
					String battery = GetBatteryLevel();
					map.putBoolean("ConnectionState", true);
					map.putString("BatteryLevel", battery);
					dispatchEvent("RFIDStatusEvent", map);
				} else if (getCommander().getConnectionState().equals(ConnectionState.DISCONNECTED)) {
					map.putBoolean("ConnectionState", false);
					dispatchEvent("RFIDStatusEvent", map);
				}
			} catch (Exception ex) {
				HandleError(ex.getMessage(), "mCommanderMessageReceiver");
			}
		}
	};

	void ReadBarcode(boolean value) {
		isReadBarcode = value;

		//If read barcode, then turn off RFID mode.
		setEnabled(!value);
	}

	private void resetDevice() {
		if (getCommander().isConnected()) {
			FactoryDefaultsCommand fdCommand = new FactoryDefaultsCommand();
			fdCommand.setResetParameters(TriState.YES);
			getCommander().executeCommand(fdCommand);
		}
	}

	private void HandleError(String msg, String code) {
		Log.e(code, msg);
//		String msg = ex.getMessage();
		WritableMap map = Arguments.createMap();
		map.putString("code", code);
		map.putString("msg", msg);
		dispatchEvent("HandleError", map);
	}

	private boolean addTagToList(String strEPC) {
		if (strEPC != null) {
			if (!checkIsExisted(strEPC)) {
				cacheTags.add(strEPC);
				return true;
			}
		}
		return false;
	}

	private boolean checkIsExisted(String strEPC) {
		for (int i = 0; i < cacheTags.size(); i++) {
			String tag = cacheTags.get(i);
			if (strEPC != null && strEPC.equals(tag)) {
				return true;
			}
		}
		return false;
	}
}
