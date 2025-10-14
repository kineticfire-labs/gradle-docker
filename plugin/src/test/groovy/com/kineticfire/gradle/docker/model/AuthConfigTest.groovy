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
 * Unit tests for AuthConfig
 */
class AuthConfigTest extends Specification {

    def "can create empty AuthConfig"() {
        when:
        def auth = new AuthConfig()

        then:
        auth.username == null
        auth.password == null
        auth.registryToken == null
        // serverAddress removed - extracted automatically from image reference
        !auth.hasCredentials()
        !auth.isUsernamePassword()
        !auth.isTokenBased()
    }

    def "can create username/password AuthConfig"() {
        when:
        def auth = new AuthConfig("testuser", "testpass", null)

        then:
        auth.username == "testuser"
        auth.password == "testpass"
        auth.registryToken == null
        // serverAddress removed - extracted automatically from image reference
        auth.hasCredentials()
        auth.isUsernamePassword()
        !auth.isTokenBased()
    }

    def "can create token-based AuthConfig"() {
        when:
        def auth = new AuthConfig(null, null, "ghp_abcd1234")

        then:
        auth.username == null
        auth.password == null
        auth.registryToken == "ghp_abcd1234"
        // serverAddress removed - extracted automatically from image reference
        auth.hasCredentials()
        !auth.isUsernamePassword()
        auth.isTokenBased()
    }

    def "can create minimal username/password config"() {
        when:
        def auth = new AuthConfig("user", "pass")

        then:
        auth.username == "user"
        auth.password == "pass"
        // serverAddress removed - extracted automatically from image reference
        auth.hasCredentials()
        auth.isUsernamePassword()
    }

    def "can create minimal token config"() {
        when:
        def auth = new AuthConfig(null, null, "token123")

        then:
        auth.registryToken == "token123"
        // serverAddress removed - extracted automatically from image reference
        auth.hasCredentials()
        auth.isTokenBased()
    }

    def "hasCredentials returns false for incomplete username/password"() {
        expect:
        !new AuthConfig("user", null).hasCredentials()
        !new AuthConfig(null, "pass").hasCredentials()
        !new AuthConfig("", "pass").hasCredentials()
        !new AuthConfig("user", "").hasCredentials()
    }

    def "hasCredentials returns true for valid username/password"() {
        expect:
        new AuthConfig("user", "pass").hasCredentials()
        new AuthConfig("user", "pass", null).hasCredentials() // serverAddress ignored
    }

    def "hasCredentials returns true for token-based auth"() {
        expect:
        new AuthConfig(null, null, "token").hasCredentials()
        new AuthConfig("", "", "token").hasCredentials()
    }

    def "isUsernamePassword validates both fields are present"() {
        expect:
        new AuthConfig("user", "pass").isUsernamePassword()
        new AuthConfig("user", "pass", "token").isUsernamePassword() // Both types present
        
        !new AuthConfig("user", null).isUsernamePassword()
        !new AuthConfig(null, "pass").isUsernamePassword()
        !new AuthConfig("", "pass").isUsernamePassword()
        !new AuthConfig("user", "").isUsernamePassword()
        !new AuthConfig(null, null, "token").isUsernamePassword()
    }

    def "isTokenBased validates token is present"() {
        expect:
        new AuthConfig(null, null, "token").isTokenBased()
        new AuthConfig("user", "pass", "token").isTokenBased() // Both types present
        new AuthConfig("", "", "token").isTokenBased()
        
        !new AuthConfig("user", "pass").isTokenBased()
        !new AuthConfig().isTokenBased()
        !new AuthConfig(null, null, null).isTokenBased()
    }

    def "toDockerJavaAuthConfig creates proper Docker Java AuthConfig"() {
        given:
        def auth = new AuthConfig("testuser", "testpass", "testtoken") // serverAddress ignored

        when:
        def dockerAuth = auth.toDockerJavaAuthConfig()

        then:
        dockerAuth != null
        dockerAuth.username == "testuser"
        dockerAuth.password == "testpass"
        dockerAuth.registrytoken == "testtoken"
        // serverAddress removed - registry address extracted automatically from image reference
    }

