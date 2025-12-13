# TODO: Cleanup and Enhance `docs/usage/usage-docker-orch.md`

**Status**: ✅ COMPLETE (Updated 2025-11-21)
**Priority**: Medium (reduced from High - most critical items done)
**Target**: Next release
**Effort**: 3-3.5 hours (updated to include conflict detection and projectName documentation)

---

## Important Context: Annotation Fix Plan Changes

**This plan has been updated** to reflect changes from the completed annotation fix plan
(`docker-orch-dsl-annoations-fix.md`). Key implementation changes:

1. **Zero-parameter `@ComposeUp` annotation** - All parameters now optional; config comes from build.gradle
2. **`usesCompose()` method** - Bridges build.gradle config to test framework extensions
3. **`composeStacks {}` DSL** - Replaces old `stacks {}` syntax
4. **`waitForHealthy {}` / `waitForRunning {}` blocks** - Replaces old `wait {}` syntax
5. **"Choosing a Test Framework"** section - Already added to documentation

**All code examples in this plan use the CURRENT implementation patterns.**

---

## Executive Summary

The `usage-docker-orch.md` document is **90% complete** after the annotation fix plan updates. Remaining gaps:

**Completed (via annotation fix plan)** ✅:
1. ✅ "Choosing a Test Framework" comparison table added
2. ✅ `usesCompose()` pattern documented
3. ✅ Zero-parameter `@ComposeUp` as recommended pattern
4. ✅ Both Spock and JUnit 5 frameworks documented

**Remaining Gaps** ⚠️:
1. ⚠️ Missing `database-app` multi-service example in "Complete Examples" section
2. ⚠️ Wait capability (RUNNING vs HEALTHY) not explained prominently as core feature
3. ⚠️ No TL;DR / Quick Start section
4. ⚠️ Could enhance multi-service examples in main body
5. ⚠️ Configuration conflict detection not documented (helps users avoid duplication errors)
6. ⚠️ Optional `projectName` parameter not demonstrated (useful for multi-stack projects)

---

## What's Working Well ✅

### Strengths (Keep These)
- **Comprehensive lifecycle pattern coverage** (CLASS and METHOD) with clear when-to-use guidance
- **Accurate code examples** using `usesCompose()` and zero-parameter annotations
- **"Choosing a Test Framework" section** with comparison table (lines 99-131)
- **Excellent troubleshooting guide** with practical solutions
- **Detailed best practices section** with actionable guidance
- **Integration test source set convention** includes migration guide
- **Good organization** with progressive complexity and decision guides

### Technical Accuracy
- All documented code examples match current implementation
- `composeStacks {}` DSL syntax is correct
- `usesCompose(stack: "name", lifecycle: "class")` pattern documented
- Zero-parameter `@ComposeUp` as recommended approach
- State file format documentation is correct
- System properties documentation is accurate

---

## Remaining Gaps ⚠️ (SHOULD FIX)

### 1. Missing database-app Example

**Location**: "Complete Examples" section

**Problem**:
- `database-app` example exists at `plugin-integration-test/dockerTest/examples/database-app/`
- Shows critical **multi-service pattern** (app + PostgreSQL database)
- Demonstrates **dual validation** (REST API + direct database access with JDBC)
- **NOT mentioned** in usage-docker-orch.md

**Solution**: Add database-app to examples list:

```markdown
- **Spock Examples**:
  - CLASS lifecycle (basic): `plugin-integration-test/dockerTest/examples/web-app/`
  - CLASS lifecycle (multi-service): `plugin-integration-test/dockerTest/examples/database-app/`
  - CLASS lifecycle (stateful): `plugin-integration-test/dockerTest/examples/stateful-web-app/`
  - METHOD lifecycle: `plugin-integration-test/dockerTest/examples/isolated-tests/`

- **JUnit 5 Examples**:
  - CLASS lifecycle: `plugin-integration-test/dockerTest/examples/web-app-junit/`
  - METHOD lifecycle: `plugin-integration-test/dockerTest/examples/isolated-tests-junit/`
```

**Additional context to add**:

