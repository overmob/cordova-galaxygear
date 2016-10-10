package com.overmob.cordova.galaxygear;

import com.overmob.cordova.galaxygear.GearMessageListener;

interface GearMessageApi {
	void sendData(int connectionId, String data);
   
	void addListener(GearMessageListener listener);
	
	void removeListener(GearMessageListener listener);
}
