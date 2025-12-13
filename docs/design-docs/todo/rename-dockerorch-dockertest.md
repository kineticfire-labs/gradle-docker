# Rename dockerTest DSL to dockerTest

## Document Metadata

- **Status**: Draft
- **Type**: Design Decision & Implementation Plan
- **Priority**: Medium
- **Dependencies**: None (no external users yet)

## Executive Summary

This document proposes renaming the `dockerTest` DSL to `dockerTest` to better communicate its purpose to users. The
current name emphasizes implementation details (orchestration) rather than user value (testing Docker images).

**Recommendation**: Rename `dockerTest` to `dockerTest`.

## Background

### Current State

The `dockerTest` DSL was named to reflect its implementation: it **orchestrates** Docker containers via Docker Compose
during integration testing. The name emphasizes the "how" (orchestration) rather than the "what" (testing).

Current DSL usage:
```groovy
dockerTest {
    composeStacks {
        myAppTest {
            files.from('src/integrationTest/resources/compose/app.yml')
            waitForHealthy.set(['app'])
        }
    }
}
```

### The Problem

1. **Name doesn't communicate purpose**: Users must learn that "dockerTest" means "Docker image testing via
   orchestration." The name requires explanation.

2. **Inconsistency with dockerProject**: The `dockerProject` DSL uses `test { }` for its testing configuration, not
   `orch { }`. This creates conceptual inconsistency.

3. **Documentation mismatch**: The `usage-docker-orch.md` documentation describes the DSL's purpose as "Tests Docker
   images using Docker Compose" - the word "test" appears prominently, not "orchestration."

4. **User confusion potential**: New users seeing `dockerTest` may not immediately understand what it does or when to
   use it.

## Analysis

### What Does dockerTest Actually Do?

Examining `usage-docker-orch.md`, the DSL provides:

| Feature | Purpose |
|---------|---------|
| `composeStacks { }` | Define Docker Compose configurations for tests |
| `waitForHealthy` / `waitForRunning` | Wait for containers to be ready before tests |
| `@ComposeUp` annotation | Manage compose lifecycle in Spock tests |
| `DockerComposeClassExtension` | Manage compose lifecycle in JUnit 5 tests |
| `usesCompose()` | Wire test tasks to compose lifecycle |
| `composeUp*` / `composeDown*` tasks | Start/stop containers for test execution |

**Key observation**: Every feature exists to support **testing**. The orchestration (Docker Compose up/down) is purely
an implementation detail to enable testing.

### Orchestration vs Testing: What's the User Value?

| Aspect | Orchestration (Implementation) | Testing (User Value) |
|--------|-------------------------------|---------------------|
| What user cares about | ❌ "How do containers start?" | ✅ "Can I test my image?" |
| Documentation focus | ❌ Compose mechanics | ✅ Integration testing |
| Task naming | Reflects orchestration (`composeUp*`) | Could reflect testing |
| Mental model | "I orchestrate containers" | "I test my Docker image" |

Users don't choose this plugin because they want to orchestrate containers - they choose it because they want to
**test their Docker images**. Orchestration is the mechanism, not the goal.

### Comparison with Other Gradle Plugins

| Plugin | Testing DSL Name | Notes |
|--------|------------------|-------|
| Spring Boot | `test` block | Standard Gradle |
| Testcontainers | `@Testcontainers` | Annotation-based |
| Docker Compose Gradle Plugin | `dockerCompose` | Implementation-focused |
| **This plugin (proposed)** | `dockerTest` | User-value-focused |

### Name Alternatives Considered

| Name | Pros | Cons |
|------|------|------|
| `dockerTest` | Clear purpose, consistent with `dockerProject.test` | Might be confused with unit testing |
| `dockerImageTest` | Very explicit | Verbose |
| `dockerIntegrationTest` | Extremely explicit | Very verbose |
| `composeTest` | Reflects Compose usage | Doesn't mention Docker |
| `dockerTest` (current) | Technically accurate | Unclear purpose |

**Selected**: `dockerTest`

