
# react-native-rfid-tsl

## Getting started

`$ npm install react-native-rfid-tsl --save`

### Mostly automatic installation

`$ react-native link react-native-rfid-tsl`

### Manual installation


#### Android

1. Open up `android/app/src/main/java/[...]/MainActivity.java`
  - Add `import com.yaoweili.tsl.rfid.RNRfidTslPackage;` to the imports at the top of the file
  - Add `new RNRfidTslPackage()` to the list returned by the `getPackages()` method
2. Append the following lines to `android/settings.gradle`:
  	```
  	include ':react-native-rfid-tsl'
  	project(':react-native-rfid-tsl').projectDir = new File(rootProject.projectDir, 	'../node_modules/react-native-rfid-tsl/android')
  	```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
  	```
      compile project(':react-native-rfid-tsl')
  	```


## Usage
```javascript
import RNRfidTsl from 'react-native-rfid-tsl';

// TODO: What to do with the module?
RNRfidTsl;
```
  