package com.yaoweili.tsl.rfid;

import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import com.uk.tsl.rfid.asciiprotocol.AsciiCommander;
import com.uk.tsl.rfid.asciiprotocol.commands.BatteryStatusCommand;
import com.uk.tsl.rfid.asciiprotocol.device.ConnectionState;
import com.uk.tsl.rfid.asciiprotocol.device.IAsciiTransport;
import com.uk.tsl.rfid.asciiprotocol.device.ObservableReaderList;
import com.uk.tsl.rfid.asciiprotocol.device.Reader;
import com.uk.tsl.rfid.asciiprotocol.device.ReaderManager;
import com.uk.tsl.rfid.asciiprotocol.device.TransportType;
import com.uk.tsl.rfid.asciiprotocol.responders.IAsciiCommandResponder;
import com.uk.tsl.rfid.asciiprotocol.responders.LoggerResponder;
import com.uk.tsl.utils.Observable;

import java.util.ArrayList;
import java.util.List;

public abstract class RNRfidTslThread extends Thread {
	private ReactApplicationContext context = null;

	// The Reader currently in use
	private Reader mReader = null;
	private boolean mIsSelectingReader = false;

	public RNRfidTslThread(ReactApplicationContext context) {
		this.context = context;
	}

	public abstract void dispatchEvent(String name, WritableMap data);

	public abstract void dispatchEvent(String name, String data);

	public abstract void dispatchEvent(String name, WritableArray data);

	public void onHostResume() {
		ReaderManager.sharedInstance().onResume();
		ReaderManager.sharedInstance().updateList();
	}

	public void onHostPause() {
		ReaderManager.sharedInstance().onPause();
	}

	public void onHostDestroy() {
		DisconnectDevice();
	}

	private void Init() {
		if (mReader != null) {
			DisconnectDevice();
		}

		// Ensure the shared instance of AsciiCommander exists
		AsciiCommander.createSharedInstance(context);

		final AsciiCommander commander = getCommander();

		// Ensure that all existing responders are removed
		commander.clearResponders();

		// Add the LoggerResponder - this simply echoes all lines received from the reader to the log
		// and passes the line onto the next responder
		// This is ADDED FIRST so that no other responder can consume received lines before they are logged.
		commander.addResponder(new LoggerResponder());

		commander.addResponder(new IAsciiCommandResponder() {
			@Override
			public boolean isResponseFinished() {
				Log.e("isResponseFinished", "isResponseFinished");
				return false;
			}

			@Override
			public void clearLastResponse() {
				Log.e("clearLastResponse", "clearLastResponse");
			}

			@Override
			public boolean processReceivedLine(String fullLine, boolean moreLinesAvailable) {
				// don't consume the line - allow others to receive it
				Log.e("processReceivedLine", "processReceivedLine");
				return false;
			}
		});

		// Add responder to enable the synchronous commands
		commander.addSynchronousResponder();

		// Configure the ReaderManager when necessary
		ReaderManager.create(context);

		// Add observers for changes
		ReaderManager.sharedInstance().getReaderList().readerAddedEvent().addObserver(mAddedObserver);
		ReaderManager.sharedInstance().getReaderList().readerUpdatedEvent().addObserver(mUpdatedObserver);
		ReaderManager.sharedInstance().getReaderList().readerRemovedEvent().addObserver(mRemovedObserver);

	}

	public void DisconnectDevice() {
		if (mReader != null) {
			// Remove observers for changes
			ReaderManager.sharedInstance().getReaderList().readerAddedEvent().removeObserver(mAddedObserver);
			ReaderManager.sharedInstance().getReaderList().readerUpdatedEvent().removeObserver(mUpdatedObserver);
			ReaderManager.sharedInstance().getReaderList().readerRemovedEvent().removeObserver(mRemovedObserver);

			mReader.disconnect();
			mReader = null;
		}
	}

	public void ConnectDevice() {
		//
	}

	//
	// Select the Reader to use and reconnect to it as needed
	//
	private void AutoSelectReader(boolean attemptReconnect) {
		ObservableReaderList readerList = ReaderManager.sharedInstance().getReaderList();
		Reader usbReader = null;
		if (readerList.list().size() >= 1) {
			// Currently only support a single USB connected device so we can safely take the
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
//				appendMessage("Disconnecting from: " + mReader.getDisplayName());
				mReader.disconnect();

				mReader = usbReader;

				// Use the Reader found, if any
				getCommander().setReader(mReader);
			}
		}

		// Reconnect to the chosen Reader
		if (mReader != null && (mReader.getActiveTransport() == null || mReader.getActiveTransport().connectionStatus().value() == ConnectionState.DISCONNECTED)) {
			// Attempt to reconnect on the last used transport unless the ReaderManager is cause of OnPause (USB device connecting)
			if (attemptReconnect) {
				if (mReader.allowMultipleTransports() || mReader.getLastTransportType() == null) {
//					appendMessage("Connecting to: " + mReader.getDisplayName());
					// Reader allows multiple transports or has not yet been connected so connect to it over any available transport
					mReader.connect();
				} else {
//					appendMessage("Connecting (over last transport) to: " + mReader.getDisplayName());
					// Reader supports only a single active transport so connect to it over the transport that was last in use
					mReader.connect(mReader.getLastTransportType());
				}
			}
		}
	}

	// ReaderList Observers
	Observable.Observer<Reader> mAddedObserver = new Observable.Observer<Reader>() {
		@Override
		public void update(Observable<? extends Reader> observable, Reader reader) {
			// See if this newly added Reader should be used
			AutoSelectReader(true);
		}
	};
	Observable.Observer<Reader> mUpdatedObserver = new Observable.Observer<Reader>() {
		@Override
		public void update(Observable<? extends Reader> observable, Reader reader) {
		}
	};

	Observable.Observer<Reader> mRemovedObserver = new Observable.Observer<Reader>() {
		@Override
		public void update(Observable<? extends Reader> observable, Reader reader) {
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
}
