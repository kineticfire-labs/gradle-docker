# TODO: Cleanup and Enhance `docs/usage/usage-docker-orch.md`

**Status**: Pending
**Priority**: High
**Target**: Next release
**Effort**: 5-7 hours

## Executive Summary

The `usage-docker-orch.md` document is **85% complete** and technically accurate but has **critical gaps** that prevent
readers from understanding the relationship between `docker` and `dockerOrch` DSLs. The document also misses
documenting one significant multi-service example (database-app) and lacks an end-to-end workflow demonstration.

**Key Issues**:
1. ❌ No introduction explaining how `dockerOrch` complements `docker` DSL
2. ❌ Missing reciprocal cross-reference to `usage-docker.md`
3. ❌ Missing `database-app` multi-service example in "Complete Examples" section
4. ❌ Wait capability (RUNNING vs HEALTHY) not explained as core feature
5. ⚠️ No end-to-end build → test workflow example

## What's Working Well ✅

### Strengths (Keep These)
- **Comprehensive lifecycle pattern coverage** (CLASS and METHOD) with clear when-to-use guidance
- **Accurate code examples** that match actual integration test implementation
- **Excellent troubleshooting guide** (lines 869-1007) with practical solutions
- **Detailed best practices section** (lines 1008-1091) with actionable guidance
- **Integration test source set convention** (lines 1110-1356) includes migration guide
- **Good organization** with progressive complexity and decision guides

### Technical Accuracy
- All documented code examples are correct and match implementation
- Configuration examples are accurate
- State file format documentation is correct
- System properties documentation is accurate
- Gradle 9/10 compatibility guidance is sound

## Critical Gaps ❌ (MUST FIX)

### 1. Missing 'docker' DSL Context and Cross-Reference

**Location**: Beginning of document (before line 6)

**Problem**:
- Document jumps directly into `dockerOrch` without explaining its purpose and relationship to the `docker` DSL
- No mention that `dockerOrch` tests Docker images (from any source)
- While `docker` DSL is the **typical** way to build images for testing, it's not **required**
- No cross-reference to `docs/usage/usage-docker.md` (reciprocal link missing)
- Note: `usage-docker.md` DOES reference this document at line 17-18

**Solution**: Add introduction section after Prerequisites (line 14):

```markdown
## Overview: Testing Docker Images

The `dockerOrch` DSL tests **Docker images** by orchestrating containers with Docker Compose.

**Images can come from:**
- ✅ Built with `docker` DSL ([docker DSL Usage Guide](usage-docker.md)) - **typical workflow**
- ✅ External registries (Docker Hub, private registries, etc.)
- ✅ Built by other tools (docker build, Buildpacks, etc.)
- ✅ Built in CI/CD pipelines external to Gradle

**Note:** The `docker` and `dockerOrch` DSLs are **independent but complementary**. You can use `dockerOrch`
without the `docker` DSL, and vice versa.

## Container Lifecycles

The plugin supports **two container lifecycles**:

1. **METHOD lifecycle** - Containers restart for each test method
   - Start: `@BeforeEach` (JUnit) / `setup()` (Spock)
   - Stop: `@AfterEach` (JUnit) / `cleanup()` (Spock)
   - Complete isolation between tests
   - Slower but guarantees independence

2. **CLASS lifecycle** - Containers start once for all tests in a class
   - Start: `@BeforeAll` (JUnit) / `setupSpec()` (Spock) OR Gradle task
   - Stop: `@AfterAll` (JUnit) / `cleanupSpec()` (Spock) OR Gradle task
   - State persists between test methods
   - Faster execution

**Implementation approaches:**
- **Test framework extensions**: Support both METHOD and CLASS lifecycles
- **Gradle tasks**: Support CLASS lifecycle only (manual container management)

## Typical Workflow (Using docker DSL)

While the `docker` DSL is **not required**, this is the most common pattern:

1. **Build** your image using the `docker` DSL ([docker DSL Usage Guide](usage-docker.md))
2. **Test** your image using the `dockerOrch` DSL (this document)

**Example pipeline:**
```groovy
// Step 1: Build image with 'docker' DSL (see usage-docker.md)
docker {
    images {
        myApp {
            imageName = 'my-app'
            tags = ['1.0.0', 'latest']
            contextTask = // ... prepare Dockerfile and artifacts
        }
    }
}
// Generates task: dockerBuildMyApp

