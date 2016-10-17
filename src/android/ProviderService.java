/*
 * Copyright (c) 2015 Samsung Electronics Co., Ltd. All rights reserved. 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that 
 * the following conditions are met:
 * 
 *     * Redistributions of source code must retain the above copyright notice, 
 *       this list of conditions and the following disclaimer. 
 *     * Redistributions in binary form must reproduce the above copyright notice, 
 *       this list of conditions and the following disclaimer in the documentation and/or 
 *       other materials provided with the distribution. 
 *     * Neither the name of Samsung Electronics Co., Ltd. nor the names of its contributors may be used to endorse
 *       or promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.overmob.cordova.galaxygear;

import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.accessory.SA;
import com.samsung.android.sdk.accessory.SAAgent;
import com.samsung.android.sdk.accessory.SAAuthenticationToken;
import com.samsung.android.sdk.accessory.SAPeerAgent;
import com.samsung.android.sdk.accessory.SASocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ProviderService extends SAAgent {
    private static final String TAG = "HelloAccessory(P)";
    private static final int HELLOACCESSORY_CHANNEL_ID = 104;
    private static final Class<ServiceConnection> SASOCKET_CLASS = ServiceConnection.class;
    // private final IBinder mBinder = new LocalBinder();
    // private ServiceConnection mConnectionHandler = null;
    // Handler mHandler = new Handler();

    private List<GearMessageListener> listeners = new ArrayList<GearMessageListener>();
    private SparseArray<ServiceConnection> mConnectionsMap = new SparseArray<ServiceConnection>();

    public ProviderService() {
        super(TAG, SASOCKET_CLASS);
    }

    private final GearMessageApi.Stub apiEndpoint = new GearMessageApi.Stub() {
        @Override
        public void sendData(final int connectionId, String data)
                throws RemoteException {
            Log.d(TAG, "GearMessageApi.sendData");
            final byte[] message = data.getBytes();
            synchronized (mConnectionsMap) {
                final ServiceConnection connection = mConnectionsMap.get(connectionId);
                if (connection == null) {
                    Log.e(TAG, "Connection handler not found! :" + connectionId + "  size: " + mConnectionsMap.size());
                    return;
                }

                new Thread(new Runnable() {
                    public void run() {
                        try {

                            connection.send(HELLOACCESSORY_CHANNEL_ID, message);
                        } catch (IOException e) {
                            Log.e(TAG, "send failed text: " + message);
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        }

        @Override
        public void removeListener(GearMessageListener listener)
                throws RemoteException {
            Log.d(TAG, "GearMessageApi.removeListener");

            synchronized (listeners) {
                listeners.remove(listener);
            }
        }

        @Override
        public void addListener(final GearMessageListener listener)
                throws RemoteException {
            Log.d(TAG, "GearMessageApi.addListener");

            synchronized (listeners) {
                listeners.add(listener);
            }

            // Tell the new listener of any existing connections
            synchronized (mConnectionsMap) {
                int key = 0;
                for (int i = 0; i < mConnectionsMap.size(); i++) {
                    key = mConnectionsMap.keyAt(i);
                    Log.d(TAG, Integer.toString(key));
                    listener.onConnect(key);
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        SA mAccessory = new SA();
        try {
            mAccessory.initialize(this);
        } catch (SsdkUnsupportedException e) {
            // try to handle SsdkUnsupportedException
            if (processUnsupportedException(e) == true) {
                return;
            }
        } catch (Exception e1) {
            e1.printStackTrace();
            /*
             * Your application can not use Samsung Accessory SDK. Your application should work smoothly
             * without using this SDK, or you may want to notify user and close your application gracefully
             * (release resources, stop Service threads, close UI thread, etc.)
             */
            stopSelf();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return apiEndpoint;
    }

    @Override
    protected void onFindPeerAgentsResponse(SAPeerAgent[] peerAgents, int result) {
        Log.d(TAG, "onFindPeerAgentResponse : result =" + result);
    }

    @Override
    protected void onServiceConnectionRequested(SAPeerAgent peerAgent) {
        if (peerAgent != null) {
            Log.d(TAG, "onServiceConnectionRequested");
            acceptServiceConnectionRequest(peerAgent);
        }
    }

    @Override
    protected void onServiceConnectionResponse(SAPeerAgent peerAgent, SASocket socket, int result) {
        if (result == SAAgent.CONNECTION_SUCCESS) {
            if (socket != null) {

                ServiceConnection myConnection = (ServiceConnection) socket;

                if (mConnectionsMap == null) {
                    mConnectionsMap = new SparseArray<ServiceConnection>();
                }

                myConnection.mConnectionId = (int) (System.currentTimeMillis() & 255);

                //Log.d(TAG, "onServiceConnection connectionID = "+ myConnection.mConnectionId);

                mConnectionsMap.put(myConnection.mConnectionId, myConnection);
                Log.i(TAG, "Connection Success --");


                synchronized (listeners) {
                    for (GearMessageListener listener : listeners) {
                        try {
                            listener.onConnect(myConnection.mConnectionId);
                        } catch (RemoteException e) {
                            Log.w(TAG, "Failed to notify listener " + listener, e);
                        }
                    }
                }


            }
        } else if (result == SAAgent.CONNECTION_ALREADY_EXIST) {
            Log.e(TAG, "onServiceConnectionResponse, CONNECTION_ALREADY_EXIST");
        }
    }

    @Override
    protected void onAuthenticationResponse(SAPeerAgent peerAgent, SAAuthenticationToken authToken, int error) {
        Log.d(TAG,"onAuthenticationResponse");
        /*
         * The authenticatePeerAgent(peerAgent) API may not be working properly depending on the firmware
         * version of accessory device. Please refer to another sample application for Security.
         */
    }

    @Override
    protected void onError(SAPeerAgent peerAgent, String errorMessage, int errorCode) {
        super.onError(peerAgent, errorMessage, errorCode);
        Log.e(TAG, "onError.");
    }

    private boolean processUnsupportedException(SsdkUnsupportedException e) {
        e.printStackTrace();
        int errType = e.getType();
        if (errType == SsdkUnsupportedException.VENDOR_NOT_SUPPORTED
                || errType == SsdkUnsupportedException.DEVICE_NOT_SUPPORTED) {
            /*
             * Your application can not use Samsung Accessory SDK. You application should work smoothly
             * without using this SDK, or you may want to notify user and close your app gracefully (release
             * resources, stop Service threads, close UI thread, etc.)
             */
            Log.e(TAG, "SsdkUnsupportedException.");
            stopSelf();
        } else if (errType == SsdkUnsupportedException.LIBRARY_NOT_INSTALLED) {
            Log.e(TAG, "You need to install Samsung Accessory SDK to use this application.");
        } else if (errType == SsdkUnsupportedException.LIBRARY_UPDATE_IS_REQUIRED) {
            Log.e(TAG, "You need to update Samsung Accessory SDK to use this application.");
        } else if (errType == SsdkUnsupportedException.LIBRARY_UPDATE_IS_RECOMMENDED) {
            Log.e(TAG, "We recommend that you update your Samsung Accessory SDK before using this application.");
            return false;
        }
        return true;
    }

    public class ServiceConnection extends SASocket {
        private int mConnectionId;

        public ServiceConnection() {
            super(ServiceConnection.class.getName());
        }

        @Override
        public void onError(int channelId, String errorMessage, int errorCode) {
            synchronized (listeners) {
                for (GearMessageListener listener : listeners) {
                    try {
                        listener.onError(mConnectionId, errorMessage);
                    } catch (RemoteException e) {
                        Log.w(TAG, "Failed to notify listener " + listener, e);
                    }
                }
            }
        }

        @Override
        public void onReceive(int channelId, byte[] data) {
            Log.d("provider", "onReceive: " + channelId);
            String message = new String(data);
            Log.d("provider", "onReceive: " + message);



            synchronized (listeners) {
                for (GearMessageListener listener : listeners) {
                    try {
                        Log.d(TAG, "onReceive channelId: " + mConnectionId);
                        listener.onDataReceived(mConnectionId, message);
                    } catch (RemoteException e) {
                        Log.w(TAG, "Failed to notify listener " + listener, e);
                    }
                }
            }
        }

        @Override
        protected void onServiceConnectionLost(int reason) {
            if (mConnectionsMap != null) {
                mConnectionsMap.remove(mConnectionId);
            }
        }
    }
}
