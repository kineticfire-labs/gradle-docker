# Design Document: Add `waitForLog` Block to `dockerTest` DSL -- Integration Tests

## Prerequisites

Read these documents first:
- `add-wait-for-log-0000-overview.md` - Feature overview and context
- `add-wait-for-log-0200-implementation.md` - Implementation details and component structure
- `add-wait-for-log-0300-unit-tests.md` - Unit test specifications
- `add-wait-for-log-0400-functional-tests.md` - Functional test specifications

Reference project testing standards:
- `docs/project-standards/testing/integration-testing.md` (if exists)
- `plugin-integration-test/README.md` - Integration test project structure

## Purpose

Define integration test specifications for the `waitForLog` feature implementation. Integration tests verify:
- Real Docker Compose execution with actual containers
- Log pattern matching with real container output
- Timeout behavior with real timing
- Reject pattern behavior with actual log streams
- Verbose and progress logging output
- Container cleanup (no lingering containers)

This document should contain integration tests ONLY. Unit tests are in `add-wait-for-log-0300-unit-tests.md`,
functional tests are in `add-wait-for-log-0400-functional-tests.md`.

## Checklist

- [ ] Create integration test scenario for `waitForLog` with CLASS lifecycle
  - [ ] Test with real Docker containers
  - [ ] Test timeout behavior
  - [ ] Test reject pattern behavior
  - [ ] Test verbose logging
  - [ ] Test progress interval logging
  - [ ] Verify no lingering containers
- [ ] Create integration test scenario for `waitForLog` with METHOD lifecycle
  - [ ] Test with real Docker containers
  - [ ] Test timeout behavior
  - [ ] Test reject pattern behavior
  - [ ] Test verbose logging
  - [ ] Test progress interval logging
  - [ ] Verify no lingering containers
- [ ] Create integration test scenario for `waitForRunning` with METHOD lifecycle
  - [ ] Verify DSL settings are honored (not hardcoded)
- [ ] Create integration test scenario for `waitForHealthy` with METHOD lifecycle
  - [ ] Verify DSL settings are honored (timeout, poll interval)

### Final Verification
- [ ] All integration tests pass
- [ ] `docker ps -a` shows no lingering containers
- [ ] No compilation warnings

---

## Notes from Implementation

The following content was extracted from the implementation document (`add-wait-for-log-0200-implementation.md`)
and should be incorporated into the integration test specifications below.

### Memory Usage Considerations

From Section 15 (Performance Considerations):

> **Important**: The `fetchServiceLogs()` method retrieves ALL container logs (`tailLines = 0`) on each poll
> iteration. For containers with extremely high log volume (thousands of lines per second over extended
> periods), this can cause memory pressure.

**Testing implications**:
- Monitor JVM heap usage during integration tests with verbose logging services
- Consider using `--no-daemon` for CI builds with high-volume logging tests
- Restart daemon periodically (`./gradlew --stop`) between test runs

### Gradle Daemon Considerations

> If running repeated integration tests with high-volume logging services, logs accumulate in the Gradle
> daemon's heap across test runs. Consider using `--no-daemon` for CI builds or restarting the daemon
> periodically (`./gradlew --stop`) to reclaim memory between test runs.

### Container Restart Behavior

From Section 16 (Known Limitations):

> **Limitation**: If a container crashes and restarts during the wait period (e.g., due to `restart: always`
> policy in docker-compose.yml), the log output may reset but the pattern match state is NOT reset.

Consider testing this scenario to document expected behavior.

---

## Integration Test Specifications

### 1. waitForLog with CLASS Lifecycle

**Location**: `plugin-integration-test/compose/scenario-<N>/`

TODO: Define test scenario structure and specifications

### 2. waitForLog with METHOD Lifecycle

TODO: Define test scenario structure and specifications

### 3. waitForRunning with METHOD Lifecycle

TODO: Define test scenario to verify DSL settings are honored

### 4. waitForHealthy with METHOD Lifecycle

TODO: Define test scenario to verify timeout and poll interval settings

---

## Test Execution Commands

```bash
# Build plugin and publish to Maven local first
cd plugin && ./gradlew -Pplugin_version=<version> clean build publishToMavenLocal

# Run integration tests
cd plugin-integration-test && ./gradlew -Pplugin_version=<version> cleanAll integrationTest

# Verify no lingering containers
docker ps -a
```