// Step 2: Test image with 'dockerOrch' DSL (this document)
dockerOrch {
    stacks {
        myAppTest {
            projectName = 'my-app-test'
            composeFiles = [file('src/integrationTest/resources/compose/app.yml')]
            wait { services = ['my-app']; waitForStatus = 'HEALTHY' }
        }
    }
}
// Generates tasks: composeUpMyAppTest, composeDownMyAppTest

// Step 3: Wire build → test
afterEvaluate {
    tasks.named('integrationTest') {
        dependsOn tasks.named('dockerBuildMyApp')  // Build before test!
    }
}
```

For complete details on building images, see [docker DSL Usage Guide](usage-docker.md).
```

**Files to modify**:
- `docs/usage/usage-docker-orch.md` - Add overview section

**Acceptance criteria**:
- [ ] Introduction clearly states dockerOrch tests images from any source
- [ ] Clarifies docker DSL is typical but NOT required
- [ ] Lists alternative image sources (registries, other build tools, CI/CD)
- [ ] Defines two lifecycles upfront (METHOD and CLASS) with concise explanations
- [ ] Cross-reference to usage-docker.md is present
- [ ] Example shows typical build → test workflow with both DSLs
- [ ] Located prominently at beginning of document (before line 50)

---

### 2. Missing database-app Example

**Location**: Lines 1094-1108 ("Complete Examples" section)

**Problem**:
- `database-app` example exists at `plugin-integration-test/dockerOrch/examples/database-app/`
- Has comprehensive README and is documented in `examples/README.md` (lines 217-249)
- Shows critical **multi-service pattern** (app + PostgreSQL database)
- Demonstrates **dual validation** (REST API + direct database access with JDBC)
- **NOT mentioned** in usage-docker-orch.md

**Current state** (lines 1094-1108):
```markdown
- **Spock Examples**:
  - CLASS lifecycle: `plugin-integration-test/dockerOrch/examples/web-app/`
  - METHOD lifecycle: `plugin-integration-test/dockerOrch/examples/isolated-tests/`
```

**Solution**: Add database-app to list:

```markdown
- **Spock Examples**:
  - CLASS lifecycle (basic): `plugin-integration-test/dockerOrch/examples/web-app/`
  - CLASS lifecycle (multi-service): `plugin-integration-test/dockerOrch/examples/database-app/`
  - CLASS lifecycle (stateful): `plugin-integration-test/dockerOrch/examples/stateful-web-app/`
  - METHOD lifecycle: `plugin-integration-test/dockerOrch/examples/isolated-tests/`
```

**Additional context to add**:

```markdown
### Multi-Service Example: Database Integration

The `database-app` example demonstrates:
- **Two-service orchestration** (Spring Boot app + PostgreSQL database)
- **Dual validation pattern** - verify API responses AND database state with JDBC
- **Health checks for multiple services** - wait for both app and database
- **Port mapping for both services** - extract ports from state file for app and database

See `plugin-integration-test/dockerOrch/examples/database-app/README.md` for complete example with:
- Direct database verification using Groovy SQL
- Multi-service health check configuration
- Database connection pattern from state file
```

**Files to modify**:
- `docs/usage/usage-docker-orch.md` - Update "Complete Examples" section (lines 1094-1108)

**Acceptance criteria**:
- [ ] database-app listed in "Complete Examples" section
- [ ] Brief description of multi-service pattern added
- [ ] Highlights dual validation approach (API + database)
- [ ] Maintains alphabetical/logical ordering with other examples

---

### 3. Missing Typical Workflow Example

**Location**: New section after "Overview" (around line 50)

**Problem**:
- Document shows `dockerOrch` DSL in isolation
- No demonstration of **typical complete pipeline** from build to test
- Readers don't see how `docker` and `dockerOrch` DSLs work together (most common pattern)
- Missing the critical step of wiring tasks with `dependsOn`
- Should clarify this is typical but not required

**Solution**: Add new section "Typical Workflow Example: Build and Test" after the overview:

```markdown
## Typical Workflow Example: Build and Test

This example shows the **most common pattern**: build image with `docker` DSL → test image with `dockerOrch` DSL.

**Note:** While this is typical, the `docker` DSL is **not required**. You can also test images from external
registries, other build tools, or CI/CD pipelines.

### Project Structure

```
my-app-project/
├── app/                        # Build JAR
│   └── build.gradle
└── app-image/                  # Build + test Docker image
    ├── build.gradle            # Contains docker + dockerOrch DSL
    ├── src/
    │   ├── main/docker/
    │   │   └── Dockerfile
    │   └── integrationTest/
    │       ├── groovy/
    │       │   └── MyAppIT.groovy
    │       └── resources/compose/
    │           └── app.yml
    └── build/
        ├── docker/             # Image outputs
        └── compose-logs/       # Test logs
```

### Step 1: Build Image (docker DSL)

```groovy
// app-image/build.gradle

plugins {
    id 'com.kineticfire.gradle.docker' version '1.0.0'
}

docker {
    images {
        myApp {
            imageName = 'my-app'
            tags = ['1.0.0', 'latest']

            // Prepare Docker build context (Dockerfile + JAR)
            contextTask = tasks.register('prepareDockerContext', Copy) {
                into layout.buildDirectory.dir('docker-context')
                from('src/main/docker')  // Dockerfile
                from(project(':app').tasks.named('jar')) { into 'app' }  // JAR from app project
            }
        }
    }
}
// Generates task: dockerBuildMyApp
```

### Step 2: Test Image (dockerOrch DSL)

```groovy
// app-image/build.gradle (continued)

dockerOrch {
    stacks {
        myAppTest {
            projectName = 'my-app-test'
            stackName = 'myAppTest'
            composeFiles = [file('src/integrationTest/resources/compose/app.yml')]

            wait {
                services = ['my-app']
                timeout = duration(60, 'SECONDS')
                waitForStatus = 'HEALTHY'
            }

            logs {
                outputFile = file("${buildDir}/compose-logs/my-app-test.log")
                tailLines = 1000
            }
        }
    }
}
// Generates tasks: composeUpMyAppTest, composeDownMyAppTest
```

### Step 3: Write Integration Test

```groovy
// app-image/src/integrationTest/groovy/MyAppIT.groovy

import com.kineticfire.gradle.docker.spock.ComposeUp
import com.kineticfire.gradle.docker.spock.LifecycleMode
import spock.lang.Specification
import groovy.json.JsonSlurper
import io.restassured.RestAssured

@ComposeUp(lifecycle = LifecycleMode.CLASS)
class MyAppIT extends Specification {

    @Shared String baseUrl

    def setupSpec() {
        def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))
        def stateData = new JsonSlurper().parse(stateFile)
        def port = stateData.services['my-app'].publishedPorts[0].host
        baseUrl = "http://localhost:${port}"
        RestAssured.baseURI = baseUrl
    }

    def "should respond to health check"() {
        when:
        def response = RestAssured.get("/health")

        then:
        response.statusCode() == 200
    }
}
```

### Step 4: Wire Tasks Together

```groovy
// app-image/build.gradle (continued)

tasks.register('integrationTest', Test) {
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath
    useJUnitPlatform()

    systemProperty 'docker.compose.stack', 'myAppTest'
    systemProperty 'docker.compose.project', 'my-app-test'
}

// Wire dependencies: JAR → Docker build → integration test
afterEvaluate {
    tasks.named('dockerBuildMyApp') {
        dependsOn project(':app').tasks.named('jar')  // Build JAR first
    }

    tasks.named('integrationTest') {
        dependsOn tasks.named('dockerBuildMyApp')  // Build image before test
    }
}
```

### Step 5: Run Complete Workflow

```bash
# Run everything: build JAR → build image → test image
./gradlew :app-image:integrationTest

# Or step-by-step:
./gradlew :app:jar                        # 1. Build JAR
./gradlew :app-image:dockerBuildMyApp     # 2. Build Docker image
./gradlew :app-image:integrationTest      # 3. Test image
```

**What happens:**
1. ✅ JAR is built from `app/` project
2. ✅ Docker image is built from Dockerfile + JAR
3. ✅ Container starts via `composeUpMyAppTest`
4. ✅ Health check waits for container to be HEALTHY
5. ✅ Integration tests run against live container
6. ✅ Container stops automatically (extension manages lifecycle)
7. ✅ Logs captured to `build/compose-logs/my-app-test.log`

For complete details on the `docker` DSL (step 1), see [docker DSL Usage Guide](usage-docker.md).
```

**Files to modify**:
- `docs/usage/usage-docker-orch.md` - Add new section after overview

