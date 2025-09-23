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

package com.kineticfire.gradle.docker.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Default implementation of ServiceLogger using SLF4J
 */
class DefaultServiceLogger implements ServiceLogger {
    
    private final Logger logger
    
    DefaultServiceLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz)
    }
    
    @Override
    void info(String message) {
        logger.info(message)
    }
    
    @Override
    void debug(String message) {
        logger.debug(message)
    }
    
    @Override
    void error(String message, Throwable throwable) {
        logger.error(message, throwable)
    }
    
    @Override
    void error(String message) {
        logger.error(message)
    }
    
    @Override
    void warn(String message) {
        logger.warn(message)
    }
}