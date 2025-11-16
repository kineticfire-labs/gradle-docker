# DockerOrch Examples README Cleanup Plan

**Created:** 2025-11-16
**Status:** TODO
**Priority:** HIGH

## Executive Summary

The `plugin-integration-test/dockerOrch/examples/README.md` and individual example READMEs contain critical inconsistencies
and missing documentation. The main README describes examples as using test framework extensions (`@ComposeUp`,
`@ExtendWith`), but some examples don't use these extensions, and individual READMEs describe an obsolete Gradle tasks
approach that contradicts the actual implementation.

## Critical Issues

### 🔴 ISSUE #1: Missing Example - `database-app`

**Finding:** The `database-app` example directory exists with a complete, high-quality integration test but is
**completely undocumented** in the main README.

**What it demonstrates:**
- Spring Boot + JPA + PostgreSQL integration testing
- Multi-service Docker Compose (app + database)
- REST API testing with RestAssured
- **Direct database validation with JDBC/Groovy SQL** (unique among examples!)
- CLASS lifecycle with Spock
- Uses the new `@ComposeUp` annotation with inline configuration:
  ```groovy
  @ComposeUp(
      stackName = "databaseAppTest",
      composeFile = "src/integrationTest/resources/compose/database-app.yml",
      lifecycle = LifecycleMode.CLASS,
      waitForHealthy = ["app", "postgres"],
      timeoutSeconds = 90,
      pollSeconds = 3
  )
  ```

**Location:** `plugin-integration-test/dockerOrch/examples/database-app/`

**Impact:** Users looking for database integration testing examples won't find this valuable resource.

**Action Required:**
1. Add new section to main README documenting `database-app` example
2. Position it between `web-app-junit` and `stateful-web-app` sections
3. Highlight unique features:
   - Multi-service orchestration (app + PostgreSQL)
   - Direct database validation with Groovy SQL
   - Demonstrates dual validation (REST API + database state)
4. Include code examples showing database validation pattern
5. Update "Running Examples" section to include database-app commands

---

### 🔴 ISSUE #2: Major Documentation Inconsistency - Extension Approach vs Gradle Tasks

**Finding:** The main README and individual READMEs describe different approaches than what's actually implemented.

**Actual Implementation Status:**

| Example              | Main README Says                              | Individual README Says         | Actual Code Uses               | Status |
|----------------------|-----------------------------------------------|--------------------------------|--------------------------------|--------|
| database-app         | NOT DOCUMENTED                                | NO README                      | `@ComposeUp` annotation        | ❌ GAP |
| isolated-tests       | `@ComposeUp(lifecycle = LifecycleMode.METHOD)`| Gradle tasks + DSL             | `@ComposeUp` annotation        | ⚠️ MISMATCH |
| isolated-tests-junit | `@ExtendWith(DockerComposeMethodExtension)`   | N/A (no individual README)     | `@ExtendWith(...)` annotation  | ✅ OK |
| web-app              | `@ComposeUp(lifecycle = LifecycleMode.CLASS)` | Gradle tasks + DSL             | **NO extension, manual cleanup**| ❌ WRONG |
| web-app-junit        | `@ExtendWith(DockerComposeClassExtension)`    | N/A (no individual README)     | `@ExtendWith(...)` annotation  | ✅ OK |
| stateful-web-app     | Gradle Tasks + SUITE Lifecycle                | Gradle tasks + DSL             | **NO extension, manual cleanup**| ⚠️ CONFUSING |

**Specific Problems:**

1. **web-app (Spock):**
   - Main README claims it uses `@ComposeUp(lifecycle = LifecycleMode.CLASS)`
   - **ACTUAL:** Uses plain Spock with manual `docker compose down` in `cleanupSpec()`
   - No annotation at all!

2. **stateful-web-app:**
   - Main README says "Gradle Tasks + SUITE Lifecycle"
   - **ACTUAL:** Uses plain Spock with manual cleanup (same pattern as web-app)
   - "SUITE" lifecycle doesn't exist in codebase (it's CLASS lifecycle with persistent state)

3. **Individual README files** (web-app, isolated-tests, stateful-web-app):
   - Describe the **old Gradle tasks approach** with `composeUpWebAppTest`/`composeDownWebAppTest`
   - Show `dockerOrch { composeStacks { ... } }` DSL configuration
   - Explain manual task dependency wiring
   - **BUT:** Tests using `@ComposeUp` annotation don't need any of this!

**Impact:**
- Users copying examples will be confused by the mismatch
- Individual READMEs provide outdated/incorrect guidance
- Inconsistent presentation of the plugin's capabilities
- "SUITE lifecycle" terminology is undefined and confusing

