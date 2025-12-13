# Plan: Update dockerTest Integration Test README for Consistency

**Status:** ✅ Completed
**Created:** 2025-11-23
**Completed:** 2025-11-23
**Priority:** High
**Estimated Effort:** 2-3 hours
**Actual Effort:** ~2 hours

---

## Context

The `plugin-integration-test/dockerTest/README.md` is missing critical plugin features and troubleshooting guidance that are well-documented in `docs/usage/usage-docker-orch.md`. This plan addresses consistency gaps to improve the integration test developer experience.

**Related Documents:**
- `plugin-integration-test/dockerTest/README.md` - Integration test documentation
- `docs/usage/usage-docker-orch.md` - Usage guide for dockerTest DSL
- `docs/usage/spock-junit-test-extensions.md` - Test framework extensions guide

---

## Issues Addressed

1. **Missing Auto-Wiring Feature Documentation**: usage-docker-orch.md has extensive auto-wiring explanation, README has none
2. **Missing Integration Test Source Set Convention**: usage-docker-orch.md documents automatic source set creation, README has none
3. **No Troubleshooting Section**: usage-docker-orch.md has 8 common issues covered, README has no troubleshooting
4. **Wait Mechanisms Under-Explained**: README mentions wait mechanisms but doesn't explain waitForHealthy vs waitForRunning
5. **No Cross-Reference to Usage Documentation**: README doesn't point to comprehensive usage guide
6. **Incorrect Lifecycle Terminology**: References to "suite lifecycle" are incorrect - only "class" and "method" lifecycles exist

---

## Important Terminology Correction

**CRITICAL**: There are only TWO lifecycles:
- **CLASS lifecycle**: Containers start once for all tests in a class (used by both test framework extensions and Gradle tasks)
- **METHOD lifecycle**: Containers restart for each test method

**NO "suite lifecycle" exists** - this was incorrect terminology. Gradle tasks provide an alternative way to manage CLASS lifecycle containers, but it's still CLASS lifecycle, not a different lifecycle type.

---

## Implementation Plan

### Task 1: Add Cross-Reference to Usage Documentation

**File:** `plugin-integration-test/dockerTest/README.md`
**Location:** After line 13 (Purpose section)
**Action:** Add prominent forward reference to usage documentation

**Content to Add:**

```markdown
**For comprehensive usage documentation**, see:
- [dockerTest DSL Usage Guide](../../docs/usage/usage-docker-orch.md) - Complete guide with all examples and patterns
- [Spock and JUnit Test Extensions Guide](../../docs/usage/spock-junit-test-extensions.md) - Detailed extension documentation

This README focuses on the integration test structure and organization. For complete DSL usage, configuration patterns, and detailed examples, refer to the usage guides above.
```

**Rationale:** Establishes clear documentation navigation. Users know where to find comprehensive information.

---

### Task 2: Add Key Plugin Features Section

**File:** `plugin-integration-test/dockerTest/README.md`
**Location:** After "Test Organization" section (after line 91)
**Action:** Add new section highlighting major plugin features with references

**Content to Add:**

```markdown
## Key Plugin Features

### Auto-Wiring of Test Dependencies

When using `usesCompose()` with **class** or **method** lifecycles, the plugin **automatically wires** compose lifecycle task dependencies to your test task. You no longer need manual `afterEvaluate` blocks for `composeUp*` / `composeDown*` dependencies!

**What gets auto-wired:**
```gradle
tasks.named('integrationTest') {
    usesCompose(stack: "myStack", lifecycle: "class")
}

// Plugin automatically adds (you don't write this!):
// integrationTest.dependsOn 'composeUpMyStack'
// integrationTest.finalizedBy 'composeDownMyStack'
```

**Manual wiring still required:** You must still wire **image build dependencies** to compose tasks (plugin cannot auto-detect image sources).

```gradle
afterEvaluate {
    tasks.named('composeUpMyStack') {
        dependsOn tasks.named('dockerBuildMyApp')  // Required: ensure image exists
    }
}
```

**See:** [Understanding Task Dependencies](../../docs/usage/usage-docker-orch.md#understanding-task-dependencies) for complete details and patterns.

---

### Integration Test Source Set Convention

The plugin **automatically creates** the `integrationTest` source set when java or groovy plugin is present. This eliminates ~40-50 lines of boilerplate per project!

**Automatic setup includes:**
- Source directories: `src/integrationTest/java`, `src/integrationTest/groovy`, `src/integrationTest/resources`
- Configurations: `integrationTestImplementation`, `integrationTestRuntimeOnly`
- Tasks: `integrationTest`, `processIntegrationTestResources`
- Classpath: Automatic access to main source set classes

**Minimal configuration example:**
```gradle
plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker'
}

