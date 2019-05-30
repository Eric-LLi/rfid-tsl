package com.yaoweili.tsl.rfid;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
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
import com.uk.tsl.rfid.asciiprotocol.device.ConnectionState;
import com.uk.tsl.rfid.asciiprotocol.device.IAsciiTransport;
import com.uk.tsl.rfid.asciiprotocol.device.ObservableReaderList;
import com.uk.tsl.rfid.asciiprotocol.device.Reader;
import com.uk.tsl.rfid.asciiprotocol.device.ReaderManager;
import com.uk.tsl.rfid.asciiprotocol.device.TransportType;
import com.uk.tsl.rfid.asciiprotocol.enumerations.BuzzerTone;
import com.uk.tsl.rfid.asciiprotocol.enumerations.TriState;
import com.uk.tsl.rfid.asciiprotocol.parameters.AntennaParameters;
import com.uk.tsl.rfid.asciiprotocol.responders.ICommandResponseLifecycleDelegate;
import com.uk.tsl.rfid.asciiprotocol.responders.ITransponderReceivedDelegate;
import com.uk.tsl.rfid.asciiprotocol.responders.LoggerResponder;
import com.uk.tsl.rfid.asciiprotocol.responders.TransponderData;
import com.uk.tsl.utils.HexEncoding;
import com.uk.tsl.utils.Observable;

import java.util.ArrayList;
import java.util.Locale;

public abstract class RNRfidTslThread extends Thread {
	private ReactApplicationContext context = null;

	// The Reader currently in use
	private Reader mReader = null;
	// Available reader list
	private ArrayList<Reader> mReaders = null;
	// User selected reader
	private String selectedReader = null;

	private InventoryCommand mInventoryCommand = null;
	private InventoryCommand mInventoryResponder = null;
	private boolean mAnyTagSeen;
	private ArrayList<String> cacheTags = null;

	public RNRfidTslThread(ReactApplicationContext context) {
		this.context = context;
		Init();
	}

	public abstract void dispatchEvent(String name, WritableMap data);

	public abstract void dispatchEvent(String name, String data);

	public abstract void dispatchEvent(String name, WritableArray data);

	public void onHostResume() {
		if (mReader != null && ReaderManager.sharedInstance() != null) {
			// setEnabled(true);
			// Remember if the pause/resume was caused by ReaderManager - this will be
			// cleared when ReaderManager.onResume() is called
			// boolean readerManagerDidCauseOnPause =
			// ReaderManager.sharedInstance().didCauseOnPause();

			// The ReaderManager needs to know about Activity lifecycle changes
			ReaderManager.sharedInstance().onResume();

			// The Activity may start with a reader already connected (perhaps by another
			// App)
			// Update the ReaderList which will add any unknown reader, firing events
			// appropriately
			ReaderManager.sharedInstance().updateList();

			// Locate a Reader to use when necessary
			AutoSelectReader(true);

		}
	}

	public void onHostPause() {
		if (mReader != null && ReaderManager.sharedInstance() != null) {
			setEnabled(false);
			// Disconnect from the reader to allow other Apps to use it
			// unless pausing when USB device attached or using the DeviceListActivity to
			// select a Reader
			if (!ReaderManager.sharedInstance().didCauseOnPause() && mReader != null) {
				mReader.disconnect();
			}

			ReaderManager.sharedInstance().onPause();
		}

	}

	public void onHostDestroy() {
		DisconnectDevice();

		LocalBroadcastManager.getInstance(this.context).unregisterReceiver(mCommanderMessageReceiver);
	}

