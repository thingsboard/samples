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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    private AtomicInteger mulfunctionIterations = new AtomicInteger(0);

    public MqttClientEmulator(ScheduledExecutorService executor, long period, String uri, String deviceName, String deviceToken) throws Exception {
        this.executor = executor;
        this.period = period;
        this.client = new SampleMqttClient(uri, deviceName, deviceToken);
    }

    public void simulateMalfunction() {
        mulfunctionIterations.addAndGet(30);
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
                if (mulfunctionIterations.get() > 0) {
                    anomaly = true;
                    mulfunctionIterations.decrementAndGet();
                } else {
                    anomaly = false;
                }

                telemetry.forEach(t -> t.getNextValue(anomaly).ifPresent(v -> values.put(t.getKey(), v)));

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