    def "toDockerJavaAuthConfig handles null values properly"() {
        given:
        def auth = new AuthConfig("user", null, null)

        when:
        def dockerAuth = auth.toDockerJavaAuthConfig()

        then:
        dockerAuth != null
        dockerAuth.username == "user"
        dockerAuth.password == null
        dockerAuth.registrytoken == null
        // serverAddress removed - registry address extracted automatically from image reference
    }

    def "toDockerJavaAuthConfig handles empty AuthConfig"() {
        given:
        def auth = new AuthConfig()

        when:
        def dockerAuth = auth.toDockerJavaAuthConfig()

        then:
        dockerAuth != null
        dockerAuth.username == null
        dockerAuth.password == null
        dockerAuth.registrytoken == null
        // serverAddress removed - registry address extracted automatically from image reference
    }

    def "toString masks sensitive information"() {
        expect:
        new AuthConfig().toString() == "AuthConfig{empty}"
        
        new AuthConfig("user", "secret", null).toString() == 
            "AuthConfig{username='user', password=*****}" // serverAddress removed
            
        new AuthConfig(null, null, "secrettoken").toString() == 
            "AuthConfig{token=*****}" // serverAddress removed
    }

    def "toString doesn't include serverAddress"() {
        expect:
        new AuthConfig("user", "pass").toString() == 
            "AuthConfig{username='user', password=*****}"
            
        new AuthConfig(null, null, "token").toString() == 
            "AuthConfig{token=*****}"
    }

    def "toString prioritizes token over username/password when both present"() {
        when:
        def auth = new AuthConfig("user", "pass", "token") // serverAddress ignored

        then:
        auth.toString() == "AuthConfig{token=*****}" // serverAddress removed
    }

    def "edge case: empty strings vs null values"() {
        given:
        def auth = new AuthConfig("", "", "") // serverAddress ignored

        expect:
        // Empty strings are falsy in Groovy boolean context, but still valid for auth purposes
        auth.hasCredentials()  // True because both username/password and token methods are valid
        auth.isUsernamePassword()  // True because both username and password are empty (anonymous auth)
        // Empty string registryToken is not null, so isTokenBased returns true
        auth.isTokenBased()
        // Since isTokenBased is true and takes priority, toString shows token format
        auth.toString() == "AuthConfig{token=*****}" // serverAddress removed
    }

    def "edge case: whitespace-only strings"() {
        given:
        def auth = new AuthConfig("  ", "  ", "  ") // serverAddress ignored

        expect:
        auth.hasCredentials() // Whitespace strings are truthy in Groovy
        auth.isUsernamePassword()
        auth.isTokenBased()
    }

    // ===== EQUALS METHOD TESTS =====

    def "equals returns true for same object"() {
        given:
        def auth = new AuthConfig("user", "pass", "token")

        expect:
        auth.equals(auth)
    }

    def "equals returns true for equal objects"() {
        given:
        def auth1 = new AuthConfig("user", "pass", "token")
        def auth2 = new AuthConfig("user", "pass", "token")

        expect:
        auth1.equals(auth2)
    }

    def "equals returns true for equal empty objects"() {
        given:
        def auth1 = new AuthConfig()
        def auth2 = new AuthConfig()

        expect:
        auth1.equals(auth2)
    }

    def "equals returns false for different username"() {
        given:
        def auth1 = new AuthConfig("user1", "pass", null)
        def auth2 = new AuthConfig("user2", "pass", null)

        expect:
        !auth1.equals(auth2)
    }

    def "equals returns false for different password"() {
        given:
        def auth1 = new AuthConfig("user", "pass1", null)
        def auth2 = new AuthConfig("user", "pass2", null)

        expect:
        !auth1.equals(auth2)
    }

    def "equals returns false for different token"() {
        given:
        def auth1 = new AuthConfig(null, null, "token1")
        def auth2 = new AuthConfig(null, null, "token2")

        expect:
        !auth1.equals(auth2)
    }

    def "equals returns false for null object"() {
        given:
        def auth = new AuthConfig("user", "pass")

        expect:
        !auth.equals(null)
    }

