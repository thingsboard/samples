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

import lombok.Getter;
import lombok.Setter;

import java.util.Optional;
import java.util.Random;

/**
 * Created by ashvayka on 21.12.16.
 */
public class TelemetryDescriptor<T> {

    private final Random random = new Random();

    @Getter
    private String key;
    @Setter
    private T defaultValue;
    @Setter
    private T anomalyValue;
    @Setter
    private T minValue;
    @Setter
    private T maxValue;

    private Double previousValue;

    public TelemetryDescriptor() {

    }

    public TelemetryDescriptor(String key) {
        this.key = key;
    }

    public Optional<T> getNextValue(boolean anomaly) {
        if (anomaly) {
            if (anomalyValue != null) {
                return Optional.of(anomalyValue);
            }
        }
        if (defaultValue != null) {
            return Optional.of(defaultValue);
        }
        if (minValue != null && maxValue != null) {
            if (Double.class.isInstance(minValue) && Double.class.isInstance(maxValue)) {
                Double min = Double.class.cast(minValue);
                Double max = Double.class.cast(maxValue);

                previousValue = generateNextValue((Double) previousValue, min, max);
                return Optional.of((T) previousValue);
            } else if (Long.class.isInstance(minValue) && Long.class.isInstance(maxValue)) {
                Long min = Long.class.cast(minValue);
                Long max = Long.class.cast(maxValue);

                previousValue = generateNextValue(previousValue, min.doubleValue(), max.doubleValue());
                return Optional.of((T) Long.valueOf(previousValue.longValue()));
            }
        }
        return Optional.empty();
    }

    public Optional<T> getAnomalyValue() {
        return Optional.ofNullable(anomalyValue);
    }


    private Double generateNextValue(Double previous, Double min, Double max) {
        if (previous == null) {
            previous = min + (max - min) * random.nextDouble();
        }

        Double next = previous + ((Math.random() - 0.5) * (max - min)) * 0.1;

        if (next > max) {
            next = max;
        } else if (next < min) {
            next = min;
        }
        return next;
    }
}
