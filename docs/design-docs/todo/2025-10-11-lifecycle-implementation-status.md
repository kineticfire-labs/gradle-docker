# Lifecycle Terminology and Tests - Implementation Status

**Date**: 2025-10-11
**Status**: Partially Complete
**Related Plan**: `2025-10-11-enhance-docker-orch-integration-tests-3.md`

## Summary

Phase 1 and Phase 2.1 are **100% complete**. Phase 2.2, Phase 3, and parts of Phase 4 remain.

## ✅ Completed Work

### Phase 1: Lifecycle Terminology Update (100% Complete)

All dockerOrch documentation has been updated from "SUITE/TEST" to "CLASS/METHOD" terminology:

**Files Updated:**
- ✅ `dockerOrch/README.md` - Verification and example tables, lifecycle definitions
- ✅ `dockerOrch/verification/README.md` - Scenario table, lifecycle definitions
- ✅ `dockerOrch/examples/README.md` - All SUITE references changed to CLASS
- ✅ `dockerOrch/verification/basic/README.md` - Lifecycle type and descriptions
- ✅ `dockerOrch/examples/web-app/README.md` - Lifecycle type and descriptions

**Terminology:**
- **CLASS** = Containers start once per test class (setupSpec/@BeforeAll), all test methods run, containers stop once
- **METHOD** = Containers start/stop per test method (setup/@BeforeEach)

### Phase 2.1: lifecycle-class Verification Test (100% Complete)

**Location**: `dockerOrch/verification/lifecycle-class/`

**Files Created:**
- ✅ `app/src/main/java/com/kineticfire/test/Application.java`
- ✅ `app/src/main/java/com/kineticfire/test/StateController.java`
- ✅ `app/src/main/resources/application.properties`
- ✅ `app/build.gradle`
- ✅ `app-image/src/main/docker/Dockerfile`
- ✅ `app-image/src/main/docker/entrypoint.sh`
- ✅ `app-image/src/integrationTest/resources/compose/lifecycle-class.yml`
- ✅ `app-image/src/integrationTest/groovy/com/kineticfire/test/LifecycleClassIT.groovy`
- ✅ `app-image/build.gradle`
- ✅ `build.gradle`
- ✅ `gradle` symlink → `../../../gradle`
- ✅ `gradlew` symlink → `../../../gradlew`
- ✅ `README.md`

**Integration:**
- ✅ Added to `settings.gradle`
- ✅ Updated tracking tables in `dockerOrch/README.md`
- ✅ Updated tracking table in `dockerOrch/verification/README.md`

**Test Validation Points:**
- setupSpec called exactly once
- Containers persist between test methods
- State persists across tests (write in one, read in another)
- cleanupSpec called exactly once after all tests
- No resource leaks

---

## ⏳ Remaining Work

### Phase 2.2: lifecycle-method Verification Test (NOT STARTED)

**Objective**: Verify METHOD lifecycle works correctly - containers restart for each test method.

**Copy Pattern**: Use `lifecycle-class/` as reference, adapt for METHOD lifecycle

**Key Changes from lifecycle-class:**
1. **Test file** (`LifecycleMethodIT.groovy`):
   - Use `setup()` and `cleanup()` instead of `setupSpec()` and `cleanupSpec()`
   - Make counters non-static (instance variables)
   - Test that state does NOT persist between methods (write in test1, verify gone in test2)
   - Verify cleanup called after each test

2. **Same application code**: StateController can be reused as-is

3. **Build files**:
   - Update all project references: `lifecycle-class` → `lifecycle-method`
   - Update image name: `verification-lifecycle-class-state-app` → `verification-lifecycle-method-state-app`
   - Update stack name: `lifecycleClassTest` → `lifecycleMethodTest`
   - Update project name: `verification-lifecycle-class-test` → `verification-lifecycle-method-test`

4. **settings.gradle**: Add after lifecycle-class:
```groovy
include 'dockerOrch:verification:lifecycle-method'
include 'dockerOrch:verification:lifecycle-method:app'
include 'dockerOrch:verification:lifecycle-method:app-image'
```

5. **Update tables**: Change status from "Planned" to "Complete"

**Full Spec**: See original plan document section "Phase 2.2"

---

### Phase 3.1: stateful-web-app Example (NOT STARTED)

**Objective**: Demonstrate CLASS lifecycle for stateful testing (session management).

**Location**: `dockerOrch/examples/stateful-web-app/`

**Application**: Session management REST API
- `SessionController.java` with endpoints:
  - POST `/register` - Create session
  - POST `/login` - Login with session ID
  - PUT `/profile` - Update profile in session
  - DELETE `/logout` - Clear session

**Test Flow** (`StatefulWebAppIT.groovy`):
- Test 1: User registers → save sessionId
- Test 2: User logs in with sessionId from test 1
- Test 3: User updates profile with same sessionId
- Test 4: User logs out, verify session cleared

**Key Point**: Tests build on each other, demonstrating why CLASS lifecycle is appropriate.

