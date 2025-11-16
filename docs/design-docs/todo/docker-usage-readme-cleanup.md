# Docker DSL Usage Documentation Cleanup Plan

**Status**: DRAFT - Ready for Review
**Created**: 2025-01-16
**Purpose**: Document gaps and inconsistencies in `docs/usage/usage-docker.md` based on actual integration test usage

## Executive Summary

The `docs/usage/usage-docker.md` document is comprehensive but has critical gaps when compared to actual integration
test usage patterns. Key issues:

1. **Property assignment style mismatch** - docs show `.set()` but tests use simple assignment
2. **Missing multi-project JAR provider pattern** - critical real-world pattern not documented
3. **Missing minimal example** - all examples are "comprehensive" with many optional properties
4. **Missing task dependency wiring** - docker + dockerOrch integration not shown
5. **Build context convention not documented** - directory structure pattern unclear

## Analysis Results

### ✅ Strengths

1. **Comprehensive Coverage** - Documents both Build Mode (new images) and SourceRef Mode (existing images)
2. **Gradle 9/10 Compliance** - Uses Provider API patterns correctly (`providers.provider`, `.set()`, etc.)
3. **Multiple Approaches** - Shows both namespace+imageName and repository patterns
4. **Good Examples** - Includes authentication, compression, labels, buildArgs, and publishing scenarios
5. **Clear Task Naming** - Explains generated task naming conventions well
6. **Validation Rules** - Clearly documents mutually exclusive options and required properties

## Critical Gaps Found

### 🚨 CRITICAL ISSUE #1: Property Assignment Style Mismatch

**Integration test examples use:**
```groovy
imageName = 'example-web-app'
tags = ['latest', '1.0.0']
```

**Documentation shows:**
```groovy
imageName.set("time-server")
tags.set(["latest", "1.0.0"])
```

**Impact**: Users copying from docs will write more verbose code than necessary.

**Recommendation**: Document BOTH styles with explanation:
- Simple assignment (`imageName = 'value'`) - Groovy DSL convenience
- Explicit `.set()` - Required for Kotlin DSL, clearer for complex providers
- When each style is preferred

**Fix Location**: Add new section "Property Assignment Styles"

---

### 🚨 CRITICAL ISSUE #2: Multi-Project JAR Provider Pattern Missing

**Every integration test uses this pattern:**
```groovy
// Get JAR file from app subproject using Provider API (Gradle 9/10)
def jarFileProvider = project(':dockerOrch:examples:web-app:app').tasks.named('bootJar').flatMap { it.archiveFile }
def jarFileNameProvider = jarFileProvider.map { it.asFile.name }

docker {
    images {
        webApp {
            contextTask = tasks.register('prepareWebAppContext', Copy) {
                from(jarFileProvider) {
                    rename { jarFileNameProvider.get() }
                }
                dependsOn project(':dockerOrch:examples:web-app:app').tasks.named('bootJar')
            }
            buildArgs.put('JAR_FILE', jarFileNameProvider)
        }
    }
}
```

**Current docs show:**
```groovy
from(file('../../app/build/libs')) {
    include 'app-*.jar'
    rename { String fileName -> "app-${versionString}.jar" }
}
```

**Impact**: This is a CRITICAL usage pattern for multi-project builds. Missing from docs!

**Recommendation**: Add dedicated example showing the multi-project JAR provider pattern with explanation.

**Fix Location**: Add new section "Multi-Project JAR Build" under "Build Mode Examples"

---

### 🚨 CRITICAL ISSUE #3: Minimal Configuration Example Missing

**Integration tests show minimal usage:**
```groovy
docker {
    images {
        webApp {
            imageName = 'example-web-app'
            tags = ['latest', '1.0.0']
            contextTask = tasks.register('prepareContext', Copy) { ... }
            buildArgs.put('JAR_FILE', jarFileNameProvider)
        }
    }
}
```

**NO registry, NO namespace, NO version, NO labels, NO save, NO publish**

**Current docs**: Every example is "comprehensive" with many optional properties.

**Impact**: Developers can't quickly see the bare minimum required configuration.

**Recommendation**: Add "Minimal Example" section at the top:
```groovy
// Absolute minimum for building an image
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
```

**Fix Location**: Add new section "Quick Start: Minimal Example" near the top

---