**Action Required:**

**Option A (Recommended):** Update Tests to Use Extensions Consistently
1. Update `web-app` example to use `@ComposeUp(lifecycle = LifecycleMode.CLASS)` annotation
2. Update `stateful-web-app` to use `@ComposeUp(lifecycle = LifecycleMode.CLASS)` annotation
3. Remove manual cleanup code from both tests
4. Update individual READMEs to document annotation-based approach
5. Remove references to Gradle tasks and `dockerOrch.composeStacks` DSL

**Option B:** Update Documentation to Match Current Implementation
1. Update main README to accurately describe web-app and stateful-web-app as NOT using extensions
2. Explain why some examples use extensions and others don't
3. Add decision guide: when to use extensions vs manual approach
4. Keep individual READMEs as-is but clarify they show the "manual approach"

**Recommendation:** Choose **Option A** - the annotation-based approach is simpler, more reliable (automatic cleanup),
and appears to be the "new way" that should be promoted.

---

### 🟡 ISSUE #3: Lifecycle Terminology Confusion - "SUITE Lifecycle"

**Finding:** The main README describes `stateful-web-app` as using "SUITE Lifecycle" but:
- The actual test uses `setupSpec()`/`cleanupSpec()` (CLASS lifecycle in Spock)
- No special "SUITE" lifecycle exists in the codebase
- The term creates confusion about what differentiates it from the `web-app` example

**Current main README text:**
```markdown
### Stateful Web App (Gradle Tasks + SUITE Lifecycle)

**Directory**: `stateful-web-app/`

**Use Case**: Testing stateful workflows with Gradle tasks (suite lifecycle)

**Lifecycle**: SUITE (containers run for entire test suite using Gradle tasks)
```

**What "SUITE" appears to mean:**
- Containers persist across test method executions (same as CLASS)
- State persists between test methods (sessionId carried from test 2 to tests 3, 4, 5)
- Tests build on each other (register → login → update → logout)
- Uses Gradle tasks for orchestration (but other examples also do this)

**Impact:**
- Users expect a fourth lifecycle type (beyond CLASS and METHOD)
- No clear definition of what makes this "SUITE" vs "CLASS"
- Terminology inconsistency across documentation

**Action Required:**

**Option A:** Remove "SUITE" terminology
1. Change to "CLASS Lifecycle with Stateful Testing Patterns"
2. Emphasize that it demonstrates tests building on each other
3. Explain this is still CLASS lifecycle, but showcases state persistence use case

**Option B:** Define "SUITE" clearly
1. Add glossary section defining all lifecycle types
2. Clarify "SUITE" means "CLASS lifecycle where tests intentionally share state"
3. Explain when to use this pattern vs truly isolated CLASS tests

**Recommendation:** Choose **Option A** - avoid inventing new terminology that doesn't exist in the framework.

---

### 🟡 ISSUE #4: Individual READMEs Describe Obsolete Gradle Tasks Approach

**Finding:** Individual READMEs for `web-app`, `isolated-tests`, and `stateful-web-app` all document the Gradle tasks
approach, but tests using `@ComposeUp` annotation don't need this complexity.

**What individual READMEs currently show:**

1. **dockerOrch DSL configuration:**
   ```groovy
   dockerOrch {
       composeStacks {
           isolatedTestsTest {
               files.from('src/integrationTest/resources/compose/isolated-tests.yml')
               projectName = "example-isolated-tests-test"
               waitForHealthy {
                   waitForServices.set(['isolated-tests'])
                   timeoutSeconds.set(60)
                   pollSeconds.set(2)
               }
           }
       }
   }
   ```

2. **Gradle task orchestration:**
   - `composeUpIsolatedTestsTest` - start containers
   - `integrationTest` - run tests
   - `composeDownIsolatedTestsTest` - stop containers
   - Task dependency wiring

**What annotation-based tests actually need:**
```groovy
// In test class
@ComposeUp(
    stackName = "isolatedTestsTest",
    composeFile = "src/integrationTest/resources/compose/isolated-tests.yml",
    lifecycle = LifecycleMode.METHOD,
    waitForHealthy = ["isolated-tests"],
    timeoutSeconds = 60,
    pollSeconds = 2
)

// In build.gradle - NO dockerOrch DSL needed!
dependencies {
    integrationTestImplementation "com.kineticfire.gradle:gradle-docker:${plugin_version}"
    integrationTestImplementation libs.rest.assured
}

tasks.named('integrationTest') {
    description = 'Runs integration tests'
    // Extension handles everything - no task dependencies needed!
}
```

