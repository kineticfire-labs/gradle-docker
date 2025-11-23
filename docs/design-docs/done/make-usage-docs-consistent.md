# Plan: Make Usage Documentation Consistent

**Status:** ✅ Completed
**Created:** 2025-11-22
**Completed:** 2025-11-23
**Priority:** High
**Estimated Effort:** 4-6 hours
**Actual Effort:** ~6 hours

---

## Context

This plan addresses consistency issues identified between `docs/usage/usage-docker.md` and
`docs/usage/usage-docker-orch.md` to improve maintainability, navigation, and user experience.

**Related Documents:**
- `docs/usage/usage-docker.md` - Docker DSL guide (build, tag, save, publish)
- `docs/usage/usage-docker-orch.md` - Docker Orchestration DSL guide (testing with compose)
- `docs/usage/gradle-9-and-10-compatibility-practices.md` - Gradle 9/10 compatibility guide
- `docs/usage/spock-junit-test-extensions.md` - Test framework extensions guide

---

## Issues Addressed

1. **Missing Cross-References**: usage-docker-orch.md doesn't reference usage-docker.md
2. **Missing Forward References**: Integration test convention not clearly signposted in usage-docker.md
3. **Provider API Duplication**: Pattern examples scattered across documents
4. **Missing Troubleshooting**: usage-docker.md lacks troubleshooting section
5. **Missing Configuration Cache Help**: usage-docker.md lacks config cache troubleshooting
6. **Unclear afterEvaluate Guidance**: Design doc appears to contradict usage examples
7. **No Documentation Index**: No overview/map of all usage documentation

---

## Implementation Plan

### Phase 1: Cross-References and Navigation (Immediate Priority)

#### Task 1.1: Add Cross-Reference in usage-docker-orch.md
**File:** `docs/usage/usage-docker-orch.md`
**Location:** After "Overview of Docker Compose Orchestration" section (approximately line 99)
**Action:** Add bidirectional cross-reference to usage-docker.md

**Content to Add:**
```markdown
**Building Docker Images:** This guide focuses on testing images with Docker Compose orchestration.
For building, tagging, saving, and publishing images, see [Docker DSL Guide](usage-docker.md).
```

**Rationale:** Users reading docker-orch docs see examples using `docker` DSL but have no explicit
pointer to the docker DSL documentation. This creates a clear navigation path.

---

#### Task 1.2: Add Integration Test Convention Forward Reference
**File:** `docs/usage/usage-docker.md`
**Location:** After brief integration test mention (approximately line 243)
**Action:** Add forward reference to comprehensive guide in usage-docker-orch.md

**Content to Add:**
```markdown
## Integration Test Support

The plugin automatically creates the `integrationTest` source set when java or groovy plugin is applied.
This allows you to add integration test dependencies without boilerplate:

```groovy
dependencies {
    integrationTestImplementation 'org.spockframework:spock-core:2.3'
}
```

**For complete details** on directory structure, task configuration, customization, and migration from
manual setup, see [Integration Test Source Set Convention](usage-docker-orch.md#integration-test-source-set-convention)
in the Docker Orchestration guide.
```

**Rationale:** Docker DSL users get enough information to start, with clear path to comprehensive
documentation when needed. Follows principle of progressive disclosure.

---

### Phase 2: Provider API Patterns Document (High Priority)

#### Task 2.1: Create Provider Patterns Reference Document
**File:** `docs/usage/provider-patterns.md` (new file)
**Action:** Extract and consolidate Provider API patterns from usage-docker.md and design docs

**Document Structure:**
```markdown
# Gradle Provider API Patterns for Configuration Cache

Essential patterns for configuration-cache compatible build scripts with the gradle-docker plugin.

## Why Use Provider API?

The Provider API enables:
- **Configuration cache** (Gradle 9/10 requirement)
- **Lazy evaluation** - values resolved at execution time, not configuration time
- **Performance** - avoids unnecessary work during configuration

**Golden Rule:** Never call `.get()` during configuration phase (outside of task actions).

---

## Pattern 1: Task Output as Provider

**Use Case:** Get JAR file from task output for Docker build context

**Example:**
```groovy
// Get JAR file from task output
def jarFileProvider = project(':app').tasks.named('bootJar').flatMap { it.archiveFile }

