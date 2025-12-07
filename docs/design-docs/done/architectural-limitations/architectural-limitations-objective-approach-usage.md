# Option C: Pipeline/Workflow DSL - Complete Usage Examples

This document demonstrates the proposed **Pipeline/Workflow DSL** (Option C) for addressing the architectural
limitations identified in `architectural-limitations-analysis.md`.

**Status**: Design proposal, not yet implemented

**Related**: `docs/design-docs/todo/architectural-limitations-analysis.md`

---

## Implementation Approach Summary

### Three-DSL Architecture

The solution introduces a **third DSL** (`dockerWorkflows`) that orchestrates the existing two:

1. **`docker` DSL** - Image definition (build configuration) - **EXISTING**
2. **`dockerOrch` DSL** - Test stack definition (compose configuration) - **EXISTING**
3. **`dockerWorkflows` DSL** - Pipeline orchestration - **NEW**

### Reuse Pattern

**The workflow DSL references existing DSLs:**
- `build { image = docker.images.webApp }` → reuses docker DSL definition
- `test { stack = dockerOrch.composeStacks.webAppTest }` → reuses dockerOrch DSL definition

**The workflow DSL replicates conditional APIs:**
- `addTags()` - mirrors `docker` tag API
- `save {}` - mirrors `docker` save API
- `publish {}` - mirrors `docker` publish API

**Implementation strategy:**
- Underlying code (services, task generation) **mostly reused** from existing `docker` DSL
- Workflow DSL adds:
  - Conditional execution logic (`onTestSuccess` / `onTestFailure`)
  - Automatic task dependency management
  - Pipeline-level orchestration

### Trade-off

**API duplication** (save, publish appear in both `docker` and `dockerWorkflows`)

**versus**

**Workflow semantics** (build → test → conditional publish)

**This is reasonable because:**
- Code reuse remains high (same services/tasks underneath)
- Users get clear, declarative workflow semantics
- Backward compatible (can use `docker` alone, or with `dockerWorkflows`)
- Separates concerns: image definition vs pipeline execution

### Plugin-Specific Features

**`always` and `cleanup` blocks:**
- **Plugin-specific**, not Gradle built-in
- Mimics Gradle's `finalizedBy` but at workflow level
- Provides custom cleanup logic (remove containers, images, etc.)

**Generated tasks:**
- `runMyPipeline` - Master pipeline task
- `buildMyPipeline` - Build step
- `testMyPipeline` - Test step
- `publishMyPipeline` - Conditional publish step

---

## Table of Contents

