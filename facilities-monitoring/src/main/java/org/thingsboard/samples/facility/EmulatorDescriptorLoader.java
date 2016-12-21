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
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ashvayka on 21.12.16.
 */
@Slf4j
public class EmulatorDescriptorLoader {

    private static final String EMULATOR_JSON = "emulator.json";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static List<EmulatorDescriptor> loadDescriptors() throws IOException {
        InputStream stream = EmulatorDescriptorLoader.class.getClassLoader().getResourceAsStream(EMULATOR_JSON);
        JsonNode node = objectMapper.readTree(stream);
        JsonNode descriptors = node.get("devices");
        List<EmulatorDescriptor> result = new ArrayList<>();
        descriptors.forEach(json -> {
            try {
                result.add(objectMapper.treeToValue(json, EmulatorDescriptor.class));
            } catch (Exception e) {
                log.error("Unable to load plugins from json!");
                log.error("Cause:", e);
                System.exit(-1);
            }
        });
        return result;
    }

}