// Transform provider value
def jarFileNameProvider = jarFileProvider.map { it.asFile.name }

// Use in buildArgs
docker {
    images {
        myApp {
            buildArgs.put('JAR_FILE', jarFileNameProvider)
        }
    }
}
```

**Key Points:**
- `tasks.named()` returns `TaskProvider<T>`
- `.flatMap()` extracts output from task
- `.map()` transforms provider value
- No `.get()` called during configuration

---

## Pattern 2: Environment Variables

**Use Case:** Read credentials or configuration from environment

**Example:**
```groovy
docker {
    images {
        myApp {
            publish {
                to('ghcr') {
                    registry.set("ghcr.io")
                    auth {
                        username.set(providers.environmentVariable("GHCR_USER"))
                        password.set(providers.environmentVariable("GHCR_TOKEN"))
                    }
                }
            }
        }
    }
}
```

**Key Points:**
- `providers.environmentVariable()` returns `Provider<String>`
- Evaluation deferred until execution time
- Use `.orElse()` for defaults: `providers.environmentVariable("VAR").orElse("default")`

---

## Pattern 3: Project Properties

**Use Case:** Use project version or custom properties

**Example:**
```groovy
docker {
    images {
        myApp {
            // Use project version as provider
            version.set(providers.provider { project.version.toString() })

            // Custom property
            buildArgs.put('BUILD_DATE', providers.provider {
                new Date().format('yyyy-MM-dd')
            })
        }
    }
}
```

**Key Points:**
- `providers.provider { }` wraps dynamic computation
- Closure evaluated at execution time
- Use for any dynamic value that changes between builds

---

## Pattern 4: File Paths with Layout API

**Use Case:** Reference build directory or project files

**Example:**
```groovy
docker {
    images {
        myApp {
            contextTask = tasks.register('prepareContext', Copy) {
                // Use layout API for build directory
                into layout.buildDirectory.dir('docker-context')

                // Project directory for source files
                from layout.projectDirectory.dir('src/main/docker')
            }

            save {
                // Output file under build directory
                outputFile.set(layout.buildDirectory.file('docker/my-app.tar'))
            }
        }
    }
}
```

**Key Points:**
- `layout.buildDirectory` is a `DirectoryProperty`
- `.dir()` and `.file()` return providers
- Avoids string concatenation of paths
- Relocatable across machines (configuration cache safe)

---

## Pattern 5: Provider Composition

**Use Case:** Combine multiple providers

**Example:**
```groovy
// Chain transformations
def jarFileProvider = project(':app').tasks.named('bootJar')
    .flatMap { it.archiveFile }
    .map { it.asFile.name }

// Combine providers with zip
def imageTag = providers.zip(
    providers.provider { project.name },
    providers.provider { project.version.toString() }
) { name, version -> "${name}:${version}" }
```

**Key Points:**
- `.flatMap()` for provider-returning transformations
- `.map()` for value transformations
- `.zip()` combines multiple providers

---

## Pattern 6: Value Sources for External State

**Use Case:** Read git commit SHA, timestamps, or external system state

**Example:**
```groovy
// Define value source
abstract class GitCommitValueSource implements ValueSource<String, ValueSourceParameters.None> {
    @Override
    String obtain() {
        'git rev-parse --short HEAD'.execute().text.trim()
    }
}

