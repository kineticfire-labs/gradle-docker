# Scenario-6 Integration Test Plan

## Overview
Create a new integration test at `plugin-integration-test/docker/scenario-6/` that demonstrates and verifies:
- Building 1 Docker image using **repository mode** naming
- Adding build args and labels using **`putAll` configuration method**
- Creating 2 tags for the built image
- Saving the image with **XZ compression**
- Publishing to a local authenticated registry using **repository mode**

## Test Coverage Analysis

Based on the README.md coverage matrix, scenario-6 will fill these gaps:

### Build Mode Features
- ✅ Repository Mode: repository, 2 tags (currently "todo")
- ✅ number of build args w/ putAll > 1 (currently "todo")
- ✅ number of labels w/ putAll > 1 (currently "todo")

### Save Features
- ✅ save w/ compression type = xz (currently "todo")

### Publish Features
- ✅ publish to private registry w/ authentication (currently "todo")
- ✅ Repository Mode: registry, repository, 2 tags (currently "todo")

## Directory Structure

```
plugin-integration-test/docker/scenario-6/
├── gradle/
│   ├── wrapper/
│   │   └── gradle-wrapper.properties
│   └── libs.versions.toml
├── src/
│   └── main/
│       └── docker/
│           └── Dockerfile
├── build.gradle
├── settings.gradle
├── gradle.properties
└── gradlew
```

## Key Configuration Details

### Image Naming (Repository Mode)
- **repository**: `scenario-6/time-server` (no registry at build time)
- **tags**: `["1.0.0", "latest"]` (2 tags at build)
- **Resulting local images**:
  - `scenario-6/time-server:1.0.0`
  - `scenario-6/time-server:latest`

### Build Args (using putAll with 2+ args)
```groovy
buildArgs.putAll(providers.provider {
    [
        'JAR_FILE': "app-${project.version}.jar",
        'BUILD_VERSION': project.version.toString()
    ]
})
```

### Labels (using putAll with 2+ args)
```groovy
labels.putAll(providers.provider {
    [
        "org.opencontainers.image.version": project.version.toString(),
        "maintainer": "team@kineticfire.com"
    ]
})
```

### Save Configuration (XZ compression)
```groovy
import static com.kineticfire.gradle.docker.model.SaveCompression.XZ

save {
    compression.set(XZ)
    outputFile.set(layout.buildDirectory.file("docker-images/scenario6-time-server.tar.xz"))
}
```

### Publish Configuration (Authenticated Registry, Repository Mode)
- **Registry port**: `5060` (follows naming convention: 50 + scenario number in tens digit)
- **Authentication**: username=`testuser`, password=`testpass`
- **Repository mode**: specify registry + repository + 2 tags (different from build tags)
- **Published tags**: `["edge", "test"]` (different from build tags to prove override)

```groovy
publish {
    to('authenticatedRegistry') {
        registry.set('localhost:5060')
        repository.set('scenario-6/time-server')
        publishTags.set(['edge', 'test'])

        auth {
            username.set('testuser')
            password.set('testpass')
        }
    }
}
```

## File-by-File Plan

### settings.gradle
- Standard plugin management configuration
- Reference plugin version from gradle property
- Set rootProject.name = 'scenario-6'

### gradle.properties
- Enable Gradle 9/10 compatibility flags:
  - `org.gradle.configuration-cache=true`
  - `org.gradle.configuration-cache.problems=warn`
  - `org.gradle.caching=true`
  - `org.gradle.warning.mode=all`

### gradle/libs.versions.toml
- Define gradle-docker plugin version
- Match structure from scenario-1

### src/main/docker/Dockerfile
- Copy Dockerfile from scenario-1 (standard Java app containerization)
- Use ARGs: JAR_FILE, BUILD_VERSION
- Use base image: `eclipse-temurin:21-jre`
- EXPOSE port 8080

### build.gradle (main configuration)

**Structure**:
1. **Header comment** documenting test coverage
2. **Plugins block**: groovy + gradle-docker
3. **Standalone guard** (prevent running from scenario-6 directory directly)
4. **Imports**: `SaveCompression.XZ`
5. **Project metadata**: group, version
6. **Docker DSL configuration**:
   - contextTask (Copy task to prepare build context)
   - repository.set('scenario-6/time-server')
   - tags.set(['1.0.0', 'latest'])
   - buildArgs.putAll(...) with 2 args
   - labels.putAll(...) with 2 labels
   - save { compression.set(XZ), outputFile }
   - publish { to('authenticatedRegistry') {...} }
7. **Apply testing libraries**:
   - `docker-image-testing.gradle`
   - `RegistryManagementPlugin`
8. **Registry configuration**:
   - `withAuthenticatedRegistry('test-registry-scenario6', 5060, 'testuser', 'testpass')`
9. **Register verification tasks**:
   - `registerBuildWorkflowTasks(...)` for built images
   - `registerVerifySavedImagesTask(...)` for saved tar.xz file
   - `registerVerifyRegistryImagesTask(...)` for published images
10. **integrationTest task**: orchestrate workflow
11. **Task ordering configuration**: mustRunAfter dependencies

## Verification Strategy

### Built Image Verification
- Verify local images exist:
  - `scenario-6/time-server:1.0.0`
  - `scenario-6/time-server:latest`
- Use `registerBuildWorkflowTasks` function