1. [Overview](#overview)
2. [Basic Example](#basic-example)
3. [Complete Example](#complete-example)
4. [Multiple Pipelines](#multiple-pipelines)
5. [Configuration Options](#configuration-options)
6. [CI/CD Integration](#cicd-integration)
7. [Benefits](#benefits)

---

## Overview

The **Pipeline/Workflow DSL** introduces a new top-level `dockerWorkflows` block that orchestrates the complete CI/CD
pipeline: build → test → conditional publish.

### Key Concepts

- **Separation of Concerns**: `docker`, `dockerOrch`, and `dockerWorkflows` remain independent
- **Declarative**: Express "what" not "how"
- **Conditional Logic**: Built-in support for "only if tests pass"
- **Multiple Pipelines**: Support dev, staging, production in one build file
- **Backward Compatible**: Existing DSLs unchanged

### Architecture

```
docker DSL          →  Defines what to build (image configuration)
      ↓
dockerOrch DSL      →  Defines what to test (test stack configuration)
      ↓
dockerWorkflows DSL →  Defines how to orchestrate (pipeline execution)
```

---

## Basic Example

### Minimal Pipeline Configuration

```groovy
plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker' version '2.0.0'
}

// STEP 1: Define image
docker {
    images {
        myApp {
            imageName = 'my-app'
            tags = ['latest']
            contextTask = tasks.register('prepareContext', Copy) {
                into layout.buildDirectory.dir('docker-context')
                from('src/main/docker')
            }
        }
    }
}

// STEP 2: Define test stack
dockerOrch {
    composeStacks {
        myAppTest {
            files.from('src/integrationTest/resources/compose/app.yml')
            waitForHealthy {
                waitForServices.set(['my-app'])
            }
        }
    }
}

// STEP 3: Define pipeline
dockerWorkflows {
    ciPipeline {
        build {
            image = docker.images.myApp
        }

        test {
            stack = dockerOrch.composeStacks.myAppTest
            testTask = tasks.named('integrationTest')
        }

        onTestSuccess {
            publish {
                to('production') {
                    registry.set('ghcr.io')
                    repository.set('mycompany/my-app')
                    publishTags.set(['latest'])
                }
            }
        }
    }
}
```

**Usage:**
```bash
./gradlew runCiPipeline
```

**Generated Task Flow:**
```
runCiPipeline
  ├─> dockerBuildMyApp
  ├─> composeUpMyAppTest
  ├─> integrationTest
  ├─> composeDownMyAppTest
  └─> [IF TESTS PASS] dockerPublishMyAppProduction
```

---

## Complete Example

### Web Application with Full CI/CD Pipeline

```groovy
plugins {
    id 'groovy'
    id 'com.kineticfire.gradle.docker' version '2.0.0'
}

repositories {
    mavenCentral()
}

// Get JAR file from app subproject
def jarFileProvider = project(':app').tasks.named('bootJar').flatMap { it.archiveFile }
def jarFileNameProvider = jarFileProvider.map { it.asFile.name }

// ============================================================================
// STEP 1: Define Docker Image (Build Configuration)
// ============================================================================
docker {
    images {
        webApp {
            imageName = 'my-web-app'
            tags = ['latest', 'dev']  // Initial tags for development

            contextTask = tasks.register('prepareWebAppContext', Copy) {
                into layout.buildDirectory.dir('docker-context/webApp')
                from('src/main/docker')
                from(jarFileProvider) {
                    rename { jarFileNameProvider.get() }
                }
                dependsOn project(':app').tasks.named('bootJar')
            }

            buildArgs.put('JAR_FILE', jarFileNameProvider)

            // NOTE: No publish/save here - handled by pipeline
        }
    }
}

// ============================================================================
// STEP 2: Define Test Stack (Testing Configuration)
// ============================================================================
dockerOrch {
    composeStacks {
        webAppTest {
            files.from('src/integrationTest/resources/compose/web-app.yml')
            projectName = "my-web-app-test"

            waitForHealthy {
                waitForServices.set(['web-app', 'postgres'])
                timeoutSeconds.set(90)
                pollSeconds.set(2)
            }

            logs {
                outputFile.set(layout.buildDirectory.file("compose-logs/web-app-test.log"))
                tailLines.set(1000)
            }
        }
    }
}

// ============================================================================
// STEP 3: Define CI/CD Pipeline (Workflow Orchestration)
// ============================================================================
dockerWorkflows {
    webAppPipeline {
        description = 'Complete CI/CD pipeline: build → test → publish'

        // Step 1: Build the image
        build {
            image = docker.images.webApp

            // Optional: Custom build behavior
            beforeBuild {
                // Pre-build validation, cleanup, etc.
                logger.lifecycle("Starting build for ${image.imageName.get()}...")
            }

            afterBuild {
                logger.lifecycle("Build complete: ${image.imageName.get()}:${image.tags.get().join(', ')}")
            }
        }

        // Step 2: Test the image
        test {
            stack = dockerOrch.composeStacks.webAppTest
            testTask = tasks.named('integrationTest')

            // Optional: Custom test behavior
            beforeTest {
                logger.lifecycle("Starting integration tests...")
            }

            afterTest { testResult ->
                if (testResult.success) {
                    logger.lifecycle("✅ All tests passed!")
                } else {
                    logger.error("❌ Tests failed - stopping pipeline")
                }
            }

            // Optional: Test timeout
            timeoutMinutes = 15
        }

        // Step 3: Conditional operations (only if tests pass)
        onTestSuccess {
            // Add production tags
            addTags(['stable', 'production', "${project.version}"])

            // Save image to tar file
            save {
                compression = docker.saveCompression.GZIP
                outputFile = layout.buildDirectory.file("images/my-web-app-${project.version}.tar.gz")
            }

            // Publish to staging registry
            publish {
                to('staging') {
                    registry.set('staging-registry.company.com')
                    namespace.set('myteam')
                    imageName.set('my-web-app')
                    publishTags.set(['latest', 'stable'])

                    auth {
                        username.set(providers.environmentVariable('STAGING_REGISTRY_USER'))
                        password.set(providers.environmentVariable('STAGING_REGISTRY_TOKEN'))
                    }
                }

                to('production') {
                    registry.set('ghcr.io')
                    repository.set('mycompany/my-web-app')
                    publishTags.set(['production', project.version.toString()])

                    auth {
                        username.set(providers.environmentVariable('GHCR_USER'))
                        password.set(providers.environmentVariable('GHCR_TOKEN'))
                    }

                    // Optional: Only publish to production from main branch
                    condition {
                        onlyIf {
                            System.getenv('CI_BRANCH') == 'main'
                        }
                    }
                }
            }

            // Optional: Custom success actions
            afterSuccess {
                logger.lifecycle("🎉 Pipeline completed successfully!")
                logger.lifecycle("   - Image built: my-web-app:${project.version}")
                logger.lifecycle("   - Tests passed")
                logger.lifecycle("   - Published to staging and production")
            }
        }

        // Step 4: Conditional operations (only if tests fail)
        onTestFailure {
            // Optional: Failure handling
            saveFailureLogs {
                composeLogsDir = layout.buildDirectory.dir("compose-logs/failures")
                includeServices = ['web-app', 'postgres']
            }

            // Optional: Tag with failure marker
            addTags(['failed-build'])

            afterFailure {
                logger.error("❌ Pipeline failed - check logs in build/compose-logs/failures/")
            }
        }

        // Optional: Always run (regardless of test result)
        always {
            // Cleanup, notifications, etc.
            cleanup {
                removeTestContainers = true
                keepFailedContainers = false
            }
        }
    }
}

// ============================================================================
// Integration Test Configuration
// ============================================================================
dependencies {
    integrationTestImplementation "com.kineticfire.gradle:gradle-docker:${project.findProperty('plugin_version')}"
    integrationTestImplementation 'org.spockframework:spock-core:2.3'
    integrationTestImplementation 'io.rest-assured:rest-assured:5.3.0'
}

tasks.named('integrationTest') {
    // Pipeline automatically configures this via workflow
    useJUnitPlatform()
    outputs.cacheIf { false }
}

// ============================================================================
// USAGE: Run the complete pipeline
// ============================================================================
// ./gradlew runWebAppPipeline
//
// This automatically:
// 1. Builds the image (dockerBuildWebApp)
// 2. Starts test stack (composeUpWebAppTest)
// 3. Runs integration tests (integrationTest)
// 4. IF TESTS PASS:
//    - Adds tags: stable, production, <version>
//    - Saves to: build/images/my-web-app-<version>.tar.gz
//    - Publishes to staging registry
//    - Publishes to production registry (only from main branch)
// 5. IF TESTS FAIL:
//    - Saves failure logs
//    - Tags with: failed-build
//    - Stops pipeline
// 6. ALWAYS:
//    - Stops and removes test containers (composeDownWebAppTest)
//    - Cleans up temporary resources
```

### Generated Pipeline Task

**Command:**
```bash
./gradlew runWebAppPipeline
```

**Task Dependency Graph:**
```
runWebAppPipeline
  ├─> buildWebAppPipeline (Step 1: Build)
  │     └─> dockerBuildWebApp
  │           └─> prepareWebAppContext
  │                 └─> :app:bootJar
  │
  ├─> testWebAppPipeline (Step 2: Test)
  │     ├─> composeUpWebAppTest
  │     │     └─> buildWebAppPipeline (ensures image is built)
  │     ├─> integrationTest
  │     │     └─> composeUpWebAppTest (ensures stack is up)
  │     └─> composeDownWebAppTest (finalizer)
  │
  └─> [CONDITIONAL] publishWebAppPipeline (Step 3: Conditional Publish)
        ├─> dockerTagWebAppProduction (adds tags)
        ├─> dockerSaveWebApp (saves tar)
        ├─> dockerPublishWebAppStaging
        └─> dockerPublishWebAppProduction
              └─> [CONDITIONAL] onlyIf { CI_BRANCH == 'main' }
```

**Output:**
```
> Task :prepareWebAppContext
> Task :app:bootJar
> Task :dockerBuildWebApp
Starting build for my-web-app...
Build complete: my-web-app:latest, dev

> Task :composeUpWebAppTest
> Task :integrationTest
Starting integration tests...
✅ All tests passed!

> Task :dockerTagWebAppProduction
> Task :dockerSaveWebApp
> Task :dockerPublishWebAppStaging
> Task :dockerPublishWebAppProduction
🎉 Pipeline completed successfully!
   - Image built: my-web-app:1.0.0
   - Tests passed
   - Published to staging and production

> Task :composeDownWebAppTest

BUILD SUCCESSFUL in 2m 15s
```

---

## Multiple Pipelines

### Multiple Pipelines for Different Environments

```groovy
dockerWorkflows {
    // ========================================================================
    // Development pipeline (quick feedback)
    // ========================================================================
    devPipeline {
        description = 'Fast feedback for local development'

        build {
            image = docker.images.webApp
        }

        test {
            stack = dockerOrch.composeStacks.webAppTest
            testTask = tasks.named('integrationTest')
        }

        onTestSuccess {
            addTags(['dev-latest'])
            // No publish - dev only

            afterSuccess {
                logger.lifecycle("✅ Dev build successful - ready for local testing")
            }
        }
    }

    // ========================================================================
    // Staging pipeline
    // ========================================================================
    stagingPipeline {
        description = 'Deploy to staging environment'

        build {
            image = docker.images.webApp
        }

        test {
            stack = dockerOrch.composeStacks.webAppTest
            testTask = tasks.named('integrationTest')
        }

        onTestSuccess {
            addTags(['staging', "rc-${project.version}"])

            publish {
                to('staging') {
                    registry.set('staging-registry.company.com')
                    namespace.set('myteam')
                    imageName.set('my-web-app')
                    publishTags.set(['latest', 'staging'])

                    auth {
                        username.set(providers.environmentVariable('STAGING_REGISTRY_USER'))
                        password.set(providers.environmentVariable('STAGING_REGISTRY_TOKEN'))
                    }
                }
            }

            afterSuccess {
                logger.lifecycle("✅ Deployed to staging: staging-registry.company.com/myteam/my-web-app:staging")
            }
        }
    }

    // ========================================================================
    // Production pipeline (full validation)
    // ========================================================================
    productionPipeline {
        description = 'Deploy to production with full validation'

        build {
            image = docker.images.webApp
        }

        test {
            stack = dockerOrch.composeStacks.webAppTest
            testTask = tasks.named('integrationTest')
            timeoutMinutes = 20
        }

        // Optional: Additional validation steps
        securityScan {
            tool = 'trivy'
            failOnCritical = true
            reportFile = layout.buildDirectory.file('security/trivy-report.json')
        }

        onTestSuccess {
            addTags(['production', project.version.toString()])

            save {
                compression = docker.saveCompression.GZIP
                outputFile = layout.buildDirectory.file("releases/my-web-app-${project.version}.tar.gz")
            }

            publish {
                to('production') {
                    registry.set('ghcr.io')
                    repository.set('mycompany/my-web-app')
                    publishTags.set(['production', 'latest', project.version.toString()])

                    auth {
                        username.set(providers.environmentVariable('GHCR_USER'))
                        password.set(providers.environmentVariable('GHCR_TOKEN'))
                    }

                    // Only publish from main branch
                    condition {
                        onlyIf {
                            System.getenv('CI_BRANCH') == 'main'
                        }
                    }
                }
            }

            // Optional: Create release artifacts
            createRelease {
                releaseNotes = file('CHANGELOG.md')
                artifacts = [
                    layout.buildDirectory.file("releases/my-web-app-${project.version}.tar.gz")
                ]
            }

            afterSuccess {
                logger.lifecycle("🎉 Production deployment successful!")
                logger.lifecycle("   Version: ${project.version}")
                logger.lifecycle("   Registry: ghcr.io/mycompany/my-web-app")
                logger.lifecycle("   Tags: production, latest, ${project.version}")
            }
        }

        onTestFailure {
            saveFailureLogs {
                composeLogsDir = layout.buildDirectory.dir("compose-logs/failures")
            }

            afterFailure {
                logger.error("❌ Production deployment FAILED - tests did not pass")
            }
        }
    }
}
```

**Usage:**
```bash
# Development workflow (local)
./gradlew runDevPipeline

# Staging workflow (CI - feature branch)
./gradlew runStagingPipeline

# Production workflow (CI - main branch only)
./gradlew runProductionPipeline
```

**Expected Output:**

**Development:**
```
> Task :runDevPipeline
✅ Dev build successful - ready for local testing
BUILD SUCCESSFUL in 45s
```

**Staging:**
```
> Task :runStagingPipeline
✅ Deployed to staging: staging-registry.company.com/myteam/my-web-app:staging
BUILD SUCCESSFUL in 1m 30s
```

**Production:**
```
> Task :runProductionPipeline
🎉 Production deployment successful!
   Version: 1.2.3
   Registry: ghcr.io/mycompany/my-web-app
   Tags: production, latest, 1.2.3
BUILD SUCCESSFUL in 2m 45s
```

---

## Configuration Options

### Complete Configuration Reference

```groovy
dockerWorkflows {
    myPipeline {
        // Optional: Pipeline description
        description = 'Pipeline description for documentation'

        // ====================================================================
        // Build Configuration
        // ====================================================================
        build {
            // Required: Image reference
            image = docker.images.myApp

            // Optional: Build in parallel (if multiple images)
            parallel = false

            // Optional: Docker build cache
            cacheFrom = ['myapp:latest']

            // Optional: Build arguments (override image buildArgs)
            buildArgs = [
                'CUSTOM_ARG': 'value'
            ]

            // Optional: Before build hook
            beforeBuild {
                logger.lifecycle("Pre-build actions...")
            }

            // Optional: After build hook
            afterBuild {
                logger.lifecycle("Post-build actions...")
            }
        }

        // ====================================================================
        // Test Configuration
        // ====================================================================
        test {
            // Required: Stack reference
            stack = dockerOrch.composeStacks.myTest

            // Required: Test task
            testTask = tasks.named('integrationTest')

            // Optional: Test timeout
            timeoutMinutes = 10

            // Optional: Retry on failure
            retryOnFailure = 2

            // Optional: Before test hook
            beforeTest {
                logger.lifecycle("Pre-test actions...")
            }

            // Optional: After test hook (receives TestResult)
            afterTest { testResult ->
                logger.lifecycle("Tests ${testResult.success ? 'passed' : 'failed'}")
            }
        }

        // ====================================================================
        // Optional: Security Scan
        // ====================================================================
        securityScan {
            tool = 'trivy'  // or 'grype', 'snyk'
            failOnCritical = true
            failOnHigh = false
            reportFile = layout.buildDirectory.file('security/scan-report.json')
        }

        // ====================================================================
        // Success Path (only if tests pass)
        // ====================================================================
        onTestSuccess {
            // Add additional tags
            addTags(['tag1', 'tag2', providers.provider { project.version.toString() }])

            // Save image to tar
            save {
                compression = docker.saveCompression.GZIP
                outputFile = layout.buildDirectory.file('images/app.tar.gz')
            }

            // Publish to registries
            publish {
                to('registry1') {
                    registry.set('registry1.example.com')
                    repository.set('myteam/myapp')
                    publishTags.set(['latest'])

                    auth {
                        username.set(providers.environmentVariable('REG1_USER'))
                        password.set(providers.environmentVariable('REG1_TOKEN'))
                    }

                    // Optional: Conditional publish
                    condition {
                        onlyIf {
                            System.getenv('DEPLOY_TO_REG1') == 'true'
                        }
                    }
                }

                to('registry2') {
                    registry.set('registry2.example.com')
                    repository.set('myteam/myapp')
                    publishTags.set(['stable'])
                }
            }

            // Optional: Create release
            createRelease {
                releaseNotes = file('CHANGELOG.md')
                artifacts = [
                    layout.buildDirectory.file('images/app.tar.gz')
                ]
            }

            // Optional: After success hook
            afterSuccess {
                logger.lifecycle("Pipeline succeeded!")
            }
        }

        // ====================================================================
        // Failure Path (only if tests fail)
        // ====================================================================
        onTestFailure {
            // Save failure logs
            saveFailureLogs {
                composeLogsDir = layout.buildDirectory.dir('compose-logs/failures')
                includeServices = ['app', 'db']
            }

            // Add failure tag
            addTags(['failed-build'])

            // Optional: After failure hook
            afterFailure {
                logger.error("Pipeline failed!")
            }
        }

        // ====================================================================
        // Always Execute (regardless of test result)
        // ====================================================================
        always {
            // Cleanup
            cleanup {
                removeTestContainers = true
                keepFailedContainers = false
                cleanupImages = false
            }

            // Send notifications
            notify {
                slack {
                    webhookUrl = providers.environmentVariable('SLACK_WEBHOOK')
                    channel = '#ci-notifications'
                }

                email {
                    to = ['team@example.com']
                    subject = "Build ${project.version} - ${testResult.success ? 'SUCCESS' : 'FAILED'}"
                }
            }
        }
    }
}
```

---

## CI/CD Integration

### GitHub Actions Example

**.github/workflows/ci.yml:**
```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:

env:
  JAVA_VERSION: '21'
  GRADLE_VERSION: '9.0'

jobs:
  # Development builds (feature branches, PRs)
  dev:
    runs-on: ubuntu-latest
    if: github.ref != 'refs/heads/main' && github.ref != 'refs/heads/develop'
    steps:
      - name: Checkout
        uses: actions/checkout@v3

      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'

      - name: Run Dev Pipeline
        run: ./gradlew runDevPipeline

      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-results-dev
          path: build/test-results/

  # Staging builds (develop branch)
  staging:
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/develop'
    steps:
      - name: Checkout
        uses: actions/checkout@v3

      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'

      - name: Run Staging Pipeline
        run: ./gradlew runStagingPipeline
        env:
          STAGING_REGISTRY_USER: ${{ secrets.STAGING_REGISTRY_USER }}
          STAGING_REGISTRY_TOKEN: ${{ secrets.STAGING_REGISTRY_TOKEN }}

      - name: Upload Artifacts
        uses: actions/upload-artifact@v3
        with:
          name: staging-artifacts
          path: build/images/

  # Production builds (main branch)
  production:
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - name: Checkout
        uses: actions/checkout@v3

      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'

      - name: Run Production Pipeline
        run: ./gradlew runProductionPipeline
        env:
          GHCR_USER: ${{ secrets.GHCR_USER }}
          GHCR_TOKEN: ${{ secrets.GHCR_TOKEN }}
          CI_BRANCH: ${{ github.ref_name }}

      - name: Create Release
        if: startsWith(github.ref, 'refs/tags/')
        uses: softprops/action-gh-release@v1
        with:
          files: build/releases/*.tar.gz
          body_path: CHANGELOG.md
```

### GitLab CI Example

**.gitlab-ci.yml:**
```yaml
stages:
  - build
  - test
  - deploy

variables:
  JAVA_VERSION: "21"

# Development builds
dev:build:
  stage: build
  image: gradle:9.0-jdk21
  script:
    - ./gradlew runDevPipeline
  only:
    - branches
  except:
    - main
    - develop
  artifacts:
    reports:
      junit: build/test-results/**/*.xml

# Staging builds
staging:deploy:
  stage: deploy
  image: gradle:9.0-jdk21
  script:
    - ./gradlew runStagingPipeline
  environment:
    name: staging
    url: https://staging.example.com
  only:
    - develop
  artifacts:
    paths:
      - build/images/

# Production builds
production:deploy:
  stage: deploy
  image: gradle:9.0-jdk21
  script:
    - ./gradlew runProductionPipeline
  environment:
    name: production
    url: https://example.com
  only:
    - main
  when: manual  # Require manual approval
  artifacts:
    paths:
      - build/releases/
```

---

## Benefits

### 1. Clear Pipeline Semantics

The workflow explicitly models the CI/CD pipeline:
```
Build → Test → Conditional Publish
```

### 2. Declarative Configuration

Express "what" you want, not "how" to do it:

**Before (imperative):**
```groovy
afterEvaluate {
    tasks.named('composeUpMyTest') {
        dependsOn tasks.named('dockerBuildMyApp')
    }
    tasks.named('dockerPublishMyApp') {
        dependsOn tasks.named('integrationTest')
        onlyIf {
            tasks.named('integrationTest').get().state.failure == null
        }
    }
}
```

**After (declarative):**
```groovy
dockerWorkflows {
    pipeline {
        build { image = docker.images.myApp }
        test { stack = dockerOrch.composeStacks.myTest }
        onTestSuccess {
            publish { to('prod') { /* ... */ } }
        }
    }
}
```

### 3. Separation of Concerns

Each DSL has a clear responsibility:

| DSL | Responsibility | Answers |
|-----|----------------|---------|
| `docker` | Image definition | What to build? |
| `dockerOrch` | Test stack definition | What to test? |
| `dockerWorkflows` | Pipeline orchestration | How to orchestrate? |

### 4. Conditional Logic Built-In

No need for manual task configuration:

```groovy
onTestSuccess {
    addTags(['stable'])
    publish { /* ... */ }
}

onTestFailure {
    saveFailureLogs { /* ... */ }
}
```

### 5. Multiple Pipelines

Support different workflows in one build file:

```groovy
dockerWorkflows {
    devPipeline { /* fast feedback */ }
    stagingPipeline { /* deploy to staging */ }
    productionPipeline { /* full validation + production */ }
}
```

### 6. Extensible

Easy to add new pipeline features:

```groovy
dockerWorkflows {
    pipeline {
        build { /* ... */ }
        test { /* ... */ }
        securityScan { /* NEW */ }
        performanceTest { /* NEW */ }
        onTestSuccess { /* ... */ }
    }
}
```

### 7. Backward Compatible

Existing `docker` and `dockerOrch` DSLs remain unchanged. Users can:
- Keep using current approach
- Migrate incrementally to workflows
- Mix both approaches

---

## Implementation Notes

### Task Generation

The `dockerWorkflows` DSL would generate pipeline tasks:

```
dockerWorkflows {
    myPipeline { /* ... */ }
}

// Generates:
// - runMyPipeline           (master task)
// - buildMyPipeline         (build step)
// - testMyPipeline          (test step)
// - publishMyPipeline       (conditional publish)
```

### Error Handling

Pipeline execution stops on first failure:

```
Build FAILED → Stop (no tests run)
Build SUCCESS → Tests FAILED → Stop (no publish)
Build SUCCESS → Tests SUCCESS → Publish (conditional)
```

### Gradle Configuration Cache

All pipeline configuration must be compatible with Gradle's configuration cache:
- Use `Provider` API throughout
- No eager evaluation during configuration
- Serialize pipeline state correctly

---

## Related Documents

- **Analysis**: `docs/design-docs/todo/architectural-limitations-analysis.md`
- **Current Usage**: `docs/usage/usage-docker.md`, `docs/usage/usage-docker-orch.md`
- **Integration Examples**: `plugin-integration-test/dockerOrch/examples/`

## Version Information

- **Proposed for**: Version 2.0.0 (breaking changes)
- **Alternative**: Option D (convention-based auto-wiring) for 1.x
- **Status**: Design proposal, not yet implemented
