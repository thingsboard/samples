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

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.demo.loader.ConsoleReader;
import org.thingsboard.demo.loader.DemoLoader;
import org.thingsboard.demo.loader.data.DemoData;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.security.DeviceCredentials;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class FacilitiesDemoMain {

    public static void main(String[] args) {
        final ScheduledExecutorService executor;
        try {
            executor = Executors.newScheduledThreadPool(4);

            if (args.length == 0) {
                System.err.println(String.format("Invalid arguments count: %d", args.length));
                printUsage();
                System.exit(-1);
            }
            String host = args[0];
            String httpUrl = "http://" + host + (args.length >= 2 ? ":" + args[1] : "");
            String mqttUrl = "tcp://" + host + ":1883";

            DemoLoader demoLoader = new DemoLoader(httpUrl);

            boolean authorized = false;
            while (!authorized) {
                String username = ConsoleReader.readLine("Enter Tenant Admin username: ");
                String password = new String(ConsoleReader.readPassword("Enter Tenant Admin password: "));
                authorized = demoLoader.authorize(username, password);
                if (!authorized) {
                    System.err.println("Bad username or password!");
                }
            }

            demoLoader.loadDemoFromClasspath("demo");
            DemoData data = demoLoader.getDemoData();
            Map<String, Device> demoDevices = data.getDemoDevices().stream().collect(Collectors.toMap(Device::getName, Function.identity()));

            List<EmulatorDescriptor> descriptors = EmulatorDescriptorLoader.loadDescriptors();


            List<MqttClientEmulator> emulators = new ArrayList<>(data.getDemoDevices().size());

            for (EmulatorDescriptor descriptor : descriptors) {
                Device device = demoDevices.get(descriptor.getName());
                DeviceCredentials token = data.getDeviceCredentialsByDeviceId(device.getId());
                MqttClientEmulator emulator = new MqttClientEmulator(executor, TimeUnit.SECONDS.toMillis(1), mqttUrl, descriptor.getName(), token.getCredentialsId());
                emulator.setAttributes(descriptor.getAttributes());
                emulator.setTelemetry(descriptor.getTimeseries());
                emulator.connect();
                emulators.add(emulator);
            }

            for (MqttClientEmulator emulator : emulators) {
                emulator.start();
            }

            boolean quit = false;
            while (!quit) {
                String chars = ConsoleReader.readLine("Press Q to quite or M to simulate malfunction");
                if ("Q".equalsIgnoreCase(chars)) {
                    quit = true;
                } else if ("M".equalsIgnoreCase(chars)) {
                    for (int i = 0; i < descriptors.size(); i++) {
                        System.out.println("[" + i + "]: " + descriptors.get(i).getName());
                    }
                    String deviceNumberStr = ConsoleReader.readLine("Please input device number (from 0 to " + (descriptors.size() - 1) + "): ");
                    try {
                        Integer deviceNumber = Integer.valueOf(deviceNumberStr);
                        if (deviceNumber < 0 || deviceNumber >= descriptors.size()) {
                            throw new NumberFormatException();
                        }
                        MqttClientEmulator emulator = emulators.get(deviceNumber);
                        emulator.simulateMalfunction();
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid device number: " + deviceNumberStr + " !");
                    }
                }
            }

            for (MqttClientEmulator emulator : emulators) {
                emulator.stop();
            }
            executor.shutdownNow();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: ");
        System.out.println("java -jar facilities-monitoring.jar <thingsboard host>");
    }

}
