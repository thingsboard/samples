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

package org.thingsboard.demo.loader;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.thingsboard.demo.loader.data.DemoData;
import org.thingsboard.demo.loader.data.LoginRequest;
import org.thingsboard.demo.loader.data.TokenResponse;
import org.thingsboard.server.common.data.User;

import java.io.IOException;
import java.net.URI;

@Slf4j
public class DemoLoader {

    private String baseUrl;
    private RestTemplate restTemplate;

    private String jwtToken = null;
    private String email = null;
    private DemoData demoData = null;

    public DemoLoader(String baseUrl) {
        this.baseUrl = baseUrl;
        this.restTemplate = createRestTemplate();
    }

    public boolean authorize(String username, String password) {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);
        try {
            TokenResponse tokenResponse = restTemplate.postForObject(this.baseUrl + "/api/auth/login", loginRequest, TokenResponse.class);
            this.jwtToken = tokenResponse.getToken();
            this.email = username;
            return true;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                return false;
            } else {
                throw e;
            }
        }
    }

    public boolean loadDemoFromClasspath(String demoPath) {
        log.info("Start to load demo data to [{}] from [{}] for user [{}]...", baseUrl, demoPath, email);
        try {
            try {
                demoData = DemoData.fromClasspathDir(demoPath, email);
            } catch (Exception e) {
                System.err.println(String.format("Unable to load data from classpath: %s", demoPath));
                System.err.println(e.getMessage());
                e.printStackTrace();
                System.exit(-1);
            }
            demoData.uploadData(restTemplate, baseUrl);
            log.info("Demo data successfully loaded!");
            return true;
        } catch (Exception e) {
            log.error("Failed to load demo data. Cause: ", e);
        }
        return false;
    }

    public boolean loadDemoFromArchive(String demoDataArchive) {
        log.info("Start to load demo data to [{}] from [{}] for user [{}]...", baseUrl, demoDataArchive, email);
        try {

            try {
                demoData = DemoData.fromArchiveFile(demoDataArchive, email);
            } catch (Exception e) {
                System.err.println(String.format("Invalid demo archive: %s", demoDataArchive));
                System.err.println(e.getMessage());
                System.exit(-1);
            }
            demoData.uploadData(restTemplate, baseUrl);
            log.info("Demo data successfully loaded!");
            return true;
        } catch (Exception e) {
            log.error("Failed to load demo data. Cause: ", e);
        }
        return false;
    }

    public DemoData getDemoData() {
        return demoData;
    }

    private RestTemplate createRestTemplate() {
        return new RestTemplate() {
            @Override
            protected ClientHttpRequest createRequest(URI url, HttpMethod method) throws IOException {
                ClientHttpRequest httpRequest = super.createRequest(url, method);
                if (DemoLoader.this.jwtToken != null) {
                    httpRequest.getHeaders().add("X-Authorization",
                            String.format("Bearer %s", DemoLoader.this.jwtToken));
                }
                return httpRequest;
            }
        };
    }

}
