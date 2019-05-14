import { NativeModules, DeviceEventEmitter } from 'react-native';
import { RFIDScannerEvent } from './RFIDScannerEvent';
import _ from 'lodash';
const { RNRfidTsl } = NativeModules;

let instance = null;

export default class RFIDScanner {
	constructor() {
		if (_.isEmpty(instance)) {
			instance = this;
			this.opened = false;
			this.onCallBacks = [];

			DeviceEventEmitter.addListener(RFIDScannerEvent.TAG, this.HandleTagEvent);
		}
	}

	HandleTagEvent = () => {
		if (this.onCallBacks.hasOwnProperty(RFIDScannerEvent.TAG)) {
			this.onCallBacks[RFIDScannerEvent.TAG](tag);
		}
	};

	ConnectDevice = () => {
		RNRfidTsl.ConnectDevice();
	};

	DisconnectDevice = () => {
		RNRfidTsl.DisconnectDevice();
	};

	On = (event, callback) => {
		this.onCallBacks[event] = callback;
	};

	RemoveOn = (event, callback) => {
		if (this.onCallBacks.hasOwnProperty(event)) {
			this.onCallBacks[event] = null;
		}
	};
}
