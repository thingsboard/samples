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
package org.thingsboard.demo.loader.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleState;
import org.thingsboard.server.common.data.plugin.PluginMetaData;
import org.thingsboard.server.common.data.rule.RuleMetaData;
import org.thingsboard.server.common.data.security.DeviceCredentials;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class DemoData {

    private static final String DEMO_PLUGINS_JSON = "demo_plugins.json";
    private static final String DEMO_RULES_JSON = "demo_rules.json";
    private static final String DEMO_CUSTOMERS_JSON = "demo_customers.json";
    private static final String DEMO_DEVICES_JSON = "demo_devices.json";
    private static final String DASHBOARDS_DIR = "dashboards";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private List<PluginMetaData> plugins = new ArrayList<>();
    private List<RuleMetaData> rules = new ArrayList<>();
    private List<Customer> customers = new ArrayList<>();
    private List<Device> devices = new ArrayList<>();
    private Map<String, String> customerDevices = new HashMap<>();
    private Map<String, Map<String, JsonNode>> devicesAttributes = new HashMap<>();

    private List<Dashboard> dashboards = new ArrayList<>();

    private Map<DeviceId, DeviceCredentials> loadedDevicesCredentialsMap = new HashMap<>();

    private DemoData() {
    }

    public List<Dashboard> getDashboards() {
        return dashboards;
    }

    public List<Device> getevices() {
        return devices;
    }

    public DeviceCredentials getDeviceCredentialsByDeviceId(DeviceId deviceId) {
        return loadedDevicesCredentialsMap.get(deviceId);
    }

    public static DemoData fromArchiveFile(String demoDataArchive, String email) throws Exception {
        DemoData demoData = new DemoData();
        demoData.readFromArchiveFile(demoDataArchive, email);
        return demoData;
    }

    public static DemoData fromClasspathDir(String demoDataPath, String email) throws Exception {
        DemoData demoData = new DemoData();
        demoData.readFromClasspath(demoDataPath, email);
        return demoData;
    }

    private void readFromClasspath(String demoDataPath, String email) throws Exception {
        byte[] pluginsContent = null;
        byte[] rulesContent = null;
        byte[] customersContent = null;
        byte[] devicesContent = null;
        List<byte[]> dashboardsContent = new ArrayList<>();
        String basePath = demoDataPath;
        if (!basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }
        InputStream in = getResourceAsStream(basePath + "/" + DEMO_PLUGINS_JSON);
        if (in != null) {
            pluginsContent = IOUtils.toByteArray(in);
        }
        in = getResourceAsStream(basePath + "/" + DEMO_RULES_JSON);
        if (in != null) {
            rulesContent = IOUtils.toByteArray(in);
        }
        in = getResourceAsStream(basePath + "/" + DEMO_CUSTOMERS_JSON);
        if (in != null) {
            customersContent = IOUtils.toByteArray(in);
        }
        in = getResourceAsStream(basePath + "/" + DEMO_DEVICES_JSON);
        if (in != null) {
            devicesContent = IOUtils.toByteArray(in);
        }
        List<String> dashboardFiles = getResourceFiles(basePath + "/" + DASHBOARDS_DIR);
        dashboardFiles.forEach(
                dashboardFile -> {
                    InputStream input = getResourceAsStream(dashboardFile);
                    try {
                        byte[] dashboardContent = IOUtils.toByteArray(input);
                        dashboardsContent.add(dashboardContent);
                    } catch (IOException e) {
                        log.error("Failed to read dashboard data from classpath [{}]", dashboardFile);
                        log.error("Cause: ", e);
                    }
                }
        );
        readData(email, pluginsContent, rulesContent, customersContent, devicesContent, dashboardsContent);
    }

    private InputStream getResourceAsStream(String resource) {
        final InputStream in
                = getContextClassLoader().getResourceAsStream(resource);

        return in == null ? getClass().getResourceAsStream(resource) : in;

    }

    private ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    private List<String> getResourceFiles(String path) throws IOException {
        List<String> filenames = new ArrayList<>();
        ClassLoader cl = this.getClass().getClassLoader();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(cl);
        Resource[] resources = resolver.getResources("classpath*:" + path + "/*.json");
        for (Resource resource : resources) {
            filenames.add(path + "/" + resource.getFilename());
        }
        return filenames;
    }

    private void readFromArchiveFile(String demoDataArchive, String email) throws Exception {
        InputStream inputStream = new BufferedInputStream(Files.newInputStream(Paths.get(demoDataArchive)));
        CompressorInputStream input = new CompressorStreamFactory()
                .createCompressorInputStream(inputStream);
        ArchiveInputStream archInput = new ArchiveStreamFactory()
                .createArchiveInputStream(new BufferedInputStream(input));
        ArchiveEntry entry;

        byte[] pluginsContent = null;
        byte[] rulesContent = null;
        byte[] customersContent = null;
        byte[] devicesContent = null;
        List<byte[]> dashboardsContent = new ArrayList<>();
        while ((entry = archInput.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
                String name = entry.getName();
                if (name.equals(DEMO_PLUGINS_JSON)) {
                    pluginsContent = IOUtils.toByteArray(archInput);
                } else if (name.equals(DEMO_RULES_JSON)) {
                    rulesContent = IOUtils.toByteArray(archInput);
                } else if (name.equals(DEMO_CUSTOMERS_JSON)) {
                    customersContent = IOUtils.toByteArray(archInput);
                } else if (name.equals(DEMO_DEVICES_JSON)) {
                    devicesContent = IOUtils.toByteArray(archInput);
                } else if (name.startsWith(DASHBOARDS_DIR + "/")) {
                    byte[] dashboardContent = IOUtils.toByteArray(archInput);
                    dashboardsContent.add(dashboardContent);
                }
            }
        }
        readData(email, pluginsContent, rulesContent, customersContent, devicesContent, dashboardsContent);
    }

    private void readData(String email,
                          byte[] pluginsContent,
                          byte[] rulesContent,
                          byte[] customersContent,
                          byte[] devicesContent,
                          List<byte[]> dashboardsContent) throws Exception {
        Map<String, String> pluginTokenMap = new HashMap<>();
        if (pluginsContent != null) {
            JsonNode pluginsJson = objectMapper.readTree(pluginsContent);
            JsonNode pluginsArray = pluginsJson.get("plugins");
            String hash = DigestUtils.md5DigestAsHex(email.getBytes()).substring(0, 10);
            pluginsArray.forEach(
                    jsonNode -> {
                        try {
                            PluginMetaData plugin = objectMapper.treeToValue(jsonNode, PluginMetaData.class);
                            String pluginToken = plugin.getApiToken();
                            String newPluginToken = pluginToken + "_" + hash;
                            pluginTokenMap.put(pluginToken, newPluginToken);
                            plugin.setApiToken(newPluginToken);
                            if (plugin.getState() == ComponentLifecycleState.ACTIVE) {
                                plugin.setState(ComponentLifecycleState.SUSPENDED);
                            }
                            this.plugins.add(plugin);
                        } catch (Exception e) {
                            log.error("Unable to load plugins from json!");
                            log.error("Cause:", e);
                            System.exit(-1);
                        }
                    }
            );
        }

        if (rulesContent != null) {
            JsonNode rulesJson = objectMapper.readTree(rulesContent);
            JsonNode rulesArray = rulesJson.get("rules");
            rulesArray.forEach(
                    jsonNode -> {
                        try {
                            String jsonBody = objectMapper.writeValueAsString(jsonNode);
                            jsonBody = jsonBody.replaceAll("\\$EMAIL", email);
                            RuleMetaData rule = objectMapper.treeToValue(objectMapper.readTree(jsonBody), RuleMetaData.class);
                            String newPluginToken;
                            // Need to be able to reference system plugins.
                            if (pluginTokenMap.containsKey(rule.getPluginToken())) {
                                newPluginToken = pluginTokenMap.get(rule.getPluginToken());
                            } else {
                                newPluginToken = rule.getPluginToken();
                            }
                            if (newPluginToken != null) {
                                rule.setPluginToken(newPluginToken);
                            }
                            if (rule.getState() == ComponentLifecycleState.ACTIVE) {
                                rule.setState(ComponentLifecycleState.SUSPENDED);
                            }
                            this.rules.add(rule);
                        } catch (Exception e) {
                            log.error("Unable to load rule from json!");
                            log.error("Cause:", e);
                        }
                    }
            );
        }

        if (customersContent != null) {
            JsonNode customersJson = objectMapper.readTree(customersContent);
            JsonNode customersArray = customersJson.get("customers");
            Map<String, CustomerId> customerIdMap = new HashMap<>();
            customersArray.forEach(
                    jsonNode -> {
                        try {
                            Customer customer = objectMapper.treeToValue(jsonNode, Customer.class);
                            this.customers.add(customer);
                        } catch (Exception e) {
                            log.error("Unable to load customer from json!");
                            log.error("Cause:", e);
                        }
                    }
            );
        }

        if (devicesContent != null) {
            JsonNode devicesJson = objectMapper.readTree(devicesContent);
            JsonNode devicesArray = devicesJson.get("devices");
            devicesArray.forEach(
                    jsonNode -> {
                        try {
                            Device device = objectMapper.treeToValue(jsonNode, Device.class);
                            this.devices.add(device);
                        } catch (Exception e) {
                            log.error("Unable to load device from json!");
                            log.error("Cause:", e);
                        }
                    }
            );
            JsonNode customerDevicesJson = devicesJson.get("customerDevices");
            customerDevicesJson.forEach(
                    jsonNode -> {
                        String deviceName = jsonNode.get("deviceName").asText();
                        String customerTitle = jsonNode.get("customerTitle").asText();
                        customerDevices.put(deviceName, customerTitle);
                    }
            );

            JsonNode deviceAttributesJson = devicesJson.get("deviceAttributes");
            deviceAttributesJson.forEach(
                    jsonNode -> {
                        String deviceName = jsonNode.get("deviceName").asText();
                        Map<String, JsonNode> attributesMap = new HashMap<>();
                        if (jsonNode.has("server")) {
                            JsonNode serverAttributes = jsonNode.get("server");
                            attributesMap.put(DataConstants.SERVER_SCOPE, serverAttributes);
                        }
                        if (jsonNode.has("shared")) {
                            JsonNode sharedAttributes = jsonNode.get("shared");
                            attributesMap.put(DataConstants.SHARED_SCOPE, sharedAttributes);
                        }
                        devicesAttributes.put(deviceName, attributesMap);
                    }
            );

        }
        dashboardsContent.forEach(
                dashboardContent -> {
                    try {
                        JsonNode dashboardJson = objectMapper.readTree(dashboardContent);
                        Dashboard dashboard = objectMapper.treeToValue(dashboardJson, Dashboard.class);
                        this.dashboards.add(dashboard);
                    } catch (Exception e) {
                        log.error("Unable to load dashboard from json!");
                        log.error("Cause:", e);
                    }
                }
        );
    }

    public void uploadData(RestTemplate restTemplate, String baseUrl) throws Exception {
        plugins.forEach(
                plugin -> {
                    try {
                        String apiToken = plugin.getApiToken();
                        PluginMetaData savedPlugin = null;
                        try {
                            savedPlugin = restTemplate.getForObject(baseUrl + "/api/plugin/token/" + apiToken, PluginMetaData.class);
                        } catch (HttpClientErrorException e) {
                            if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                                throw e;
                            }
                        }
                        if (savedPlugin == null) {
                            savedPlugin = restTemplate.postForObject(baseUrl + "/api/plugin", plugin, PluginMetaData.class);
                        }
                        if (savedPlugin.getState() == ComponentLifecycleState.SUSPENDED) {
                            restTemplate.postForLocation(baseUrl + "/api/plugin/" + savedPlugin.getId().getId().toString() + "/activate", null);
                        }
                    } catch (Exception e) {
                        log.error("Unable to upload plugin!");
                        log.error("Cause:", e);
                    }
                }
        );

        rules.forEach(
                rule -> {
                    try {
                        RuleMetaData savedRule = null;
                        TextPageData<RuleMetaData> rules;
                        ResponseEntity<TextPageData<RuleMetaData>> entity =
                                restTemplate.exchange(baseUrl + "/api/rule?limit={limit}&textSearch={textSearch}", HttpMethod.GET, null,
                                        new ParameterizedTypeReference<TextPageData<RuleMetaData>>() {
                                        }, 1000, rule.getName());
                        rules = entity.getBody();
                        if (rules.getData().size() > 0) {
                            savedRule = rules.getData().get(0);
                        }
                        if (savedRule == null) {
                            savedRule = restTemplate.postForObject(baseUrl + "/api/rule", rule, RuleMetaData.class);
                        }
                        if (savedRule.getState() == ComponentLifecycleState.SUSPENDED) {
                            restTemplate.postForLocation(baseUrl + "/api/rule/" + savedRule.getId().getId().toString() + "/activate", null);
                        }
                    } catch (Exception e) {
                        log.error("Unable to upload rule!");
                        log.error("Cause:", e);
                    }
                }
        );

        Map<String, CustomerId> customerIdMap = new HashMap<>();
        customers.forEach(
                customer -> {
                    try {
                        Customer savedCustomer = null;
                        TextPageData<Customer> customers;
                        ResponseEntity<TextPageData<Customer>> entity =
                                restTemplate.exchange(baseUrl + "/api/customers?limit={limit}&textSearch={textSearch}", HttpMethod.GET, null,
                                        new ParameterizedTypeReference<TextPageData<Customer>>() {
                                        }, 1000, customer.getTitle());
                        customers = entity.getBody();
                        if (customers.getData().size() > 0) {
                            savedCustomer = customers.getData().get(0);
                        }
                        if (savedCustomer == null) {
                            savedCustomer = restTemplate.postForObject(baseUrl + "/api/customer", customer, Customer.class);
                        }
                        customerIdMap.put(savedCustomer.getTitle(), savedCustomer.getId());
                    } catch (Exception e) {
                        log.error("Unable to upload customer!");
                        log.error("Cause:", e);
                    }
                }
        );

        List<Device> loadedDevices = new ArrayList<>();

        Map<String, DeviceId> deviceIdMap = new HashMap<>();
        devices.forEach(
                device -> {
                    try {
                        CustomerId customerId = null;
                        String customerTitle = customerDevices.get(device.getName());
                        if (customerTitle != null) {
                            customerId = customerIdMap.get(customerTitle);
                        }
                        Device savedDevice = null;
                        TextPageData<Device> devices;
                        if (customerId != null) {
                            ResponseEntity<TextPageData<Device>> entity =
                                    restTemplate.exchange(baseUrl + "/api/customer/{customerId}/devices?limit={limit}&textSearch={textSearch}", HttpMethod.GET, null,
                                            new ParameterizedTypeReference<TextPageData<Device>>() {
                                            }, customerId.getId().toString(), 1000, device.getName());
                            devices = entity.getBody();
                            if (devices.getData().size() > 0) {
                                savedDevice = devices.getData().get(0);
                            }
                        }
                        if (savedDevice == null) {
                            ResponseEntity<TextPageData<Device>> entity =
                                    restTemplate.exchange(baseUrl + "/api/tenant/devices?limit={limit}&textSearch={textSearch}", HttpMethod.GET, null,
                                            new ParameterizedTypeReference<TextPageData<Device>>() {
                                            }, 1000, device.getName());
                            devices = entity.getBody();
                            if (devices.getData().size() > 0) {
                                savedDevice = devices.getData().get(0);
                            }
                        }
                        if (savedDevice == null) {
                            savedDevice = restTemplate.postForObject(baseUrl + "/api/device", device, Device.class);
                        }
                        if (customerId != null && savedDevice.getCustomerId().isNullUid()) {
                            restTemplate.postForLocation(baseUrl + "/api/customer/{customerId}/device/{deviceId}", null, customerId.getId().toString(),
                                    savedDevice.getId().toString());
                        }
                        String deviceName = savedDevice.getName();
                        DeviceId deviceId = savedDevice.getId();
                        deviceIdMap.put(deviceName, deviceId);
                        loadedDevices.add(savedDevice);
                        DeviceCredentials credentials =
                                restTemplate.getForObject(baseUrl + "/api/device/{deviceId}/credentials", DeviceCredentials.class, deviceId.getId().toString());
                        loadedDevicesCredentialsMap.put(deviceId, credentials);

                        Map<String, JsonNode> attributesMap = devicesAttributes.get(deviceName);
                        if (attributesMap != null) {
                            attributesMap.forEach(
                                    (k, v) -> {
                                        restTemplate.postForObject(baseUrl + "/api/plugins/telemetry/{deviceId}/{attributeScope}", v, Void.class,
                                                deviceId.getId().toString(), k);
                                    }
                            );
                        }
                    } catch (Exception e) {
                        log.error("Unable to upload device!");
                        log.error("Cause:", e);
                    }
                }
        );

        devices = loadedDevices;

        Map<String, Dashboard> pendingDashboards = dashboards.stream().collect(Collectors.toMap(Dashboard::getTitle, Function.identity()));
        Map<String, Dashboard> savedDashboards = new HashMap<>();
        while (pendingDashboards.size() != 0) {
            Dashboard savedDashboard = null;
            for (Dashboard dashboard : pendingDashboards.values()) {
                savedDashboard = getDashboardByName(restTemplate, baseUrl, dashboard);
                if (savedDashboard == null) {
                    try {
                        Optional<String> dashboardConfigurationBody = resolveLinks(savedDashboards, dashboard.getConfiguration());
                        if (dashboardConfigurationBody.isPresent()) {
                            dashboard.setConfiguration(objectMapper.readTree(dashboardConfigurationBody.get()));
                            JsonNode dashboardConfiguration = dashboard.getConfiguration();
                            JsonNode deviceAliases = dashboardConfiguration.get("deviceAliases");
                            deviceAliases.forEach(
                                    jsonNode -> {
                                        String aliasName = jsonNode.get("alias").asText();
                                        DeviceId deviceId = deviceIdMap.get(aliasName);
                                        if (deviceId != null) {
                                            ((ObjectNode) jsonNode).put("deviceId", deviceId.getId().toString());
                                        }
                                    }
                            );
                            savedDashboard = restTemplate.postForObject(baseUrl + "/api/dashboard", dashboard, Dashboard.class);
                        }
                    } catch (Exception e) {
                        log.error("Unable to upload dashboard!");
                        log.error("Cause:", e);
                    }
                }
                if (savedDashboard != null) {
                    break;
                }
            }
            if (savedDashboard != null) {
                savedDashboards.put(savedDashboard.getTitle(), savedDashboard);
                pendingDashboards.remove(savedDashboard.getTitle());
            } else {
                log.error("Unable to upload dashboards due to unresolved references!");
                break;
            }
        }

        dashboards = new ArrayList<>(savedDashboards.values());
    }

    private Optional<String> resolveLinks(Map<String, Dashboard> savedDashboards, JsonNode dashboardConfiguration) throws JsonProcessingException {
        String dashboardConfigurationBody = objectMapper.writeValueAsString(dashboardConfiguration);
        boolean resolved = true;
        for (Dashboard other : dashboards) {
            String otherLink = "$" + other.getTitle().toUpperCase().replace(' ', '_') + "_LINK";
            if (dashboardConfigurationBody.contains(otherLink)) {
                if (savedDashboards.containsKey(other.getTitle())) {
                    dashboardConfigurationBody = dashboardConfigurationBody.replace(otherLink, savedDashboards.get(other.getTitle()).getId().getId().toString());
                } else {
                    resolved = false;
                    break;
                }
            }
        }
        return resolved ? Optional.of(dashboardConfigurationBody) : Optional.empty();
    }

    private Dashboard getDashboardByName(RestTemplate restTemplate, String baseUrl, Dashboard dashboard) {
        Dashboard savedDashboard = null;
        TextPageData<Dashboard> dashboards;
        ResponseEntity<TextPageData<Dashboard>> entity =
                restTemplate.exchange(baseUrl + "/api/tenant/dashboards?limit={limit}&textSearch={textSearch}", HttpMethod.GET, null,
                        new ParameterizedTypeReference<TextPageData<Dashboard>>() {
                        }, 1000, dashboard.getTitle());
        dashboards = entity.getBody();
        for (Dashboard saved : dashboards.getData()) {
            if (saved.getTitle().equalsIgnoreCase(dashboard.getTitle())) {
                savedDashboard = saved;
                break;
            }
        }
        return savedDashboard;
    }
}
