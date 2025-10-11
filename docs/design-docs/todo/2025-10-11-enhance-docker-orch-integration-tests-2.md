# Integration Tests: Reorganization and Enhancement Plan

**Date Created**: 2025-10-11
**Author**: Engineering Team
**Status**: Ready to Implement
**Estimated Effort**: 32 hours (4-5 days)
**Depends On**: Scenario 1 complete with working wait-for-healthy functionality
**Related**: Supersedes initial plan (2025-10-11-enhance-docker-orch-integration-tests.md)

---

## Executive Summary

Reorganize dockerOrch integration tests into two distinct categories:
1. **Verification Tests** - Validate plugin mechanics (for developers)
2. **Example Tests** - Demonstrate real-world usage (for users)

Current scenario-1 mixes both types. This plan separates them and adds comprehensive user-facing examples.

---

## Problem Statement

### Current Issues
1. **Mixed Purpose**: Scenario-1 contains both internal validation (DockerComposeValidator) and user testing (HTTP
   requests)
2. **Unclear Audience**: Not obvious which tests are for plugin developers vs plugin users
3. **Limited Examples**: Only one scenario doesn't demonstrate breadth of use cases
4. **Documentation Gap**: No clear examples of how users should test their applications

### Goals
1. **Clear Separation**: Distinct directories for verification vs examples
2. **Complete Coverage**: Test all plugin features (verification) AND all use cases (examples)
3. **Living Documentation**: Examples serve as copy-paste templates for users
4. **Maintainability**: Easy to add new scenarios in either category

---

## Directory Structure

```
plugin-integration-test/
  buildSrc/                           # Shared validators (DockerComposeValidator, etc.)

  dockerOrch/
    verification/                     # Plugin mechanics validation (for developers)
      basic/                          # Basic up/down, state files, cleanup
      wait-healthy/                   # Wait for healthy functionality
      wait-running/                   # Wait for running functionality
      mixed-wait/                     # Both wait types together
      lifecycle-suite/                # Class-level lifecycle
      lifecycle-test/                 # Method-level lifecycle
      logs-capture/                   # Log capture functionality
      multi-service/                  # Complex orchestration
      existing-images/                # Public images (nginx, redis)

    examples/                         # User-facing demonstrations (for users)
      web-app/                        # Simple REST API testing
      database-app/                   # App with PostgreSQL
      microservices/                  # Multiple interacting services
      kafka-app/                      # Event-driven architecture
      batch-job/                      # Scheduled processing
```

---

## Verification Tests (Plugin Mechanics)

### Purpose
Validate that the plugin infrastructure works correctly. These tests use internal validators (buildSrc) to verify
plugin behavior that users typically wouldn't test.

### Characteristics
- **Uses**: buildSrc validators (DockerComposeValidator, StateFileValidator, CleanupValidator, etc.)
- **Tests**: Plugin features, not application logic
- **Audience**: Plugin developers
- **README**: Explicitly states "Do NOT copy these for your own tests"

### Verification Test Scenarios

| Scenario | Location | Purpose | Hours | Plugin Features Tested |
|----------|----------|---------|-------|------------------------|
| Basic | `verification/basic/` | Basic compose up/down, state files, cleanup | 3 | - composeUp<br>- composeDown<br>- State file generation<br>- Port mapping<br>- Container cleanup |
| Wait Healthy | `verification/wait-healthy/` | Wait for healthy functionality | 2 | - waitForHealthy<br>- Health check timing<br>- Timeout handling<br>- Poll interval |
| Wait Running | `verification/wait-running/` | Wait for running functionality | 2 | - waitForRunning<br>- Running state detection<br>- Timeout handling |
| Mixed Wait | `verification/mixed-wait/` | Both wait types together (app + database) | 3 | - waitForHealthy + waitForRunning<br>- Sequential wait processing<br>- Multiple services |
| Lifecycle Suite | `verification/lifecycle-suite/` | Class-level lifecycle (suite) | 2 | - Lifecycle.SUITE<br>- setupSpec/cleanupSpec timing<br>- Single compose up/down |
| Lifecycle Test | `verification/lifecycle-test/` | Method-level lifecycle (test) | 2 | - Lifecycle.TEST<br>- setup/cleanup timing<br>- Multiple compose up/down |
| Logs Capture | `verification/logs-capture/` | Log capture functionality | 3 | - Logs configuration<br>- Log file generation<br>- Log content validation |
| Multi Service | `verification/multi-service/` | Complex orchestration (3+ services) | 2 | - Multiple services<br>- Service dependencies<br>- Port mapping for all |
| Existing Images | `verification/existing-images/` | Public images (nginx, redis) | 2 | - sourceRef pattern<br>- No image building<br>- Public Docker Hub images |
| **Total** | | | **21 hours** | |