// Use in build script
docker {
    images {
        myApp {
            labels.put(
                "org.opencontainers.image.revision",
                providers.of(GitCommitValueSource) {}
            )
        }
    }
}
```

**Key Points:**
- Value sources are configuration-cache safe
- Use for external commands, file reads, network calls
- Gradle caches and serializes the result

---

## Anti-Patterns (What NOT to Do)

### ❌ Anti-Pattern 1: Eager Evaluation with .get()

**Problem:**
```groovy
docker {
    images {
        myApp {
            // BAD - calls .get() during configuration
            def jarFile = project(':app').tasks.named('jar').get().archiveFile.get()
            buildArgs.put('JAR', jarFile.asFile.name)
        }
    }
}
```

**Why Bad:**
- Forces task execution during configuration
- Breaks configuration cache
- Causes unnecessary work

**Solution:**
```groovy
docker {
    images {
        myApp {
            // GOOD - use provider chain
            def jarFileName = project(':app').tasks.named('jar')
                .flatMap { it.archiveFile }
                .map { it.asFile.name }
            buildArgs.put('JAR', jarFileName)
        }
    }
}
```

---

### ❌ Anti-Pattern 2: Capturing Project References

**Problem:**
```groovy
docker {
    images {
        myApp {
            // BAD - captures project reference
            buildArgs.put('VERSION', project.version.toString())
        }
    }
}
```

**Why Bad:**
- Captures `Project` instance in configuration cache
- Prevents configuration cache reuse

**Solution:**
```groovy
docker {
    images {
        myApp {
            // GOOD - use provider
            buildArgs.put('VERSION', providers.provider { project.version.toString() })
        }
    }
}
```

---

### ❌ Anti-Pattern 3: String Concatenation for Paths

**Problem:**
```groovy
docker {
    images {
        myApp {
            contextTask = tasks.register('prepareContext', Copy) {
                // BAD - string concatenation
                into "${project.buildDir}/docker-context"
            }
        }
    }
}
```

**Why Bad:**
- Hard-coded paths not relocatable
- Breaks configuration cache on different machines
- Not compatible with Gradle's build directory tracking

**Solution:**
```groovy
docker {
    images {
        myApp {
            contextTask = tasks.register('prepareContext', Copy) {
                // GOOD - use layout API
                into layout.buildDirectory.dir('docker-context')
            }
        }
    }
}
```

---

### ❌ Anti-Pattern 4: Reading Environment During Configuration

**Problem:**
```groovy
docker {
    images {
        myApp {
            // BAD - reads env during configuration
            def token = System.getenv('REGISTRY_TOKEN')
            publish {
                to('registry') {
                    auth {
                        password.set(token)
                    }
                }
            }
        }
    }
}
```

**Why Bad:**
- Reads external state during configuration
- Not tracked by configuration cache
- Value baked into cache, won't update if env changes

**Solution:**
```groovy
docker {
    images {
        myApp {
            // GOOD - use provider
            publish {
                to('registry') {
                    auth {
                        password.set(providers.environmentVariable('REGISTRY_TOKEN'))
                    }
                }
            }
        }
    }
}
```

---

### ❌ Anti-Pattern 5: File Operations During Configuration

**Problem:**
```groovy
docker {
    images {
        myApp {
            // BAD - reads file during configuration
            def version = new File('version.txt').text.trim()
            buildArgs.put('APP_VERSION', version)
        }
    }
}
```

**Why Bad:**
- I/O during configuration phase
- Not tracked as input
- Breaks configuration cache

**Solution:**
```groovy
docker {
    images {
        myApp {
            // GOOD - use provider with file input
            buildArgs.put('APP_VERSION', providers.fileContents(
                layout.projectDirectory.file('version.txt')
            ).asText.map { it.trim() })
        }
    }
}
```

---

## Quick Reference Checklist

When writing build script code:

- [ ] No `.get()` calls during configuration
- [ ] Use `providers.provider { }` for dynamic values
- [ ] Use `providers.environmentVariable()` for env vars
- [ ] Use `layout.buildDirectory` for build paths
- [ ] Use `tasks.named()` instead of `tasks.getByName()`
- [ ] Use `.flatMap()` for task outputs
- [ ] No `System.getenv()` or `System.getProperty()` in configuration
- [ ] No file I/O during configuration
- [ ] No string path concatenation

---

## See Also

- [Gradle 9 and 10 Compatibility Practices](gradle-9-and-10-compatibility-practices.md) - Full
  compatibility guide
- [Docker DSL Usage](usage-docker.md) - Docker image operations
- [Docker Orchestration Usage](usage-docker-orch.md) - Testing with Docker Compose
- [Gradle Provider API Documentation](https://docs.gradle.org/current/userguide/lazy_configuration.html) -
  Official Gradle docs
```

**Rationale:** Centralizes all Provider API patterns in one place with both correct patterns and common
mistakes. Eliminates duplication and provides single source of truth.

---

