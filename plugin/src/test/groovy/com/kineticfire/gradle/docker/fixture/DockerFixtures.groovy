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

package com.kineticfire.gradle.docker.fixture

import com.kineticfire.gradle.docker.model.*
import com.kineticfire.gradle.docker.exception.DockerServiceException
import com.kineticfire.gradle.docker.exception.ComposeServiceException
import java.time.Duration

/**
 * Test fixture generators for standard Docker responses, error scenarios,
 * and callback patterns used in unit testing.
 */
class DockerFixtures {
    
    /**
     * Build response fixtures
     */
    static class Build {
        
        static List<String> successfulBuildLog() {
            return [
                "Step 1/5 : FROM alpine:latest",
                " ---> a24bb4013296",
                "Step 2/5 : WORKDIR /app",
                " ---> Using cache",
                " ---> 4e7c41b9d93d",
                "Step 3/5 : COPY . /app",
                " ---> 7f5b8e4c3d2a",
                "Step 4/5 : RUN chmod +x /app/entrypoint.sh",
                " ---> Running in abc123def456",
                "Removing intermediate container abc123def456",
                " ---> 9e8d7c6b5a49",
                "Step 5/5 : ENTRYPOINT [\"/app/entrypoint.sh\"]",
                " ---> Running in def456abc123",
                "Removing intermediate container def456abc123",
                " ---> 2f1e0b9c8d7a",
                "Successfully built 2f1e0b9c8d7a",
                "Successfully tagged test-app:latest"
            ]
        }
        
        static List<String> buildWithWarnings() {
            return [
                "Step 1/3 : FROM alpine:latest",
                " ---> a24bb4013296",
                "Step 2/3 : RUN apk add --no-cache curl",
                " ---> Running in xyz789abc123",
                "fetch http://dl-cdn.alpinelinux.org/alpine/v3.18/main/x86_64/APKINDEX.tar.gz",
                "WARNING: Ignoring APKINDEX.70c88391.tar.gz: No such file or directory",
                "(1/4) Installing ca-certificates (20230506-r0)",
                "(2/4) Installing brotli-libs (1.0.9-r14)",
                "(3/4) Installing libcurl (8.1.2-r0)",
                "(4/4) Installing curl (8.1.2-r0)",
                "Executing busybox-1.36.1-r0.trigger",
                "OK: 11 MiB in 18 packages",
                "Removing intermediate container xyz789abc123",
                " ---> b3e4f5c6d7a8",
                "Step 3/3 : CMD [\"curl\", \"--version\"]",
                " ---> Running in 123abc456def",
                "Removing intermediate container 123abc456def",
                " ---> c4d5e6f7a8b9",
                "Successfully built c4d5e6f7a8b9",
                "Successfully tagged curl-test:latest"
            ]
        }
        
        static List<String> buildFailureLog() {
            return [
                "Step 1/3 : FROM nonexistent:latest",
                "pull access denied for nonexistent, repository does not exist",
                "ERROR: failed to solve: nonexistent:latest: pull access denied, repository does not exist or may require 'docker login'"
            ]
        }
        
        static BuildContext createBuildContext(String contextPath = "/tmp/docker", List<String> tags = ["latest"]) {
            def context = new File(contextPath)
            def dockerfile = new File(context, "Dockerfile")
            return new BuildContext(
                context.toPath(),
                dockerfile.toPath(),
                [:],
                tags
            )
        }
        
        static BuildContext createBuildContextWithArgs(Map<String, String> buildArgs) {
            def context = createBuildContext()
            return new BuildContext(
                context.contextPath,
                context.dockerfile,
                buildArgs,
                context.tags
            )
        }
    }
    
    /**
     * Push/Pull response fixtures
     */
    static class Registry {
        
        static List<String> successfulPushProgress() {
            return [
                "The push refers to repository [docker.io/library/test-app]",
                "preparing",
                "waiting",
                "layer already exists",
                "pushing",
                "pushed",
                "latest: digest: sha256:1234567890abcdef size: 1234"
            ]
        }
        
        static List<String> successfulPullProgress() {
            return [
                "latest: Pulling from library/alpine",
                "31e352740f53: Pull complete",
                "Digest: sha256:82d1e9d7ed48a7523bdebc18cf6872d99fdf6309bdc9d4fb5e2e858d4c7b5c44",
                "Status: Downloaded newer image for alpine:latest",
                "docker.io/library/alpine:latest"
            ]
        }
        
        static List<String> authenticationFailure() {
            return [
                "denied: requested access to the resource is denied",
                "unauthorized: authentication required"
            ]
        }
        
        static AuthConfig createAuthConfig(String username = "testuser", String password = "testpass") {
            return new AuthConfig(username, password, null, "docker.io")
        }
        
        static AuthConfig createRegistryAuthConfig(String registry = "registry.example.com") {
            return new AuthConfig("testuser", "testpass", null, registry)
        }
    }
    
    /**
     * Image inspection fixtures
     */
    static class Images {
        
