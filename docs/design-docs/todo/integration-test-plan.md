# dockerOrch Integration Test Plan

**Status**: IN PROGRESS (as of 2025-10-20)

**Purpose**: Track implementation progress for dockerOrch integration tests and ensure documentation stays synchronized
with actual implementation.

**Primary Reference**: [`plugin-integration-test/dockerOrch/README.md`](../../../plugin-integration-test/dockerOrch/README.md)

**IMPORTANT**: As scenarios are implemented or documentation is updated, ensure the main README at
`plugin-integration-test/dockerOrch/README.md` is kept current with the actual implementation status.

---

## Overview

The dockerOrch integration tests are organized into two categories:

1. **Verification Tests** (`plugin-integration-test/dockerOrch/verification/`)
   - Validate plugin mechanics using internal validators from `buildSrc/`
   - Test infrastructure (state files, port mapping, container states, cleanup)
   - **NOT user-facing** - for plugin developers only

2. **Example Tests** (`plugin-integration-test/dockerOrch/examples/`)
   - Demonstrate real-world usage patterns
   - Use standard testing libraries (RestAssured, Jackson)
   - Test application logic (not plugin internals)
   - **User-facing** - designed to be copied and adapted

---

## Current Implementation Status

### Verification Tests (Plugin Mechanics)

| Scenario | Status | Lifecycle | Features Tested | Priority |
|----------|--------|-----------|-----------------|----------|
| `basic/` | ✅ Complete | CLASS | composeUp, composeDown, state files, port mapping, cleanup | Done |
| `lifecycle-class/` | ✅ Complete | CLASS | setupSpec/cleanupSpec timing, state persistence across methods | Done |
| `lifecycle-method/` | ✅ Complete | METHOD | setup/cleanup timing, state isolation between methods | Done |
| `wait-healthy/` | ✅ Complete | CLASS | waitForHealthy, health check timing, timeout handling | Done |
| `wait-running/` | ✅ Complete | CLASS | waitForRunning, running state detection | Done |
| `mixed-wait/` | ✅ Complete | CLASS | Both wait types together (app + database) | Done |
| `logs-capture/` | ❌ Not Implemented | CLASS | Log capture configuration, file generation | **MEDIUM** |
| `multi-service/` | ❌ Not Implemented | CLASS | Complex orchestration (3+ services) | **MEDIUM** |
| `existing-images/` | ❌ Not Implemented | CLASS | Public images (nginx, redis), sourceRef pattern | **MEDIUM** |

**Progress**: 6 of 9 scenarios complete (67%)

**Lifecycle Types:**
- **CLASS** - Containers start once per test class in setupSpec/@BeforeAll, all test methods run against same
  containers, containers stop in cleanupSpec/@AfterAll
- **METHOD** - Containers start in setup/@BeforeEach, one test method runs, containers stop in cleanup/@AfterEach

---

### Example Tests (User-Facing)

| Scenario | Status | Test Framework | Lifecycle | Use Case | Priority |
|----------|--------|----------------|-----------|----------|----------|
| `web-app/` | ✅ Complete | Spock | CLASS | REST API testing | Done |
| `web-app-junit/` | ✅ Complete | **JUnit 5** | CLASS | REST API testing with JUnit 5 extension | Done |
| `stateful-web-app/` | ✅ Complete | Spock | Gradle Tasks | Session management, workflow tests | Done |
| `isolated-tests/` | ✅ Complete | Spock | METHOD | Database isolation, independent tests | Done |
| `isolated-tests-junit/` | ✅ Complete | **JUnit 5** | METHOD | Database isolation with JUnit 5 extension | Done |
| `database-app/` | ❌ Not Implemented | TBD | CLASS | Database integration (JPA, PostgreSQL) | **HIGH** |
| `microservices/` | ❌ Not Implemented | TBD | CLASS | Service orchestration, inter-service communication | **LOW** |
| `kafka-app/` | ❌ Not Implemented | TBD | CLASS | Event-driven architecture | **LOW** |
| `batch-job/` | ❌ Not Implemented | TBD | CLASS | Scheduled processing | **LOW** |