    def "equals returns false for different class"() {
        given:
        def auth = new AuthConfig("user", "pass")

        expect:
        !auth.equals("not an AuthConfig")
        !auth.equals(123)
        !auth.equals([username: "user", password: "pass"])
    }

    def "equals handles null vs empty string username"() {
        given:
        def auth1 = new AuthConfig(null, "pass", null)
        def auth2 = new AuthConfig("", "pass", null)

        expect:
        !auth1.equals(auth2)
    }

    def "equals handles null vs empty string password"() {
        given:
        def auth1 = new AuthConfig("user", null, null)
        def auth2 = new AuthConfig("user", "", null)

        expect:
        !auth1.equals(auth2)
    }

    def "equals handles null vs empty string token"() {
        given:
        def auth1 = new AuthConfig(null, null, null)
        def auth2 = new AuthConfig(null, null, "")

        expect:
        !auth1.equals(auth2)
    }

    // ===== HASHCODE METHOD TESTS =====

    def "hashCode returns same value for equal objects"() {
        given:
        def auth1 = new AuthConfig("user", "pass", "token")
        def auth2 = new AuthConfig("user", "pass", "token")

        expect:
        auth1.hashCode() == auth2.hashCode()
    }

    def "hashCode returns same value for equal empty objects"() {
        given:
        def auth1 = new AuthConfig()
        def auth2 = new AuthConfig()

        expect:
        auth1.hashCode() == auth2.hashCode()
    }

    def "hashCode returns different values for different username"() {
        given:
        def auth1 = new AuthConfig("user1", "pass", null)
        def auth2 = new AuthConfig("user2", "pass", null)

        expect:
        auth1.hashCode() != auth2.hashCode()
    }

    def "hashCode returns different values for different password"() {
        given:
        def auth1 = new AuthConfig("user", "pass1", null)
        def auth2 = new AuthConfig("user", "pass2", null)

        expect:
        auth1.hashCode() != auth2.hashCode()
    }

    def "hashCode returns different values for different token"() {
        given:
        def auth1 = new AuthConfig(null, null, "token1")
        def auth2 = new AuthConfig(null, null, "token2")

        expect:
        auth1.hashCode() != auth2.hashCode()
    }

    def "hashCode is consistent across multiple calls"() {
        given:
        def auth = new AuthConfig("user", "pass", "token")
        def hash1 = auth.hashCode()
        def hash2 = auth.hashCode()
        def hash3 = auth.hashCode()

        expect:
        hash1 == hash2
        hash2 == hash3
    }

    def "hashCode uses all significant fields"() {
        given:
        def configs = [
            new AuthConfig("user", "pass", null),
            new AuthConfig("user", "pass", "token"),
            new AuthConfig("different", "pass", null),
            new AuthConfig("user", "different", null),
            new AuthConfig(null, null, "token")
        ]

        when:
        def hashCodes = configs.collect { it.hashCode() }

        then:
        // All hash codes should be different (though collisions are theoretically possible)
        hashCodes.toSet().size() == 5
    }

    def "equals and hashCode contract is maintained"() {
        given:
        def auth1 = new AuthConfig("user", "pass", "token")
        def auth2 = new AuthConfig("user", "pass", "token")
        def auth3 = new AuthConfig("different", "pass", "token")

        expect:
        // If objects are equal, hashCodes must be equal
        auth1.equals(auth2) implies auth1.hashCode() == auth2.hashCode()

        // If objects are not equal, hashCodes should be different (though not required)
        !auth1.equals(auth3)
    }

    // ===== ANONYMOUS AUTH EDGE CASE =====

    def "hasCredentials returns true for anonymous auth (empty username and password)"() {
        given:
        def auth = new AuthConfig("", "", null)

        expect:
        auth.hasCredentials()
        auth.isUsernamePassword()
        !auth.isTokenBased()
    }

    def "equals works correctly for anonymous auth"() {
        given:
        def auth1 = new AuthConfig("", "", null)
        def auth2 = new AuthConfig("", "", null)

        expect:
        auth1.equals(auth2)
        auth1.hashCode() == auth2.hashCode()
    }
}