```markdown
### Multi-Service Example: Database Integration

The `database-app` example demonstrates:
- **Two-service orchestration** (Spring Boot app + PostgreSQL database)
- **Dual validation pattern** - verify API responses AND database state with JDBC
- **Health checks for multiple services** - wait for both app and database
- **Port mapping for both services** - extract ports from state file for app and database

**build.gradle configuration:**
```groovy
dockerTest {
    composeStacks {
        databaseAppTest {
            files.from('src/integrationTest/resources/compose/database-app.yml')

            // Optional: Override Docker Compose project name
            // Default: <directory-name>_databaseAppTest
            // Custom name ensures unique container identification and cleaner docker ps output
            projectName = "db-integration-test"

            waitForHealthy {
                waitForServices.set(['app', 'postgres'])
                timeoutSeconds.set(90)
                pollSeconds.set(2)
            }
        }
    }
}

tasks.named('integrationTest') {
    usesCompose(stack: "databaseAppTest", lifecycle: "class")
}
```

**Test class:**
```groovy
@ComposeUp  // No parameters! All config from build.gradle
class DatabaseAppExampleIT extends Specification {
    @Shared String baseUrl
    @Shared String dbUrl

    def setupSpec() {
        def stateData = new JsonSlurper().parse(new File(System.getProperty('COMPOSE_STATE_FILE')))

        // Get app port
        def appPort = stateData.services['app'].publishedPorts[0].host
        baseUrl = "http://localhost:${appPort}"

        // Get database port for direct JDBC access
        def dbPort = stateData.services['postgres'].publishedPorts[0].host
        dbUrl = "jdbc:postgresql://localhost:${dbPort}/testdb"
    }

    def "should verify data via API and database"() {
        // Test both API and direct database access
    }
}
```

See `plugin-integration-test/dockerTest/examples/database-app/README.md` for complete example.
```

**Acceptance criteria**:
- [ ] database-app listed in "Complete Examples" section
- [ ] Multi-service example with correct `composeStacks` DSL syntax
- [ ] Shows `usesCompose()` pattern
- [ ] Shows zero-parameter `@ComposeUp` annotation
- [ ] Demonstrates optional `projectName` parameter with explanation
- [ ] Explains when/why to use `projectName` (multi-stack scenarios, naming conventions)

---

### 2. Wait Capability (RUNNING vs HEALTHY) Not Prominent

**Location**: New section or enhance existing

**Problem**:
- Wait capability is a **CORE FEATURE** but not explained prominently
- Examples show `waitForHealthy` but don't explain RUNNING vs HEALTHY difference
- No guidance on when to use each option

**Solution**: Add dedicated section "Container Readiness: Waiting for Services":

```markdown
## Container Readiness: Waiting for Services

**Key Capability**: The plugin automatically waits for containers to be ready before running tests,
preventing flaky tests caused by containers that aren't fully started.

### Wait Options

#### waitForHealthy (RECOMMENDED)
- **What it means**: Container is running AND health check passed
- **When to use**: Services that need initialization (databases, web apps, APIs)
- **Requirement**: Health check must be defined in compose file

```groovy
dockerTest {
    composeStacks {
        myTest {
            files.from('compose.yml')
            waitForHealthy {
                waitForServices.set(['app', 'postgres'])
                timeoutSeconds.set(60)
                pollSeconds.set(2)
            }
        }
    }
}
```

**Compose file health check:**
```yaml
services:
  app:
    image: my-app:latest
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 5s
      timeout: 3s
      retries: 10
```

#### waitForRunning
- **What it means**: Container process has started (but may not be ready)
- **When to use**: Simple services without health checks, or when RUNNING is sufficient
- **Requirement**: None

```groovy
dockerTest {
    composeStacks {
        simpleTest {
            files.from('compose.yml')
            waitForRunning {
                waitForServices.set(['nginx'])
                timeoutSeconds.set(30)
                pollSeconds.set(1)
            }
        }
    }
}
```

### Decision Guide

| Factor | waitForRunning | waitForHealthy |
|--------|----------------|----------------|
| **Service has health check** | Optional | ✅ Required |
| **Service needs initialization** | ❌ Not reliable | ✅ Recommended |
| **Speed** | ⚡ Faster | ⏱️ Waits for health |
| **Test reliability** | ⚠️ May fail if not ready | ✅ Runs when ready |
| **Examples** | Static files, proxies | Databases, web apps, APIs |

**Best Practice**: Default to `waitForHealthy` for reliable tests.
```

