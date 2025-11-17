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

package com.kineticfire.test

import com.kineticfire.gradle.docker.spock.ComposeUp
import com.kineticfire.gradle.docker.spock.LifecycleMode
import spock.lang.Specification
import spock.lang.Shared
import groovy.json.JsonSlurper
import io.restassured.RestAssured
import static io.restassured.RestAssured.given
import groovy.sql.Sql

/**
 * Example: Database Integration Testing with PostgreSQL
 *
 * This example demonstrates real-world database integration testing with:
 * - Spring Boot + JPA + PostgreSQL
 * - REST API testing with RestAssured
 * - Direct database validation with JDBC
 * - CLASS lifecycle for efficient testing
 *
 * RECOMMENDED PATTERN:
 * ===================
 * ✅ Configure Docker Compose in build.gradle (dockerOrch.composeStacks)
 * ✅ Use usesCompose() in integrationTest task to pass configuration
 * ✅ Use zero-parameter @ComposeUp annotation in test class
 *
 * CONFIGURATION LOCATION:
 * ======================
 * See build.gradle for:
 *   - dockerOrch.composeStacks { databaseAppTest { ... } }
 *   - integrationTest.usesCompose(stack: "databaseAppTest", lifecycle: "class")
 *
 * Tests validate both API behavior AND database state to ensure data persistence works correctly.
 *
 * Copy and adapt this example for your own database integration scenarios!
 */
@ComposeUp  // No parameters! All configuration from build.gradle via usesCompose()
class DatabaseAppExampleIT extends Specification {

    @Shared String baseUrl
    @Shared String dbUrl
    @Shared String dbUser = "testuser"
    @Shared String dbPass = "testpass"

    def setupSpec() {
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')
        def stateFile = new File(stateFilePath)
        def stateData = new JsonSlurper().parse(stateFile)

        def appPort = stateData.services['app'].publishedPorts[0].host
        baseUrl = "http://localhost:${appPort}"

        def dbPort = stateData.services['postgres'].publishedPorts[0].host
        dbUrl = "jdbc:postgresql://localhost:${dbPort}/testdb"

        RestAssured.baseURI = baseUrl
        println "=== Database App Test - App at ${baseUrl}, DB at ${dbUrl} ==="
    }

    def "test 1: health check endpoint responds"() {
        expect: "health endpoint returns OK"
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString() == "OK"
    }

    def "test 2: create user via REST API and verify in database"() {
        when: "create user via API"
        def response = given()
            .contentType("application/json")
            .body('{"username":"alice","email":"alice@example.com","fullName":"Alice Smith"}')
            .when()
            .post("/api/users")
            .then()
            .statusCode(201)
            .extract()
            .response()

        def userId = response.path("id")

        then: "user exists in database"
        Sql.withInstance(dbUrl, dbUser, dbPass, "org.postgresql.Driver") { sql ->
            def rows = sql.rows("SELECT * FROM users WHERE username = 'alice'")
            assert rows.size() == 1
            assert rows[0].email == "alice@example.com"
            assert rows[0].full_name == "Alice Smith"
        }
    }

    def "test 3: retrieve user via REST API"() {
        given: "user alice exists"
        def createResponse = given()
            .contentType("application/json")
            .body('{"username":"bob","email":"bob@example.com","fullName":"Bob Jones"}')
            .post("/api/users")
            .then()
            .statusCode(201)
            .extract()
            .path("id")

        when: "retrieve user by ID"
        def user = given()
            .when()
            .get("/api/users/${createResponse}")
            .then()
            .statusCode(200)
            .extract()
            .response()

        then: "correct user data returned"
        user.path("username") == "bob"
        user.path("email") == "bob@example.com"
        user.path("fullName") == "Bob Jones"
    }

    def "test 4: update user and verify in database"() {
        given: "user exists"
        def userId = given()
            .contentType("application/json")
            .body('{"username":"charlie","email":"charlie@old.com","fullName":"Charlie Brown"}')
            .post("/api/users")
            .then()
            .statusCode(201)
            .extract()
            .path("id")

        when: "update user email"
        given()
            .contentType("application/json")
            .body('{"email":"charlie@new.com","fullName":"Charlie Brown Updated"}')
            .when()
            .put("/api/users/${userId}")
            .then()
            .statusCode(200)

        then: "database reflects update"
        Sql.withInstance(dbUrl, dbUser, dbPass, "org.postgresql.Driver") { sql ->
            def rows = sql.rows("SELECT * FROM users WHERE id = ${userId}")
            assert rows.size() == 1
            assert rows[0].email == "charlie@new.com"
            assert rows[0].full_name == "Charlie Brown Updated"
        }
    }

    def "test 5: delete user and verify removed from database"() {
        given: "user exists"
        def userId = given()
            .contentType("application/json")
            .body('{"username":"dave","email":"dave@example.com","fullName":"Dave Wilson"}')
            .post("/api/users")
            .then()
            .statusCode(201)
            .extract()
            .path("id")

        when: "delete user"
        given()
            .when()
            .delete("/api/users/${userId}")
            .then()
            .statusCode(204)

        then: "user removed from database"
        Sql.withInstance(dbUrl, dbUser, dbPass, "org.postgresql.Driver") { sql ->
            def rows = sql.rows("SELECT * FROM users WHERE id = ${userId}")
            assert rows.size() == 0
        }
    }
}