**Copy Pattern**: Similar structure to `examples/web-app/`, but with stateful logic

**Integration:**
- Add to `settings.gradle`
- Update example table in `dockerOrch/README.md`
- Update example list in `dockerOrch/examples/README.md`

**Full Spec**: See original plan document section "Phase 3.1"

---

### Phase 3.2: isolated-tests Example (NOT STARTED)

**Objective**: Demonstrate METHOD lifecycle for tests requiring clean state.

**Location**: `dockerOrch/examples/isolated-tests/`

**Application**: Database operations (UserRepository + PostgreSQL)
- CRUD operations on users table
- Each test needs clean database

**Test Flow** (`IsolatedDatabaseTestsIT.groovy`):
- Each test uses `setup()` to get fresh containers
- Test 1: Create user "alice"
- Test 2: Create user "alice" again (should succeed - fresh DB!)
- Test 3: Delete user "bob"
- Test 4: Update user "charlie"
- Test 5: Verify no users from previous tests exist

**Key Point**: Tests are independent, each gets fresh state, demonstrating why METHOD lifecycle is needed.

**Docker Compose**: Include PostgreSQL with tmpfs for speed
```yaml
services:
  db-app:
    image: example-isolated-tests-app:latest
    depends_on:
      - postgres
  postgres:
    image: postgres:15-alpine
    tmpfs:
      - /var/lib/postgresql/data
```

**Copy Pattern**: Similar to `examples/web-app/`, but with METHOD lifecycle in tests

**Integration:**
- Add to `settings.gradle`
- Update example table in `dockerOrch/README.md`
- Update example list in `dockerOrch/examples/README.md`

**Full Spec**: See original plan document section "Phase 3.2"

---

## Completion Steps

### To Complete Phase 2.2 (lifecycle-method):

1. **Copy lifecycle-class directory**:
```bash
cd plugin-integration-test/dockerOrch/verification
cp -r lifecycle-class lifecycle-method
```

2. **Update all references** (use find/replace):
   - `lifecycle-class` → `lifecycle-method`
   - `lifecycleClass` → `lifecycleMethod`
   - `LifecycleClass` → `LifecycleMethod`
   - `verification-lifecycle-class` → `verification-lifecycle-method`

3. **Rewrite test file** (`LifecycleMethodIT.groovy`):
   - Change to instance variables (no `static`)
   - Use `setup()` and `cleanup()` instead of `setupSpec()` and `cleanupSpec()`
   - Test that state does NOT persist (404 when reading previous test's data)

4. **Update settings.gradle**: Add 3 includes for lifecycle-method

5. **Update tables**: Mark lifecycle-method as complete

6. **Test it**: Run `./gradlew dockerOrch:verification:lifecycle-method:app-image:runIntegrationTest`

### To Complete Phase 3.1 (stateful-web-app):

1. **Copy web-app example**:
```bash
cd plugin-integration-test/dockerOrch/examples
cp -r web-app stateful-web-app
```

2. **Replace application** with session management code:
   - Create `SessionController.java` (see plan document for full code)
   - Update `Application.java` if needed
   - Add session storage (ConcurrentHashMap)

3. **Rewrite tests** (`StatefulWebAppIT.groovy`):
   - Store sessionId from first test
   - Use it in subsequent tests
   - Test the full workflow: register → login → update → logout

4. **Update all references** (find/replace):
   - `web-app` → `stateful-web-app`
   - `webApp` → `statefulWebApp`
   - Update image names, stack names, project names

5. **Update settings.gradle, tables, README**

6. **Test it**

### To Complete Phase 3.2 (isolated-tests):

1. **Copy web-app example**:
```bash
cd plugin-integration-test/dockerOrch/examples
cp -r web-app isolated-tests
```

2. **Replace application** with database app:
   - Create `UserRepository.java`, `UserController.java`
   - Add PostgreSQL dependencies to build.gradle
   - Configure data source in application.properties

3. **Add PostgreSQL to compose file**:
   - Add postgres service with tmpfs for speed
   - Configure app to depend on postgres

4. **Rewrite tests** (`IsolatedDatabaseTestsIT.groovy`):
   - Use `setup()` (METHOD lifecycle)
   - Test database operations
   - Verify isolation between tests (no data from previous tests)

5. **Update all references, settings.gradle, tables**

6. **Test it**

---

## Reference Documents

- **Full Implementation Plan**: `docs/design-docs/todo/2025-10-11-enhance-docker-orch-integration-tests-3.md`
- **Completed Reference**: `plugin-integration-test/dockerOrch/verification/lifecycle-class/`
- **Testing Requirements**: `docs/project-standards/testing/integration-testing.md`

---

## Estimated Remaining Effort

| Phase | Task | Estimated Time |
|-------|------|----------------|
| 2.2 | lifecycle-method verification | 2 hours |
| 3.1 | stateful-web-app example | 1.75 hours |
| 3.2 | isolated-tests example | 1.75 hours |
| **Total** | | **5.5 hours** |

**Note**: These can be done independently in any order.