**Acceptance criteria**:
- [ ] Explains both `waitForHealthy` and `waitForRunning` options
- [ ] Shows current DSL syntax with `waitForServices.set([...])`
- [ ] Decision guide table included
- [ ] Compose file health check example included

---

### 3. Add TL;DR / Quick Start Section

**Location**: Top of document (after Prerequisites)

**Solution**: Add quick-start summary:

```markdown
## TL;DR (Quick Start)

**What is dockerTest?**
- Tests Docker images using Docker Compose
- Images can come from: `docker` DSL, registries, or any build tool
- Automatic container lifecycle management via test framework extensions

**Recommended pattern (3 steps):**

**Step 1: Configure compose stack in build.gradle**
```groovy
dockerTest {
    composeStacks {
        myTest {
            files.from('src/integrationTest/resources/compose/app.yml')
            waitForHealthy {
                waitForServices.set(['my-service'])
                timeoutSeconds.set(60)
            }
        }
    }
}
```

**Step 2: Wire test task with usesCompose()**
```groovy
tasks.named('integrationTest') {
    usesCompose(stack: "myTest", lifecycle: "class")
}
```

**Step 3: Use zero-parameter annotation in test**
```groovy
// Spock
@ComposeUp  // No parameters! Config from build.gradle
class MyAppIT extends Specification { ... }

// JUnit 5
@ExtendWith(DockerComposeClassExtension.class)  // Already parameter-less
class MyAppIT { ... }
```

**Next steps:**
- See [Test Framework Extensions](#test-framework-extensions-recommended) for details
- See [Complete Examples](#complete-examples) for copy-paste examples
```

**Acceptance criteria**:
- [ ] Shows current `composeStacks` DSL syntax
- [ ] Shows `usesCompose()` pattern
- [ ] Shows zero-parameter annotations for both frameworks
- [ ] Located at top of document

---

### 4. Enhance Multi-Service Examples in Main Body

**Location**: Throughout document

**Problem**:
- Most inline examples show single-service setups
- Multi-service orchestration is common but not well demonstrated

**Solution**: Add multi-service example using current syntax:

```markdown
### Multi-Service Stack Example

Testing an app that depends on a database:

**build.gradle:**
```groovy
dockerTest {
    composeStacks {
        appWithDbTest {
            files.from('src/integrationTest/resources/compose/app-with-db.yml')
            waitForHealthy {
                waitForServices.set(['app', 'postgres'])  // Wait for BOTH services
                timeoutSeconds.set(90)
                pollSeconds.set(2)
            }
        }
    }
}

tasks.named('integrationTest') {
    usesCompose(stack: "appWithDbTest", lifecycle: "class")
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
        condition: service_healthy
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
      - "5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U test -d testdb"]
      interval: 2s
      timeout: 1s
      retries: 5
```

**Test class accessing both services:**
```groovy
@ComposeUp  // No parameters!
class AppWithDbIT extends Specification {
    @Shared String baseUrl
    @Shared String dbUrl

    def setupSpec() {
        def stateData = new JsonSlurper().parse(new File(System.getProperty('COMPOSE_STATE_FILE')))

        // Get app port
        def appPort = stateData.services['app'].publishedPorts[0].host
        baseUrl = "http://localhost:${appPort}"

        // Get database port
        def dbPort = stateData.services['postgres'].publishedPorts[0].host
        dbUrl = "jdbc:postgresql://localhost:${dbPort}/testdb"
    }
}
```
```

**Acceptance criteria**:
- [ ] Shows multi-service `waitForHealthy` with multiple services
- [ ] Demonstrates `depends_on` with health check condition
- [ ] Shows extracting ports for multiple services from state file
- [ ] Uses current DSL syntax