### 🚨 CRITICAL ISSUE #4: Task Dependency Wiring Missing (docker + dockerOrch Integration)

**Integration tests show:**
```groovy
afterEvaluate {
    tasks.named('composeUpWebAppTest') {
        dependsOn tasks.named('dockerBuildWebApp')
    }

    tasks.named('integrationTest') {
        dependsOn tasks.named('composeUpWebAppTest')
        finalizedBy tasks.named('composeDownWebAppTest')
    }
}
```

**Current docs**: No mention of how docker and dockerOrch DSLs work together.

**Recommendation**: Add brief section at the end:
```markdown
## Integration with dockerOrch DSL

The docker and dockerOrch DSLs are independent but commonly used together. Build an image with docker DSL,
then test it with dockerOrch DSL:

```groovy
docker {
    images {
        myApp {
            imageName = 'my-app'
            tags = ['latest']
            contextTask = ...
        }
    }
}

dockerOrch {
    composeStacks {
        myTest {
            files.from('src/integrationTest/resources/compose/app.yml')
            projectName = 'my-app-test'
        }
    }
}

// Wire tasks: build image before compose up
afterEvaluate {
    tasks.named('composeUpMyTest') {
        dependsOn tasks.named('dockerBuildMyApp')
    }
}
```

**Note**: The docker and dockerOrch DSLs can be used independently and are mutually exclusive in purpose.
See docs/usage/usage-docker-orch.md for dockerOrch details.
```

**Fix Location**: Add new section "Integration with dockerOrch DSL" near the end

---

### 🚨 CRITICAL ISSUE #5: contextTask Explanation Missing

**Issue**: The document shows `contextTask = tasks.register('prepareTimeServerContext', Copy) {...}` but doesn't
explain:
- **What** `contextTask` is
- **Why** it's needed (to prepare the Docker build context)
- **When** it's required (for Build Mode only)

**Recommendation**: Add brief note:
```groovy
// contextTask prepares the Docker build context (files available during 'docker build')
// Required for Build Mode; copies Dockerfile, app artifacts, and other build assets
contextTask = tasks.register('prepareContext', Copy) { ... }
```

**Fix Location**: Add new section "Build Context Setup" explaining contextTask purpose

---

### 🚨 CRITICAL ISSUE #6: Build Context vs Dockerfile Location Unclear

**Issue**: The relationship between:
- `contextTask` output directory (`layout.buildDirectory.dir('docker-context/timeServer')`)
- Dockerfile location (defaults to `build/docker-context/timeServer/Dockerfile`)
- `dockerfile.set()` vs `dockerfileName.set()` options

**Integration tests use convention:**
```groovy
into layout.buildDirectory.dir('docker-context/webApp')
```

**Finding**: Convention is `docker-context/<imageName>` but this isn't documented.

**Recommendation**: Document the convention:
```markdown
## Build Context Setup

### Directory Convention
- **Context output**: `layout.buildDirectory.dir('docker-context/<imageName>')`
- **Default Dockerfile location**: `build/docker-context/<imageName>/Dockerfile`
- **Override with**: `dockerfile.set()` (full path) or `dockerfileName.set()` (name within context)

### Example
```groovy
contextTask = tasks.register('prepareContext', Copy) {
    into layout.buildDirectory.dir('docker-context/myApp')  // Convention: docker-context/<imageName>
    from('src/main/docker')  // Must contain Dockerfile
}
// Default Dockerfile: build/docker-context/myApp/Dockerfile
```
```

**Fix Location**: Add new section "Build Context Setup"

---

### 🚨 ISSUE #7: Provider API Patterns from Real Usage Missing

**Integration tests show:**
```groovy
// Provider composition for JAR name
def jarFileNameProvider = jarFileProvider.map { it.asFile.name }

// Used in buildArgs
buildArgs.put('JAR_FILE', jarFileNameProvider)

// Used in rename
from(jarFileProvider) {
    rename { jarFileNameProvider.get() }
}
```

**Current docs**: Shows `providers.provider { project.version.toString() }` but not `.flatMap()` or `.map()` chains.

**Recommendation**: Add "Provider API Patterns" section with real examples showing:
- `.flatMap()` for task output providers
- `.map()` for transforming values
- Composition chains
- When to use `.get()` (execution time only)

**Fix Location**: Add new section "Provider API Patterns"

---

