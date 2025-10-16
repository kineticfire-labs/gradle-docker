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

package com.kineticfire.gradle.docker.spock

import org.spockframework.runtime.extension.ExtensionAnnotation
import spock.lang.Specification

import java.lang.annotation.ElementType
import java.lang.annotation.RetentionPolicy

/**
 * Unit tests for {@link ComposeUp} annotation.
 */
class ComposeUpTest extends Specification {

    def "annotation should exist and be accessible"() {
        expect:
        ComposeUp != null
    }

    def "annotation should have RUNTIME retention policy"() {
        when:
        def retention = ComposeUp.getAnnotation(java.lang.annotation.Retention)

        then:
        retention != null
        retention.value() == RetentionPolicy.RUNTIME
    }

    def "annotation should target TYPE (class level)"() {
        when:
        def target = ComposeUp.getAnnotation(java.lang.annotation.Target)

        then:
        target != null
        target.value().contains(ElementType.TYPE)
        target.value().length == 1
    }

    def "annotation should be marked as ExtensionAnnotation"() {
        when:
        def extensionAnnotation = ComposeUp.getAnnotation(ExtensionAnnotation)

        then:
        extensionAnnotation != null
        extensionAnnotation.value() == DockerComposeSpockExtension
    }

    def "annotation should have required stackName method"() {
        when:
        def method = ComposeUp.getMethod('stackName')

        then:
        method != null
        method.returnType == String
        method.defaultValue == null  // Required, no default
    }

    def "annotation should have required composeFile method"() {
        when:
        def method = ComposeUp.getMethod('composeFile')

        then:
        method != null
        method.returnType == String
        method.defaultValue == null  // Required, no default
    }

    def "annotation should have lifecycle method with default CLASS"() {
        when:
        def method = ComposeUp.getMethod('lifecycle')

        then:
        method != null
        method.returnType == LifecycleMode
        method.defaultValue == LifecycleMode.CLASS
    }

    def "annotation should have projectName method with empty string default"() {
        when:
        def method = ComposeUp.getMethod('projectName')

        then:
        method != null
        method.returnType == String
        method.defaultValue == ""
    }

    def "annotation should have waitForHealthy method with empty array default"() {
        when:
        def method = ComposeUp.getMethod('waitForHealthy')

        then:
        method != null
        method.returnType.isArray()
        method.returnType.componentType == String
        (method.defaultValue as String[]).length == 0
    }

    def "annotation should have waitForRunning method with empty array default"() {
        when:
        def method = ComposeUp.getMethod('waitForRunning')

        then:
        method != null
        method.returnType.isArray()
        method.returnType.componentType == String
        (method.defaultValue as String[]).length == 0
    }

    def "annotation should have timeoutSeconds method with default 60"() {
        when:
        def method = ComposeUp.getMethod('timeoutSeconds')

        then:
        method != null
        method.returnType == int
        method.defaultValue == 60
    }

    def "annotation should have pollSeconds method with default 2"() {
        when:
        def method = ComposeUp.getMethod('pollSeconds')

        then:
        method != null
        method.returnType == int
        method.defaultValue == 2
    }

    def "annotation should have all expected methods"() {
        when:
        def methods = ComposeUp.declaredMethods.findAll { !it.synthetic }

        then:
        methods.size() == 8
        methods.collect { it.name }.sort() == [
            'composeFile',
            'lifecycle',
            'pollSeconds',
            'projectName',
            'stackName',
            'timeoutSeconds',
            'waitForHealthy',
            'waitForRunning'
        ].sort()
    }
}
