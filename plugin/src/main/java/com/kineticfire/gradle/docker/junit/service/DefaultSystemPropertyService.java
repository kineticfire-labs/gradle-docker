/*
 * (c) Copyright 2023-2025 gradle-docker Contributors. All rights reserved.
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

package com.kineticfire.gradle.docker.junit.service;

/**
 * Default implementation of SystemPropertyService using Java System properties.
 * <p>
 * This implementation delegates to the standard Java System.getProperty and System.setProperty methods.
 */
public class DefaultSystemPropertyService implements SystemPropertyService {

    /**
     * Creates a new DefaultSystemPropertyService instance.
     */
    public DefaultSystemPropertyService() {
        // Default constructor
    }

    @Override
    public String getProperty(String key) {
        return System.getProperty(key);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        return System.getProperty(key, defaultValue);
    }

    @Override
    public void setProperty(String key, String value) {
        System.setProperty(key, value);
    }
}