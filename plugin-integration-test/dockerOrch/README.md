# Integration Test Coverage - 'dockerOrch' Task

This document tracks integration test scenarios for the `dockerOrch` task.

## Test Philosophy

These integration tests:
1. **Mirror real-world usage**: Build app → Build image → Orchestrate → Test → Teardown
2. **Double as examples**: Users can copy scenarios as starting points
3. **Use real Docker**: No mocks, actual containers
4. **Validate Phase 1 work**: End-to-end testing of wait, logs, state files
5. **Are Gradle 9/10 compatible**: Build cache, configuration cache, version catalogs

## Scenario Coverage Matrix

| Scenario | User Story | Image Source | Wait Config | Test Lang | Lifecycle | Logs | Status |
|----------|-----------|--------------|-------------|-----------|-----------|------|--------|
| 1 | Web app build + test | Built (docker DSL) | HEALTHY | Groovy | Suite | ❌ | TODO |
| 2 | App + database | Built + postgres | RUNNING + HEALTHY | Java | Suite | ❌ | TODO |
| 3 | Microservices | Multiple built | Mixed | Groovy | Suite | ❌ | TODO |
| 4 | Existing images | sourceRef only | RUNNING | Groovy | Suite | ❌ | TODO |
| 5 | Class lifecycle | Built | HEALTHY | Java | Class | ❌ | TODO |
| 6 | Method lifecycle | Built | HEALTHY | Java | Method | ❌ | TODO |
| 7 | Logs capture | Built | HEALTHY | Groovy | Suite | ✅ | TODO |

## Plugin Feature Coverage

| Feature | Tested By Scenarios | Notes |
|---------|---------------------|-------|
| `composeUp` task | All | Start containers |
| `composeDown` task | All | Stop and remove containers |
| `waitForHealthy` | 1, 2, 3, 5, 6, 7 | Wait for HEALTHY status |
| `waitForRunning` | 2, 4 | Wait for RUNNING status (no health check) |
| Mixed wait states | 2, 3 | Some HEALTHY, some RUNNING |
| `logs` capture | 7 | Automatic log capture on teardown |
| State file generation | All | JSON state in build/compose-state/ |
| State file consumption | All | Tests read state file for ports/IDs |
| JUnit class extension | 5 | `@ExtendWith(DockerComposeClassExtension)` |
| JUnit method extension | 6 | `@ExtendWith(DockerComposeMethodExtension)` |
| Integration with `docker` DSL | 1, 2, 3, 5, 6, 7 | Build image then orchestrate |
| Using existing images | 2 (postgres), 4 (all) | Reference public images |

## Gradle 9/10 Compatibility

All scenarios demonstrate proper Gradle 9/10 usage:
- ✅ Version catalogs (`gradle/libs.versions.toml`)
- ✅ Build cache enabled and working
- ✅ Configuration cache compatible
- ✅ Provider API usage (lazy configuration)
- ✅ Task configuration avoidance
- ✅ No deprecated APIs

## Running Scenarios

From top-level plugin-integration-test directory:

```bash
# Run all dockerOrch scenarios
./gradlew -Pplugin_version=1.0.0-SNAPSHOT dockerOrchIntegrationTest

# Run single scenario
./gradlew -Pplugin_version=1.0.0-SNAPSHOT dockerOrch:scenario-1:integrationTest

# Clean and run single scenario
./gradlew dockerOrch:scenario-1:clean dockerOrch:scenario-1:integrationTest

# Verify no leaks
docker ps -a | grep scenario
```

## Validation Infrastructure

Common validators in `buildSrc/`:
- `DockerComposeValidator`: Container state validation
- `StateFileValidator`: Parse and validate state JSON
- `HttpValidator`: HTTP endpoint testing
- `CleanupValidator`: Verify no resource leaks

## Success Criteria

For each scenario:
- [ ] All files created as specified
- [ ] Build completes successfully with Gradle 9/10
- [ ] All integration tests pass
- [ ] Containers cleaned up automatically
- [ ] `docker ps -a` shows no containers after completion
- [ ] Can run scenario multiple times successfully
- [ ] Build cache works correctly
- [ ] Configuration cache works correctly

## De-conflict Integration Tests

Integration tests run in parallel, including those from `docker/`. Shared resources must be manually de-conflicted:
- Docker images must have unique names per integration test
- Ports must be unique for exposed services and compose stacks
- Compose project names must be unique (use `scenario-<number>-<stack-name>` pattern)