---

### 5. Add Configuration Conflict Detection Documentation

**Location**: "Common Errors", "Troubleshooting", or near "Recommended Pattern" section

**Problem**:
- Plugin **actively prevents configuration duplication** by detecting conflicts
- When BOTH build.gradle (`usesCompose()`) AND annotation (`@ComposeUp` parameters) specify the same parameter,
  plugin throws `IllegalStateException`
- Users may naturally try to specify config in both places
- Error message is clear but behavior should be proactively documented

**What Conflict Detection Does** (from annotation fix plan Part 1):
```groovy
// build.gradle - Configuration source #1
dockerTest {
    composeStacks {
        myTest {
            files.from('compose.yml')
            waitForHealthy { waitForServices.set(['app']) }
        }
    }
}
tasks.named('integrationTest') {
    usesCompose(stack: "myTest", lifecycle: "class")  // Sets system property
}

// Test file - Configuration source #2
@ComposeUp(stackName = "myTest")  // ❌ CONFLICT! stackName also in build.gradle
class MyAppIT extends Specification {
    // Test will FAIL with IllegalStateException before execution
}
```

**Error Message** (actual from implementation):
```
IllegalStateException: Configuration conflict for 'stackName':
Specified in BOTH build.gradle (via usesCompose: 'myTest')
AND @ComposeUp annotation ('myTest').
Remove annotation parameter to use build.gradle configuration.
To fix: use EITHER build.gradle OR annotation, not both.
```

**Solution**: Add documentation in "Troubleshooting" or "Common Errors" section:

```markdown
### Configuration Conflict Error (Spock Only)

**Symptom**: Test fails during initialization with `IllegalStateException` containing "Configuration conflict"

**Example error:**
```
Configuration conflict for 'stackName': Specified in BOTH build.gradle
(via usesCompose: 'myTest') AND @ComposeUp annotation ('myTest').
Remove annotation parameter to use build.gradle configuration.
```

**Cause**: You specified the same parameter in both places:
- build.gradle via `usesCompose(stack: "myTest", ...)`
- Test annotation via `@ComposeUp(stackName = "myTest")`

**Why this matters**: The plugin enforces "single source of truth" to prevent configuration
duplication and maintenance burden. When using `usesCompose()`, ALL configuration must be
in build.gradle.

**Fix**: Remove ALL parameters from `@ComposeUp` annotation

❌ **Wrong** (causes conflict):
```groovy
// build.gradle
usesCompose(stack: "myTest", lifecycle: "class")

// Test
@ComposeUp(stackName = "myTest")  // ❌ Duplicates configuration
```

✅ **Correct**:
```groovy
// build.gradle
usesCompose(stack: "myTest", lifecycle: "class")

// Test
@ComposeUp  // ✅ No parameters! All config from build.gradle
```

**Note**:
- This only applies to Spock. JUnit 5 extensions are parameter-less by design.
- For backward compatibility, you CAN use annotation-only configuration (without `usesCompose()`),
  but mixing both sources is not allowed.
```

**Alternative location**: Add note in "Recommended Pattern" section:

```markdown
## Recommended Pattern: Configuration in build.gradle

**Important**: The plugin enforces "single source of truth" - if you use `usesCompose()` in
build.gradle, you MUST NOT specify parameters in the `@ComposeUp` annotation (Spock).
Specifying configuration in both places will cause a "Configuration conflict" error.

**Why**: Prevents configuration duplication, ensures tests always use the build.gradle
configuration, and makes code easier to maintain.
```

