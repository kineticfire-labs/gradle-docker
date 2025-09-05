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

package com.kineticfire.gradle.docker.model

/**
 * Types of compression for Docker image saves
 */
enum CompressionType {
    NONE("none", "tar"),
    GZIP("gzip", "tar.gz"),
    BZIP2("bzip2", "tar.bz2"),
    XZ("xz", "tar.xz"),
    ZIP("zip", "zip")
    
    final String type
    final String extension
    
    CompressionType(String type, String extension) {
        this.type = type
        this.extension = extension
    }
    
    /**
     * Parse compression type from string
     */
    static CompressionType fromString(String value) {
        if (!value) return NONE
        
        def lowerValue = value.toLowerCase()
        switch (lowerValue) {
            case 'gzip':
            case 'gz':
                return GZIP
            case 'bzip2':
            case 'bz2':
                return BZIP2
            case 'xz':
                return XZ
            case 'zip':
                return ZIP
            case 'none':
            case 'tar':
            default:
                return NONE
        }
    }
    
    /**
     * Get file extension for this compression type
     */
    String getFileExtension() {
        return extension
    }
    
    @Override
    String toString() {
        return type
    }
}