### Saved Image Verification
- Verify file exists: `build/docker-images/scenario6-time-server.tar.xz`
- Use `registerVerifySavedImagesTask` function

### Published Image Verification
- Verify registry images exist:
  - `localhost:5060/scenario-6/time-server:edge`
  - `localhost:5060/scenario-6/time-server:test`
- Use `registerVerifyRegistryImagesTask` function

## Integration Test Workflow

```
integrationTest task dependencies:
1. startTestRegistries (start authenticated registry on port 5060)
2. cleanDockerImages (remove any existing images)
3. dockerImages (build, tag, save, publish)
4. verifyDockerImages (check local images)
5. verifySavedDockerImages (check tar.xz file)
6. verifyRegistryDockerImages (check registry images)
7. stopTestRegistries (cleanup registry)
```

**Task ordering**:
- `cleanDockerImages` mustRunAfter `startTestRegistries`
- `dockerImages` mustRunAfter `cleanDockerImages`
- `verifyDockerImages` mustRunAfter `dockerImages`
- `verifySavedDockerImages` mustRunAfter `dockerImages`
- `verifyRegistryDockerImages` mustRunAfter `dockerImages`
- `stopTestRegistries` mustRunAfter `verifyRegistryDockerImages`

## De-confliction Strategy

### Image Names
- All images prefixed with `scenario-6/` to avoid conflicts
- Full image references:
  - Build: `scenario-6/time-server:1.0.0`, `scenario-6/time-server:latest`
  - Registry: `localhost:5060/scenario-6/time-server:edge`, `localhost:5060/scenario-6/time-server:test`

### Registry Port
- Port `5060` (unique, follows pattern)
- Other scenarios use: 5021, 5031, 5041, 5051
- Port 5060 = 50 (base) + 6 (tens digit for scenario 6) + 0 (ones digit)

### Saved File Path
- Unique path: `build/docker-images/scenario6-time-server.tar.xz`

## Gradle 9/10 Compatibility Considerations

### Provider API Usage
- Use `providers.provider { ... }` for dynamic values
- Use `.set()` for all property assignments
- Use `layout.buildDirectory.file()` for file outputs
- Capture `project.version.toString()` in variables during configuration

### Configuration Cache
- No `Project` references in task actions
- No eager property resolution during configuration
- All dynamic values wrapped in providers

### Build Cache
- Task inputs/outputs properly declared
- `outputs.upToDateWhen { ... }` for custom caching logic

## Expected Test Outcomes

### Success Criteria
1. ✅ Build succeeds with repository mode naming
2. ✅ 2 build args applied via putAll
3. ✅ 2 labels applied via putAll
4. ✅ 2 local tags created (1.0.0, latest)
5. ✅ Image saved as tar.xz file
6. ✅ Image published to authenticated registry
7. ✅ 2 registry tags created (edge, test)
8. ✅ All verification tasks pass
9. ✅ No leftover containers after test

### Failure Modes to Catch
- Missing authentication causes publish failure
- Wrong compression type
- Tag name mismatches
- Registry connection failures
- File not saved with correct extension

## Documentation Requirements

### Header Comments in build.gradle
Document test coverage in detail:
```groovy
/**
 * Integration test that tests:
 *   - Build Image Features
 *       - number of images = 1
 *       - build image + follow-on save and publish
 *       - naming style: Repository Mode: repository, 2 tags
 *       - build args
 *           - number of build args > 1
 *           - number of build args w/ putAll > 1
 *       - labels
 *           - number of labels > 1
 *           - number of labels w/ putAll > 1
 *       - dockerfile = default
 *   - Tag Features
 *       - number of tags > 1
 *   - Save Features
 *       - save compression = xz
 *   - Publish Features
 *       - publish same image > 1 tags, same registry
 *       - publish to private registry w/ authentication
 *       - Repository Mode: registry, repository, 2 tags
 */
```

### Inline Comments
- Explain repository mode naming convention
- Explain putAll usage for build args and labels
- Explain XZ compression choice
- Explain authentication configuration
- Explain tag override in publish (edge/test vs 1.0.0/latest)

## Files to Create

1. **scenario-6/build.gradle** (main test configuration, ~220 lines)
2. **scenario-6/settings.gradle** (~28 lines)
3. **scenario-6/gradle.properties** (~6 lines)
4. **scenario-6/gradle/libs.versions.toml** (~15 lines)
5. **scenario-6/src/main/docker/Dockerfile** (copy from scenario-1, ~32 lines)
6. **scenario-6/gradlew** (wrapper script, copy from scenario-1)
7. **scenario-6/gradle/wrapper/gradle-wrapper.properties** (copy from scenario-1)

## Implementation Notes

This is a **PLAN ONLY** - implementation will follow after approval.

The plan covers:

✅ **Test purpose**: Verify repository mode, putAll methods, XZ compression, authenticated registry publishing

✅ **Coverage gaps filled**: 6 items from the README matrix

✅ **De-confliction**: Unique image names (scenario-6/*), unique port (5060), unique save file

✅ **Gradle 9/10 compatibility**: Provider API, configuration cache, build cache

✅ **Verification strategy**: Build, save file, and registry verification tasks

✅ **Documentation**: Comprehensive comments explaining configuration choices

✅ **Integration workflow**: Clean → Build → Save → Publish → Verify → Cleanup

This plan is ready for implementation once approved.