### ⚠️ ISSUE #8: Task Dependencies Not Explained

**Issue**: Example shows `dependsOn ':app:jar'` but doesn't explain:
- Why this dependency exists
- How to set up task dependencies for multi-project builds
- Automatic dependency wiring between docker tasks

**Recommendation**: Add note about dependency patterns:
```groovy
// Ensure JAR is built before Docker context is prepared
dependsOn project(':app').tasks.named('jar')

// Docker tasks have automatic dependencies:
// - dockerSaveTimeServer depends on dockerBuildTimeServer (when contextTask exists)
// - dockerPublishTimeServer depends on dockerBuildTimeServer (when contextTask exists)
```

**Fix Location**: Add to "Task Dependencies" section

---

### ⚠️ ISSUE #9: Provider API Rationale Missing

**Issue**: Shows `providers.provider { project.version.toString() }` without explaining **why** this pattern is
necessary.

**Recommendation**: Add brief note in "Gradle 9 and 10 Compatibility" section:
```markdown
## Why use providers.provider?
- Enables configuration cache (Gradle 9/10)
- Avoids eager evaluation during configuration phase
- See gradle-9-and-10-compatibility-practices.md for details
```

**Fix Location**: Expand "Gradle 9 and 10 Compatibility" section

---

### ⚠️ ISSUE #10: version Property Redundancy

**Issue**: Documentation states "Defaults to project.version" but then shows
`version.set(providers.provider { project.version.toString() })` in examples.

**Recommendation**: Clarify:
- When you can omit `version.set()` (default behavior)
- When you need to explicitly set it (override default, custom versioning)

**Fix Location**: Add to "Key API Properties" section

---

### ⚠️ ISSUE #11: Missing Build Mode vs SourceRef Mode Decision Guide

**Issue**: Document explains both modes but doesn't help developers choose which to use.

**Recommendation**: Add decision table:
```markdown
| Scenario | Mode | Why |
|----------|------|-----|
| Building custom app image | Build Mode | Need to specify Dockerfile, build context |
| Tagging existing image | SourceRef Mode | No build needed |
| Publishing third-party image | SourceRef Mode | Image already exists |
| Building from scratch | Build Mode | Creating new image |
```

**Fix Location**: Add to "Two Usage Modes" section

---

## Minor Issues

### 📋 ISSUE #12: Optional vs Required Properties Not Always Clear

**Issue**: Which properties are truly required? What are the actual defaults?
- Example: `tags.set([...])` is marked "Required" but is it required for all operations?

**Recommendation**: Add table of optional vs required properties with defaults

**Fix Location**: Enhance "Key API Properties" section

---

### 📋 ISSUE #13: Missing Troubleshooting Hints

**Issue**: No examples of:
- Common validation errors
- What happens when required properties are missing
- Authentication failure scenarios

**Recommendation**: Add "Common Issues and Troubleshooting" section

**Fix Location**: Add new section at the end

---

## Consistency Check with Examples

Based on `plugin-integration-test/dockerOrch/examples/README.md`, the integration tests focus on:
- `dockerOrch` DSL for testing (CLASS and METHOD lifecycles)
- Test framework extensions (Spock `@ComposeUp`, JUnit 5 `@ExtendWith`)
- Multi-service stacks (app + database)

**Finding**: The integration test examples in `plugin-integration-test/dockerOrch/examples/` appear to focus primarily
on **testing** (dockerOrch DSL), not building (docker DSL). However, they DO provide real-world examples of using the
`docker` DSL for building images before testing.

**Sources reviewed**:
- `plugin-integration-test/dockerOrch/examples/web-app/app-image/build.gradle`
- `plugin-integration-test/dockerOrch/examples/database-app/app-image/build.gradle`
- `plugin-integration-test/dockerOrch/examples/isolated-tests/app-image/build.gradle`
- `plugin-integration-test/dockerOrch/examples/stateful-web-app/app-image/build.gradle`

---

## Recommended Document Structure

