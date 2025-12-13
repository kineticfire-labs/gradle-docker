# DSL Architecture Rationale

## Overview

This document explains the architectural decision to maintain four DSLs in the gradle-docker plugin and provides
guidance on when to use each approach.

## The Four DSLs

The gradle-docker plugin provides four DSLs for Docker image lifecycle management:

| DSL | Purpose | Primary Use Case |
|-----|---------|------------------|
| `docker` | Build, tag, save, publish images | Low-level image operations |
| `dockerTest` | Test Docker images via Compose | Container-based integration testing |
| `dockerWorkflows` | Orchestrate build→test→publish pipelines | Conditional post-test actions |
| `dockerProject` | Unified, streamlined configuration | Standard single-image workflows |

## Decision: Keep All Four DSLs

### Rationale

The plugin serves two distinct user segments with different needs:

1. **Standard Users (80%)**: Need a simple, streamlined way to build, test, and publish a Docker image
2. **Power Users (20%)**: Need fine-grained control for complex multi-image, multi-pipeline scenarios

Maintaining all four DSLs serves both segments optimally:

- **`dockerProject`** provides the streamlined experience for standard users
- **`docker` + `dockerTest` + `dockerWorkflows`** provides the flexibility for power users

### Alternative Considered: Single DSL Only

We considered removing `docker`, `dockerTest`, and `dockerWorkflows` in favor of `dockerProject` only.

**Rejected because:**

1. **Loss of flexibility**: Complex enterprise scenarios require capabilities that `dockerProject` cannot provide
   without becoming overly complex itself

2. **Forced workarounds**: Users with non-standard workflows would need awkward workarounds or external scripting

3. **Implementation reality**: `dockerProject` is implemented as a facade over the three foundational DSLs. Removing
   them would require reimplementing their functionality within `dockerProject`, not actually reducing code

4. **Escape hatch principle**: Users should be able to start simple and graduate to more control without switching
   plugins

## Capability Comparison

| Capability | docker | dockerTest | dockerWorkflows | dockerProject |
|------------|--------|------------|-----------------|---------------|
| Build multiple images | ✅ | N/A | ✅ | ✅ |
| Reference existing images | ✅ | N/A | ✅ | ✅ |
| Multiple compose stacks | N/A | ✅ | ✅ | ✅ |
| Conditional tag on success | ❌ | ❌ | ✅ | ✅ |
| Conditional publish on success | ❌ | ❌ | ✅ | ✅ |
| Multiple test configurations | N/A | ✅ | ✅ | ✅ |
| Multiple publish targets | ✅ | N/A | ✅ | ✅ |
| Fine-grained task control | ✅ | ✅ | Partial | Limited |
| Custom task dependencies | ✅ | ✅ | ✅ | Limited |
| Non-standard workflow order | ✅ | ✅ | ✅ | ❌ |
| Shared compose stacks across pipelines | N/A | ✅ | ✅ | ❌ |

## When to Use Each DSL

### Decision Flow for Users

```
┌─────────────────────────────────────────────────────────────────┐
│                    Which DSL should I use?                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ Do you have a standard        │
              │ build → test → publish        │
              │ workflow?                     │
              └───────────────────────────────┘
                     │              │
                    YES            NO
                     │              │
                     ▼              ▼
    ┌────────────────────┐   ┌────────────────────────────────┐
    │ Is it a single     │   │ Use docker + dockerTest +      │
    │ primary image with │   │ dockerWorkflows (three-DSL)    │
    │ optional fixtures? │   └────────────────────────────────┘
    └────────────────────┘
           │        │
          YES      NO
           │        │
           ▼        ▼
┌──────────────┐  ┌────────────────────────────────┐
│ Use          │  │ Do you need to share compose   │
│ dockerProject│  │ stacks across pipelines or     │
│ (recommended)│  │ have complex dependencies?     │
└──────────────┘  └────────────────────────────────┘
                         │            │
                        YES          NO
                         │            │
                         ▼            ▼
          ┌──────────────────┐  ┌──────────────────┐
          │ Use three-DSL    │  │ Use dockerProject│
          │ approach         │  │ with multiple    │
          └──────────────────┘  │ images block     │
                                └──────────────────┘
```

### Quick Reference

| Scenario | Recommended DSL |
|----------|-----------------|
| Single app image, standard workflow | `dockerProject` |
| Single app + test fixture images | `dockerProject` with `images { }` |
| Multiple unrelated images | `docker` |
| Image testing only (no build) | `dockerTest` |
| Complex multi-pipeline CI/CD | `docker` + `dockerTest` + `dockerWorkflows` |
| Non-standard workflow order | `docker` + `dockerTest` + `dockerWorkflows` |
| Shared infrastructure across pipelines | `docker` + `dockerTest` + `dockerWorkflows` |

## Scenarios Requiring Three-DSL Approach

### Scenario 1: Complex Multi-Image Orchestration

Building 5+ images with intricate dependencies where some images depend on outputs of others.