### Current Scenario-1 Mapping
- Scenario-1 → `verification/basic/` (keep validator tests only)
- Extract HTTP tests → Move to `examples/web-app/`

---

## Example Tests (User Demonstrations)

### Purpose
Demonstrate how real users would test their applications using the dockerOrch plugin. These serve as living
documentation and copy-paste templates.

### Characteristics
- **Uses**: Standard libraries (RestAssured, JDBC, Kafka clients, etc.)
- **Tests**: Application business logic, not plugin mechanics
- **Audience**: Plugin users
- **README**: "Copy and adapt these for your own projects!"

### Example Test Scenarios

| Example | Location | Use Case | Hours | Testing Libraries | Application Stack |
|---------|----------|----------|-------|-------------------|-------------------|
| Web App | `examples/web-app/` | REST API testing | 2 | RestAssured, HTTP client | Spring Boot + REST endpoints |
| Database App | `examples/database-app/` | Database integration testing | 3 | JDBC, JPA, Testcontainers patterns | Spring Boot + PostgreSQL |
| Microservices | `examples/microservices/` | Service orchestration | 4 | RestAssured, Service discovery | Frontend + Backend + Auth service |
| Kafka App | `examples/kafka-app/` | Event-driven architecture | 3 | Kafka client, TestProducer/Consumer | Spring Boot + Kafka |
| Batch Job | `examples/batch-job/` | Scheduled processing | 2 | Spring Batch, Scheduler | Spring Boot + Batch + PostgreSQL |
| **Total** | | | **14 hours** | | |

### Example Details

#### 1. Web App (`examples/web-app/`)
**Use Case**: Testing REST API endpoints
```groovy
dockerOrch {
    composeStacks {
        webApp {
            files.from('compose.yml')
            projectName = "example-web-app"
            waitForHealthy {
                waitForServices.set(['web-app'])
                timeoutSeconds.set(60)
            }
        }
    }
}
```

**Tests**:
- User registration endpoint
- Authentication flow
- CRUD operations
- Concurrent request handling
- Error responses (4xx, 5xx)

**Libraries**: RestAssured, HTTP client

---

#### 2. Database App (`examples/database-app/`)
**Use Case**: Testing application with database persistence
```groovy
dockerOrch {
    composeStacks {
        appStack {
            files.from('compose.yml')
            projectName = "example-database-app"
            waitForHealthy {
                waitForServices.set(['app'])
            }
            waitForRunning {
                waitForServices.set(['postgres'])
            }
        }
    }
}
```

**Tests**:
- Database connection
- CRUD operations
- Transactions (commit/rollback)
- Data persistence across requests
- Migration validation

**Libraries**: JDBC, JPA, Spring Data

**Stack**: Spring Boot + PostgreSQL

---

#### 3. Microservices (`examples/microservices/`)
**Use Case**: Testing multiple interacting services
```groovy
dockerOrch {
    composeStacks {
        microservices {
            files.from('compose.yml')
            projectName = "example-microservices"
            waitForHealthy {
                waitForServices.set(['frontend', 'backend', 'auth'])
            }
        }
    }
}
```