#### Task 2.2: Update References to Provider Patterns
**Files:**
- `docs/usage/usage-docker.md`
- `docs/usage/usage-docker-orch.md`

**Action:** Replace inline pattern sections with references to new document

**In usage-docker.md:**
Replace section "Provider API Patterns" (lines 642-693) with:
```markdown
## Provider API Patterns

For comprehensive Provider API patterns and examples, see [Provider API Patterns](provider-patterns.md).

**Quick patterns:**
- Task outputs: Use `.flatMap()` and `.map()`
- Environment variables: `providers.environmentVariable()`
- File paths: Use `layout.buildDirectory.dir/file()`
- Never call `.get()` during configuration

See [Provider API Patterns](provider-patterns.md) for complete examples and anti-patterns.
```

**In usage-docker-orch.md:**
Add reference in appropriate location (suggest after "Gradle 9 and 10 Compatibility" section):
```markdown
For Provider API patterns and configuration cache best practices, see
[Provider API Patterns](provider-patterns.md).
```

**Rationale:** Reduces duplication while maintaining quick reference in context.

---

### Phase 3: Troubleshooting Sections (High Priority)

#### Task 3.1: Add Troubleshooting Section to usage-docker.md
**File:** `docs/usage/usage-docker.md`
**Location:** Before "Available Gradle Tasks" section (approximately line 1120)
**Action:** Add comprehensive troubleshooting section

**Content to Add:**
```markdown
## Troubleshooting Guide

### Common Issues and Solutions

#### 1. Build Context Not Found

**Symptom:** `docker build` fails with "unable to locate Dockerfile" or "COPY failed: no source files"

**Possible Causes:**
- Context task output directory doesn't match expected location
- Dockerfile not in context directory
- Context task didn't run or failed silently

**Solutions:**
```bash
# Check context directory exists and has Dockerfile
ls -la build/docker-context/

# Verify contextTask configuration
./gradlew prepareContext --info

# Check task dependencies
./gradlew dockerBuild --dry-run
```

```groovy
// Verify configuration
docker {
    images {
        myApp {
            contextTask = tasks.register('prepareContext', Copy) {
                into layout.buildDirectory.dir('docker-context')
                from('src/main/docker')  // Ensure this contains Dockerfile
            }
            // Default: looks for Dockerfile at build/docker-context/Dockerfile
            // Override if needed:
            // dockerfileName.set("CustomDockerfile")
        }
    }
}
```

---

#### 2. Authentication Failed When Publishing

**Symptom:** "authentication required" or "unauthorized: authentication required" when running
`dockerPublish*`

**Possible Causes:**
- Incorrect credentials
- Registry URL mismatch
- Token/password expired
- Environment variables not set

**Solutions:**
```groovy
docker {
    images {
        myApp {
            publish {
                to('registry') {
                    registry.set("ghcr.io")  // Verify exact registry URL

                    auth {
                        // Use environment variables for credentials
                        username.set(providers.environmentVariable("GHCR_USER"))
                        password.set(providers.environmentVariable("GHCR_TOKEN"))
                        // serverAddress automatically extracted from registry
                    }
                }
            }
        }
    }
}
```

```bash
# Verify environment variables are set
echo $GHCR_USER
echo $GHCR_TOKEN  # Just check it's set, don't print value

# Test authentication manually
echo $GHCR_TOKEN | docker login ghcr.io -u $GHCR_USER --password-stdin

# Check registry URL format
# Include port for non-standard registries: "my-registry.com:5000"
```

**Common Registry URLs:**
- Docker Hub: `docker.io` or `registry-1.docker.io`
- GitHub Container Registry: `ghcr.io`
- Google Container Registry: `gcr.io`
- AWS ECR: `<account-id>.dkr.ecr.<region>.amazonaws.com`

---

#### 3. Source Image Not Found (SourceRef Mode)

**Symptom:** Tag/save/publish task fails with "No such image" or "image not found"

**Possible Causes:**
- Source image doesn't exist locally
- Image name mismatch
- Image was removed

**Solutions:**
```bash
# List local images
docker images