**Progress**: 5 of 9 scenarios complete (56%)

**Test Frameworks:**
- **Spock** - Uses `@ComposeUp` annotation with `LifecycleMode.CLASS` or `LifecycleMode.METHOD`
- **JUnit 5** - Uses `@ExtendWith(DockerComposeClassExtension.class)` or `@ExtendWith(DockerComposeMethodExtension.class)`

**Lifecycle Patterns:**
- **CLASS** - Containers start once, all tests share same containers (efficient)
- **METHOD** - Containers restart for each test (complete isolation)
- **Gradle Tasks** - Manual control using `composeUp*`/`composeDown*` (suite lifecycle)

---

## Documentation Discrepancies

### 1. Main README Outdated

**Issue**: The main README at `plugin-integration-test/dockerOrch/README.md` is missing two implemented JUnit 5
examples.

**Missing from README Table (but actually implemented)**:
- `examples/web-app-junit/` - JUnit 5 version of web-app (CLASS lifecycle)
- `examples/isolated-tests-junit/` - JUnit 5 version of isolated-tests (METHOD lifecycle)

**Action Required**:
- [ ] Update main README to include `web-app-junit` in the examples table
- [ ] Update main README to include `isolated-tests-junit` in the examples table
- [ ] Mark both as "✅ Complete" with JUnit 5 test framework and appropriate lifecycle

### 2. Build Configuration is Accurate

**Status**: ✅ The `plugin-integration-test/dockerOrch/build.gradle` correctly references all 8 implemented tests:
- 3 verification tests
- 5 example tests (including the 2 JUnit 5 examples)

No build configuration updates needed.

---

## Test Architecture Validation

### ✅ Strengths

1. **Clear Separation of Concerns**
   - Verification tests validate plugin mechanics (using buildSrc validators)
   - Example tests demonstrate user patterns (using standard libraries)
   - Good documentation differentiating the two

2. **Comprehensive Coverage of Basic Features**
   - Docker Compose up/down ✅
   - Health checks and wait mechanisms ✅
   - State file generation and port mapping ✅
   - Class and method lifecycle patterns ✅
   - Cleanup validation ✅

3. **Both Spock and JUnit 5 Support**
   - Provides examples for both popular frameworks
   - Shows extension-based approach for automatic lifecycle
   - Shows Gradle task approach for manual control

4. **Real-World Examples**
   - REST API testing (web-app, web-app-junit)
   - Stateful workflows (stateful-web-app)
   - Database isolation (isolated-tests, isolated-tests-junit)

5. **Gradle 9/10 Compliance**
   - Uses Provider API for lazy evaluation
   - No `version:` field in Docker Compose files
   - Random port assignment to avoid conflicts
   - Tests marked as non-cacheable (`outputs.cacheIf { false }`)

### ⚠️ Gaps

1. **Advanced Wait Mechanisms Not Tested**
   - `wait-healthy/` - Not implemented
   - `wait-running/` - Not implemented
   - `mixed-wait/` - Not implemented

2. **Log Capture Not Demonstrated**
   - `logs-capture/` - Not implemented
   - Users may not know how to use log capture feature

3. **Multi-Service Orchestration Not Shown**
   - `multi-service/` - Not implemented
   - No examples of complex orchestration (3+ services)

4. **Public Image Usage Not Demonstrated**
   - `existing-images/` - Not implemented
   - No examples using Docker Hub images (nginx, redis, postgres)

5. **Missing Real-World Database Scenario**
   - `database-app/` - Not implemented
   - Database integration is the #1 use case for Docker Compose testing

6. **Advanced Scenarios Deferred**
   - Microservices communication
   - Event-driven architecture (Kafka)
   - Batch processing

