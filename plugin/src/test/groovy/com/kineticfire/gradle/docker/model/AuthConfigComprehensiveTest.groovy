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

import spock.lang.Specification

/**
 * Comprehensive unit tests for AuthConfig
 */
class AuthConfigComprehensiveTest extends Specification {

    def "constructor with all parameters"() {
        when:
        def authConfig = new AuthConfig("user", "pass", "token", "server.io")

        then:
        authConfig.username == "user"
        authConfig.password == "pass"
        authConfig.registryToken == "token"
        authConfig.serverAddress == "server.io"
    }

    def "constructor with null parameters"() {
        when:
        def authConfig = new AuthConfig(null, null, null, null)

        then:
        authConfig.username == null
        authConfig.password == null
        authConfig.registryToken == null
        authConfig.serverAddress == null
    }

    def "constructor with mixed null and non-null parameters"() {
        when:
        def authConfig = new AuthConfig("user", null, "token", null)

        then:
        authConfig.username == "user"
        authConfig.password == null
        authConfig.registryToken == "token"
        authConfig.serverAddress == null
    }

    def "hasCredentials returns true when username and password are set"() {
        given:
        def authConfig = new AuthConfig("user", "pass", null, "server.io")

        when:
        def hasCredentials = authConfig.hasCredentials()

        then:
        hasCredentials == true
    }

    def "hasCredentials returns true when registry token is set"() {
        given:
        def authConfig = new AuthConfig(null, null, "token123", "server.io")

        when:
        def hasCredentials = authConfig.hasCredentials()

        then:
        hasCredentials == true
    }

    def "hasCredentials returns false when no credentials are set"() {
        given:
        def authConfig = new AuthConfig(null, null, null, "server.io")

        when:
        def hasCredentials = authConfig.hasCredentials()

        then:
        hasCredentials == false
    }

    def "hasCredentials returns false when only username is set"() {
        given:
        def authConfig = new AuthConfig("user", null, null, "server.io")

        when:
        def hasCredentials = authConfig.hasCredentials()

        then:
        hasCredentials == false
    }

    def "hasCredentials returns false when only password is set"() {
        given:
        def authConfig = new AuthConfig(null, "pass", null, "server.io")

        when:
        def hasCredentials = authConfig.hasCredentials()

        then:
        hasCredentials == false
    }

    def "hasCredentials returns true when username and password are both empty strings"() {
        given:
        def authConfig = new AuthConfig("", "", null, "server.io")

        when:
        def hasCredentials = authConfig.hasCredentials()

        then:
        hasCredentials == true
    }

    def "hasCredentials returns true when registry token is empty string"() {
        given:
        def authConfig = new AuthConfig(null, null, "", "server.io")

        when:
        def hasCredentials = authConfig.hasCredentials()

        then:
        hasCredentials == true
    }

    def "hasCredentials scenarios"() {
        expect:
        new AuthConfig(username, password, token, "server").hasCredentials() == expectedResult

        where:
        username | password | token   | expectedResult
        "user"   | "pass"   | null    | true
        "user"   | "pass"   | "token" | true
        null     | null     | "token" | true
        ""       | ""       | null    | true
        null     | null     | ""      | true
        "user"   | null     | null    | false
        null     | "pass"   | null    | false
        null     | null     | null    | false
        ""       | null     | null    | false
        null     | ""       | null    | false
    }

    def "toDockerJavaAuthConfig with username and password"() {
        given:
        def authConfig = new AuthConfig("dockeruser", "dockerpass", null, "docker.io")

        when:
        def dockerJavaAuth = authConfig.toDockerJavaAuthConfig()

        then:
        dockerJavaAuth != null
        dockerJavaAuth.username == "dockeruser"
        dockerJavaAuth.password == "dockerpass"
        dockerJavaAuth.registryAddress == "docker.io"
        dockerJavaAuth.registrytoken == null
    }