**Acceptance criteria**:
- [ ] Conflict detection behavior documented
- [ ] Example error message shown (actual from implementation)
- [ ] Clear "wrong vs correct" examples
- [ ] Explains why plugin enforces this (single source of truth, DRY principle)
- [ ] Notes that Spock-only behavior (JUnit 5 doesn't have this issue)
- [ ] Mentions backward compatibility (annotation-only still works)
- [ ] Located in easily-discoverable section (Troubleshooting or Recommended Pattern)

---

## Completed Tasks ✅ (Via Annotation Fix Plan)

The following tasks from the original plan are **DONE**:

### ✅ Task: "Choosing a Test Framework" Section
- **Status**: COMPLETE
- **Location**: Lines 99-131 of `usage-docker-orch.md`
- **Contains**: Framework comparison table, same build.gradle pattern for both

### ✅ Task: Recommended Approach Callout
- **Status**: COMPLETE
- **Location**: Lines 55-68, 132-140 of `usage-docker-orch.md`
- **Contains**: Extensions as recommended approach, `usesCompose()` pattern

### ✅ Task: Cross-Reference to usage-docker.md
- **Status**: PARTIALLY COMPLETE
- **Note**: Implicit through examples, could be more prominent

### ✅ Task: Lifecycle Patterns Documentation
- **Status**: COMPLETE
- **Location**: Lines 85-97 of `usage-docker-orch.md`
- **Contains**: CLASS, METHOD, SUITE lifecycles explained

---

## Implementation Checklist

### Priority 1: Remaining Gaps (Target: Next release)

- [ ] **Task 1**: Add database-app to Complete Examples section
  - Add to examples list
  - Add multi-service example with current DSL syntax
  - Include `projectName` parameter demonstration
  - Estimate: 40 minutes (increased from 30 to include projectName)

- [ ] **Task 2**: Add Wait Capability explanation section
  - Explain `waitForHealthy` vs `waitForRunning`
  - Add decision guide table
  - Show compose file health check examples
  - Estimate: 45 minutes

- [ ] **Task 3**: Add TL;DR / Quick Start section
  - 3-step summary with current syntax
  - Links to key sections
  - Estimate: 30 minutes

- [ ] **Task 4**: Add multi-service example in main body
  - App + database pattern
  - Current DSL syntax
  - Estimate: 30 minutes

- [ ] **Task 5**: Add Configuration Conflict Detection documentation
  - Document conflict detection behavior (Spock-only)
  - Show actual error message from implementation
  - Provide "wrong vs correct" examples
  - Explain single source of truth principle
  - Add to Troubleshooting or Recommended Pattern section
  - Estimate: 30 minutes

### Priority 2: Nice to Have (Optional) - EXCLUDED

- [x] **Task 6**: ~~Add multi-service troubleshooting~~
  - **Status**: EXCLUDED - Decision made not to implement
  - **Reason**: Existing troubleshooting section is sufficient; multi-service examples demonstrate best practices
    that prevent most issues
  - Database connection issues, service ordering, networking
  - Estimate: 20 minutes

---

## Testing / Verification

After making changes, verify:

- [ ] All cross-references work (no broken links)
- [ ] All code examples use current syntax:
  - `composeStacks {}` (not `stacks {}`)
  - `usesCompose(stack: "...", lifecycle: "...")`
  - Zero-parameter `@ComposeUp` annotation
  - `waitForHealthy { waitForServices.set([...]) }`
- [ ] Line length ≤ 120 characters (project standard)
- [ ] Examples match actual integration test patterns
- [ ] database-app example referenced actually exists

---

## Success Metrics

**Before annotation fix plan**: 85% complete
**After annotation fix plan**: 90% complete
**After this cleanup**: 100% complete (all priority 1 tasks done, priority 2 excluded by decision)

---

## Notes

- **COMPLETED 2025-11-21**: All priority 1 tasks implemented; priority 2 task excluded by decision
- **Updated 2025-11-21**: Revised to reflect annotation fix plan changes
- **Updated 2025-11-21**: Added Gap 5 (configuration conflict detection documentation)
- **Updated 2025-11-21**: Enhanced Task 1 to include `projectName` parameter demonstration
- All code examples now use current implementation patterns
- Effort estimate updated to 3-3.5 hours (from 2-3 hours) to include new tasks
- Focus on remaining gaps only

---

## References

- Source document: `docs/usage/usage-docker-orch.md`
- Related document: `docs/usage/usage-docker.md`
- Annotation fix plan: `docs/design-docs/todo/docker-orch-dsl-annoations-fix.md`
- Examples: `plugin-integration-test/dockerTest/examples/`