---

## Recommended Implementation Plan

### Phase 1: Update Documentation (✅ COMPLETE - 2025-10-20)

**Goal**: Synchronize documentation with actual implementation

**Tasks**:
- [x] Update `plugin-integration-test/dockerOrch/README.md` to add `web-app-junit` to examples table
- [x] Update `plugin-integration-test/dockerOrch/README.md` to add `isolated-tests-junit` to examples table
- [x] Mark both as "✅ Complete" with JUnit 5 framework
- [x] Verify all other README statuses match actual implementation
- [x] Cross-reference with build.gradle dependencies

**Acceptance Criteria**: ✅ ALL MET
- ✅ Main README examples table includes all 5 implemented examples
- ✅ All "✅ Complete" entries in README actually exist and pass tests
- ✅ No implemented scenarios are marked as "⏳ Planned"
- ✅ Build.gradle references match all implemented scenarios (3 verification + 5 examples)

**Changes Made**:
- Added "Test Framework" column to examples table
- Added `Web App (JUnit 5)` entry for `examples/web-app-junit/`
- Added `Isolated Tests (JUnit 5)` entry for `examples/isolated-tests-junit/`
- Renamed existing entries to clarify Spock framework usage
- Added "Test Framework Variants" section explaining Spock vs JUnit 5
- Verified all entries match build.gradle dependencies

---

### Phase 2: Implement Wait Mechanism Tests (✅ COMPLETE - 2025-10-20)

**Goal**: Validate core plugin wait functionality

**Scenarios**:

1. **`verification/wait-healthy/`**
   - Test `waitForHealthy` with various timeout and poll configurations
   - Verify plugin blocks until container health check passes
   - Test timeout handling (should fail gracefully if timeout exceeded)
   - Validate state file indicates healthy status
   - **Lifecycle**: CLASS

2. **`verification/wait-running/`**
   - Test `waitForRunning` functionality
   - Verify plugin blocks until container reaches running state
   - Test with containers that have no health check
   - Validate state file indicates running status
   - **Lifecycle**: CLASS

3. **`verification/mixed-wait/`**
   - Test both wait types together (e.g., app with `waitForHealthy`, database with `waitForRunning`)
   - Verify sequential wait processing
   - Test complex orchestration timing
   - **Lifecycle**: CLASS

**Rationale**: These test core plugin features that users will rely on. Without these tests, we cannot confidently
claim the wait mechanisms work correctly.

**Acceptance Criteria**: ✅ ALL MET
- ✅ All 3 scenarios implemented and passing
- ✅ Tests use internal validators (DockerComposeValidator, StateFileValidator)
- ✅ README updated with "✅ Complete" status
- ✅ Tests verify timeout, poll interval, and wait status configurations

**Implementation Summary**:
- `wait-healthy/` - 17 files, 7 integration tests, validates waitForHealthy with configurable timeouts/polls
- `wait-running/` - 17 files, 9 integration tests, validates waitForRunning for containers without health checks
- `mixed-wait/` - 17+ files, 11 integration tests, validates both mechanisms together with app+database stack
- All tests added to `plugin-integration-test/dockerOrch/build.gradle`
- All tests marked as ✅ Complete in `plugin-integration-test/dockerOrch/README.md`

---

### Phase 3: Demonstrate Real Database Integration (HIGH - Priority 2)

**Goal**: Show the #1 use case for Docker Compose testing

**Scenario**: `examples/database-app/`

**Stack**:
- Spring Boot + JPA + REST API
- PostgreSQL database container
- Database initialization via schema.sql
- Health check on app container

**Tests** (Spock or JUnit 5):
- Create user via REST API
- Retrieve user via REST API
- Update user via REST API
- Delete user via REST API
- Verify database state via JDBC (direct database queries)
- Test concurrent requests with database transactions