**Tests**:
- Service-to-service communication
- Authentication/authorization flow
- API gateway routing
- Circuit breaker behavior
- Service discovery

**Libraries**: RestAssured, HTTP client

**Stack**: Frontend (React) + Backend (Spring Boot) + Auth Service (Spring Boot)

---

#### 4. Kafka App (`examples/kafka-app/`)
**Use Case**: Testing event-driven applications
```groovy
dockerOrch {
    composeStacks {
        kafkaApp {
            files.from('compose.yml')
            projectName = "example-kafka-app"
            waitForHealthy {
                waitForServices.set(['app'])
            }
            waitForRunning {
                waitForServices.set(['kafka', 'zookeeper'])
            }
        }
    }
}
```

**Tests**:
- Produce messages to topic
- Consume messages from topic
- Message ordering
- Consumer groups
- Error handling (dead letter queue)

**Libraries**: Kafka client, TestProducer, TestConsumer

**Stack**: Spring Boot + Kafka + Zookeeper

---

#### 5. Batch Job (`examples/batch-job/`)
**Use Case**: Testing scheduled/batch processing
```groovy
dockerOrch {
    composeStacks {
        batchJob {
            files.from('compose.yml')
            projectName = "example-batch-job"
            waitForHealthy {
                waitForServices.set(['app'])
            }
            waitForRunning {
                waitForServices.set(['postgres'])
            }
        }
    }
}
```

**Tests**:
- Trigger batch job via API
- Validate job execution status
- Verify processed data in database
- Test job failure/retry logic
- Schedule-based execution

**Libraries**: Spring Batch, JDBC, RestAssured

**Stack**: Spring Boot + Spring Batch + PostgreSQL

---

## Implementation Plan

### Phase 1: Reorganize Existing Work (3 hours)
**Goal**: Restructure scenario-1 into verification/examples separation

1. ✅ Create directory structure:
   - `dockerOrch/verification/`
   - `dockerOrch/examples/`

2. ✅ Move scenario-1 → `verification/basic/`:
   - Keep validator tests (DockerComposeValidator, StateFileValidator, CleanupValidator)
   - Keep state file validation
   - Keep cleanup verification

3. ✅ Extract scenario-1 HTTP tests → `examples/web-app/`:
   - Create new example project
   - Rewrite with RestAssured (not HttpValidator)
   - Add more realistic tests (CRUD, auth, errors)
   - Add detailed README

4. ✅ Update `plugin-integration-test/README.md`:
   - Add verification tracking table
   - Add example tracking table
   - Document purpose of each category

---

### Phase 2: Complete Verification Scenarios (18 hours)
**Goal**: Implement remaining 8 verification scenarios

**Order** (by priority):
1. **Wait Running** (2 hours) - Complement to wait-healthy
2. **Existing Images** (2 hours) - Simple, no building
3. **Logs Capture** (3 hours) - Feature validation
4. **Mixed Wait** (3 hours) - App + database
5. **Lifecycle Suite** (2 hours) - Class-level
6. **Lifecycle Test** (2 hours) - Method-level
7. **Multi Service** (2 hours) - Complex orchestration

**Each scenario includes**:
- build.gradle with dockerOrch configuration
- Docker Compose file
- Integration test using buildSrc validators
- Cleanup verification

---

### Phase 3: Create Example Scenarios (14 hours)
**Goal**: Demonstrate real-world usage for plugin users

**Order** (by priority):
1. **Web App** (2 hours) - Most common use case
2. **Database App** (3 hours) - Very common pattern
3. **Batch Job** (2 hours) - Scheduled processing
4. **Kafka App** (3 hours) - Event-driven
5. **Microservices** (4 hours) - Most complex

