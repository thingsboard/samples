/**
 * Copyright © 2016 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.sample.gpiocontrol;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class GpioControlActivity extends Activity {

    private static final String TAG = GpioControlActivity.class.getSimpleName();

    private static final String THINGSBOARD_HOST = "demo.thingsboard.io";
    private static final String ACCESS_TOKEN = "YOUR_DEVICE_ACCESS_TOKEN";

    private static final Map<Integer, String> gpioPinout = new HashMap<>();

    static {
        gpioPinout.put(7, "BCM4");
        gpioPinout.put(11, "BCM17");
        gpioPinout.put(12, "BCM18");
        gpioPinout.put(13, "BCM27");
        gpioPinout.put(15, "BCM22");
        gpioPinout.put(16, "BCM23");
        gpioPinout.put(18, "BCM24");
        gpioPinout.put(22, "BCM25");
        gpioPinout.put(29, "BCM5");
        gpioPinout.put(31, "BCM6");
        gpioPinout.put(32, "BCM12");
        gpioPinout.put(33, "BCM13");
        gpioPinout.put(35, "BCM19");
        gpioPinout.put(36, "BCM16");
        gpioPinout.put(37, "BCM26");
        gpioPinout.put(38, "BCM20");
        gpioPinout.put(40, "BCM21");
    }

    private Map<Integer, Gpio> mGpiosMap = new HashMap<>();
    private MqttAsyncClient mThingsboardMqttClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        PeripheralManagerService manager = new PeripheralManagerService();
        for (int k : gpioPinout.keySet()) {
            String gpioName = gpioPinout.get(k);
            try {
                Gpio gpio = manager.openGpio(gpioName);
                gpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
                Log.d(TAG, String.format("[%d] Gpio [%s] value: %b", k, gpioName, gpio.getValue()));
                mGpiosMap.put(k, gpio);
            } catch (IOException e) {
                Log.w(TAG, "Unable to access GPIO", e);
            }
        }

        // Initialize MQTT client
        try {
            mThingsboardMqttClient = new MqttAsyncClient("tcp://" + THINGSBOARD_HOST + ":1883", "Raspberry Pi 3", new MemoryPersistence());
        } catch (MqttException e) {
            Log.e(TAG, "Unable to create MQTT client", e);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mqttConnect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mqttDisconnect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (mGpiosMap != null) {
            for (Gpio gpio : mGpiosMap.values()) {
                try {
                    gpio.close();
                } catch (IOException e) {
                    Log.w(TAG, "Unable to close GPIO", e);
                }
            }
            mGpiosMap = null;
        }
    }

    private void mqttConnect() {
        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true);
        connOpts.setUserName(ACCESS_TOKEN);
        mThingsboardMqttClient.setCallback(mMqttCallback);
        try {
            mThingsboardMqttClient.connect(connOpts, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "MQTT client connected!");
                    try {
                        mThingsboardMqttClient.subscribe("v1/devices/me/rpc/request/+", 0);
                    } catch (MqttException e) {
                        Log.e(TAG, "Unable to subscribe to rpc requests topic", e);
                    }
                    try {
                        mThingsboardMqttClient.publish("v1/devices/me/attributes", getGpiosStatusMessage());
                    } catch (Exception e) {
                        Log.e(TAG, "Unable to publish GPIO status to Thingsboard server", e);
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable e) {
                    if (e instanceof MqttException) {
                        MqttException mqttException = (MqttException) e;
                        Log.e(TAG, String.format("Unable to connect to Thingsboard server: %s, code: %d", mqttException.getMessage(),
                                mqttException.getReasonCode()), e);
                    } else {
                        Log.e(TAG, String.format("Unable to connect to Thingsboard server: %s", e.getMessage()), e);
                    }
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, String.format("Unable to connect to Thingsboard server: %s, code: %d", e.getMessage(), e.getReasonCode()), e);
        }
    }

    private void mqttDisconnect() {
        try {
            mThingsboardMqttClient.disconnect();
            Log.i(TAG, "MQTT client disconnected!");
        } catch (MqttException e) {
            Log.e(TAG, "Unable to disconnect from the Thingsboard server", e);
        }
    }

    private MqttCallback mMqttCallback = new MqttCallback() {

        @Override
        public void connectionLost(Throwable e) {
            Log.e(TAG, "Disconnected from Thingsboard server", e);
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            Log.d(TAG, String.format("Received message from topic [%s]", topic));
            String requestId = topic.substring("v1/devices/me/rpc/request/".length());
            JSONObject messageData = new JSONObject(new String(message.getPayload()));
            String method = messageData.getString("method");
            if (method != null) {
                if (method.equals("getGpioStatus")) {
                    sendGpioStatus(requestId);
                } else if (method.equals("setGpioStatus")) {
                    JSONObject params = messageData.getJSONObject("params");
                    Integer pin = params.getInt("pin");
                    boolean enabled = params.getBoolean("enabled");
                    if (pin != null) {
                        updateGpioStatus(pin, enabled, requestId);
                    }
                } else {
                    //Client acts as an echo service
                    mThingsboardMqttClient.publish("v1/devices/me/rpc/response/" + requestId, message);
                }
            }
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }
    };

    private MqttMessage getGpiosStatusMessage() throws Exception {
        JSONObject gpioStatus = new JSONObject();
        for (int k : mGpiosMap.keySet()) {
            Gpio gpio = mGpiosMap.get(k);
            boolean value = gpio.getValue();
            gpioStatus.put(k + "", value);
        }
        MqttMessage message = new MqttMessage(gpioStatus.toString().getBytes());
        return message;
    }

    private void sendGpioStatus(String requestId) throws Exception {
        mThingsboardMqttClient.publish("v1/devices/me/rpc/response/" + requestId, getGpiosStatusMessage());
    }

    private void updateGpioStatus(int pin, boolean enabled, String requestId) throws Exception {
        JSONObject response = new JSONObject();
        Gpio gpio = mGpiosMap.get(pin);
        if (gpio != null) {
            gpio.setValue(enabled);
            response.put(pin + "", gpio.getValue());
        } else {
            response.put(pin + "", false);
        }
        MqttMessage message = new MqttMessage(response.toString().getBytes());
        mThingsboardMqttClient.publish("v1/devices/me/rpc/response/" + requestId, message);
        mThingsboardMqttClient.publish("v1/devices/me/attributes", message);
    }

}