```markdown
# 'docker' DSL Usage Guide

## Prerequisites
[Existing content]

## Recommended Directory Layout
[Existing content]

## Quick Start: Minimal Example
[NEW - Bare minimum to build an image]

## Build Context Setup
[NEW - Explain contextTask, directory convention, Dockerfile location]

## Two Usage Modes
[Existing content - enhance with decision guide]

## Gradle 9 and 10 Compatibility
[Existing content - add Provider API rationale]

## Build Mode: Building New Docker Images

### Minimal Build Configuration
[NEW - imageName + tags only]

### Multi-Project JAR Build
[NEW - CRITICAL MISSING PATTERN - Provider pattern for JAR from another project]

### Approach 1: Registry + Namespace + ImageName + Tag(s)
[Existing content - simplified]

### Approach 2: Registry + Repository + Tag(s)
[Existing content - simplified]

## SourceRef Mode: Working with Existing Images
[Existing content]

## Property Assignment Styles
[NEW - Simple assignment vs .set() - when to use each]

## Provider API Patterns
[NEW - .flatMap(), .map(), composition from real examples]

## Task Dependencies
[NEW - Multi-project dependencies, afterEvaluate wiring, automatic dependencies]

## Comprehensive Publishing Examples
[Existing content]

## Key API Properties
[Existing content - enhance with required vs optional table]

## Integration with dockerOrch DSL
[NEW - Brief note on wiring docker + dockerOrch tasks]

## Running Generated Tasks
[Existing content]

## Validation Rules
[Existing content]

## Key Benefits
[Existing content]

## Available Gradle Tasks
[Existing content]

## Common Issues and Troubleshooting
[NEW - Optional, nice to have]
```

---

## Summary Recommendations

### **MUST FIX (Critical - reflects actual usage):**

1. ✅ **Add "Minimal Example" section** at the top (imageName + tags + contextTask only)
2. ✅ **Document property assignment styles** (simple assignment vs `.set()`)
3. ✅ **Add multi-project JAR provider pattern** (critical for real-world usage)
4. ✅ **Add task dependency wiring example** (docker + dockerOrch integration with `afterEvaluate`)
5. ✅ **Document build context directory convention** (docker-context/<imageName>)
6. ✅ **Clarify contextTask purpose and relationship to Dockerfile location**

### **SHOULD ADD (for completeness):**

7. ✅ **Add Provider API composition patterns** (.flatMap(), .map() chains)
8. ✅ **Simplify comprehensive examples** - move optional properties to separate sections
9. ✅ **Add "docker vs dockerOrch" relationship note** (independent, mutually exclusive purpose)
10. ✅ **Document `dependsOn` pattern for multi-project builds**
11. ✅ **Add Build Mode vs SourceRef Mode decision guide**
12. ✅ **Clarify when `version.set()` is needed vs optional**
13. ✅ **Add Provider API rationale** (why use providers.provider?)

### **NICE TO HAVE:**

14. ⚪ Add troubleshooting scenarios
15. ⚪ Add table of optional vs required properties with defaults

---

## Key Insight

The documentation shows **comprehensive feature showcase** but the integration tests reveal **actual usage patterns**
that are:
- Simpler (minimal properties)
- More focused on multi-project builds
- Use provider composition extensively
- Show task wiring patterns

**The docs need to bridge this gap by showing both minimal and comprehensive examples, with the multi-project JAR
provider pattern prominently featured.**

---

## Implementation Notes

### Priority Order

1. **Phase 1 (Critical)**: Add missing patterns that users actually need
   - Minimal example
   - Multi-project JAR provider pattern
   - Build context setup explanation
   - Property assignment styles
   - Task dependency wiring

2. **Phase 2 (Important)**: Enhance existing content
   - Provider API patterns section
   - docker + dockerOrch integration note
   - Decision guides
   - Simplify comprehensive examples

3. **Phase 3 (Nice to have)**: Polish
   - Troubleshooting section
   - Required vs optional properties table

### Style Guidelines

- **Concise and example-driven** - this is for internal developers, not customers
- **Brief inline comments** explaining "why" rather than lengthy sections
- **120 character line limit** for all documentation
- **Real-world patterns** from integration tests take precedence over theoretical examples

### Acceptance Criteria

- [ ] All critical patterns from integration tests are documented
- [ ] Minimal example shows bare minimum configuration
- [ ] Multi-project JAR provider pattern is prominently featured
- [ ] Build context convention is clearly documented
- [ ] Property assignment styles (simple vs .set()) are explained
- [ ] Task dependency wiring (docker + dockerOrch) is shown
- [ ] Document follows 120-character line limit
- [ ] Examples match integration test usage patterns
