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
package org.thingsboard.samples.facility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;

/**
 * @author Andrew Shvayka
 */
@Slf4j
public class SampleMqttClient {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    @Getter
    private final String deviceToken;
    @Getter
    private final String deviceName;
    @Getter
    private final String clientId;
    private final MqttClientPersistence persistence;
    private final MqttAsyncClient client;

    public SampleMqttClient(String uri, String deviceName, String deviceToken) throws Exception {
        this.clientId = MqttAsyncClient.generateClientId();
        this.deviceToken = deviceToken;
        this.deviceName = deviceName;
        this.persistence = new MemoryPersistence();
        this.client = new MqttAsyncClient(uri, clientId, persistence);
    }

    public boolean connect() throws Exception {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(deviceToken);
        try {
            client.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken iMqttToken) {
                    log.info("[{}] connected to Thingsboard!", deviceName);
                }

                @Override
                public void onFailure(IMqttToken iMqttToken, Throwable e) {
                    log.error("[{}] failed to connect to Thingsboard!", deviceName, e);
                }
            }).waitForCompletion();
        } catch (MqttException e) {
            log.error("Failed to connect to the server", e);
        }
        return client.isConnected();
    }

    public void disconnect() throws Exception {
        client.disconnect().waitForCompletion();
    }

    public void publishAttributes(JsonNode data) throws Exception {
        publish("v1/devices/me/attributes", data, true);
    }

    public void publishTelemetry(JsonNode data) throws Exception {
        publish("v1/devices/me/telemetry", data, false);
    }

    private void publish(String topic, JsonNode data, boolean sync) throws Exception {
        MqttMessage msg = new MqttMessage(MAPPER.writeValueAsString(data).getBytes(StandardCharsets.UTF_8));
        IMqttDeliveryToken deliveryToken = client.publish(topic, msg, null, new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                log.trace("Data updated!");
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                log.error("[{}] Data update failed!", deviceName, exception);
            }
        });
        if (sync) {
            deliveryToken.waitForCompletion();
        }
    }
}