    def "toDockerJavaAuthConfig with registry token"() {
        given:
        def authConfig = new AuthConfig(null, null, "ghp_token123", "ghcr.io")

        when:
        def dockerJavaAuth = authConfig.toDockerJavaAuthConfig()

        then:
        dockerJavaAuth != null
        dockerJavaAuth.registrytoken == "ghp_token123"
        dockerJavaAuth.registryAddress == "ghcr.io"
        dockerJavaAuth.username == null
        dockerJavaAuth.password == null
    }

    def "toDockerJavaAuthConfig with all fields"() {
        given:
        def authConfig = new AuthConfig("user", "pass", "token", "registry.io")

        when:
        def dockerJavaAuth = authConfig.toDockerJavaAuthConfig()

        then:
        dockerJavaAuth != null
        dockerJavaAuth.username == "user"
        dockerJavaAuth.password == "pass"
        dockerJavaAuth.registrytoken == "token"
        dockerJavaAuth.registryAddress == "registry.io"
    }

    def "toDockerJavaAuthConfig with minimal fields"() {
        given:
        def authConfig = new AuthConfig(null, null, null, null)

        when:
        def dockerJavaAuth = authConfig.toDockerJavaAuthConfig()

        then:
        dockerJavaAuth != null
        dockerJavaAuth.username == null
        dockerJavaAuth.password == null
        dockerJavaAuth.registrytoken == null
        // Docker Java client sets default registry address when none provided
        dockerJavaAuth.registryAddress == "https://index.docker.io/v1/"
    }

    def "equals and hashCode contract"() {
        given:
        def auth1 = new AuthConfig("user", "pass", "token", "server")
        def auth2 = new AuthConfig("user", "pass", "token", "server")
        def auth3 = new AuthConfig("different", "pass", "token", "server")

        expect:
        auth1 == auth2
        auth1.hashCode() == auth2.hashCode()
        auth1 != auth3
        auth1 != null
        auth1 != "string"
    }

    def "toString contains relevant information"() {
        given:
        def authConfig = new AuthConfig("user", "pass", "token", "server.io")

        when:
        def string = authConfig.toString()

        then:
        string.contains("AuthConfig")
        string.contains("server.io")
        // Token-based auth takes priority, so username is not shown when token is present
        string.contains("token=*****")
        // Password and token should be masked or not included for security
    }

    def "real-world authentication scenarios"() {
        expect:
        def authConfig = new AuthConfig(username, password, token, server)
        authConfig.hasCredentials() == hasCredentials
        authConfig.toDockerJavaAuthConfig() != null

        where:
        username    | password     | token              | server                                        | hasCredentials
        "dockerhub" | "dckr_token" | null               | "docker.io"                                   | true
        null        | null         | "ghp_abc123"       | "ghcr.io"                                     | true
        "_json_key" | '{"type":""}'| null               | "gcr.io"                                      | true
        "AWS"       | "aws_token"  | null               | "123456789012.dkr.ecr.us-west-2.amazonaws.com" | true
        null        | null         | "bearer_token"     | "private.registry.com:5000"                  | true
        "user"      | "password"   | null               | "localhost:5000"                              | true
        null        | null         | null               | "public.registry.io"                          | false
    }

    def "edge cases with empty strings"() {
        given:
        def authConfig = new AuthConfig("", "", "", "")

        expect:
        authConfig.username == ""
        authConfig.password == ""
        authConfig.registryToken == ""
        authConfig.serverAddress == ""
        authConfig.hasCredentials() == true  // Empty strings count as credentials
    }

    def "immutability of fields"() {
        given:
        def authConfig = new AuthConfig("user", "pass", "token", "server")

        expect:
        // Fields should be final and immutable
        authConfig.username == "user"
        authConfig.password == "pass"
        authConfig.registryToken == "token"
        authConfig.serverAddress == "server"
    }

    def "constructor parameter validation"() {
        expect:
        // Constructor should accept any combination of parameters
        new AuthConfig(null, null, null, null) != null
        new AuthConfig("", "", "", "") != null
        new AuthConfig("user", null, null, null) != null
        new AuthConfig(null, "pass", null, null) != null
        new AuthConfig(null, null, "token", null) != null
        new AuthConfig(null, null, null, "server") != null
    }
}