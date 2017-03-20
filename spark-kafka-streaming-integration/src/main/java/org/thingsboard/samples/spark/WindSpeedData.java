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
public class WindSpeedData implements Serializable {

    private String geoZone;
    private double windSpeed;

}