**Configuration**:
- **Lifecycle**: CLASS (containers shared across tests)
- **Wait Strategy**: `waitForHealthy` on both app and database
- **Test Framework**: Provide both Spock and JUnit 5 versions

**Dependencies**:
- Spring Boot Starter Data JPA
- PostgreSQL JDBC driver
- RestAssured for API testing
- Direct JDBC for database validation

**Rationale**: Database integration is the most common real-world use case. Users need a complete, working example
showing:
- Multi-service orchestration (app + database)
- Database initialization
- JPA entity mapping
- REST API testing with database persistence
- Direct database validation

**Acceptance Criteria**:
- Example implemented with complete application code
- Docker Compose file with app + PostgreSQL
- Integration tests using RestAssured + JDBC
- README with detailed explanation
- Tests verify both API behavior and database state

---

### Phase 4: Demonstrate Public Image Usage (MEDIUM - Priority 3)

**Goal**: Show how to use existing Docker Hub images

**Scenario**: `verification/existing-images/`

**Stack**:
- Nginx (public image, no build needed)
- Redis (public image, no build needed)
- Custom app that uses both services

**Tests**:
- Verify Nginx serves static content
- Verify Redis accepts connections
- Test app interaction with both services
- Validate sourceRef pattern for public images

**Configuration**:
- **Lifecycle**: CLASS
- **Images**: Use `sourceRef` to reference public images (no Dockerfile)
- **Wait Strategy**: `waitForRunning` (most public images lack health checks)

**Rationale**: Users often need to test against standard services (databases, caches, web servers) without building
custom images. This demonstrates the `sourceRef` pattern.

**Acceptance Criteria**:
- Uses public images from Docker Hub (no custom Dockerfiles)
- Demonstrates `sourceRef` configuration
- Tests validate service functionality
- README explains public image usage pattern

---

### Phase 5: Demonstrate Log Capture (MEDIUM - Priority 4)

**Goal**: Show how to capture container logs for debugging

**Scenario**: `verification/logs-capture/`

**Tests**:
- Verify log file is created at configured location
- Verify log file contains expected content
- Test `tailLines` configuration (captures last N lines)
- Test log capture with multiple services

**Configuration**:
- **Lifecycle**: CLASS
- **Log Capture**: Configure `outputFile` and `tailLines` in dockerOrch DSL

**Rationale**: Log capture is essential for debugging test failures. Users need to know how to configure and access
logs.

**Acceptance Criteria**:
- Tests verify log files are created
- Tests validate log content (app startup messages, request logs)
- README explains log capture configuration
- Shows how to use logs for debugging

---

### Phase 6: Demonstrate Multi-Service Orchestration (MEDIUM - Priority 5)

**Goal**: Show complex orchestration with 3+ services

**Scenario**: `verification/multi-service/`

**Stack**:
- Web app (custom image)
- PostgreSQL database (public image)
- Redis cache (public image)
- Nginx reverse proxy (public image)

**Tests**:
- Verify all services start correctly
- Test service dependencies (app depends on database and cache)
- Validate inter-service communication
- Test wait strategies for multiple services

**Configuration**:
- **Lifecycle**: CLASS
- **Wait Strategy**: Mixed (healthy for app, running for others)

**Rationale**: Real applications often have complex orchestration requirements.

**Acceptance Criteria**:
- 3+ services in Docker Compose file
- Tests verify all services communicate correctly
- Demonstrates service dependencies
- Shows mixed wait strategies

---

### Phase 7: Advanced User Examples (LOW - Priority 6)

**Goal**: Demonstrate specialized use cases

**Scenarios**:

1. **`examples/microservices/`**
   - Multiple app services communicating
   - Service discovery patterns
   - Inter-service REST calls

2. **`examples/kafka-app/`**
   - Event-driven architecture
   - Kafka producer/consumer testing
   - Message verification

3. **`examples/batch-job/`**
   - Scheduled processing
   - Spring Batch integration
   - Job execution validation