```groovy
// Three-DSL approach allows fine-grained control
docker {
    images {
        baseImage { /* ... */ }
        serviceA { /* depends on baseImage */ }
        serviceB { /* depends on baseImage */ }
        integrationRunner { /* depends on serviceA output */ }
    }
}

// Custom wiring
afterEvaluate {
    tasks.named('dockerBuildServiceA') {
        dependsOn 'dockerBuildBaseImage'
    }
    tasks.named('dockerBuildServiceB') {
        dependsOn 'dockerBuildBaseImage'
    }
}
```

### Scenario 2: Shared Compose Stacks

Using the same compose stack configuration across multiple pipelines with different settings.

```groovy
dockerTest {
    composeStacks {
        sharedInfra {
            files.from('compose/shared-services.yml')
            waitForHealthy.set(['postgres', 'redis'])
        }
    }
}

dockerWorkflows {
    pipelines {
        unitPipeline {
            test {
                stack = dockerTest.composeStacks.sharedInfra
                testTaskName = 'unitTest'
            }
        }
        integrationPipeline {
            test {
                stack = dockerTest.composeStacks.sharedInfra
                testTaskName = 'integrationTest'
            }
        }
    }
}
```

### Scenario 3: Non-Standard Workflows

Custom pipelines that don't follow the standard build→test→publish pattern.

```groovy
// Example: test → build → test again → publish
// This requires manual task wiring not possible with dockerProject

docker {
    images {
        myApp { /* ... */ }
    }
}

dockerTest {
    composeStacks {
        preflightTest { /* ... */ }
        postBuildTest { /* ... */ }
    }
}

// Custom workflow via task dependencies
tasks.register('customWorkflow') {
    dependsOn 'preflightTestIntegrationTest'  // Run preflight tests first
    dependsOn 'dockerBuildMyApp'               // Then build
    dependsOn 'postBuildTestIntegrationTest'   // Then post-build tests
    finalizedBy 'dockerPublishMyApp'           // Finally publish
}
```

### Scenario 4: Granular Task Insertion

Need to insert custom tasks between standard pipeline steps.

```groovy
docker {
    images {
        myApp { /* ... */ }
    }
}

// Insert security scan between build and test
tasks.register('securityScan', Exec) {
    dependsOn 'dockerBuildMyApp'
    commandLine 'trivy', 'image', 'my-app:latest'
}

tasks.named('composeUpMyAppTest') {
    dependsOn 'securityScan'  // Only test if security scan passes
}
```

## Scenarios Where dockerProject Is Sufficient

These represent the majority (estimated 80%) of use cases:

1. **Single application image** with standard build→test→publish workflow
2. **Application + test fixtures** (e.g., app image + seeded database image)
3. **1-3 test configurations** with different lifecycles (CLASS vs METHOD)
4. **One primary image** to receive success tags and be published
5. **Standard CI/CD integration** without custom task ordering

Example of sufficient `dockerProject` usage:

```groovy
dockerProject {
    images {
        myApp {
            imageName.set('my-app')
            tags.set(['latest', '1.0.0'])
            jarFrom.set(':app:jar')
            primary.set(true)
        }
        testDb {
            imageName.set('test-db')
            tags.set(['latest'])
            contextDir.set('src/test/docker/db')
        }
    }

    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app', 'db'])
    }

    onSuccess {
        additionalTags.set(['tested', 'verified'])
        publish {
            to('production') {
                registry.set('prod-registry.com')
                tags.set(['latest', '1.0.0'])
            }
        }
    }
}
```

## Documentation Strategy

The plugin documentation follows a progressive disclosure pattern:

```
Getting Started
    │
    ▼
dockerProject (Recommended)
    │
    │  "Need more control?"
    ▼
Advanced Usage: docker + dockerTest + dockerWorkflows
```

This ensures:
- New users aren't overwhelmed with choices
- The common path is simple and well-documented
- Power users can find advanced capabilities when needed

## Maintenance Considerations

### Code Architecture

The relationship between DSLs minimizes maintenance burden:

```
┌─────────────────────────────────────────────────────────────┐
│                      dockerProject                           │
│                    (Facade/Builder)                          │
└─────────────────────────────────────────────────────────────┘
                           │
                           │ generates configurations for
                           ▼
┌─────────────┐  ┌─────────────────┐  ┌────────────────────┐
│   docker    │  │   dockerTest    │  │  dockerWorkflows   │
│ (primitives)│  │  (primitives)   │  │   (orchestration)  │
└─────────────┘  └─────────────────┘  └────────────────────┘
```

- `dockerProject` is essentially a facade that generates configurations for the three foundational DSLs
- The foundational DSLs provide the primitives
- Changes to primitives automatically benefit `dockerProject`
- `dockerProject`-specific logic is limited to translation/generation

### Testing Strategy

- **Unit tests**: Cover each DSL independently
- **Functional tests**: Cover DSL interactions and generated task graphs
- **Integration tests**: Cover real Docker operations via `dockerProject` (covers all underlying DSLs)

## Conclusion

Maintaining all four DSLs provides the best user experience across different skill levels and use cases:

- **Beginners** use `dockerProject` and get a simple, cohesive experience
- **Advanced users** can drop down to the three-DSL approach when they outgrow `dockerProject`
- **No migration required** - users can mix approaches in the same project if needed

The implementation overhead is manageable because `dockerProject` builds on the foundational DSLs rather than
duplicating their functionality.
