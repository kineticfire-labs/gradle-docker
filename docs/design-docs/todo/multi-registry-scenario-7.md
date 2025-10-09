# Multi-Registry Integration Test - Scenario 7

## Overview

Implement scenario-7 integration test to demonstrate and verify publishing Docker images to multiple registries. This
fills testing gaps for multi-registry publishing and ZIP compression while showcasing the plugin's flexibility.

## Objectives

1. Enhance `registerVerifyRegistryImagesTask()` to support both single and multiple registries with unified API
2. Create scenario-7 integration test demonstrating multi-registry publishing
3. Fill testing coverage gaps: multi-registry publishing and ZIP compression
4. Document the unified registry verification API

## Design Decision: Unified Registry Verification Function

### Rationale

Instead of creating two separate functions (`registerVerifyRegistryImagesTask` and `registerMultiRegistryVerifyTask`),
we use a single unified function that handles both single and multiple registries through method overloading based on
parameter types.

**Benefits:**
- Simpler API with less user confusion
- Natural scaling from 1 to N registries
- Single implementation to maintain
- Truly backward compatible
- Leverages Groovy's dynamic typing strengths

### Function Design

**Overload 1: Single Registry (List Parameter)**
```groovy
registerVerifyRegistryImagesTask(
    project,
    List<String> imageReferences,
    String username = null,
    String password = null,
    String server = null
)
```

**Overload 2: Multiple Registries (Map Parameter)**
```groovy
registerVerifyRegistryImagesTask(
    project,
    Map<String, List<String>> imageReferencesByRegistry,
    Map<String, Map<String, String>> authByRegistry = [:]
)
```

### Usage Examples

**Single Registry (Existing Behavior):**
```groovy
registerVerifyRegistryImagesTask(project, [
    'localhost:5021/scenario2/scenario2-time-server:latest'
])
```

**Multiple Registries (New Capability):**
```groovy
registerVerifyRegistryImagesTask(project, [
    'registry1': [
        'localhost:5070/scenario7/published-timeserver:1.0.0',
        'localhost:5070/scenario7/published-timeserver:latest'
    ],
    'registry2': [
        'localhost:5071/scenario7/published-timeserver:1.0.0',
        'localhost:5071/scenario7/published-timeserver:latest'
    ]
])
```

## Scenario-7 Specification

### Test Coverage

This scenario tests:

**Build Mode Features:**
- number of images = 1
- build image + follow-on save and publish
- Image Name Mode: imageName, 1 tag (minimal config)
- number of build args = 0 (test zero build args)
- number of labels = 0 (test zero labels)
- dockerfile = default

**Save Features:**
- save w/ compression type = zip (fills testing gap)

**Publish Features:**
- ✅ publish same image to 2+ different registries (NEW - fills testing gap)
- ✅ publish same image with 2 tags to each registry
- publish to multiple private registries without authentication
- Image Name Mode override: registry, namespace, imageName, 2 tags

**Tag Features:**
- number of tags = 1

### Technical Specifications

**Image Naming:**
- **Build:** `scenario-7-time-server:latest` (minimal naming - just imageName + tag)
- **Registry 1 (port 5070):** `localhost:5070/scenario7/published-timeserver:1.0.0` and `:latest`
- **Registry 2 (port 5071):** `localhost:5071/scenario7/published-timeserver:1.0.0` and `:latest`