# Verify exact image name and tag
docker images | grep "my-app"
```

```groovy
docker {
    images {
        myApp {
            // Verify sourceRef matches actual image name
            sourceRef.set("my-app:1.0.0")  // Must match exactly

            // If image might not exist locally, enable auto-pull
            pullIfMissing.set(true)

            // Add authentication if pulling from private registry
            pullAuth {
                username.set(providers.environmentVariable("REGISTRY_USER"))
                password.set(providers.environmentVariable("REGISTRY_TOKEN"))
            }
        }
    }
}
```

---

#### 4. Build Args Not Applied

**Symptom:** ARG values not available in Dockerfile, build fails or uses wrong values

**Possible Causes:**
- Build arg not declared in Dockerfile
- Provider API not used (eager evaluation)
- Typo in ARG name

**Solutions:**
```dockerfile
# Dockerfile: Declare ARG before use
ARG JAR_FILE
ARG BUILD_VERSION=unknown

COPY ${JAR_FILE} /app/app.jar
```

```groovy
docker {
    images {
        myApp {
            // Use Provider API for build args
            buildArgs.put('JAR_FILE', providers.provider {
                "app-${project.version}.jar"
            })
            buildArgs.put('BUILD_VERSION', providers.provider {
                project.version.toString()
            })
        }
    }
}
```

```bash
# Verify build args in logs
./gradlew dockerBuild --info | grep "build arg"
```

---

#### 5. Image Tags Not Created

**Symptom:** Only first tag applied to image, or tags missing

**Possible Causes:**
- Tags contain registry/namespace (should be tag name only)
- List not properly set
- Task didn't run

**Solutions:**
```groovy
docker {
    images {
        myApp {
            registry = "ghcr.io"
            namespace = "myorg"
            imageName = "my-app"

            // CORRECT: Tag names only
            tags = ['latest', '1.0.0', 'stable']
            // or with .set()
            tags.set(['latest', '1.0.0', 'stable'])

            // WRONG: Don't include registry/namespace in tags
            // tags = ['ghcr.io/myorg/my-app:latest']  // ❌
        }
    }
}
```

```bash
# Verify tags were created
docker images | grep "my-app"

# Should show:
# ghcr.io/myorg/my-app   latest   ...
# ghcr.io/myorg/my-app   1.0.0    ...
# ghcr.io/myorg/my-app   stable   ...
```

---

#### 6. Multi-Project JAR Not Found in Context

**Symptom:** Context task fails to copy JAR from another Gradle project

**Possible Causes:**
- JAR not built before context task runs
- Wrong project path
- Task dependency missing

**Solutions:**
```groovy
// Get JAR file from app project using Provider API
def jarFileProvider = project(':app').tasks.named('bootJar').flatMap { it.archiveFile }
def jarFileNameProvider = jarFileProvider.map { it.asFile.name }

docker {
    images {
        myApp {
            contextTask = tasks.register('prepareContext', Copy) {
                into layout.buildDirectory.dir('docker-context')
                from('src/main/docker')

                // Copy JAR using provider
                from(jarFileProvider) {
                    rename { jarFileNameProvider.get() }
                }

                // Ensure JAR is built first
                dependsOn project(':app').tasks.named('bootJar')
            }

            buildArgs.put('JAR_FILE', jarFileNameProvider)
        }
    }
}
```

```bash
# Verify project path
./gradlew projects

# Test JAR build
./gradlew :app:bootJar

# Verify context has JAR
./gradlew prepareContext
ls -la build/docker-context/
```

---

#### 7. Configuration Cache Violations

**Symptom:** "configuration cache cannot be reused" warnings, or errors about captured state

**Common Violations and Solutions:**

**Violation 1: Capturing Project reference**
```groovy
// ❌ BAD
buildArgs.put('VERSION', project.version.toString())

// ✅ GOOD
buildArgs.put('VERSION', providers.provider { project.version.toString() })
```

**Violation 2: Eager file resolution**
```groovy
// ❌ BAD
context.set(file("${project.buildDir}/docker"))

// ✅ GOOD
context.set(layout.buildDirectory.dir('docker'))
```

**Violation 3: Reading environment during configuration**
```groovy
// ❌ BAD
def token = System.getenv('TOKEN')
registry.set("ghcr.io")