Rationale:
- Concise and clear
- Aligns with `dockerProject.test { }` naming
- "Docker" prefix disambiguates from general test configurations
- Users understand "test" immediately

## Proposed Change

### Before (Current)

```groovy
dockerTest {
    composeStacks {
        myAppTest {
            files.from('src/integrationTest/resources/compose/app.yml')
            waitForHealthy.set(['app'])
        }
    }
}

tasks.named('integrationTest') {
    usesCompose(stack: "myAppTest", lifecycle: "class")
}
```

### After (Proposed)

```groovy
dockerTest {
    composeStacks {
        myAppTest {
            files.from('src/integrationTest/resources/compose/app.yml')
            waitForHealthy.set(['app'])
        }
    }
}

tasks.named('integrationTest') {
    usesCompose(stack: "myAppTest", lifecycle: "class")
}
```

### Consistency with dockerProject

The rename creates conceptual alignment:

```groovy
// Standalone DSL - top-level testing configuration
dockerTest {
    composeStacks { ... }
}

// Unified DSL - embedded testing configuration
dockerProject {
    test {        // Conceptually equivalent to dockerTest
        compose.set('...')
        waitForHealthy.set([...])
    }
}
```

## Impact Assessment

### Code Changes Required

| Component | Change Required |
|-----------|-----------------|
| `DockerTestExtension` | Rename to `DockerTestExtension` |
| `GradleDockerPlugin` | Update extension registration |
| `ComposeStackSpec` | No change (internal) |
| `TestIntegrationExtension` | Update references |
| `dockerWorkflows` references | Update `stack` references |
| Unit tests | Update DSL references |
| Functional tests | Update DSL references |
| Integration tests | Update `build.gradle` files |
| Documentation | Rename `usage-docker-orch.md` to `usage-docker-test.md` |

### Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Breaking existing users | N/A | N/A | No external users yet |
| Confusion during transition | Low | Low | Single atomic change |
| Documentation inconsistency | Medium | Medium | Update all docs in same PR |
| Test failures during rename | Medium | Low | Comprehensive test coverage |

### Migration Path