// Plugin creates integrationTest source set automatically!

dependencies {
    integrationTestImplementation 'org.spockframework:spock-core:2.3'
}

// That's it! Convention provides everything else.
```

**See:** [Integration Test Source Set Convention](../../docs/usage/usage-docker-orch.md#integration-test-source-set-convention) for customization options and migration guide.

---

### Wait Mechanisms

Choose the appropriate wait mechanism for your services:

**waitForHealthy (RECOMMENDED):**
- Container is RUNNING **AND** health check passed
- Use for: databases, web apps, APIs (anything needing initialization)
- Requires: Health check defined in compose file

**waitForRunning:**
- Container process has started (may not be ready)
- Use for: simple services without health checks
- Faster but less reliable

**Decision guide:**

| Factor | waitForRunning | waitForHealthy |
|--------|----------------|----------------|
| **Service has health check** | Optional | ✅ Required |
| **Service needs initialization** | ❌ Not reliable | ✅ Recommended |
| **Speed** | ⚡ Faster | ⏱️ Waits for health |
| **Test reliability** | ⚠️ May fail if not ready | ✅ Runs when ready |
| **Examples** | Static files, proxies | Databases, web apps, APIs |

**See:** [Container Readiness: Waiting for Services](../../docs/usage/usage-docker-orch.md#container-readiness-waiting-for-services) for complete configuration examples.

---

### Lifecycle Patterns

**IMPORTANT:** There are only TWO lifecycles:

**CLASS Lifecycle:**
- Containers start once before all tests in a class
- All test methods share the same containers
- State persists between test methods
- Faster execution (containers start only once)
- Used by: Test framework extensions AND Gradle tasks

**METHOD Lifecycle:**
- Containers restart fresh for each test method
- Complete isolation between tests
- State does NOT persist between test methods
- Slower execution (containers restart for each test)
- Used by: Test framework extensions only

**Orchestration Approaches:**

1. **Test Framework Extensions (RECOMMENDED):** Use Spock or JUnit 5 extensions for automatic lifecycle management
   - Supports: CLASS and METHOD lifecycles
   - Configuration: Via `usesCompose()` in build.gradle + zero-parameter annotation
   - Auto-wiring: Compose up/down dependencies added automatically

2. **Gradle Tasks (ALTERNATIVE):** Use `composeUp*` / `composeDown*` tasks for manual orchestration
   - Supports: CLASS lifecycle only
   - Configuration: Manual `dependsOn` / `finalizedBy` in build.gradle
   - Use cases: CI/CD pipelines, custom orchestration, manual control

**See:** [Lifecycle Patterns](../../docs/usage/usage-docker-orch.md#lifecycle-patterns) for detailed comparison and when to use each.
```

**Rationale:**
- Highlights major plugin capabilities that eliminate boilerplate
- Provides quick reference with links to detailed documentation
- Corrects lifecycle terminology (no suite lifecycle)
- Clarifies auto-wiring scope (compose tasks yes, image build no)

---

### Task 3: Add Troubleshooting Section

**File:** `plugin-integration-test/dockerTest/README.md`
**Location:** Before "Validation Infrastructure" section (before line 139)
**Action:** Add troubleshooting section with forward reference

**Content to Add:**

```markdown
## Troubleshooting

### Quick Diagnostics

**Containers not starting:**
1. Check test output for error messages during setup
2. Verify `usesCompose()` matches stack name in dockerTest DSL
3. Ensure compose file exists at expected path
4. Check Docker daemon is running: `docker info`

**Containers not stopping:**
1. Extensions automatically clean up - check for test crashes
2. Manually clean: `docker compose -p <project-name> down -v`
3. Force remove: `docker ps -aq --filter name=<project-name> | xargs -r docker rm -f`

**Health checks timing out:**
1. Increase timeout in dockerTest DSL: `timeoutSeconds.set(120)`
2. Use `waitForRunning` instead of `waitForHealthy` (faster but less reliable)
3. Check service logs: `docker compose -p <project-name> logs <service-name>`

**Port conflicts:**
1. Use dynamic port assignment in compose files: `ports: - "8080"` (not `"9091:8080"`)
2. Read actual port from state file in tests
3. Find conflicting containers: `docker ps --filter publish=8080`

**State file not found:**
1. Ensure extension annotation is present on test class
2. Verify compose up completed successfully (check test output)
3. Check dockerTest DSL configuration matches system properties

**Configuration conflicts (Spock):**
1. Use EITHER `usesCompose()` in build.gradle OR annotation parameters, not both
2. Recommended: Remove all annotation parameters, use `usesCompose()` only

---

### Comprehensive Troubleshooting Guide

For complete troubleshooting with detailed solutions, see:
- [dockerTest Troubleshooting Guide](../../docs/usage/usage-docker-orch.md#troubleshooting-guide) -
  Covers 8 common issues including containers not stopping, health check timeouts, port conflicts,
  configuration conflicts, configuration cache issues, and more

Each issue includes:
- Symptom description
- Root cause explanation
- Step-by-step solutions
- Verification commands
- Prevention strategies
```