**Impact:**
- Users reading individual READMEs implement the more complex, task-based approach
- Confusion about when Gradle tasks vs annotations are needed
- Extra boilerplate configuration that isn't necessary
- Manual cleanup code that can fail if tests error

**Action Required:**
1. Update `web-app/README.md` to remove Gradle tasks approach
2. Update `isolated-tests/README.md` to remove Gradle tasks approach
3. Update `stateful-web-app/README.md` to remove Gradle tasks approach
4. Focus documentation on `@ComposeUp`/`@ExtendWith` annotation approach
5. Add section explaining when to use Gradle tasks (CI/CD, custom orchestration) vs annotations (simple test cases)
6. Show simplified build.gradle configuration for annotation-based approach

---

## Accurate Sections (Keep As-Is)

**What's correct in main README:**
- ✅ Test framework extension usage for `isolated-tests` (Spock + METHOD)
- ✅ Test framework extension usage for `isolated-tests-junit` (JUnit 5 + METHOD)
- ✅ Test framework extension usage for `web-app-junit` (JUnit 5 + CLASS)
- ✅ Code examples showing annotation usage patterns
- ✅ Explanation of CLASS vs METHOD lifecycle concepts
- ✅ "How to Adapt for Your Project" section structure
- ✅ Common Patterns section (port mapping examples)
- ✅ Testing Libraries section (RestAssured, Jackson, Spock, JUnit 5)

---

## Recommended Action Plan

### Phase 1: Fix Critical Issues (Priority 1)

1. **Add `database-app` documentation to main README**
   - [ ] Add new section after `web-app-junit`
   - [ ] Document multi-service orchestration pattern
   - [ ] Show database validation code examples
   - [ ] Update "Running Examples" section
   - [ ] Update table of contents

2. **Fix web-app inconsistency**
   - [ ] DECISION: Use Option A (update code to use `@ComposeUp` annotation)
   - [ ] Update `web-app/app-image/src/integrationTest/groovy/.../WebAppExampleIT.groovy`
   - [ ] Add `@ComposeUp(lifecycle = LifecycleMode.CLASS, ...)` annotation
   - [ ] Remove manual `cleanupSpec()` cleanup code
   - [ ] Update main README to accurately describe it
   - [ ] Verify tests still pass

3. **Fix stateful-web-app inconsistency**
   - [ ] DECISION: Use Option A (update code to use `@ComposeUp` annotation)
   - [ ] Update `stateful-web-app/app-image/src/integrationTest/groovy/.../StatefulWebAppExampleIT.groovy`
   - [ ] Add `@ComposeUp(lifecycle = LifecycleMode.CLASS, ...)` annotation
   - [ ] Remove manual `cleanupSpec()` cleanup code
   - [ ] Change "SUITE Lifecycle" to "CLASS Lifecycle with Stateful Testing"
   - [ ] Verify tests still pass

4. **Update all individual example READMEs**
   - [ ] Update `web-app/README.md` - remove Gradle tasks, show annotation approach
   - [ ] Update `isolated-tests/README.md` - remove Gradle tasks, show annotation approach
   - [ ] Update `stateful-web-app/README.md` - remove Gradle tasks, show annotation approach
   - [ ] Create `database-app/README.md` - document the example
   - [ ] Create `web-app-junit/README.md` - document JUnit 5 + CLASS pattern
   - [ ] Create `isolated-tests-junit/README.md` - document JUnit 5 + METHOD pattern

### Phase 2: Improve Clarity (Priority 2)

5. **Clarify lifecycle terminology**
   - [ ] Add "Supported Lifecycle Types" section to top of main README with concise definitions:
     - **METHOD**: Containers restart for each test method (complete isolation)
     - **CLASS**: Containers start once for all tests in a class (state persists between methods)
   - [ ] Remove "SUITE lifecycle" terminology from main README entirely
   - [ ] Add clear definitions of CLASS vs METHOD lifecycle throughout document
   - [ ] Explain state persistence patterns within CLASS lifecycle
   - [ ] Add decision guide: when to use CLASS vs METHOD

6. **Add decision guide section**
   - [ ] When to use test framework extensions (annotation approach)
   - [ ] When to use Gradle tasks approach
   - [ ] Trade-offs between CLASS and METHOD lifecycle
   - [ ] How to choose the right pattern for your use case

7. **Improve Running Examples section**
   - [ ] Verify all `./gradlew` commands actually work
   - [ ] Add database-app commands
   - [ ] Group by example type
   - [ ] Add expected output/success criteria