**Port Convention:**
- Registry 1: Port 5070 (pattern: 50[scenario#][registry#])
- Registry 2: Port 5071

**Verification Requirements:**
1. Built image exists: `scenario-7-time-server:latest`
2. Saved file exists: `build/docker-images/scenario7-time-server.zip`
3. Registry 1 has 2 images:
   - `localhost:5070/scenario7/published-timeserver:1.0.0`
   - `localhost:5070/scenario7/published-timeserver:latest`
4. Registry 2 has 2 images:
   - `localhost:5071/scenario7/published-timeserver:1.0.0`
   - `localhost:5071/scenario7/published-timeserver:latest`
5. Image has zero build args
6. (Optional) Image has zero custom labels

## Implementation Tasks

### Task 1: Enhance registerVerifyRegistryImagesTask Function

**File:** `plugin-integration-test/buildSrc/src/main/groovy/docker-image-testing.gradle`

**Implementation Steps:**

1. Add type detection logic to determine single vs multi-registry mode
2. Implement single-registry path (preserve existing behavior)
3. Implement multi-registry path:
   - Create per-registry verification tasks: `verifyRegistryDockerImagesRegistry1`, etc.
   - Create aggregate task: `verifyRegistryDockerImages`
4. Add comprehensive function documentation with examples
5. Handle authentication for both modes

**Function Signature:**
```groovy
ext.registerVerifyRegistryImagesTask = { Project project,
                                          def imageReferencesOrMap,
                                          def usernameOrAuth = null,
                                          String password = null,
                                          String server = null ->

    if (imageReferencesOrMap instanceof List) {
        // SINGLE REGISTRY MODE (existing behavior)
        // Create task: verifyRegistryDockerImages
    } else if (imageReferencesOrMap instanceof Map) {
        // MULTIPLE REGISTRY MODE (new behavior)
        // Create per-registry tasks + aggregate task
    } else {
        throw new IllegalArgumentException("Parameter must be List or Map")
    }
}
```

**Key Design Points:**
- Use `instanceof` for type detection (idiomatic Groovy)
- Single registry creates one task: `verifyRegistryDockerImages`
- Multiple registries create N+1 tasks: per-registry tasks + aggregate
- Aggregate task depends on all per-registry tasks
- Support optional authentication per registry

### Task 2: Create Scenario-7 Directory Structure

**Create directories:**
```
plugin-integration-test/docker/scenario-7/
├── build.gradle
└── src/
    └── main/
        └── docker/
            └── Dockerfile
```

### Task 3: Create Scenario-7 build.gradle

**File:** `plugin-integration-test/docker/scenario-7/build.gradle`

**Key Sections:**

1. **Plugin application:**
   ```groovy
   plugins {
       id 'groovy'
       id 'com.kineticfire.gradle.gradle-docker'
   }
   ```

2. **Docker DSL configuration:**
   ```groovy
   import static com.kineticfire.gradle.docker.model.SaveCompression.ZIP

   docker {
       images {
           timeServer {
               contextTask = tasks.register('prepareScenario7TimeServerContext', Copy) { ... }

               // Minimal naming: just imageName and tag
               imageName.set('scenario-7-time-server')
               tags.set(['latest'])

               // No build args (test zero build args)
               // No labels (test zero labels)

               save {
                   compression.set(ZIP)
                   outputFile.set(layout.buildDirectory.file('docker-images/scenario7-time-server.zip'))
               }

               publish {
                   to('registry1') {
                       registry.set('localhost:5070')
                       namespace.set('scenario7')
                       imageName.set('published-timeserver')
                       publishTags.set(['1.0.0', 'latest'])
                   }

                   to('registry2') {
                       registry.set('localhost:5071')
                       namespace.set('scenario7')
                       imageName.set('published-timeserver')
                       publishTags.set(['1.0.0', 'latest'])
                   }
               }
           }
       }
   }
   ```

3. **Registry configuration:**
   ```groovy
   apply plugin: RegistryManagementPlugin

   registryManagement {
       registry('registry1', 5070)
       registry('registry2', 5071)
   }
   ```

4. **Verification tasks:**
   ```groovy
   registerBuildWorkflowTasks(project, ['scenario-7-time-server:latest'])

   registerVerifySavedImagesTask(project, ['build/docker-images/scenario7-time-server.zip'])

   // Use unified function with Map parameter for multi-registry
   registerVerifyRegistryImagesTask(project, [
       'registry1': [
           'localhost:5070/scenario7/published-timeserver:1.0.0',
           'localhost:5070/scenario7/published-timeserver:latest'
       ],
       'registry2': [
           'localhost:5071/scenario7/published-timeserver:1.0.0',
           'localhost:5071/scenario7/published-timeserver:latest'
       ]
   ])

   registerVerifyBuildArgsTask(project, [
       'scenario-7-time-server:latest': [:]  // Empty map = zero build args
   ])
   ```

5. **Integration test task:**
   ```groovy
   tasks.register('integrationTest') {
       dependsOn 'startTestRegistries'
       dependsOn 'cleanDockerImages'
       dependsOn 'dockerImages'
       dependsOn 'verifyDockerImages'
       dependsOn 'verifySavedDockerImages'
       dependsOn 'verifyRegistryDockerImages'  // Aggregate task
       dependsOn 'verifyDockerBuildArgs'
       dependsOn 'stopTestRegistries'
   }
   ```

6. **Task ordering:**
   ```groovy
   tasks.named('cleanDockerImages') { mustRunAfter 'startTestRegistries' }
   tasks.named('dockerImages') { mustRunAfter 'cleanDockerImages' }
   tasks.named('verifyDockerImages') { mustRunAfter 'dockerImages' }
   tasks.named('verifySavedDockerImages') { mustRunAfter 'dockerImages' }
   tasks.named('verifyRegistryDockerImages') { mustRunAfter 'dockerImages' }
   tasks.named('verifyDockerBuildArgs') {
       mustRunAfter 'dockerImages'
       mustRunAfter 'verifyDockerImages'
   }
   tasks.named('stopTestRegistries') { mustRunAfter 'verifyRegistryDockerImages' }
   ```

### Task 4: Create Scenario-7 Dockerfile

**File:** `plugin-integration-test/docker/scenario-7/src/main/docker/Dockerfile`

**Content:**
```dockerfile
# Minimal Dockerfile for scenario-7 integration test
# Tests zero build args and zero labels
FROM eclipse-temurin:21-jre-alpine

# Copy the application JAR
COPY app-1.0.0.jar /app/app.jar

# Expose port for the time server
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

**Key Points:**
- No ARG instructions (testing zero build args)
- No LABEL instructions (testing zero labels)
- Minimal, functional Dockerfile

### Task 5: Update Docker Integration Test Coverage Matrix

**File:** `plugin-integration-test/docker/README.md`

**Add scenario-7 to these sections:**

1. **Build Mode section:**
   - Add to "number of images = 1"
   - Add to "build image + follow-on save and publish"
   - Add to "Image Name Mode: imageName, tag(s)"
   - Add to "number of build args = none"
   - Add to "number of labels = none"

2. **Tag Features section:**
   - Add to "number of tags = 1"

3. **Save Features section:**
   - Change "save w/ compression type = zip" from "todo" to "scenario-7"

4. **Publish Features section:**
   - Change "publish same image > 1 tags, same registry" - add scenario-7
   - Change "publish same image > 1 tags, different registries" from "todo" to "scenario-7"
   - Add to "publish to private registry without authentication"

**Example additions:**
```markdown
| # | Tested Feature                                         | Test Scenario                                    |
|---|--------------------------------------------------------|--------------------------------------------------|
| 2 | number of images = 1                                   | <ol><li>scenario-1</li>...<li>scenario-7</li></ol> |
| 2 | Image Name Mode: imageName, tag(s)                     | <ol><li>scenario-1</li><li>scenario-7</li></ol>  |

## Save Features

| # | Tested Feature                   | Test Scenario                |
|---|----------------------------------|------------------------------|
| 6 | save w/ compression type = zip   | <ol><li>scenario-7</li></ol> |

## Publish Features

| #  | Tested Feature                                          | Test Scenario                                    |
|----|--------------------------------------------------------|--------------------------------------------------|
| 3  | publish same image > 1 tags, same registry             | <ol><li>scenario-3</li><li>scenario-6</li><li>scenario-7</li></ol> |
| 4  | publish same image > 1 tags, different registries      | <ol><li>scenario-7</li></ol>                     |
```

### Task 6: Update buildSrc README

**File:** `plugin-integration-test/buildSrc/README.md`

**Update the `registerVerifyRegistryImagesTask` section:**

1. Update function description to mention both modes
2. Add parameter documentation for both overloads
3. Add usage examples for both modes
4. Add examples with authentication
5. Add task ordering examples

**Example documentation structure:**
```markdown
### registerVerifyRegistryImagesTask

Verifies that Docker images exist in one or more Docker registries. Supports both single-registry and multi-registry
modes with automatic detection based on parameter type.

```groovy
// Single registry mode
registerVerifyRegistryImagesTask(project, imageReferences, username, password, server)

// Multiple registries mode
registerVerifyRegistryImagesTask(project, imageReferencesByRegistry, authByRegistry)
```

**Parameters (Single Registry Mode):**
- `project`: The Gradle project to register tasks on
- `imageReferences`: List of Docker image references in format `"registry/image-name:tag"`
- `username`: Optional registry username for authentication
- `password`: Optional registry password for authentication
- `server`: Optional registry server for authentication

**Parameters (Multiple Registries Mode):**
- `project`: The Gradle project to register tasks on
- `imageReferencesByRegistry`: Map of registry names to their image reference lists
- `authByRegistry`: Optional map of registry names to authentication credentials

**Creates Tasks:**
- Single mode: `verifyRegistryDockerImages` in the `verification` group
- Multiple mode:
  - `verifyRegistryDockerImages<RegistryName>` per registry (e.g., `verifyRegistryDockerImagesRegistry1`)
  - `verifyRegistryDockerImages` aggregate task

**Example - Single Registry:**
```groovy
registerVerifyRegistryImagesTask(project, [
    'localhost:5000/my-app:1.0.0',
    'localhost:5000/my-app:latest'
])

// With authentication
registerVerifyRegistryImagesTask(
    project,
    ['localhost:5001/secure-app:latest'],
    'testuser',
    'testpass',
    'localhost:5001'
)
```

**Example - Multiple Registries:**
```groovy
// Configure multiple registries
registryManagement {
    registry('registry1', 5070)
    registry('registry2', 5071)
}

// Verify same images in multiple registries
registerVerifyRegistryImagesTask(project, [
    'registry1': [
        'localhost:5070/my-app:1.0.0',
        'localhost:5070/my-app:latest'
    ],
    'registry2': [
        'localhost:5071/my-app:1.0.0',
        'localhost:5071/my-app:latest'
    ]
])

// With mixed authentication (some registries need auth, others don't)
registerVerifyRegistryImagesTask(
    project,
    [
        'public': ['localhost:5070/my-app:latest'],
        'private': ['localhost:5071/secure-app:latest']
    ],
    [
        'private': [
            username: 'testuser',
            password: 'testpass',
            server: 'localhost:5071'
        ]
    ]
)
```

**Task Ordering:**
```groovy
// Single registry mode
tasks.named('verifyRegistryDockerImages') {
    mustRunAfter 'dockerPublish'
}

// Multiple registry mode - aggregate task
tasks.named('verifyRegistryDockerImages') {
    mustRunAfter 'dockerPublish'
}

// Multiple registry mode - individual registry tasks
tasks.named('verifyRegistryDockerImagesRegistry1') {
    mustRunAfter 'dockerPublish'
}
```
```

### Task 7: Update Top-Level README

**File:** `plugin-integration-test/README.md`

**Add scenario-7 to the run commands section (around line 83):**

```markdown
# Run specific Docker scenario
./gradlew -Pplugin_version=<version> docker:scenario-1:integrationTest
./gradlew -Pplugin_version=<version> docker:scenario-2:integrationTest
./gradlew -Pplugin_version=<version> docker:scenario-3:integrationTest
./gradlew -Pplugin_version=<version> docker:scenario-4:integrationTest
./gradlew -Pplugin_version=<version> docker:scenario-5:integrationTest
./gradlew -Pplugin_version=<version> docker:scenario-6:integrationTest
./gradlew -Pplugin_version=<version> docker:scenario-7:integrationTest

# Run with clean for specific scenario
./gradlew -Pplugin_version=<version> docker:scenario-7:clean docker:scenario-7:integrationTest
```

### Task 8: Testing and Verification

**Test Execution:**
```bash
cd plugin-integration-test

# Test scenario-7 individually
./gradlew -Pplugin_version=1.0.0 docker:scenario-7:clean docker:scenario-7:integrationTest

# Test all scenarios to verify backward compatibility
./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest
```

**Verification Checklist:**
- [ ] Scenario-7 builds image successfully
- [ ] Image saved to ZIP file exists
- [ ] Both registries started successfully (ports 5070, 5071)
- [ ] Image published to registry1 with both tags
- [ ] Image published to registry2 with both tags
- [ ] All 4 registry image references verified (2 per registry)
- [ ] Build args verification passes (zero args)
- [ ] No containers left running after test
- [ ] All existing scenarios (1-6) still pass
- [ ] No warnings or errors in build output

## Implementation Checklist

### Code Changes
- [ ] Enhance `registerVerifyRegistryImagesTask()` in `docker-image-testing.gradle`
  - [ ] Add type detection (List vs Map)
  - [ ] Implement single-registry logic (preserve existing)
  - [ ] Implement multi-registry logic (new)
  - [ ] Add comprehensive function documentation
- [ ] Create `plugin-integration-test/docker/scenario-7/build.gradle`
  - [ ] Configure Docker DSL with minimal naming
  - [ ] Configure save with ZIP compression
  - [ ] Configure publish to 2 registries
  - [ ] Configure registry management (2 registries)
  - [ ] Configure verification tasks
  - [ ] Configure integration test task
  - [ ] Configure task ordering
- [ ] Create `plugin-integration-test/docker/scenario-7/src/main/docker/Dockerfile`
  - [ ] Minimal Dockerfile (no ARG, no LABEL)

### Documentation Updates
- [ ] Update `plugin-integration-test/docker/README.md`
  - [ ] Add scenario-7 to Build Mode section
  - [ ] Add scenario-7 to Save Features section
  - [ ] Add scenario-7 to Publish Features section
  - [ ] Mark "zip compression" and "multi-registry" as tested
- [ ] Update `plugin-integration-test/buildSrc/README.md`
  - [ ] Update `registerVerifyRegistryImagesTask` section
  - [ ] Add single-registry examples
  - [ ] Add multi-registry examples
  - [ ] Add authentication examples
  - [ ] Add task ordering examples
- [ ] Update `plugin-integration-test/README.md`
  - [ ] Add scenario-7 run commands

### Testing
- [ ] Test scenario-7 execution individually
- [ ] Test all scenarios (1-7) to verify backward compatibility
- [ ] Verify no leftover containers
- [ ] Verify no warnings in build output

## Success Criteria

1. **Functionality:**
   - Scenario-7 builds, saves (ZIP), and publishes to 2 registries successfully
   - All 4 registry image references are verified
   - Zero build args verified correctly
   - No containers left running after test

2. **Backward Compatibility:**
   - All existing scenarios (1-6) pass without modifications
   - Single-registry verification continues to work as before
   - No breaking changes to existing API

3. **Code Quality:**
   - Function implementation is type-safe and handles errors gracefully
   - Code follows Gradle 9/10 patterns (Provider API, configuration cache compatible)
   - Documentation is clear and comprehensive

4. **Test Coverage:**
   - Fills gap: "publish same image to 2+ different registries"
   - Fills gap: "save w/ compression type = zip"
   - Tests minimal image naming configuration
   - Tests zero build args

## Estimated Effort

**Total Time:** 1.5-2 hours

**Breakdown:**
- Task 1 (Enhance function): 30 minutes
- Task 2-4 (Create scenario-7 files): 30 minutes
- Task 5-7 (Update documentation): 20 minutes
- Task 8 (Testing): 20 minutes

**Risk Level:** Low
- All changes are additive
- Leverages existing, tested infrastructure
- Type detection is safe and idiomatic Groovy
- Backward compatibility is guaranteed

## References

- Docker integration test coverage: `plugin-integration-test/docker/README.md`
- buildSrc documentation: `plugin-integration-test/buildSrc/README.md`
- Docker usage guide: `docs/usage/usage-docker.md`
- Gradle 9/10 compatibility: `docs/design-docs/gradle-9-and-10-compatibility.md`

## Notes

### Port Numbering Convention

Ports follow the pattern `50[X][Y]` where:
- X = Scenario number (tens digit)
- Y = Registry number within scenario (0-9)

**Scenario-7 Ports:**
- Registry 1: 5070
- Registry 2: 5071

This prevents conflicts with other scenarios running in parallel.

### Design Philosophy

The unified function design leverages Groovy's strengths:
- Dynamic typing with type detection
- Optional parameters with defaults
- Flexible, intuitive API
- Single function that "does the right thing" based on parameters

This is a more elegant solution than creating two separate functions for similar tasks.
