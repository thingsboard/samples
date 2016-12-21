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
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by ashvayka on 21.12.16.
 */
@Data
@Slf4j
public class MqttClientEmulator {

    private final ScheduledExecutorService executor;
    private final SampleMqttClient client;

    private Map<String, Object> attributes;
    private List<TelemetryDescriptor> telemetry;
    private ScheduledFuture<?> task;

    private long period;
    private volatile boolean anomalyTrigger;

    public MqttClientEmulator(ScheduledExecutorService executor, long period, String uri, String deviceToken) throws Exception {
        this.executor = executor;
        this.period = period;
        this.client = new SampleMqttClient(uri, deviceToken);
    }

    public static void main(String[] args) throws Exception {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);

        MqttClientEmulator deviceA = new MqttClientEmulator(executor, TimeUnit.SECONDS.toMillis(1), "tcp://demo.thingsboard.io:1883", "oCVqSIVBu2fdQjgn0xgp");
        MqttClientEmulator deviceB = new MqttClientEmulator(executor, TimeUnit.SECONDS.toMillis(1), "tcp://demo.thingsboard.io:1883", "hi8u361KZoqeeo0NRchk");
        MqttClientEmulator deviceC = new MqttClientEmulator(executor, TimeUnit.SECONDS.toMillis(1), "tcp://demo.thingsboard.io:1883", "9NhGeUhUsJ4FvvvmaEHV");

//        TelemetryDescriptor temperature = new TelemetryDescriptor("temperature", null, Double.valueOf(100.0), Double.valueOf(20.0), Double.valueOf(25.0));
//        TelemetryDescriptor humidity = new TelemetryDescriptor("humidity", null, Double.valueOf(80.0), Double.valueOf(55.0), Double.valueOf(65.0));
//
//        deviceA.setTelemetry(Arrays.asList(temperature, humidity));
//        deviceB.setTelemetry(Arrays.asList(temperature, humidity));
//        deviceC.setTelemetry(Arrays.asList(temperature, humidity));

        Map<String, Object> attributesA = new HashMap<>();
        attributesA.put("latitude", 37.7750924);
        attributesA.put("longitude", -122.4185093);
        deviceA.setAttributes(attributesA);

        Map<String, Object> attributesB = new HashMap<>();
        attributesB.put("latitude", 37.776812);
        attributesB.put("longitude", -122.419043);
        deviceB.setAttributes(attributesB);

        Map<String, Object> attributesC = new HashMap<>();
        attributesC.put("latitude", 37.7768);
        attributesC.put("longitude", -122.4166);
        deviceC.setAttributes(attributesC);

        deviceA.connect();
        deviceB.connect();
        deviceC.connect();

        deviceA.start();
        deviceB.start();
        deviceC.start();

        Thread.sleep(60000);

        deviceA.stop();
        deviceB.stop();
        deviceC.stop();

        executor.shutdownNow();
    }

    public void connect() throws Exception {
        client.connect();
        client.publishAttributes(toJson(attributes));
    }

    public void start() throws Exception {
        task = executor.scheduleAtFixedRate(() -> {
            try {
                Map<String, Object> values = new HashMap<>();

                final boolean anomaly;
                if (anomalyTrigger) {
                    anomaly = true;
                    anomalyTrigger = false;
                } else {
                    anomaly = false;
                }

                telemetry.forEach(t -> t.getValue(anomaly).ifPresent(v -> values.put(t.getKey(), v)));

                client.publishTelemetry(toJson(values));
            } catch (Exception e) {
                log.error("Failed to publish telemetry data", e);
            }
        }, 0, period, TimeUnit.MILLISECONDS);
    }

    public void stop() throws Exception {
        task.cancel(false);
        client.disconnect();
    }

    private JsonNode toJson(Map<String, Object> data) {
        final ObjectNode json = SampleMqttClient.MAPPER.createObjectNode();
        data.forEach((k, v) -> {
            if (Double.class.isInstance(v)) {
                json.put(k, Double.class.cast(v));
            } else if (Long.class.isInstance(v)) {
                json.put(k, Long.class.cast(v));
            } else if (Boolean.class.isInstance(v)) {
                json.put(k, Boolean.class.cast(v));
            } else {
                json.put(k, v.toString());
            }
        });
        return json;
    }

}
