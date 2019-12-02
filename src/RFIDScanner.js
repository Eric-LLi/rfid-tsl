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

	RemoveAllListener = () => {
		if (!_.isEmpty(this.tagEvent)) {
			this.tagEvent.remove();
			this.tagEvent = null;
		}
		if (!_.isEmpty(this.rfidStatusEvent)) {
			this.rfidStatusEvent.remove();
			this.rfidStatusEvent = null;
		}
		if (!_.isEmpty(this.barcodeTriggerEvent)) {
			this.barcodeTriggerEvent.remove();
			this.barcodeTriggerEvent = null;
		}
		if (!_.isEmpty(this.writeTagEvent)) {
			this.writeTagEvent.remove();
			this.writeTagEvent = null;
		}
		if (!_.isEmpty(this.triggerActionEvent)) {
			this.triggerActionEvent.remove();
			this.triggerActionEvent = null;
		}
		if (!_.isEmpty(this.LocateTagEvent)) {
			this.LocateTagEvent.remove();
			this.LocateTagEvent = null;
		}
	};

	ActiveAllListener = () => {
		if (_.isEmpty(this.tagEvent))
			this.tagEvent = DeviceEventEmitter.addListener(RFIDScannerEvent.TAG, this.HandleTagEvent);
		if (_.isEmpty(this.rfidStatusEvent))
			this.rfidStatusEvent = DeviceEventEmitter.addListener(
				RFIDScannerEvent.RFID_Status,
				this.HandleStatus
			);
		if (_.isEmpty(this.barcodeTriggerEvent))
			this.barcodeTriggerEvent = DeviceEventEmitter.addListener(
				RFIDScannerEvent.BarcodeTrigger,
				this.HandleBarcodeEvent
			);
		if (_.isEmpty(this.writeTagEvent))
			this.writeTagEvent = DeviceEventEmitter.addListener(
				RFIDScannerEvent.WRITETAG,
				this.HandleWriteTag
			);
		if (_.isEmpty(this.triggerActionEvent))
			this.triggerActionEvent = DeviceEventEmitter.addListener(
				RFIDScannerEvent.triggerAction,
				this.HandlerTrigger
			);
		if (_.isEmpty(this.LocateTagEvent))
			this.LocateTagEvent = DeviceEventEmitter.addListener(
				RFIDScannerEvent.LOCATE_TAG,
				this.HandleLocateTag
			);
	};

	InitialThread = () => {
		RNRfidTsl.InitialThread();
	};

	connect = () => {
		return RNRfidTsl.ConnectDevice();
	};

	disconnect = () => {
		return RNRfidTsl.DisconnectDevice();
	};

	AttemptToReconnect = () => {
		return RNRfidTsl.AttemptToReconnect();
	};

	isConnected = () => {
		return RNRfidTsl.IsConnected();
	};

	cleanTags = () => {
		return RNRfidTsl.CleanCacheTags();
	};

	GetDeviceList = async () => {
		return RNRfidTsl.GetDeviceList();
	};

	SaveCurrentRoute = value => {
		return RNRfidTsl.SaveCurrentRoute(value);
	};

	SaveSelectedScanner = name => {
		return RNRfidTsl.SaveSelectedScanner(name);
	};

	GetConnectedReader = () => {
		return RNRfidTsl.GetConnectedReader();
	};

	GetBatteryLevel = () => {
		return RNRfidTsl.GetBatteryLevel();
	};

	SetAntennaLevel = number => {
		if (!_.isEmpty(number) && !_.isEmpty(number.antennaLevel)) {
			let level = number.antennaLevel;
			if (!_.isNumber(level)) level = parseInt(level);
			return RNRfidTsl.SetAntennaLevel(level);
		}
	};

	ReadBarcode = value => {
		return RNRfidTsl.ReadBarcode(value);
	};

	ProgramTag = (oldTag, newTag) => {
		return RNRfidTsl.ProgramTag(oldTag, newTag);
	};

	SaveTagID = tag => {
		return RNRfidTsl.SaveTagID(tag);
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