// ✅ GOOD
registry.set(providers.environmentVariable('REGISTRY').orElse("ghcr.io"))
```

**Violation 4: File I/O during configuration**
```groovy
// ❌ BAD
def version = new File('version.txt').text.trim()

// ✅ GOOD
def version = providers.fileContents(
    layout.projectDirectory.file('version.txt')
).asText.map { it.trim() }
```

**Debugging Configuration Cache:**
```bash
# Enable configuration cache with problems report
./gradlew dockerBuild --configuration-cache --configuration-cache-problems=warn

# See detailed violations
./gradlew dockerBuild --configuration-cache --stacktrace
```

See [Provider API Patterns](provider-patterns.md) for complete patterns and anti-patterns.

---

### Verification Steps

When troubleshooting:

1. **Check task execution order:**
   ```bash
   ./gradlew dockerBuild --dry-run
   ```

2. **Enable detailed logging:**
   ```bash
   ./gradlew dockerBuild --info
   ```

3. **Verify Docker daemon connection:**
   ```bash
   docker info
   docker ps
   ```

4. **Check image state:**
   ```bash
   docker images
   docker inspect <image-name>
   ```

5. **Test configuration cache:**
   ```bash
   ./gradlew dockerBuild --configuration-cache
   ./gradlew dockerBuild --configuration-cache  # Should reuse
   ```

---

### Getting Help

If issues persist:
1. Check [Provider API Patterns](provider-patterns.md) for configuration examples
2. Review [Gradle 9/10 Compatibility](gradle-9-and-10-compatibility-practices.md)
3. Search existing issues at project repository
4. Create new issue with:
   - Full error message
   - Relevant build.gradle configuration
   - Output of `./gradlew dockerBuild --info --stacktrace`
   - Docker version: `docker --version`
   - Gradle version: `./gradlew --version`
```

**Rationale:** Addresses most common user issues based on Docker plugin patterns and Gradle
configuration cache requirements.

---

### Phase 4: Gradle 9/10 Compatibility Clarification (Medium Priority)

#### Task 4.1: Clarify afterEvaluate Usage
**File:** `docs/usage/gradle-9-and-10-compatibility-practices.md`
**Action:** Add section clarifying when afterEvaluate is acceptable

**Content to Add:**
```markdown
## afterEvaluate: When It's Acceptable

### General Rule
**Avoid afterEvaluate; prefer provider-based lazy wiring.**

The Provider API eliminates most needs for `afterEvaluate` through lazy evaluation and task providers.

### Exception: Cross-Project Task Dependencies
**afterEvaluate is acceptable for wiring task dependencies across projects** when the dependency cannot
be expressed through Provider API alone.

#### ✅ Acceptable Pattern: Task Dependency Wiring

```groovy
afterEvaluate {
    tasks.named('composeUpMyTest') {
        dependsOn tasks.named('dockerBuildMyApp')
    }
}
```

**Why this is safe:**
- Uses `tasks.named()` which returns `TaskProvider` (configuration-cache safe)
- Only wires task dependency graph, doesn't mutate configuration
- No `Project` references captured in closures
- No external state read or cached

**When to use:**
- Image must be built before compose up can start containers
- Cross-project dependencies where plugin cannot auto-wire
- Task exists in different project than the one declaring dependency

#### ❌ Unacceptable Patterns

**1. Creating tasks in afterEvaluate:**
```groovy
afterEvaluate {
    tasks.create('newTask', Copy) {  // ❌ BAD
        // ...
    }
}
```
**Why:** Use `tasks.register()` instead, which is lazy and doesn't need afterEvaluate.

---

**2. Mutating configuration in afterEvaluate:**
```groovy
afterEvaluate {
    docker.images.myApp.imageName = project.version  // ❌ BAD
}
```
**Why:** Use Provider API with convention/set pattern instead.

---

**3. Reading external state in afterEvaluate:**
```groovy
afterEvaluate {
    def port = System.getenv('PORT')  // ❌ BAD
    systemProperty 'test.port', port
}
```
**Why:** Use `providers.environmentVariable()` for lazy, tracked evaluation.

---

**4. Configuring other tasks from task action:**
```groovy
tasks.register('myTask') {
    doLast {
        tasks.named('otherTask').configure {  // ❌ BAD
            enabled = false
        }
    }
}
```
**Why:** Task configuration must be done during configuration phase, not execution phase.

---

### Alternative Patterns (Preferred Over afterEvaluate)

#### Pattern 1: Provider-Based Defaults
```groovy
// Instead of afterEvaluate for defaults
docker {
    images {
        myApp {
            // Use convention (provider-based default)
            version.convention(providers.provider { project.version.toString() })
        }
    }
}
```

#### Pattern 2: Lazy Task Configuration
```groovy
// Instead of afterEvaluate for task configuration
tasks.named('dockerBuild') {
    // Configuration block is already evaluated lazily by Gradle
    onlyIf { task -> !task.sourceRef.isPresent() }
}
```

#### Pattern 3: Task Provider Dependencies
```groovy
// Instead of afterEvaluate for same-project dependencies
def buildTask = tasks.named('dockerBuild')
def saveTask = tasks.named('dockerSave')