        static Map createImageInspection(String imageId = "sha256:abc123def456") {
            return [
                id: imageId,
                repoTags: ["test:latest", "test:v1.0"],
                repoDigests: ["test@sha256:82d1e9d7ed48a7523bdebc18cf6872d99fdf6309bdc9d4fb5e2e858d4c7b5c44"],
                parent: "sha256:parent123456",
                comment: "Test image",
                created: "2025-01-15T10:30:00.000Z",
                architecture: "amd64",
                os: "linux",
                size: 7328737L,
                virtualSize: 7328737L
            ]
        }
        
        static String createImageTarContent() {
            return "mock-image-tar-content-with-layers-and-metadata"
        }
        
        static byte[] createImageTarBytes() {
            return createImageTarContent().bytes
        }
    }
    
    /**
     * Compose service fixtures
     */
    static class Compose {
        
        static ComposeConfig createComposeConfig(String stackName = "test-stack", String projectName = "test-project") {
            def composeFile = createTempComposeFile()
            return new ComposeConfig(
                stackName,
                projectName,
                [composeFile.toPath()],
                [],
                [:]
            )
        }
        
        static File createTempComposeFile() {
            def tempFile = File.createTempFile("docker-compose", ".yml")
            tempFile.deleteOnExit()
            tempFile.text = """
version: '3.8'
services:
  web:
    image: nginx:alpine
    ports:
      - "80:80"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost"]
      interval: 30s
      timeout: 10s
      retries: 3
  db:
    image: postgres:13
    environment:
      POSTGRES_PASSWORD: testpass
    volumes:
      - db_data:/var/lib/postgresql/data
volumes:
  db_data:
""".trim()
            return tempFile
        }
        
        static Map<String, ServiceInfo> createServiceInfoMap() {
            return [
                "web": new ServiceInfo(
                    "web",
                    "nginx:alpine",
                    ServiceStatus.HEALTHY,
                    ["80:80"],
                    ["test-project_web_1"],
                    "container123"
                ),
                "db": new ServiceInfo(
                    "db", 
                    "postgres:13",
                    ServiceStatus.RUNNING,
                    ["5432:5432"],
                    ["test-project_db_1"],
                    "container456"
                )
            ]
        }
        
        static String createComposeUpOutput() {
            return """
Creating network "test-project_default" with the default driver
Creating volume "test-project_db_data" with default driver
Creating test-project_db_1 ... done
Creating test-project_web_1 ... done
""".trim()
        }
        
        static String createComposeDownOutput() {
            return """
Stopping test-project_web_1 ... done
Stopping test-project_db_1 ... done
Removing test-project_web_1 ... done
Removing test-project_db_1 ... done
Removing network test-project_default
""".trim()
        }
        
        static String createComposePsJsonOutput() {
            return """
{"Name":"test-project_web_1","State":"running","Status":"Up 5 minutes (healthy)","Ports":"0.0.0.0:80->80/tcp","Service":"web","Image":"nginx:alpine","ID":"abc123def456"}
{"Name":"test-project_db_1","State":"running","Status":"Up 5 minutes","Ports":"5432/tcp","Service":"db","Image":"postgres:13","ID":"def456abc123"}
""".trim()
        }
        
        static String createComposeLogsOutput() {
            return """
web_1  | /docker-entrypoint.sh: /docker-entrypoint.d/ is not empty, will attempt to perform configuration
web_1  | /docker-entrypoint.sh: Looking for shell scripts in /docker-entrypoint.d/
web_1  | 2025/01/15 10:30:00 [notice] 1#1: nginx/1.21.6
db_1   | PostgreSQL Database directory appears to contain a database; Skipping initialization
db_1   | 2025-01-15 10:30:00.123 UTC [1] LOG:  database system is ready to accept connections
""".trim()
        }
        
        static WaitConfig createWaitConfig(List<String> services = ["web", "db"], ServiceStatus targetState = ServiceStatus.RUNNING) {
            return new WaitConfig(
                "test-project",
                services,
                targetState,
                Duration.ofMinutes(2),
                Duration.ofSeconds(5)
            )
        }
        
        static LogsConfig createLogsConfig(List<String> services = [], boolean follow = false, int tail = 100) {
            return new LogsConfig(
                services,
                follow,
                tail
            )
        }
    }
    
    /**
     * Error scenario fixtures
     */
    static class Errors {
        
        static DockerServiceException.ErrorType[] getAllDockerErrorTypes() {
            return DockerServiceException.ErrorType.values()
        }
        
        static ComposeServiceException.ErrorType[] getAllComposeErrorTypes() {
            return ComposeServiceException.ErrorType.values()
        }
        
        static DockerServiceException createDockerServiceException(DockerServiceException.ErrorType type, String message = "Test error") {
            return new DockerServiceException(type, message, "Test resolution")
        }
        
        static ComposeServiceException createComposeServiceException(ComposeServiceException.ErrorType type, String message = "Test error") {
            return new ComposeServiceException(type, message, "Test resolution")
        }
    }
}