### Phase 3: Polish (Priority 3)

8. **Add consistency checks**
   - [ ] Create checklist for adding new examples
   - [ ] Ensure main README, individual READMEs, and code all align
   - [ ] Add review process for documentation updates

9. **Enhance code examples**
   - [ ] Ensure all examples have comprehensive comments
   - [ ] Add "Why this pattern?" explanations in code
   - [ ] Show both successful and error-handling scenarios

10. **Improve navigation**
    - [ ] Add breadcrumb links between main README and individual READMEs
    - [ ] Cross-reference related examples
    - [ ] Link to plugin DSL documentation

---

## Summary of Changes by File

### Main README (`plugin-integration-test/dockerOrch/examples/README.md`)

**Sections to Add:**
- Supported Lifecycle Types (at top of document) - define METHOD and CLASS lifecycles
- Database App (Spock + CLASS + PostgreSQL + Multi-service)

**Sections to Update:**
- Web App (Spock + CLASS) - verify annotation usage
- Stateful Web App - remove "SUITE lifecycle", clarify CLASS with state
- Running Examples - add database-app commands
- Example Test Structure - verify all code examples are accurate

**Sections to Remove:**
- Any references to "SUITE lifecycle" as a distinct lifecycle type

### Individual READMEs

**New files to create:**
- `database-app/README.md`
- `web-app-junit/README.md`
- `isolated-tests-junit/README.md`

**Files to update:**
- `web-app/README.md` - remove Gradle tasks approach
- `isolated-tests/README.md` - remove Gradle tasks approach
- `stateful-web-app/README.md` - remove Gradle tasks approach, clarify lifecycle

### Test Code

**Files to update (if choosing Option A):**
- `web-app/app-image/src/integrationTest/groovy/com/kineticfire/test/WebAppExampleIT.groovy`
- `stateful-web-app/app-image/src/integrationTest/groovy/com/kineticfire/test/StatefulWebAppExampleIT.groovy`

---

## Testing Requirements

After making changes, verify:

1. **All examples still pass:**
   ```bash
   cd plugin-integration-test/
   ./gradlew -Pplugin_version=1.0.0 dockerOrch:examples:integrationTest
   ```

2. **Individual examples pass:**
   ```bash
   ./gradlew :dockerOrch:examples:database-app:integrationTest
   ./gradlew :dockerOrch:examples:web-app:integrationTest
   ./gradlew :dockerOrch:examples:web-app-junit:integrationTest
   ./gradlew :dockerOrch:examples:isolated-tests:integrationTest
   ./gradlew :dockerOrch:examples:isolated-tests-junit:integrationTest
   ./gradlew :dockerOrch:examples:stateful-web-app:integrationTest
   ```

3. **No lingering containers:**
   ```bash
   docker ps -a
   # Should show no containers after tests complete
   ```

4. **Documentation examples are copy-paste ready:**
   - Test each code snippet in isolation
   - Verify gradle commands execute successfully
   - Check that file paths and references are accurate

---

## Definition of Done

- [ ] "Supported Lifecycle Types" section added to top of main README with METHOD and CLASS definitions
- [ ] All 7 examples are documented in main README
- [ ] All examples use consistent annotation-based approach (or clearly document why manual approach is used)
- [ ] Individual READMEs exist for all examples
- [ ] Individual READMEs match actual implementation
- [ ] No references to undefined "SUITE lifecycle" anywhere in documentation
- [ ] All `./gradlew` commands in documentation are verified to work
- [ ] All integration tests pass
- [ ] No lingering containers after test execution
- [ ] Code examples are copy-paste ready
- [ ] Cross-references between READMEs are accurate
- [ ] Decision guide helps users choose the right pattern
- [ ] Documentation follows line-length and formatting standards (120 chars, spaces not tabs)

---

## Notes

**Strengths of Current Documentation:**
- Clear explanation of CLASS vs METHOD lifecycle trade-offs
- Good code examples showing RestAssured usage
- Comprehensive individual example coverage
- Helpful "How to Adapt for Your Project" sections
- Well-structured common patterns section

**Key Insight:**
Tests that use extensions (`@ComposeUp`, `@ExtendWith`) have **much simpler build.gradle files** - no DSL configuration
needed. The annotation approach appears to be the "new way" and should be promoted as the primary pattern.

**Backward Compatibility:**
The Gradle tasks approach is still valid and necessary for:
- CI/CD pipelines that need explicit container control
- Custom orchestration logic
- Cases where test framework extensions can't be used

Documentation should acknowledge both approaches but recommend annotations for simple cases.