**Each example includes**:
- Full application source code (realistic Spring Boot app)
- build.gradle with typical user configuration
- Docker Compose file
- Integration tests using standard libraries
- Detailed README with:
  - Use case description
  - How to run the example
  - Explanation of test approach
  - Copy-paste instructions

---

## Documentation Updates

### plugin-integration-test/README.md

Add two tracking tables:

#### Verification Tests (Plugin Mechanics)
| Scenario | Location | Status | Plugin Features Tested |
|----------|----------|--------|------------------------|
| Basic | verification/basic/ | ✅ Complete | composeUp, composeDown, state files, cleanup |
| Wait Healthy | verification/wait-healthy/ | ⏳ Pending | waitForHealthy, health check timing |
| Wait Running | verification/wait-running/ | ⏳ Pending | waitForRunning, running state detection |
| ... | ... | ... | ... |

#### Example Tests (User Demonstrations)
| Example | Location | Status | Use Case | Testing Libraries |
|---------|----------|--------|----------|-------------------|
| Web App | examples/web-app/ | ⏳ Pending | REST API testing | RestAssured, HTTP client |
| Database App | examples/database-app/ | ⏳ Pending | Database integration | JDBC, JPA |
| ... | ... | ... | ... | ... |

### verification/README.md (new)
```markdown
# Verification Tests

These tests validate the plugin's internal mechanics. They are NOT examples of how users should write tests.

## Purpose
- Verify containers reach correct states
- Validate state files are generated correctly
- Ensure cleanup removes all resources
- Test wait mechanisms block appropriately
- Confirm port mapping works correctly

## Tools
Uses internal validators from buildSrc/:
- DockerComposeValidator
- StateFileValidator
- CleanupValidator
- HttpValidator
- PortValidator

## ⚠️ Important
**Do NOT copy these tests for your own projects.** See `examples/` for user-facing demonstrations.
```

### examples/README.md (new)
```markdown
# Example Integration Tests

These demonstrate how to use the dockerOrch plugin for testing your applications.

## Examples

### Web App
REST API testing with Spring Boot
- Location: `examples/web-app/`
- Use Case: Testing HTTP endpoints
- Libraries: RestAssured, HTTP client

### Database App
Database integration testing with PostgreSQL
- Location: `examples/database-app/`
- Use Case: Testing persistence layer
- Libraries: JDBC, JPA, Spring Data

### Microservices
Multi-service orchestration
- Location: `examples/microservices/`
- Use Case: Testing service mesh
- Libraries: RestAssured, service discovery

### Kafka App
Event-driven architecture
- Location: `examples/kafka-app/`
- Use Case: Testing message streaming
- Libraries: Kafka client, TestProducer/Consumer

### Batch Job
Scheduled processing
- Location: `examples/batch-job/`
- Use Case: Testing batch operations
- Libraries: Spring Batch, JDBC

## 📋 Copy and Adapt
These examples are designed to be copied and adapted for your own projects!

Each example includes:
- Full application source code
- Typical dockerOrch configuration
- Integration tests using standard libraries
- Detailed README with explanations
```

---

## Gradle Configuration

### plugin-integration-test/settings.gradle
```groovy
rootProject.name = 'gradle-docker-integration-tests'

// Verification tests
include 'dockerOrch:verification:basic'
include 'dockerOrch:verification:wait-healthy'
include 'dockerOrch:verification:wait-running'
include 'dockerOrch:verification:mixed-wait'
include 'dockerOrch:verification:lifecycle-suite'
include 'dockerOrch:verification:lifecycle-test'
include 'dockerOrch:verification:logs-capture'
include 'dockerOrch:verification:multi-service'
include 'dockerOrch:verification:existing-images'

// Example tests
include 'dockerOrch:examples:web-app'
include 'dockerOrch:examples:database-app'
include 'dockerOrch:examples:microservices'
include 'dockerOrch:examples:kafka-app'
include 'dockerOrch:examples:batch-job'
```