	public boolean Init() {
		try {
			if (getCommander() != null && mReader.isConnected()) {
				DisconnectDevice();
			}

			// Ensure the shared instance of AsciiCommander exists
			AsciiCommander.createSharedInstance(this.context);

			final AsciiCommander commander = getCommander();

			// Ensure that all existing responders are removed
			commander.clearResponders();

			// Add the LoggerResponder - this simply echoes all lines received from the
			// reader to the log
			// and passes the line onto the next responder
			// This is ADDED FIRST so that no other responder can consume received lines
			// before they are logged.
			commander.addResponder(new LoggerResponder());

			//
			// Add a simple Responder that sends the Reader output to the App message list
			//
			// Note - This is not the recommended way of receiving Reader input - it is just
			// a convenient
			// way to show that the Reader is connected and communicating - see the other
			// Sample Projects
			// for how to Inventory, Read, Write etc....
			//
			// commander.addResponder(new IAsciiCommandResponder() {
			// @Override
			// public boolean isResponseFinished() {
			// return false;
			// }
			//
			// @Override
			// public void clearLastResponse() {
			// }
			//
			// @Override
			// public boolean processReceivedLine(String fullLine, boolean
			// moreLinesAvailable) {
			//// appendMessage("> " + fullLine);
			//// Log.e("processReceivedLine", fullLine);
			// // don't consume the line - allow others to receive it
			// return false;
			// }
			// });

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
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

	private void InitialInventory() {

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
		mInventoryResponder.setTransponderReceivedDelegate(new ITransponderReceivedDelegate() {

			@Override
			public void transponderReceived(TransponderData transponder, boolean moreAvailable) {
				mAnyTagSeen = true;

				String tidMessage = transponder.getTidData() == null ? ""
						: HexEncoding.bytesToString(transponder.getTidData());
				String infoMsg = String.format(Locale.US, "\nRSSI: %d  PC: %04X  CRC: %04X", transponder.getRssi(),
						transponder.getPc(), transponder.getCrc());
				// sendMessageNotification("EPC: " + transponder.getEpc() + infoMsg + "\nTID: "
				// + tidMessage + "\n# " + mTagsSeen );

				boolean existedTag = false;
				for (int i = 0; i < cacheTags.size(); i++) {
					if (cacheTags.get(i).equals(transponder.getEpc())) {
						existedTag = true;
					}
				}

				if (!existedTag) {
					Log.e("Tag", transponder.getEpc());
					cacheTags.add(transponder.getEpc());
					dispatchEvent("tag", transponder.getEpc());
				}

				if (!moreAvailable) {
					// sendMessageNotification("");
					// Log.d("TagCount", String.format("Tags seen: %s", mTagsSeen));
				}
			}
		});

		mInventoryResponder.setResponseLifecycleDelegate(new ICommandResponseLifecycleDelegate() {

			@Override
			public void responseEnded() {
				if (!mAnyTagSeen && mInventoryCommand.getTakeNoAction() != TriState.YES) {
					// sendMessageNotification("No transponders seen");
				}
				mInventoryCommand.setTakeNoAction(TriState.NO);
			}

			@Override
			public void responseBegan() {
				mAnyTagSeen = false;
			}
		});
	}

	public void DisconnectDevice() {
		if (mReader != null && getCommander() != null) {
			cacheTags = new ArrayList<>();

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
		}
	}

	public boolean ConnectDevice() {
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

	public boolean IsConnected() {
		if (getCommander() != null) {
			return getCommander().isConnected();
		}
		return false;
	}

	public void CleanCacheTags() {
		if (getCommander() != null && getCommander().isConnected()) {
			cacheTags = new ArrayList<>();
		}
	}

	public void setEnabled(boolean state) {
		// Update the commander for state changes
		if (state) {
			// Listen for transponders
			getCommander().addResponder(mInventoryResponder);
			// Listen for barcodes
			// getCommander().addResponder(mBarcodeResponder);
		} else {
			// Stop listening for transponders
			getCommander().removeResponder(mInventoryResponder);
			// Stop listening for barcodes
			// getCommander().removeResponder(mBarcodeResponder);
		}

	}

	public void testForAntenna() {
		if (getCommander().isConnected()) {
			InventoryCommand testCommand = InventoryCommand.synchronousCommand();
			testCommand.setTakeNoAction(TriState.YES);
			getCommander().executeCommand(testCommand);
			if (!testCommand.isSuccessful()) {
				Log.e("Error",
						"ER:Error! Code: " + testCommand.getErrorCode() + " " + testCommand.getMessages().toString());
			}
		}
	}

	public void read() {
		try {
			testForAntenna();
			if (getCommander() != null && getCommander().isConnected()) {
				mInventoryCommand.setTakeNoAction(TriState.NO);
				getCommander().executeCommand(mInventoryCommand);
			}
		} catch (Exception ex) {
			HandleError(ex);
		}
	}

	public WritableArray GetDeviceList() {
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

	public String GetBatteryLevel() {
		String level = null;
		if (getCommander() != null && getCommander().isConnected()) {
			BatteryStatusCommand bCommand = BatteryStatusCommand.synchronousCommand();
			getCommander().executeCommand(bCommand);
			int batteryLevel = bCommand.getBatteryLevel();
			Log.e("BatteryLevel", batteryLevel + "");
			level = "BatteryLevel: " + batteryLevel + "%";
		}
		return level;
	}

	public void SetBuzzer(boolean value) {
		if (getCommander() != null && getCommander().isConnected()) {
			AlertCommand aCommand = AlertCommand.synchronousCommand();
			aCommand.setEnableBuzzer(value ? TriState.YES : TriState.NO);
			getCommander().executeCommand(aCommand);
		}
	}

	public int GetAntennaLevel() {
		if (getCommander() != null && getCommander().isConnected()) {
			getCommander().executeCommand(mInventoryCommand);
			int level = mInventoryCommand.getOutputPower();
			if (level > 0)
				return level;
		}
		return 0;
	}

	public void SetAntennaLevel(int level) {
		if (getCommander() != null && getCommander().isConnected()) {
			mInventoryCommand.setOutputPower(level);
			getCommander().executeCommand(mInventoryCommand);
		}
	}

	public void updateConfiguration() {
		if (getCommander() != null && getCommander().isConnected()) {
			mInventoryCommand.setTakeNoAction(TriState.YES);
			getCommander().executeCommand(mInventoryCommand);
		}
	}

	public void SaveSelectedScanner(String name) {
		selectedReader = name;
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
	Observable.Observer<Reader> mAddedObserver = new Observable.Observer<Reader>() {
		@Override
		public void update(Observable<? extends Reader> observable, Reader reader) {
			// Log.e("mAddedObserver", "mAddedObserver");
			// See if this newly added Reader should be used
			// AutoSelectReader(true);
		}
	};

	Observable.Observer<Reader> mUpdatedObserver = new Observable.Observer<Reader>() {
		@Override
		public void update(Observable<? extends Reader> observable, Reader reader) {
			// Log.e("mUpdatedObserver", "mUpdatedObserver");
		}
	};

	Observable.Observer<Reader> mRemovedObserver = new Observable.Observer<Reader>() {
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
	protected AsciiCommander getCommander() {
		return AsciiCommander.sharedInstance();
	}

	//
	// Handle the messages broadcast from the AsciiCommander
	//
	private BroadcastReceiver mCommanderMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			// if (true) {
			// Log.e(getClass().getName(), "AsciiCommander state changed - isConnected: " +
			// getCommander().isConnected());
			// }
			try {
				// String connectionStateMsg = intent.getStringExtra(AsciiCommander.REASON_KEY);
				WritableMap map = Arguments.createMap();
				if (getCommander().getConnectionState().equals(ConnectionState.CONNECTED)) {
					resetDevice();
					InitialInventory();
					SetBuzzer(false);
					// setEnabled(true);
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
				// if (getCommander().isConnected()) {
				// String battery = GetBatteryLevel();
				//
				// dispatchEvent("RFIDStatusEvent");
				// }
			} catch (Exception ex) {
				HandleError(ex);
			}
		}
	};

	private void resetDevice() {
		if (getCommander().isConnected()) {
			FactoryDefaultsCommand fdCommand = new FactoryDefaultsCommand();
			fdCommand.setResetParameters(TriState.YES);
			getCommander().executeCommand(fdCommand);
		}
	}

	private void HandleError(Exception ex) {
		String msg = ex.getMessage();
		dispatchEvent("error", msg);
	}
}