**Rationale:**
- Provides immediate help for most common issues
- Quick diagnostics reduce friction during test development
- Forward reference to comprehensive troubleshooting for complex issues
- Balances quick reference with detailed documentation

---

### Task 4: Correct Lifecycle Terminology Throughout

**File:** `plugin-integration-test/dockerTest/README.md`
**Action:** Review entire document and correct any references to "suite lifecycle"

**Changes Required:**

1. Remove or correct any mention of "suite lifecycle"
2. Clarify that Gradle tasks use CLASS lifecycle (not a different lifecycle)
3. Update any documentation that implies three lifecycles exist

**Key clarifications:**
- CLASS lifecycle (used by both extensions and Gradle tasks)
- METHOD lifecycle (used by extensions only)
- NO suite lifecycle exists

**Locations to check:**
- Lifecycle Patterns section
- Test Organization section
- Any comparison tables
- Configuration examples

**Rationale:** Eliminates incorrect terminology that could confuse developers.

---

## Testing and Validation

After implementation:

1. **Terminology Verification:**
   - [ ] No references to "suite lifecycle" remain
   - [ ] All lifecycle references use only "class" or "method"
   - [ ] Gradle tasks clearly described as CLASS lifecycle management

2. **Cross-Reference Validation:**
   - [ ] All links to usage-docker-orch.md are correct
   - [ ] All links to spock-junit-test-extensions.md are correct
   - [ ] All section anchors exist in target documents

3. **Content Accuracy:**
   - [ ] Auto-wiring description matches actual plugin behavior
   - [ ] Integration test convention description is accurate
   - [ ] Wait mechanisms correctly described
   - [ ] Troubleshooting solutions are correct

4. **Readability:**
   - [ ] New sections fit naturally with existing content
   - [ ] Forward references are clear and helpful
   - [ ] Quick reference format is scannable
   - [ ] Code examples are syntactically correct

---

## Success Criteria

- [ ] README has cross-reference to usage documentation in introduction
- [ ] Key Plugin Features section added with auto-wiring, source set convention, wait mechanisms
- [ ] Troubleshooting section added with quick diagnostics and forward reference
- [ ] All "suite lifecycle" terminology removed/corrected
- [ ] Only "class" and "method" lifecycles referenced
- [ ] All forward references link to correct documentation sections
- [ ] All code examples are valid and follow current patterns
- [ ] Line length limit (120 chars) enforced throughout
- [ ] Markdown formatting is consistent

---

## Non-Goals (Explicitly Out of Scope)

- Modifying usage-docker-orch.md (separate task if terminology correction needed there)
- Modifying spock-junit-test-extensions.md
- Changing integration test structure or organization
- Adding new integration test scenarios
- Rewriting existing README sections (only adding new content and correcting terminology)

---

## Estimated Time Breakdown

| Task | Estimated Time |
|------|----------------|
| Task 1: Cross-reference to usage docs | 10 minutes |
| Task 2: Key Plugin Features section | 45 minutes |
| Task 3: Troubleshooting section | 30 minutes |
| Task 4: Correct lifecycle terminology | 30 minutes |
| Validation and testing | 15 minutes |

**Total Estimated Time:** 2 hours 10 minutes

---

## Dependencies and Prerequisites

- No code changes required
- No build system changes required
- Only documentation file edits
- Must verify all cross-reference links are correct

---

## Implementation Notes

- Add new content without removing existing sections (unless correcting terminology)
- Maintain consistent formatting with existing README style
- Use code blocks for all configuration examples
- Keep cross-references concise but clear
- Focus on "just enough" information with links to comprehensive docs
- Emphasize that only two lifecycles exist: class and method

---

## Related Work

- This updates integration test README only
- May require follow-up task to correct "suite lifecycle" in usage-docker-orch.md if present
- Complements recently completed usage documentation consistency work

---

## Review and Approval

**Plan Status:** Ready for Implementation
**Approved By:** [To be filled]
**Approval Date:** [To be filled]

**Implementation Notes:**
- Terminology correction (removing suite lifecycle) is critical
- All three tasks (cross-ref, features, troubleshooting) should be completed together
- Validate all links before marking complete
