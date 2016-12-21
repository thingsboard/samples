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

@Slf4j
public class DemoLoaderMain {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println(String.format("Invalid arguments count: %d", args.length));
            printUsage();
            System.exit(-1);
        }
        String baseUrl = args[0];
        String demoDataArchive = args[1];

        DemoLoader demoLoader = new DemoLoader(baseUrl);
        try {
            boolean authorized = false;
            while (!authorized) {
                String username = ConsoleReader.readLine("Enter Tenant Admin username: ");
                String password = new String(ConsoleReader.readPassword("Enter Tenant Admin password: "));
                authorized = demoLoader.authorize(username, password);
                if (!authorized) {
                    System.err.println("Bad username or password!");
                }
            }
            System.exit(demoLoader.loadDemoFromArchive(demoDataArchive) ? 0 : -1);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: ");
        System.out.println("java -jar thingsboard-demo-loader.jar <thingsboard base url> <demo data archive>");
    }

}