saveTask.configure {
    dependsOn buildTask  // Direct provider dependency, no afterEvaluate needed
}
```

---

### Decision Flow: Do I Need afterEvaluate?

```
Need to wire task dependencies?
  ↓
  Same project?
    ↓ Yes → Use direct TaskProvider dependency (no afterEvaluate)
    ↓ No → Cross-project?
      ↓ Yes → Use afterEvaluate with tasks.named() (acceptable)

Need to set configuration values?
  ↓
  Use Provider API with .set() or .convention() (no afterEvaluate)

Need to create tasks?
  ↓
  Use tasks.register() instead (no afterEvaluate)

Need to read external state?
  ↓
  Use providers.environmentVariable() or ValueSource (no afterEvaluate)
```

---

### Summary

| Scenario | Use afterEvaluate? | Alternative |
|----------|-------------------|-------------|
| Cross-project task dependencies | ✅ Yes | None (this is the right pattern) |
| Same-project task dependencies | ❌ No | Direct TaskProvider dependency |
| Setting configuration defaults | ❌ No | `.convention()` with Provider |
| Creating tasks | ❌ No | `tasks.register()` |
| Reading environment variables | ❌ No | `providers.environmentVariable()` |
| Reading project properties | ❌ No | `providers.provider { }` |
| Mutating configuration | ❌ No | Set values during configuration normally |

**Bottom line:** The only acceptable use of `afterEvaluate` in modern Gradle is cross-project task
dependency wiring with `tasks.named()`.
```

**Rationale:** Resolves apparent contradiction between design guidance and usage examples. Makes clear
that the usage pattern shown is the recommended approach for cross-project dependencies.

---

### Phase 5: Documentation Index (Medium Priority)

#### Task 5.1: Create Usage Documentation README
**File:** `docs/usage/README.md` (new file)
**Action:** Create documentation index and navigation guide

**Content:** (See full content in main plan document above - already provided in detailed breakdown)

**Rationale:** Provides clear entry point for users, maps relationships between documents, and shows
user journey flow through documentation.

---

## Implementation Checklist

### Phase 1: Cross-References and Navigation
- [x] Task 1.1: Add cross-reference in usage-docker-orch.md (line ~99)
- [x] Task 1.2: Add integration test forward reference in usage-docker.md (line ~243)

### Phase 2: Provider API Patterns Document
- [x] Task 2.1: Create docs/usage/provider-patterns.md with patterns and anti-patterns
- [x] Task 2.2: Update usage-docker.md to reference provider-patterns.md
- [x] Task 2.3: Update usage-docker-orch.md to reference provider-patterns.md

### Phase 3: Troubleshooting Sections
- [x] Task 3.1: Add troubleshooting section to usage-docker.md (7 issues)

### Phase 4: Gradle 9/10 Compatibility Clarification
- [x] Task 4.1: Add afterEvaluate clarification to gradle-9-and-10-compatibility-practices.md

### Phase 5: Documentation Index
- [x] Task 5.1: Create docs/usage/README.md with full index

---

## Testing and Validation

After implementation:

1. **Cross-Reference Validation:**
   - [x] Verify all links work (no 404s)
   - [x] Check anchors exist at referenced locations
   - [x] Validate relative paths are correct