### plugin-integration-test/build.gradle
```groovy
subprojects {
    apply plugin: 'groovy'
    apply plugin: 'com.kineticfire.gradle.docker'

    // Shared configuration

    if (path.startsWith(':dockerOrch:verification:')) {
        // Verification-specific config
        tasks.withType(Test) {
            systemProperty 'test.type', 'verification'
        }
    }

    if (path.startsWith(':dockerOrch:examples:')) {
        // Example-specific config
        dependencies {
            testImplementation 'io.rest-assured:rest-assured:5.3.0'
        }

        tasks.withType(Test) {
            systemProperty 'test.type', 'example'
        }
    }
}

// Aggregate tasks
task allVerificationTests {
    description = 'Run all verification tests (plugin mechanics)'
    group = 'verification'
    dependsOn subprojects.findAll {
        it.path.startsWith(':dockerOrch:verification:')
    }.tasks.integrationTest
}

task allExampleTests {
    description = 'Run all example tests (user demonstrations)'
    group = 'verification'
    dependsOn subprojects.findAll {
        it.path.startsWith(':dockerOrch:examples:')
    }.tasks.integrationTest
}

task allIntegrationTests {
    description = 'Run all integration tests (verification + examples)'
    group = 'verification'
    dependsOn allVerificationTests, allExampleTests
}
```

---

## Success Criteria

### Phase 1 (Reorganization)
- ✅ Directory structure created
- ✅ Scenario-1 split into verification/basic and examples/web-app
- ✅ README.md updated with tracking tables
- ✅ Both category READMEs created

### Phase 2 (Verification Complete)
- ✅ All 9 verification scenarios implemented
- ✅ All tests use buildSrc validators
- ✅ All tests pass
- ✅ No lingering containers after any test

### Phase 3 (Examples Complete)
- ✅ All 5 example scenarios implemented
- ✅ All tests use standard libraries (not buildSrc validators)
- ✅ All tests demonstrate realistic use cases
- ✅ Each example has detailed README
- ✅ Examples serve as copy-paste templates

### Overall
- ✅ Clear separation between verification and examples
- ✅ All plugin features covered by verification tests
- ✅ All common use cases covered by example tests
- ✅ Documentation clearly explains purpose of each category
- ✅ Easy for users to find and adapt examples

---

## Time Estimates

| Phase | Description | Hours |
|-------|-------------|-------|
| 1 | Reorganize existing work | 3 |
| 2 | Complete verification scenarios | 18 |
| 3 | Create example scenarios | 14 |
| | **Total** | **35 hours** |

**Calendar Time**: 4-5 days of focused work

---

## Dependencies

### Before Starting
- ✅ Scenario-1 complete with working wait-for-healthy
- ✅ buildSrc validators working
- ✅ Plugin builds and publishes to Maven local
- ✅ All unit tests passing

### During Implementation
- Docker daemon running
- Gradle 9.0.0 with Java 17+
- Network access for pulling images
- Sufficient disk space for test images

---

## Risks and Mitigations

### Risk 1: Example Applications Too Complex
**Mitigation**: Keep examples focused on single use case; avoid over-engineering

### Risk 2: Breaking Changes to buildSrc Validators
**Mitigation**: Verify all verification tests after any validator changes

### Risk 3: Docker Image Build Times
**Mitigation**: Use lightweight base images; implement proper cleanup; consider caching

### Risk 4: Test Flakiness (Network, Timing)
**Mitigation**: Proper wait configurations; retry logic; clear timeout settings

---

## Next Steps (Immediate)

1. **Create directory structure** (`verification/` and `examples/`)
2. **Move scenario-1** → `verification/basic/` (keep validators)
3. **Extract HTTP tests** → `examples/web-app/` (rewrite with RestAssured)
4. **Update README.md** with tracking tables
5. **Create category READMEs** (verification/README.md, examples/README.md)
6. **Verify reorganization** (run both new test suites)

Then proceed with Phase 2 (verification scenarios) and Phase 3 (example scenarios).