**Acceptance criteria**:
- [ ] Clearly titled as "Typical Workflow" not required workflow
- [ ] States docker DSL is NOT required upfront
- [ ] Shows complete pipeline from JAR → image → test
- [ ] Demonstrates wiring tasks with `dependsOn`
- [ ] Includes both `docker` and `dockerOrch` DSL configuration
- [ ] Shows test framework extension usage
- [ ] Links to usage-docker.md for build details
- [ ] Located early in document (before detailed examples)

---

### 4. Missing Prominent Explanation of Wait Capability (RUNNING vs HEALTHY)

**Location**: New section after "Container Lifecycles" (around line 85)

**Problem**:
- Wait capability is a **CORE FEATURE** (CLAUDE.md line 9: "Performing health checks and waiting for containers
  to reach `RUNNING` or `HEALTHY` states") but not explained prominently
- Examples show `waitForStatus` but don't explain RUNNING vs HEALTHY
- No guidance on when to use each option
- Missing connection: HEALTHY requires health check in compose file
- Value proposition (prevents flaky tests) not stated clearly
- Feature is buried in examples rather than highlighted as main capability

**Current state**:
- Line 202: Shows `waitForStatus = 'HEALTHY'` but no explanation
- Line 683: Has inline comment `// Options: 'RUNNING', 'HEALTHY'` but no detail
- Line 912: Troubleshooting mentions both but doesn't explain difference
- No dedicated section explaining this critical capability

**Solution**: Add dedicated section "Container Readiness: Waiting for RUNNING or HEALTHY":

```markdown
## Container Readiness: Waiting for RUNNING or HEALTHY

**Key Capability**: The plugin automatically waits for containers to be ready before running tests, preventing
flaky tests caused by containers that aren't fully started.

### Wait Statuses

The `waitForStatus` configuration supports two modes:

#### RUNNING Status
- **What it means**: Container process has started
- **When to use**: Fast startup, no complex initialization, or no health check defined
- **Requirement**: None (Docker always reports running status)
- **Example use cases**: Simple nginx, static file servers, basic services

```groovy
wait {
    services = ['my-app']
    timeout = duration(30, 'SECONDS')
    waitForStatus = 'RUNNING'  // Container started (fast)
}
```

#### HEALTHY Status (RECOMMENDED)
- **What it means**: Container is running AND health check passed
- **When to use**: Service needs initialization (database, app startup, etc.)
- **Requirement**: **Health check must be defined in compose file**
- **Example use cases**: Web apps, databases, services with startup time

```groovy
wait {
    services = ['my-app', 'postgres']
    timeout = duration(60, 'SECONDS')
    waitForStatus = 'HEALTHY'  // Container running + health check passed (reliable)
}
```

**Corresponding compose file:**
```yaml
services:
  my-app:
    image: my-app:latest
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 5s
      timeout: 3s
      retries: 10
```

### Decision Guide: RUNNING vs HEALTHY

| Factor | Use RUNNING | Use HEALTHY |
|--------|-------------|-------------|
| **Service has health check** | Optional | ✅ Required |
| **Service needs initialization** | ❌ Not reliable | ✅ Recommended |
| **Speed matters** | ✅ Faster (no health check wait) | ⚠️ Slower (waits for health) |
| **Test reliability** | ⚠️ May fail if service not ready | ✅ Tests run when ready |
| **Examples** | Static files, proxies | Databases, web apps, APIs |

### Best Practices

1. **Default to HEALTHY** - More reliable, prevents flaky tests
2. **Define good health checks** - Test actual service readiness, not just process running
3. **Set appropriate timeouts** - Allow enough time for service initialization
4. **Wait for ALL services** - Include all dependencies in `services` list

### Why This Matters

**Without waiting:**
```groovy
// ❌ BAD - Tests may run before service is ready
dockerOrch {
    stacks {
        test {
            composeFiles = [file('compose.yml')]
            // No wait configuration - tests start immediately!
        }
    }
}
```

**Result**: Flaky tests that sometimes pass, sometimes fail depending on timing.

**With waiting:**
```groovy
// ✅ GOOD - Tests wait for service to be fully ready
dockerOrch {
    stacks {
        test {
            composeFiles = [file('compose.yml')]
            wait {
                services = ['app', 'database']
                waitForStatus = 'HEALTHY'  // Wait for health checks
                timeout = duration(90, 'SECONDS')
            }
        }
    }
}
```

**Result**: Reliable tests that only run when containers are ready.
```

**Files to modify**:
- `docs/usage/usage-docker-orch.md` - Add new section after "Container Lifecycles"

**Acceptance criteria**:
- [ ] Section explains both RUNNING and HEALTHY statuses clearly
- [ ] Clear decision guide: when to use RUNNING vs HEALTHY
- [ ] Explains HEALTHY requires health check in compose file
- [ ] Explains value proposition (prevents flaky tests)
- [ ] Includes comparison table
- [ ] Shows examples of both statuses
- [ ] Shows compose file health check example
- [ ] Includes "Why This Matters" with before/after comparison
- [ ] Located prominently after "Container Lifecycles" section
- [ ] Cross-references to troubleshooting section for health check issues

**Additional updates**:
- Update "Overview" section to mention wait capability as key feature
- Update "TL;DR" section to highlight wait capability
- Enhance existing troubleshooting entry (line 897-913) with more detail

---

## Moderate Improvements ⚠️ (SHOULD FIX)

### 5. Make Recommended Approach More Prominent

**Location**: Beginning of document (around line 20)

**Problem**:
- Test framework extensions are the recommended approach
- But this isn't clear until line 54 ("Test Framework Extensions (RECOMMENDED)")
- Users might miss this and use Gradle tasks unnecessarily

**Solution**: Add prominent callout at top of document:

```markdown
## ⭐ Recommended Approach

**Use test framework extensions (Spock `@ComposeUp` or JUnit 5 `@ExtendWith`) for automatic container lifecycle
management.** This is the primary and recommended approach for 95% of integration testing scenarios.

**Why extensions?**
- ✅ Automatic container management (no manual task dependencies)
- ✅ Support CLASS and METHOD lifecycles
- ✅ Clean test code with minimal boilerplate
- ✅ Test framework controls setup/teardown timing

**Only use Gradle tasks** (`composeUp*`/`composeDown*`) for:
- CLASS lifecycle with manual container control (containers run for all tests, managed by Gradle tasks)
- Manual container control in CI/CD pipelines
- Custom orchestration scenarios outside test frameworks

See [Test Framework Extensions](#test-framework-extensions-recommended) section below for details.
```

**Files to modify**:
- `docs/usage/usage-docker-orch.md` - Add callout near beginning

**Acceptance criteria**:
- [ ] Callout box clearly states extensions are primary approach
- [ ] Lists benefits of using extensions
- [ ] Clarifies when to use Gradle tasks instead
- [ ] Located before line 50

---

### 6. Add Quick-Start / TL;DR Section

**Location**: Very top of document (after Prerequisites, before Overview)

**Problem**:
- Document is long (1356 lines)
- No quick summary for users who want to get started fast
- Takes time to understand the big picture

**Solution**: Add TL;DR section:

```markdown
## TL;DR (Quick Start)

**What is dockerOrch?**
- Tests Docker images from any source: `docker` DSL ([usage guide](usage-docker.md)), registries, or other tools
- Uses Docker Compose to start containers → run tests → stop containers
- The `docker` DSL is **typical but not required**

**Recommended pattern:**
```groovy
// 1. Use test framework extension in your test
@ComposeUp(lifecycle = LifecycleMode.CLASS)  // Spock
class MyAppIT extends Specification { ... }

// 2. Configure dockerOrch DSL in build.gradle
dockerOrch {
    stacks {
        myAppTest {
            composeFiles = [file('src/integrationTest/resources/compose/app.yml')]
            wait { services = ['my-app']; waitForStatus = 'HEALTHY' }
        }
    }
}

// 3. Wire image build → test
afterEvaluate {
    tasks.named('integrationTest') {
        dependsOn tasks.named('dockerBuildMyApp')  // From docker DSL
    }
}
```

**Next steps:**
- See [Overview](#overview-docker-image-testing-workflow) for complete workflow
- See [Test Framework Extensions](#test-framework-extensions-recommended) for Spock/JUnit usage
- See [Complete Examples](#complete-examples) for copy-paste examples
```

**Files to modify**:
- `docs/usage/usage-docker-orch.md` - Add TL;DR section at top

**Acceptance criteria**:
- [ ] 3-5 sentence summary of what dockerOrch does
- [ ] Shows minimal working example
- [ ] Links to key sections of document
- [ ] Located at very top (after prerequisites)

---

### 7. Enhance Multi-Service Examples in Main Body

**Location**: Throughout document (especially examples sections)

**Problem**:
- Most inline examples show single-service setups
- Multi-service orchestration (app + database, app + redis, etc.) is common
- Current examples don't show:
  - Multiple services in wait configuration
  - Multiple health checks
  - Service dependencies with `depends_on`
  - Extracting ports for multiple services from state file

**Solution**: Add multi-service example to main body (not just in references):

```markdown
### Multi-Service Stack Example

Testing an app that depends on a database:

```groovy
dockerOrch {
    stacks {
        appWithDbTest {
            projectName = 'app-db-test'
            stackName = 'appWithDbTest'
            composeFiles = [file('src/integrationTest/resources/compose/app-with-db.yml')]

            wait {
                services = ['app', 'postgres']  // Wait for BOTH services
                timeout = duration(90, 'SECONDS')
                waitForStatus = 'HEALTHY'
            }

            logs {
                outputFile = file("${buildDir}/compose-logs/app-db-test.log")
                services = ['app']  // Only capture app logs (not database)
                tailLines = 1000
            }
        }
    }
}
```

**Docker Compose file:**

```yaml
services:
  app:
    image: my-app:latest
    ports:
      - "8080"
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
    depends_on:
      postgres:
        condition: service_healthy  # Wait for database before starting app
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 5s
      timeout: 3s
      retries: 10

  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: testdb
      POSTGRES_USER: test
      POSTGRES_PASSWORD: test
    ports:
      - "5432"  # Random host port
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U test -d testdb"]
      interval: 2s
      timeout: 1s
      retries: 5
```

**Accessing both services in tests:**

```groovy
def setupSpec() {
    def stateData = new JsonSlurper().parse(new File(System.getProperty('COMPOSE_STATE_FILE')))

    // Get app port
    def appPort = stateData.services['app'].publishedPorts[0].host
    baseUrl = "http://localhost:${appPort}"

    // Get database port
    def dbPort = stateData.services['postgres'].publishedPorts[0].host
    dbUrl = "jdbc:postgresql://localhost:${dbPort}/testdb"
}
```

See [database-app example](../../plugin-integration-test/dockerOrch/examples/database-app/README.md) for complete
multi-service testing with direct database validation.
```

**Files to modify**:
- `docs/usage/usage-docker-orch.md` - Add multi-service example in main body

**Acceptance criteria**:
- [ ] Shows multi-service wait configuration
- [ ] Demonstrates `depends_on` with health check condition
- [ ] Shows extracting ports for multiple services
- [ ] Includes complete compose file example
- [ ] Links to database-app for full example

---

## Nice to Have 💡 (POLISH)

### 8. Add Comparison Table

**Location**: Early in document (after recommended approach callout)

**Solution**: Add decision matrix:

```markdown
## Decision Guide: Comparison Tables

### Extensions vs Gradle Tasks

| Feature | Test Framework Extensions | Gradle Tasks |
|---------|---------------------------|--------------|
| **Container lifecycle** | Automatic | Manual (`dependsOn`, `finalizedBy`) |
| **Cleanup on failure** | ✅ Automatic | ⚠️ Manual (requires `finalizedBy`) |
| **CLASS lifecycle** | ✅ Supported | ✅ Supported (manual) |
| **METHOD lifecycle** | ✅ Supported | ❌ Not supported |
| **Boilerplate** | Minimal (annotation only) | High (task wiring) |
| **Use case** | Integration tests | CI/CD, custom orchestration |
| **Recommended for** | 95% of scenarios | Advanced/manual control |

### CLASS vs METHOD Lifecycle Comparison

| Lifecycle | Containers Start | Containers Stop | Isolation | Speed | Use Case |
|-----------|------------------|-----------------|-----------|-------|----------|
| **CLASS** (extensions) | Once per class (`@BeforeAll` / `setupSpec`) | Once per class (`@AfterAll` / `cleanupSpec`) | Medium (shared within class) | ⚡⚡⚡ Fast | Read-only tests, workflows |
| **CLASS** (Gradle tasks) | Before test task (manual) | After test task (manual) | Medium (shared across test task) | ⚡⚡⚡ Fast | CI/CD, manual control |
| **METHOD** (extensions only) | Each test (`@BeforeEach` / `setup`) | Each test (`@AfterEach` / `cleanup`) | ✅ Complete | ⚡ Slow | Database tests, isolation |

### Spock vs JUnit 5

| Aspect | Spock | JUnit 5 |
|--------|-------|---------|
| **Language** | Groovy | Java |
| **Extension** | `@ComposeUp(lifecycle = ...)` | `@ExtendWith(DockerComposeClassExtension.class)` |
| **Shared state** | `@Shared` variables | `static` variables |
| **Class setup** | `setupSpec()` / `cleanupSpec()` | `@BeforeAll` / `@AfterAll` |
| **Method setup** | `setup()` / `cleanup()` | `@BeforeEach` / `@AfterEach` |
| **Style** | BDD (given/when/then) | Standard assertions |
| **Choice** | Personal preference | Personal preference |
```

**Files to modify**:
- `docs/usage/usage-docker-orch.md` - Add comparison tables

**Acceptance criteria**:
- [ ] Tables are clear and concise
- [ ] Help users make quick decisions
- [ ] Located early in document for reference

---

### 9. Add Troubleshooting for Multi-Service Scenarios

**Location**: Troubleshooting section (after line 896)

**Solution**: Add subsection:

```markdown
#### 8. Multi-Service Issues

**Symptom:** App container fails to connect to database container

**Causes & Solutions:**

1. **Wrong hostname** - Use service name as hostname:
   ```yaml
   environment:
     DB_HOST: postgres  # Service name from compose file, NOT localhost
   ```

2. **App starts before database ready** - Use `depends_on` with health check:
   ```yaml
   app:
     depends_on:
       postgres:
         condition: service_healthy  # Wait for health check
   ```

3. **Database not exposed to app** - Services communicate on internal network (no port mapping needed):
   ```yaml
   postgres:
     # No 'ports:' needed for app → database communication
     # Only needed if test code connects directly to database
   ```

4. **Test code can't connect to database** - Map database port for direct access:
   ```yaml
   postgres:
     ports:
       - "5432"  # Map port for test code JDBC access
   ```

5. **Service startup order** - Wait for ALL services:
   ```groovy
   wait {
       services = ['app', 'postgres', 'redis']  // Wait for all
       waitForStatus = 'HEALTHY'
   }
   ```
```

**Files to modify**:
- `docs/usage/usage-docker-orch.md` - Add to troubleshooting section

**Acceptance criteria**:
- [ ] Covers common multi-service pitfalls
- [ ] Provides clear solutions
- [ ] Integrated into existing troubleshooting guide

---

### 10. Enhance Gradle 9/10 Compatibility Section

**Location**: Line 46-49

**Current state**:
```markdown
## Gradle 9 and 10 Compatibility

This plugin is fully compatible with Gradle 9 and 10, including configuration cache support. Follow these patterns for
best compatibility in [Gradle 9 and 10 Compatibility](gradle-9-and-10-compatibility-practices.md).
```

**Problem**:
- Just references another document
- No inline guidance on key gotchas
- Doesn't show configuration cache compatible patterns

**Solution**: Expand section:

```markdown
## Gradle 9 and 10 Compatibility

This plugin is fully compatible with Gradle 9 and 10, including **configuration cache support**.

**Key patterns for compatibility:**

```groovy
// ✅ GOOD - Uses file() method
dockerOrch {
    stacks {
        test {
            composeFiles = [file('compose.yml')]  // Configuration cache safe
        }
    }
}

// ❌ BAD - Captures Project reference
dockerOrch {
    stacks {
        test {
            composeFiles = [project.file('compose.yml')]  // Configuration cache violation!
        }
    }
}
```

**Configuration cache is ENABLED by default** in integration test projects. Your build.gradle should work without
changes.

For complete details, see [Gradle 9 and 10 Compatibility Practices](gradle-9-and-10-compatibility-practices.md).
```

**Files to modify**:
- `docs/usage/usage-docker-orch.md` - Enhance compatibility section

**Acceptance criteria**:
- [ ] Shows configuration cache compatible patterns
- [ ] Includes good/bad examples
- [ ] States configuration cache is enabled
- [ ] Still links to full document

---

## Implementation Checklist

### Priority 1: Critical Gaps (Target: Next release)

- [ ] **Task 1.1**: Add docker DSL context introduction
  - [ ] Write overview stating dockerOrch tests images from any source
  - [ ] Clarify docker DSL is typical but NOT required
  - [ ] List alternative image sources (registries, other tools, CI/CD)
  - [ ] Add upfront lifecycle definitions (METHOD and CLASS)
  - [ ] Add cross-reference to usage-docker.md
  - [ ] Show typical build → test workflow
  - [ ] Location: After Prerequisites (line 14)
  - Estimate: 1.5 hours

- [ ] **Task 1.2**: Document database-app example
  - [ ] Add to "Complete Examples" section (lines 1094-1108)
  - [ ] Add brief multi-service example description
  - [ ] Highlight dual validation pattern
  - Estimate: 30 minutes

- [ ] **Task 1.3**: Add typical workflow example
  - [ ] Title as "Typical Workflow Example" not required workflow
  - [ ] State upfront docker DSL is NOT required
  - [ ] Create new section showing complete pipeline
  - [ ] Include docker DSL (build), dockerOrch DSL (test), wiring
  - [ ] Show integration test code
  - [ ] Location: After overview (around line 50)
  - Estimate: 2 hours

- [ ] **Task 1.4**: Add wait capability explanation (RUNNING vs HEALTHY)
  - [ ] Create dedicated section "Container Readiness: Waiting for RUNNING or HEALTHY"
  - [ ] Explain both RUNNING and HEALTHY statuses clearly
  - [ ] Include decision guide table (when to use each)
  - [ ] Explain HEALTHY requires health check in compose file
  - [ ] Show compose file health check example
  - [ ] Explain value proposition (prevents flaky tests)
  - [ ] Include "Why This Matters" before/after comparison
  - [ ] Update Overview section to mention wait capability
  - [ ] Update TL;DR section to highlight wait capability
  - [ ] Enhance troubleshooting entry (line 897-913)
  - [ ] Location: After "Container Lifecycles" section (around line 85)
  - Estimate: 1 hour

### Priority 2: Moderate Improvements (Target: Current sprint)

- [ ] **Task 2.1**: Make recommended approach more prominent
  - [ ] Add callout box at top stating extensions are primary
  - [ ] List benefits and when to use tasks instead
  - Estimate: 20 minutes

- [ ] **Task 2.2**: Add quick-start / TL;DR section
  - [ ] 3-5 sentence summary stating dockerOrch tests images from any source
  - [ ] Clarify docker DSL is typical but NOT required
  - [ ] Minimal working example
  - [ ] Links to key sections
  - Estimate: 30 minutes

- [ ] **Task 2.3**: Enhance multi-service examples
  - [ ] Add complete multi-service example in main body
  - [ ] Show multiple health checks, depends_on, port extraction
  - Estimate: 1 hour

### Priority 3: Nice to Have (Target: Next sprint)

- [ ] **Task 3.1**: Add comparison tables
  - [ ] Extensions vs tasks (showing CLASS lifecycle supported by both)
  - [ ] Lifecycle comparison (METHOD vs CLASS extensions vs CLASS Gradle tasks)
  - [ ] Framework comparison (Spock vs JUnit 5)
  - Estimate: 1 hour

- [ ] **Task 3.2**: Add multi-service troubleshooting
  - [ ] Database connection issues, service ordering, networking
  - Estimate: 30 minutes

- [ ] **Task 3.3**: Enhance Gradle 9/10 compatibility section
  - [ ] Add inline examples of good/bad patterns
  - Estimate: 20 minutes

## Testing / Verification

After making changes, verify:

- [ ] All cross-references work (no broken links)
- [ ] All code examples are syntactically correct
- [ ] Line length ≤ 120 characters (project standard)
- [ ] Examples match actual integration test patterns
- [ ] database-app example referenced actually exists
- [ ] usage-docker.md reciprocal link is present

## Success Metrics

**Completeness**: 85% → 98%
- All critical context provided
- All significant examples documented
- Complete workflow demonstrated

**Usability**: 90% → 95%
- Clear relationship between docker and dockerOrch DSLs
- Quick-start guides readers efficiently
- Decision matrices help users choose right approach

**Accuracy**: 98% → 98%
- Already accurate, just needs completeness improvements

## Notes

- This is an **internal document** for team members, not external users
- Maintain informal, concise, example-driven tone
- Focus on practical usage over theory
- Reference external docs (usage-docker.md, gradle-9-10-compatibility.md) rather than duplicating

## References

- Source document: `docs/usage/usage-docker-orch.md`
- Related document: `docs/usage/usage-docker.md`
- Examples: `plugin-integration-test/dockerOrch/examples/`
- Examples README: `plugin-integration-test/dockerOrch/examples/README.md`