**Rationale**: These are valuable but less common. Focus on fundamentals first.

**Acceptance Criteria**:
- Each scenario has complete application code
- Integration tests demonstrate the pattern
- README explains when to use this pattern

---

## Validation Checklist

Before declaring the integration test suite "complete":

### Documentation
- [ ] All README tables match actual implementation
- [ ] All "✅ Complete" scenarios are actually implemented and tested
- [ ] All implemented scenarios have corresponding README documentation
- [ ] Build.gradle references match implemented scenarios

### Test Coverage
- [ ] At least 1 example exists for each lifecycle pattern (CLASS, METHOD, SUITE)
- [ ] At least 1 example exists for each test framework (Spock, JUnit 5)
- [ ] At least 1 verification test exists for each wait mechanism (healthy, running, mixed)
- [ ] At least 1 example demonstrates database integration
- [ ] At least 1 example demonstrates public image usage

### Quality Standards
- [ ] All tests follow project standards (100 lines per function, 120 chars per line)
- [ ] All tests are Gradle 9/10 compatible (Provider API, no Project at execution)
- [ ] All Docker Compose files omit deprecated `version:` field
- [ ] All tests use random port assignment to avoid conflicts
- [ ] All tests verify cleanup (no lingering containers)

### Test Execution
- [ ] All implemented tests pass when run via `./gradlew dockerOrch:integrationTest`
- [ ] All implemented tests pass when run individually
- [ ] `docker ps -a` shows no containers after test completion
- [ ] Tests can run multiple times successfully
- [ ] Tests work with configuration cache enabled

**Current Progress**: 5/14 major items complete (36%)

---

## Success Metrics

### Overall Goals
- **100% of documented scenarios implemented** (currently 44% for examples, 33% for verification)
- **Zero documentation discrepancies** (README matches implementation)
- **All tests passing** (no failures, no skipped tests)
- **Zero resource leaks** (no lingering containers after tests)

### Phase Completion Targets
- **Phase 1** (Documentation): Complete by next commit
- **Phase 2** (Wait Mechanisms): Complete within 1 week
- **Phase 3** (Database Example): Complete within 2 weeks
- **Phase 4-7**: As time permits, prioritize based on user feedback

---

## References

- **Main README**: [`plugin-integration-test/dockerOrch/README.md`](../../../plugin-integration-test/dockerOrch/README.md)
- **Verification README**: [`plugin-integration-test/dockerOrch/verification/README.md`](../../../plugin-integration-test/dockerOrch/verification/README.md)
- **Examples README**: [`plugin-integration-test/dockerOrch/examples/README.md`](../../../plugin-integration-test/dockerOrch/examples/README.md)
- **Plugin Usage Guide**: [`docs/usage/usage-docker-orch.md`](../usage/usage-docker-orch.md)
- **Spock/JUnit Extensions Guide**: [`docs/usage/spock-junit-test-extensions.md`](../usage/spock-junit-test-extensions.md)
- **Gradle 9/10 Compatibility**: [`docs/design-docs/gradle-9-and-10-compatibility.md`](../gradle-9-and-10-compatibility.md)

---

## Notes

- **Keep README in sync**: As each scenario is implemented, immediately update `plugin-integration-test/dockerOrch/README.md`
- **Update build.gradle**: Add new scenarios to the `integrationTest` and `clean` task dependencies
- **Follow existing patterns**: Use the implemented scenarios as templates for new ones
- **Test before committing**: Run `./gradlew dockerOrch:integrationTest` to verify all tests pass
- **Verify cleanup**: Run `docker ps -a` after tests to ensure no containers remain

---

**Last Updated**: 2025-10-20
**Phase 1 Status**: ✅ COMPLETE
**Phase 2 Status**: ✅ COMPLETE
**Next Phase**: Phase 3 - Demonstrate Real Database Integration (HIGH priority)
