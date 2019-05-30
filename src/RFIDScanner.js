import { NativeModules, DeviceEventEmitter } from 'react-native';
import _ from 'lodash';
import { RFIDScannerEvent } from './RFIDScannerEvent';

const { RNRfidTsl } = NativeModules;

let instance = null;

class RFIDScanner {
	constructor() {
		if (_.isEmpty(instance)) {
			instance = this;
			this.opened = false;
			this.onCallBacks = [];

			DeviceEventEmitter.addListener(RFIDScannerEvent.TAG, this.HandleTagEvent);
			DeviceEventEmitter.addListener(RFIDScannerEvent.RFID_Status, this.HandleStatus);
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

	init = () => {
		return Promise.resolve(RNRfidTsl.Init());
	};

	connect = () => {
		try {
			const result = RNRfidTsl.ConnectDevice();
			return Promise.resolve(result);
		} catch (err) {
			return Promise.reject(err);
		}
	};

	disconnect = () => {
		RNRfidTsl.DisconnectDevice();
	};

	isConnected = async () => {
		const result = await RNRfidTsl.IsConnected();
		return Promise.resolve(result);
	};

	SetReaderEnabled = value => {
		RNRfidTsl.setEnabled(value);
	};

	cleanTags = () => {
		RNRfidTsl.CleanCacheTags();
	};

	GetDeviceList = async () => {
		try {
			const result = await RNRfidTsl.GetDeviceList();
			return Promise.resolve(result);
		} catch (err) {
			return Promise.reject(err);
		}
	};

	SaveSelectedScanner = name => {
		RNRfidTsl.SaveSelectedScanner(name);
	};

	GetBatteryLevel = () => {
		try {
			return Promise.resolve(RNRfidTsl.GetBatteryLevel());
		} catch (err) {
			return Promise.reject(err);
		}
	};

	GetAntennaLevel = () => {
		try {
			return Promise.resolve(RNRfidTsl.GetAntennaLevel());
		} catch (err) {
			return Promise.reject(err);
		}
	};

	SetAntennaLevel = number => {
		let level = number;
		if (!_.isNumber(level)) level = parseInt(level);
		RNRfidTsl.SetAntennaLevel(level);
	};

	on = (event, callback) => {
		this.onCallBacks[event] = callback;
	};

	removeon = (event, callback) => {
		if (this.onCallBacks.hasOwnProperty(event)) {
			this.onCallBacks[event] = null;
		}
	};
}

export default new RFIDScanner();