Since there are no external users (per `docker-project-dsl-config-cache.md`: "the plugin is not yet published and has
no external users"), this is a **clean rename** rather than a migration:

1. No deprecation period needed
2. No backward compatibility shim needed
3. No migration documentation needed
4. Single atomic change

## Benefits

### 1. Improved User Comprehension

```
Before: "What does dockerTest do?" → "It orchestrates Docker containers..."
After:  "What does dockerTest do?" → "It tests Docker images."
```

### 2. Consistent Mental Model

```groovy
// Clear naming hierarchy
docker {           // Build/tag/save/publish images
    images { ... }
}

dockerTest {       // Test images
    composeStacks { ... }
}

dockerWorkflows {  // Orchestrate build + test + publish
    pipelines { ... }
}

dockerProject {    // Unified: image + test + onSuccess
    image { ... }
    test { ... }   // Aligns with dockerTest
    onSuccess { ... }
}
```

### 3. Better Discoverability

Users searching for "how to test Docker images with Gradle" will more easily find `dockerTest` than `dockerTest`.

### 4. Alignment with Documentation

The documentation already describes the DSL in testing terms. The rename aligns the DSL name with how it's documented
and used.

## Implementation Plan

### Scope Analysis

#### Files to Modify

**Plugin Source (6 files)**
1. `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/DockerTestExtension.groovy` → Rename to
   `DockerTestExtension.groovy`
2. `plugin/src/main/groovy/com/kineticfire/gradle/docker/GradleDockerPlugin.groovy` - Update extension registration
3. `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/TestIntegrationExtension.groovy` - Update references
4. `plugin/src/main/groovy/com/kineticfire/gradle/docker/service/DockerProjectTranslator.groovy` - Update references
5. `plugin/src/main/groovy/com/kineticfire/gradle/docker/workflow/validation/PipelineValidator.groovy` - Update
   references
6. `plugin/src/main/groovy/com/kineticfire/gradle/docker/spock/DockerComposeSpockExtension.groovy` - Update error
   messages

**Unit Tests (3+ files)**
1. `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/DockerTestExtensionTest.groovy` → Rename to
   `DockerTestExtensionTest.groovy`
2. `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/TestIntegrationExtensionTest.groovy` - Update
   references
3. `plugin/src/test/groovy/com/kineticfire/gradle/docker/GradleDockerPluginTest.groovy` - Update extension name checks

**Functional Tests (10+ files)**
- All files in `plugin/src/functionalTest/` that use `dockerTest` in build script strings

**Integration Tests (~60+ files)**
- Rename directory: `plugin-integration-test/dockerTest/` → `plugin-integration-test/dockerTest/`
- Update all `build.gradle` files using `dockerTest` DSL
- Update all `settings.gradle` includes
- Update all README files

**Documentation (15+ files)**
- `docs/usage/usage-docker-orch.md` → Rename to `docs/usage/usage-docker-test.md`
- `docs/usage/usage-docker-project.md` - Update references
- `docs/usage/usage-docker-workflows.md` - Update references
- `docs/usage/spock-junit-test-extensions.md` - Update references
- `CLAUDE.md` - Update references
- Various design docs in `docs/design-docs/`

---

### Phase 1: Core Plugin Rename (Testable)

**Goal**: Rename the extension class and update registration. Unit tests should pass after this phase.

#### Step 1.1: Rename Extension Class
- Rename `DockerTestExtension.groovy` → `DockerTestExtension.groovy`
- Update class name: `DockerTestExtension` → `DockerTestExtension`
- Update javadoc: `dockerTest { }` → `dockerTest { }`

#### Step 1.2: Update GradleDockerPlugin.groovy
- Change extension registration:
  ```groovy
  // Before:
  def dockerTestExt = project.extensions.create('dockerTest', DockerTestExtension, project.objects)
  
  // After:
  def dockerTestExt = project.extensions.create('dockerTest', DockerTestExtension, project.objects)
  ```
- Update ALL variable names from `dockerTestExt` → `dockerTestExt`
- Update ALL method parameters: `DockerTestExtension dockerTestExt` → `DockerTestExtension dockerTestExt`

#### Step 1.3: Update Internal References
- `TestIntegrationExtension.groovy`:
  - Field: `private DockerTestExtension dockerTestExtension` → `private DockerTestExtension dockerTestExtension`
  - Setter: `setDockerTestExtension` → `setDockerTestExtension`
  - Error messages: Update all error messages referencing "dockerTest"
  
- `DockerProjectTranslator.groovy`:
  - Parameters: `DockerTestExtension dockerTestExt` → `DockerTestExtension dockerTestExt`
  - Error messages: Update references
  
- `PipelineValidator.groovy`:
  - Field and constructor parameters: `DockerTestExtension` → `DockerTestExtension`
  - Error messages: Update "dockerTest" → "dockerTest"

- `DockerComposeSpockExtension.groovy`:
  - Error messages only (string updates)

**Verification**: Run `./gradlew clean test` - All unit tests should fail at this point due to test file updates needed

---

### Phase 2: Unit Test Updates (Testable)

**Goal**: Update all unit tests. Unit tests should pass after this phase.

#### Step 2.1: Rename Unit Test File
- Rename `DockerTestExtensionTest.groovy` → `DockerTestExtensionTest.groovy`
- Update class references and assertions

#### Step 2.2: Update GradleDockerPluginTest.groovy
- Update extension name assertions: `'dockerTest'` → `'dockerTest'`
- Update type assertions: `DockerTestExtension` → `DockerTestExtension`

#### Step 2.3: Update TestIntegrationExtensionTest.groovy
- Update method calls and error message assertions

#### Step 2.4: Update Other Unit Tests
- Search for `DockerOrch` in all test files and update

**Verification**: Run `./gradlew clean test` - All unit tests should pass

---

### Phase 3: Functional Test Updates (Testable)

**Goal**: Update all functional tests. Functional tests should pass after this phase.

#### Step 3.1: Update Functional Test Build Scripts
Files with `dockerTest` in inline Groovy strings:
- `PluginInteractionFunctionalTest.groovy`
- `ValidationMessagesFunctionalTest.groovy`
- `SavePublishDslFunctionalTest.groovy`
- `MultiFileConfigurationFunctionalTest.groovy`
- `ComposeStackSpecFunctionalTest.groovy`
- And others (~15 files)

Each file needs `dockerTest` → `dockerTest` in:
- DSL blocks in build script strings
- Extension lookup assertions
- Error message assertions

**Verification**: Run `./gradlew clean functionalTest` - All functional tests should pass

---

### Phase 4: Integration Test Directory Rename (Testable)

**Goal**: Rename the integration test directory structure. Integration tests should pass after this phase.

#### Step 4.1: Rename Directory
```bash
git mv plugin-integration-test/dockerTest plugin-integration-test/dockerTest
```

#### Step 4.2: Update settings.gradle
Replace all occurrences:
- `include 'dockerTest'` → `include 'dockerTest'`
- `include 'dockerTest:verification:basic'` → `include 'dockerTest:verification:basic'`
- (All 60+ include lines)

#### Step 4.3: Update Top-Level build.gradle
- Update task names: `dockerTestIntegrationTest` → `dockerTestIntegrationTest`
- Update dependency paths: `:dockerTest:integrationTest` → `:dockerTest:integrationTest`

#### Step 4.4: Update dockerTest/build.gradle (now dockerTest/build.gradle)
- Update task dependencies
- Update log messages

**Verification**: Run `./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal` first, then
`cd plugin-integration-test && ./gradlew cleanAll dockerTest:integrationTest` on a single scenario to verify paths

---

### Phase 5: Integration Test Content Updates (Testable)

**Goal**: Update all DSL references in integration test `build.gradle` files.

#### Step 5.1: Update Verification Tests (9 directories)
For each directory in `plugin-integration-test/dockerTest/verification/`:
- `basic/`, `lifecycle-class/`, `lifecycle-method/`, `wait-healthy/`, `wait-running/`, `mixed-wait/`,
  `existing-images/`, `logs-capture/`, `multi-service/`

Update each `app-image/build.gradle`:
- DSL: `dockerTest {` → `dockerTest {`
- Comments: Update references
- Project references: `:dockerTest:` → `:dockerTest:`

#### Step 5.2: Update Example Tests (6 directories)
For each directory in `plugin-integration-test/dockerTest/examples/`:
- `database-app/`, `isolated-tests/`, `web-app-junit/`, `isolated-tests-junit/`, `web-app/`, `stateful-web-app/`

Update each `build.gradle` file with same changes as above.

#### Step 5.3: Update Integration Test Groovy/Java Files
Update any comments or string references in:
- `*.groovy` files in `src/integrationTest/`
- `*.java` files in `src/integrationTest/`

**Verification**: Run full integration test suite:
```bash
cd plugin && ./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal
cd ../plugin-integration-test && ./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest
```

---

### Phase 6: dockerWorkflows and dockerProject Updates (Testable)

**Goal**: Update related DSLs that reference dockerTest.

#### Step 6.1: Update dockerWorkflows Integration Tests
Files in `plugin-integration-test/dockerWorkflows/*/build.gradle`:
- Update `dockerTest {` → `dockerTest {`
- Update `dockerTest.composeStacks` → `dockerTest.composeStacks`

#### Step 6.2: Update dockerProject Integration Tests
Files in `plugin-integration-test/dockerProject/*/build.gradle`:
- Update comments referencing "dockerTest"

**Verification**: Run all integration tests again to ensure full compatibility

---

### Phase 7: Documentation Updates

**Goal**: Update all user-facing documentation.

#### Step 7.1: Rename Main Usage File
```bash
git mv docs/usage/usage-docker-orch.md docs/usage/usage-docker-test.md
```

Update file contents:
- Title: `# 'dockerTest' DSL Usage Guide` → `# 'dockerTest' DSL Usage Guide`
- All DSL examples
- All references to the DSL name

#### Step 7.2: Update Cross-Referenced Documentation
- `docs/usage/usage-docker-project.md`
- `docs/usage/usage-docker-workflows.md`
- `docs/usage/usage-docker.md`
- `docs/usage/spock-junit-test-extensions.md`
- `docs/usage/README.md`

#### Step 7.3: Update CLAUDE.md
- Update DSL references
- Update directory references

#### Step 7.4: Update Integration Test READMEs
- `plugin-integration-test/dockerTest/README.md`
- `plugin-integration-test/dockerTest/verification/README.md`
- `plugin-integration-test/dockerTest/examples/README.md`
- Individual example READMEs

#### Step 7.5: Update IDE Templates
- `docs/ide-templates/gradle-docker-templates.xml`
- `docs/ide-templates/README.md`

#### Step 7.6: Update Design Documents
Design documents in `docs/design-docs/` - these are historical records but should be updated for consistency:
- `docs/design-docs/todo/rename-dockerorch-dockertest.md` → Mark as COMPLETE
- `docs/design-docs/todo/docker-project-dsl-config-cache.md` → Update all `dockerTest` references to `dockerTest`:
  - Line 17: `dockerTest` DSL reference
  - Line 1657: `dockerTest` configuration example
  - Lines 1701-1706: `dockerTest` block in code example
  - Line 1726: `dockerTest` reference in explanation
- Other design docs can have references updated as time permits

**Verification**: Manual review of documentation for consistency

---

### Phase 8: Final Validation and Cleanup

**Goal**: Ensure everything works end-to-end.

#### Step 8.1: Full Build Verification
```bash
# Build plugin
cd plugin && ./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal

# Run all integration tests
cd ../plugin-integration-test && ./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest
```

#### Step 8.2: Verify No Lingering References
```bash
# Search for any remaining 'dockerTest' references
rg "dockerTest" --type groovy --type java --type md
rg "DockerOrch" --type groovy --type java
```

#### Step 8.3: Clean Up Docker Resources
```bash
docker ps -a  # Verify no lingering containers
```

#### Step 8.4: Update Version and Commit
- Update this document to mark as COMPLETE
- Create commit with descriptive message

---

### Summary of Changes

| Category | Count | Files/Directories |
|----------|-------|-------------------|
| Plugin Source | 6 | Main extension, plugin, translator, validator, test integration, spock extension |
| Unit Tests | 3+ | Extension test, plugin test, integration test |
| Functional Tests | 15+ | All tests using dockerTest in build scripts |
| Integration Test Dir | 1 | dockerTest → dockerTest directory rename |
| Integration Test Files | 60+ | build.gradle, README, source files |
| Documentation | 15+ | Usage guides, CLAUDE.md, design docs |

### Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Missing references | Use `rg` to exhaustively search before each phase completion |
| Integration test path errors | Verify settings.gradle includes match directory structure |
| Functional test string mismatches | Run functional tests after each change |
| Error message regression | Update all error message assertions in tests |

## Decision

**Recommendation**: Proceed with renaming `dockerTest` to `dockerTest`.

**Rationale Summary**:
1. Name should communicate user value (testing), not implementation (orchestration)
2. Aligns with `dockerProject.test { }` naming convention
3. No external users means no migration burden
4. Documentation already describes the DSL in testing terms
5. Improves discoverability and comprehension

## Open Questions

1. Should generated task names change? (e.g., `composeUp*` → `testComposeUp*`?)
   - **Preliminary answer**: No. Task names reflect their action (compose up/down), which is accurate.

2. Should the `composeStacks` container be renamed?
   - **Preliminary answer**: No. "Compose stacks" accurately describes what's being configured.

## References

- `docs/usage/usage-docker-orch.md` - Current DSL documentation
- `docs/usage/usage-docker-project.md` - Unified DSL documentation (uses `test { }`)
- `docs/design-docs/dsl-architecture-rationale.md` - DSL architecture decisions
