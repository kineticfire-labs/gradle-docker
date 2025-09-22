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
 * Service interface for system property operations.
 * <p>
 * This interface abstracts system property access to enable testing and dependency injection.
 * It provides methods for getting and setting system properties with optional default values.
 */
public interface SystemPropertyService {

    /**
     * Gets the system property indicated by the specified key.
     *
     * @param key the name of the system property
     * @return the string value of the system property, or null if there is no property with that key
     */
    String getProperty(String key);

    /**
     * Gets the system property indicated by the specified key, or returns the default value if not found.
     *
     * @param key the name of the system property
     * @param defaultValue the default value to return if the property is not found
     * @return the string value of the system property, or the default value if there is no property with that key
     */
    String getProperty(String key, String defaultValue);

    /**
     * Sets the system property indicated by the specified key.
     *
     * @param key the name of the system property
     * @param value the value to set for the system property
     */
    void setProperty(String key, String value);
}