# Plan: Cleanup "Suite" to "Class" Lifecycle Terminology

**Status:** ✅ COMPLETED
**Created:** 2025-12-12
**Completed:** 2025-12-12
**Priority:** HIGH (Documentation Cleanup)
**Estimated Effort:** 2-3 hours
**Actual Effort:** ~1 hour

---

## Context

The project incorrectly uses "suite lifecycle" terminology in documentation and test names. This is **incorrect
terminology** - there are only TWO lifecycles:

1. **CLASS lifecycle** - Containers start once for all tests in a class
2. **METHOD lifecycle** - Containers restart for each test method

**NO "suite lifecycle" exists.** What was called "suite lifecycle" is actually **CLASS lifecycle managed via Gradle
tasks** (`composeUp`/`composeDown`) instead of test framework extensions.

The plugin source code has already been corrected - `TestIntegrationExtension.groovy` only accepts `'class'` or
`'method'` as valid lifecycle parameters. However, documentation and test names still contain stale "suite" references.

**Reference:** See `docs/design-docs/done/remove-suite-lifecycle-terminology.md` for the original cleanup plan that
addressed the source code.

---

## Files Requiring Edits

### HIGH PRIORITY: Active User-Facing Documentation (5 files)

#### 1. `docs/usage/usage-docker-orch.md` (6 references)

| Line | Current Text | Required Change |
|------|--------------|-----------------|
| 94 | `compose logs by task/suite` | Change to `compose logs by task/class` |
| 129 | `suite-level orchestration` | Change to `class-level orchestration` |
| 132 | `Suite lifecycle (containers run for entire test suite)` | Change to `Class lifecycle (containers run for entire test class)` |
| 231 | `**suite** lifecycle: MANUAL wiring` | Change to `**class** lifecycle: MANUAL wiring` |
| 1282 | `## Gradle Tasks (Optional - Suite Lifecycle)` | Change to `## Gradle Tasks (Optional - Class Lifecycle)` |
| 1284 | `suite-level orchestration` | Change to `class-level orchestration` |

#### 2. `docs/usage/usage-docker-workflows.md` (6 references)

| Line | Current Text | Required Change |
|------|--------------|-----------------|
| 21 | `Suite Lifecycle (Default)` (TOC) | Change to `Class Lifecycle (Default)` |
| 67 | `Suite (default)` | Change to `Class (default)` |
| 233 | `### Suite Lifecycle (Default)` | Change to `### Class Lifecycle (Default)` |
| 271 | `suite lifecycle` | Change to `class lifecycle` |
| 478 | `Suite/Class` | Change to `Class` |
| 489 | `Example 1: Basic Pipeline (Suite Lifecycle)` | Change to `Example 1: Basic Pipeline (Class Lifecycle)` |

#### 3. `docs/usage/spock-junit-test-extensions.md` (1 reference)

| Line | Current Text | Required Change |
|------|--------------|-----------------|
| 218 | `Suite lifecycle (containers run for entire test suite)` | Change to `Class lifecycle (containers run for entire test class)` |

#### 4. `docs/usage/README.md` (1 reference)

| Line | Current Text | Required Change |
|------|--------------|-----------------|
| 44 | `class, method, suite` | Change to `class, method` |

#### 5. `docs/usage/usage-docker.md` (1 reference)

| Line | Current Text | Required Change |
|------|--------------|-----------------|
| 47 | `compose logs by task/suite` | Change to `compose logs by task/class` |

---

### MEDIUM PRIORITY: Test Files (2 files)

#### 6. `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/TestExtensionFunctionalTest.groovy`

Test names and variable names use "suite" terminology. These should be renamed for consistency:

| Line | Current | Required Change |
|------|---------|-----------------|
| 190 | `def "suite lifecycle configures without error"()` | Rename to `"class lifecycle configures without error"` |
| 200 | `suiteStack {` | Rename to `classStack {` |
| 201 | `files.from('suite-compose.yml')` | Rename to `files.from('class-compose.yml')` |
| 206 | `tasks.register('suiteTest', Test)` | Rename to `tasks.register('classTest', Test)` |
| 207 | `usesCompose stack: 'suiteStack'` | Update to `stack: 'classStack'` |
| 212 | `tasks.getByName('suiteTest')` | Update to `tasks.getByName('classTest')` |
| 220 | `'suite-compose.yml'` | Rename to `'class-compose.yml'` |
| 320 | `def "test task depends on composeUp with suite lifecycle"()` | Rename to `"...with class lifecycle"` |
| 363 | `def "test task finalizedBy composeDown with suite lifecycle"()` | Rename to `"...with class lifecycle"` |

#### 7. `plugin/src/test/groovy/com/kineticfire/gradle/docker/extension/TestIntegrationExtensionTest.groovy`

| Line | Current | Required Change |
|------|---------|-----------------|
| 126 | `def "usesCompose configures suite lifecycle correctly"()` | Rename to `"usesCompose configures class lifecycle correctly"` |

---

## Files NOT Requiring Changes (Acceptable Usage)

The following files contain "suite" but in acceptable contexts:

1. **`ComposeAnnotationHintListenerTest.groovy`** - References `afterSuite` which is Gradle's `TestListener` API method
   name (correct usage)

2. **`TestResultCaptureTest.groovy`** - References `<testsuite>` which is JUnit XML format tag (correct usage)

3. **`TestResultTest.groovy`** - Uses "test suite" as generic term for test collection (acceptable)

4. **`plugin-integration-test/dockerOrch/examples/README.md`** - Uses "test suites" as generic term (acceptable)

5. **`plugin-integration-test/dockerOrch/examples/web-app/.../WebAppExampleIT.groovy`** - Uses "test suites" as generic
   term (acceptable)

6. **Historical docs in `docs/design-docs/done/`** - These are completed design documents; no changes needed

---

## Implementation Steps

### Phase 1: Update User-Facing Documentation

1. Edit `docs/usage/usage-docker-orch.md` - 6 changes
2. Edit `docs/usage/usage-docker-workflows.md` - 6 changes
3. Edit `docs/usage/spock-junit-test-extensions.md` - 1 change
4. Edit `docs/usage/README.md` - 1 change
5. Edit `docs/usage/usage-docker.md` - 1 change

### Phase 2: Update Test Files

6. Edit `plugin/src/functionalTest/.../TestExtensionFunctionalTest.groovy` - rename tests/variables
7. Edit `plugin/src/test/.../TestIntegrationExtensionTest.groovy` - rename 1 test

### Phase 3: Verification

8. Run unit tests: `cd plugin && ./gradlew clean test`
9. Run functional tests: `cd plugin && ./gradlew clean functionalTest`
10. Verify no "suite lifecycle" references remain in active docs:
    ```bash
    rg "suite lifecycle|Suite Lifecycle|suite-level" docs/usage/
    ```

---

## Success Criteria

- [ ] All 15 documentation references updated (5 files)
- [ ] All test names/variables renamed (2 files)
- [ ] Unit tests pass
- [ ] Functional tests pass
- [ ] Search for "suite lifecycle" in `docs/usage/` returns no results
- [ ] Only acceptable "suite" references remain (API names, XML tags, generic terms)

---

## Summary

| Priority | File | Changes |
|----------|------|---------|
| HIGH | `docs/usage/usage-docker-orch.md` | 6 |
| HIGH | `docs/usage/usage-docker-workflows.md` | 6 |
| HIGH | `docs/usage/spock-junit-test-extensions.md` | 1 |
| HIGH | `docs/usage/README.md` | 1 |
| HIGH | `docs/usage/usage-docker.md` | 1 |
| MEDIUM | `TestExtensionFunctionalTest.groovy` | 9 |
| MEDIUM | `TestIntegrationExtensionTest.groovy` | 1 |
| **TOTAL** | **7 files** | **25 changes** |
