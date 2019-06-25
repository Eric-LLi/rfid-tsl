import { NativeModules, DeviceEventEmitter, NativeEventEmitter } from 'react-native';
import _ from 'lodash';
import { RFIDScannerEvent } from './RFIDScannerEvent';

const { RNRfidTsl } = NativeModules;

let instance = null;

class RFIDScanner {
	constructor() {
		if (_.isEmpty(instance)) {
			instance = this;
			this.opened = false;
			this.onCallBacks = {};

			this.tagEvent = DeviceEventEmitter.addListener(RFIDScannerEvent.TAG, this.HandleTagEvent);
			this.rfidStatusEvent = DeviceEventEmitter.addListener(
				RFIDScannerEvent.RFID_Status,
				this.HandleStatus
			);
			this.barcodeTriggerEvent = DeviceEventEmitter.addListener(
				RFIDScannerEvent.BarcodeTrigger,
				this.HandleBarcodeEvent
			);
			this.writeTagEvent = DeviceEventEmitter.addListener(
				RFIDScannerEvent.WRITETAG,
				this.HandleWriteTag
			);
			this.triggerActionEvent = DeviceEventEmitter.addListener(
				RFIDScannerEvent.triggerAction,
				this.HandlerTrigger
			);
			this.LocateTagEvent = DeviceEventEmitter.addListener(
				RFIDScannerEvent.LOCATE_TAG,
				this.HandleLocateTag
			);
			// this.barcodeEvent = NativeEventEmitter.addListener(
			// 	RFIDScannerEvent.BARCODE,
			// 	this.HandlerBarcode
			// );
		}
	}

	HandleTagEvent = tag => {
		if (this.onCallBacks.hasOwnProperty(RFIDScannerEvent.TAG)) {
			this.onCallBacks[RFIDScannerEvent.TAG](tag);
		}
	};

	HandleStatus = status => {
		if (this.onCallBacks.hasOwnProperty(RFIDScannerEvent.RFID_Status)) {
			this.onCallBacks[RFIDScannerEvent.RFID_Status](status);
		}
	};

	HandleBarcodeEvent = event => {
		if (this.onCallBacks.hasOwnProperty(RFIDScannerEvent.BarcodeTrigger)) {
			this.onCallBacks[RFIDScannerEvent.BarcodeTrigger](event);
		}
	};

	HandleWriteTag = event => {
		if (this.onCallBacks.hasOwnProperty(RFIDScannerEvent.WRITETAG)) {
			this.onCallBacks[RFIDScannerEvent.WRITETAG](event);
		}
	};

	HandlerTrigger = event => {
		if (this.onCallBacks.hasOwnProperty(RFIDScannerEvent.triggerAction)) {
			this.onCallBacks[RFIDScannerEvent.triggerAction](event);
		}
	};

	HandleLocateTag = event => {
		if (this.onCallBacks.hasOwnProperty(RFIDScannerEvent.LOCATE_TAG)) {
			this.onCallBacks[RFIDScannerEvent.LOCATE_TAG](event);
		}
	};
	// HandlerBarcode = event => {
	// 	if (this.onCallBacks.hasOwnProperty(RFIDScannerEvent.BARCODE)) {
	// 		this.onCallBacks[RFIDScannerEvent.BARCODE](event);
	// 	}
	// };

	RemoveAllListener = () => {
		this.tagEvent.remove();
		this.rfidStatusEvent.remove();
		this.barcodeTriggerEvent.remove();
		this.writeTagEvent.remove();
		this.triggerActionEvent.remove();
		this.LocateTagEvent.remove();
		// this.barcodeEvent.remove();
	};

	InitialThread = () => {
		return RNRfidTsl.InitialThread();
	};

	// init = () => {
	// 	return Promise.resolve(RNRfidTsl.Init());
	// };

	connect = () => {
		return RNRfidTsl.ConnectDevice();
		// try {
		// 	const result =
		// 	return Promise.resolve(result);
		// } catch (err) {
		// 	return Promise.reject(err);
		// }
	};

	disconnect = () => {
		RNRfidTsl.DisconnectDevice();
	};

	AttemptToReconnect = () => {
		return RNRfidTsl.AttemptToReconnect();
	};

	isConnected = () => {
		return RNRfidTsl.IsConnected();
	};

	SetReaderEnabled = value => {
		RNRfidTsl.setEnabled(value);
	};

	cleanTags = () => {
		RNRfidTsl.CleanCacheTags();
	};

	GetDeviceList = async () => {
		return RNRfidTsl.GetDeviceList();
	};

	SaveCurrentRoute = value => {
		RNRfidTsl.SaveCurrentRoute(value);
	};

	SaveSelectedScanner = name => {
		RNRfidTsl.SaveSelectedScanner(name);
	};

	GetConnectedReader = () => {
		return RNRfidTsl.GetConnectedReader();
	};

	GetBatteryLevel = () => {
		return RNRfidTsl.GetBatteryLevel();
		// try {
		// 	return Promise.resolve(RNRfidTsl.GetBatteryLevel());
		// } catch (err) {
		// 	return Promise.reject(err);
		// }
	};

	GetAntennaLevel = () => {
		return RNRfidTsl.GetAntennaLevel();
		// try {
		// 	return Promise.resolve(RNRfidTsl.GetAntennaLevel());
		// } catch (err) {
		// 	return Promise.reject(err);
		// }
	};

	SetAntennaLevel = number => {
		if (!_.isEmpty(number) && !_.isEmpty(number.antennaLevel)) {
			let level = number.antennaLevel;
			if (!_.isNumber(level)) level = parseInt(level);
			RNRfidTsl.SetAntennaLevel(level);
		}
	};

	TagITReadBarcode = value => {
		return RNRfidTsl.TagITReadBarcode(value);
	};

	ProgramTag = (oldTag, newTag) => {
		return RNRfidTsl.ProgramTag(oldTag, newTag);
	};

	SaveTagID = tag => {
		return RNRfidTsl.SaveTagID(tag);
	};

	LocateMode = value => {
		return RNRfidTsl.LocateMode(value);
	};

	on = (event, callback) => {
		this.onCallBacks[event] = callback;
	};

	removeon = (event, callback) => {
		if (this.onCallBacks.hasOwnProperty(event)) {
			this.onCallBacks[event] = null;
			delete this.onCallBacks[event];
		}
	};
}

export default new RFIDScanner();
