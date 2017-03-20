package org.thingsboard.samples.spark;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Created by ashvayka on 15.03.17.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvgWindSpeedData implements Serializable {

    private double value;
    private int count;

    public AvgWindSpeedData(double value) {
        this.value = value;
        this.count = 1;
    }

    public double getAvgValue() {
        return value / count;
    }

    public static AvgWindSpeedData sum(AvgWindSpeedData a, AvgWindSpeedData b) {
        return new AvgWindSpeedData(a.value + b.value, a.count + b.count);
    }

}