2. **Content Consistency:**
   - [x] Verify provider patterns examples match actual usage in both docs
   - [x] Check troubleshooting solutions reference correct sections
   - [x] Validate code examples are syntactically correct

3. **Navigation Testing:**
   - [x] Follow user journey: README → usage-docker.md → provider-patterns.md
   - [x] Follow user journey: README → usage-docker-orch.md → spock-junit-test-extensions.md
   - [x] Verify all forward/backward references work

4. **Completeness Check:**
   - [x] All usage docs listed in README
   - [x] All cross-references bidirectional where appropriate
   - [x] All troubleshooting issues have solutions
   - [x] Anti-patterns section has at least 5 examples

---

## Success Criteria

- [x] usage-docker-orch.md has clear reference to usage-docker.md
- [x] usage-docker.md has forward reference to integration test convention details
- [x] Provider API patterns consolidated in single document
- [x] Both usage docs reference provider-patterns.md
- [x] usage-docker.md has comprehensive troubleshooting section (7+ issues)
- [x] gradle-9-and-10-compatibility-practices.md clarifies afterEvaluate acceptable usage
- [x] docs/usage/README.md exists with complete index
- [x] All documentation follows 120-character line length limit
- [x] All code examples use correct Groovy syntax
- [x] All internal links verified working

---

## Non-Goals (Explicitly Out of Scope)

- Changing "Recommended Directory Layout" content (keeping as-is per user guidance)
- Addressing example naming consistency (deferred)
- Creating BuildKit-specific troubleshooting (not in current scope)
- Multi-platform build troubleshooting (not in current scope)

---

## Estimated Time Breakdown

| Phase | Task | Estimated Time |
|-------|------|----------------|
| 1 | Cross-references (2 tasks) | 30 minutes |
| 2 | Provider patterns document | 2 hours |
| 2 | Update references | 15 minutes |
| 3 | Troubleshooting section | 1.5 hours |
| 4 | afterEvaluate clarification | 30 minutes |
| 5 | Documentation index | 45 minutes |
| Testing | Validation and testing | 30 minutes |

**Total Estimated Time:** 6 hours

---

## Dependencies and Prerequisites

- No code changes required
- No build system changes required
- Only documentation file edits
- Can be implemented incrementally (each phase is independent)

---

## Rollout Plan

Suggested implementation order:

1. **Phase 1 first** (cross-references) - immediate user value, low risk
2. **Phase 5 second** (README) - provides structure for other work
3. **Phase 2 third** (provider patterns) - referenced by other phases
4. **Phase 3 fourth** (troubleshooting) - references provider patterns doc
5. **Phase 4 last** (afterEvaluate clarification) - refines design guidance

---

## Related Work

- Existing design documentation in `docs/design-docs/` remains unchanged
- Existing test examples in `plugin-integration-test/` remain unchanged
- Project standards in `docs/project-standards/` remain unchanged
- Only `docs/usage/` directory affected

---

## Maintenance Notes

After implementation:
- Update CLAUDE.md to reference docs/usage/README.md as entry point for usage docs
- Consider adding link validation to CI/CD pipeline
- Add reminder in docs/usage/README.md to keep index updated when adding new docs

---

## Questions and Decisions

**Q: Should provider-patterns.md also be referenced from CLAUDE.md?**
A: Defer - CLAUDE.md already references usage docs generally

**Q: Should we add visual diagrams showing DSL relationships?**
A: Defer - out of scope for this plan, can be future enhancement

**Q: Should troubleshooting cover Docker daemon issues?**
A: No - focus on plugin-specific issues, not general Docker problems

---

## Review and Approval

**Plan Status:** ✅ Completed
**Implemented By:** Claude Code
**Completion Date:** 2025-11-23

**Implementation Notes:**
- All 5 phases completed successfully
- All validation checklists completed
- All line length limits (120 chars) enforced
- 3 new files created (provider-patterns.md, README.md, plan document)
- 3 existing files enhanced (usage-docker.md, usage-docker-orch.md, gradle-9-and-10-compatibility-practices.md)
- Total: ~1,200+ lines of documentation added
