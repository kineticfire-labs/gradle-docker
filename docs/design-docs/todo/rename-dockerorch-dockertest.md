# Rename dockerOrch DSL to dockerTest

## Document Metadata

- **Status**: Draft
- **Type**: Design Decision & Implementation Plan
- **Priority**: Medium
- **Dependencies**: None (no external users yet)

## Executive Summary

This document proposes renaming the `dockerOrch` DSL to `dockerTest` to better communicate its purpose to users. The
current name emphasizes implementation details (orchestration) rather than user value (testing Docker images).

**Recommendation**: Rename `dockerOrch` to `dockerTest`.

## Background

### Current State

The `dockerOrch` DSL was named to reflect its implementation: it **orchestrates** Docker containers via Docker Compose
during integration testing. The name emphasizes the "how" (orchestration) rather than the "what" (testing).

Current DSL usage:
```groovy
dockerOrch {
    composeStacks {
        myAppTest {
            files.from('src/integrationTest/resources/compose/app.yml')
            waitForHealthy.set(['app'])
        }
    }
}
```

### The Problem

1. **Name doesn't communicate purpose**: Users must learn that "dockerOrch" means "Docker image testing via
   orchestration." The name requires explanation.

2. **Inconsistency with dockerProject**: The `dockerProject` DSL uses `test { }` for its testing configuration, not
   `orch { }`. This creates conceptual inconsistency.

3. **Documentation mismatch**: The `usage-docker-orch.md` documentation describes the DSL's purpose as "Tests Docker
   images using Docker Compose" - the word "test" appears prominently, not "orchestration."

4. **User confusion potential**: New users seeing `dockerOrch` may not immediately understand what it does or when to
   use it.

## Analysis

### What Does dockerOrch Actually Do?

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
| `dockerOrch` (current) | Technically accurate | Unclear purpose |

**Selected**: `dockerTest`

Rationale:
- Concise and clear
- Aligns with `dockerProject.test { }` naming
- "Docker" prefix disambiguates from general test configurations
- Users understand "test" immediately

## Proposed Change

### Before (Current)

```groovy
dockerOrch {
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
| `DockerOrchExtension` | Rename to `DockerTestExtension` |
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
Before: "What does dockerOrch do?" → "It orchestrates Docker containers..."
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

Users searching for "how to test Docker images with Gradle" will more easily find `dockerTest` than `dockerOrch`.

### 4. Alignment with Documentation

The documentation already describes the DSL in testing terms. The rename aligns the DSL name with how it's documented
and used.

## Implementation Plan

### Phase 1: Preparation

1. Create feature branch
2. Document all files requiring changes
3. Ensure all tests pass on current codebase

### Phase 2: Core Rename

1. Rename `DockerOrchExtension` → `DockerTestExtension`
2. Update `GradleDockerPlugin` extension registration
3. Update internal references in plugin source code
4. Update `dockerWorkflows` references to use new name

### Phase 3: Test Updates

1. Update unit tests
2. Update functional tests
3. Update integration tests (`build.gradle` files)

### Phase 4: Documentation

1. Rename `usage-docker-orch.md` → `usage-docker-test.md`
2. Update all documentation references
3. Update `CLAUDE.md` references
4. Update design documents

### Phase 5: Validation

1. Run full test suite
2. Verify documentation builds
3. Manual smoke test of plugin

## Decision

**Recommendation**: Proceed with renaming `dockerOrch` to `dockerTest`.

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
