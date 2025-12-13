# requiresImage DSL Method - Deferred

## Status
**DEFERRED** - Complexities outweigh benefits; manual wiring documented instead

## Original Goal

Eliminate the need for users to manually wire Docker image creation tasks as dependencies of Docker Compose up tasks.

### Problem Statement

Users must manually wire image dependencies when using Docker Compose orchestration:

```gradle
docker {
    images {
        webApp {
            imageName = 'example-web-app'
            context.set(file('src/main/docker'))
        }
    }
}

dockerTest {
    composeStacks {
        webAppTest {
            files.from('src/integrationTest/resources/compose/web-app.yml')
        }
    }
}

// Manual wiring - boilerplate
afterEvaluate {
    tasks.named('composeUpWebAppTest') {
        dependsOn tasks.named('dockerBuildWebApp')  // ← User must figure this out
    }
}
```

**Impact:**
- 3-5 lines of boilerplate per image dependency
- User must know which task to depend on (build, tag, save, publish)
- Error-prone - easy to forget or get wrong

### Proposed Solution (Deferred)

Add a DSL method to auto-wire image dependencies:

```gradle
dockerTest {
    composeStacks {
        webAppTest {
            files.from('compose.yml')
            requiresImage 'webApp'  // ← Plugin auto-detects correct task
        }
    }
}

// Plugin automatically adds: composeUpWebAppTest.dependsOn dockerBuildWebApp
```

## Why It Seemed Attractive

1. **Reduced boilerplate** - 3-5 lines eliminated per image
2. **User-friendly** - No need to understand task graph
3. **Less error-prone** - Plugin handles wiring automatically
4. **Consistent** - All projects get correct dependencies

## Complexities Discovered

### Complexity 1: SourceRef Images May Have No Tasks

**Issue:** Images defined with `sourceRef` don't always create tasks.

**Example 1: SourceRef with no other DSL**
```gradle
docker {
    images {
        postgres {
            sourceRef {
                imageName = 'postgres'
                tag = '15'
            }
            // No tags[], no save{}, no publish{}
        }
    }
}

dockerTest {
    composeStacks {
        test {
            requiresImage 'postgres'  // ← What task to depend on? NONE exist!
        }
    }
}
```

**Reality:** Docker Compose pulls the image automatically when `composeUp` runs. **No task dependency needed.**

**Example 2: SourceRef with only save task**
```gradle
docker {
    images {
        postgres {
            sourceRef {
                imageName = 'postgres'
                tag = '15'
            }
            save {
                file.set(layout.buildDirectory.file('docker/postgres.tar'))
            }
        }
    }
}
```

**Reality:** Only `dockerSavePostgres` exists. Should `requiresImage` depend on that? Probably not - saving doesn't
prepare the image for compose (pulling does).

### Complexity 2: Task Creation is DSL-Dependent

The plugin creates tasks based on configured DSL blocks:

| DSL Configuration | Tasks Created |
|-------------------|---------------|
| `context{}` only | `dockerBuild*` |
| `context{}` + `tags[]` | `dockerBuild*`, `dockerTag*` |
| `sourceRef{}` only | **NONE** |
| `sourceRef{}` + `tags[]` | `dockerTag*` |
| `sourceRef{}` + `save{}` | `dockerSave*` |
| `context{}` + `save{}` + `publish{}` | `dockerBuild*`, `dockerSave*`, `dockerPublish*` |

**Problem:** `requiresImage` cannot assume which task exists or which task is appropriate to depend on.

### Complexity 3: Multiple Valid Tasks Per Image

**Example:**
```gradle
docker {
    images {
        webApp {
            context.set(file('src/main/docker'))
            tags.set(['latest', '1.0.0'])
            save {
                file.set(layout.buildDirectory.file('docker/webApp.tar'))
            }
            publish {
                registry.set('docker.io')
                namespace.set('myorg')
            }
        }
    }
}
```

**Tasks created:**
- `dockerBuildWebApp` (builds image)
- `dockerTagWebApp` (tags the built image)
- `dockerSaveWebApp` (saves to tar)
- `dockerPublishWebApp` (pushes to registry)

**Question:** Which task should `requiresImage 'webApp'` depend on?
- `dockerBuildWebApp`? Image is built but not tagged
- `dockerTagWebApp`? Image is built and tagged (most likely for compose)
- `dockerSaveWebApp`? Not relevant for compose
- `dockerPublishWebApp`? Not relevant for compose

**Answer is context-dependent:**
- For local testing → `dockerTagWebApp` (or `dockerBuildWebApp` if no tags)
- For testing saved tar → `dockerSaveWebApp`
- For testing published image → `dockerPublishWebApp`

### Complexity 4: Images Not in docker DSL

**Example: External images in compose file**
```yaml
# compose.yml
services:
  nginx:
    image: nginx:alpine
  redis:
    image: redis:7
```

```gradle
dockerTest {
    composeStacks {
        test {
            files.from('compose.yml')
            requiresImage 'nginx'  // ← ERROR: not defined in docker.images
        }
    }
}
```

**Options:**
1. Throw error (forces user to define all images in docker DSL - burdensome)
2. Silently ignore (confusing - why does it work for some images but not others?)
3. Require user to define minimal sourceRef (verbose)

None are satisfactory.

### Complexity 5: Task Name Ambiguity

The "image name" in `requiresImage` is ambiguous:

```gradle
docker {
    images {
        myApp {  // ← DSL block name
            imageName = 'example-app'  // ← Actual image name
        }
    }
}

dockerTest {
    composeStacks {
        test {
            requiresImage 'myApp'  // ← DSL block name? or 'example-app'?
        }
    }
}
```

**Clarification needed:** Should `requiresImage` reference:
- The DSL block name (`myApp`) for lookup in `docker.images`?
- The actual image name (`example-app`) for matching against compose files?

**Decision made:** DSL block name is clearer and more direct for lookup.

## Attempted Solutions and Their Issues

### Solution A: Explicit Task Name
```gradle
requiresTask 'dockerBuildWebApp'  // User specifies exact task
```
**Issue:** Not much better than manual `afterEvaluate` wiring; loses abstraction benefit.

### Solution B: Auto-Detect "Last" Task
```gradle
requiresImage 'webApp'  // Plugin detects final task in lifecycle
```
**Detection logic:**
- If has `publish{}` → depend on `dockerPublish*`
- Else if has `save{}` → depend on `dockerSave*`
- Else if has `tags[]` → depend on `dockerTag*`
- Else if has `context{}` → depend on `dockerBuild*`
- Else (sourceRef only) → **no dependency**

**Issues:**
- Complex and potentially surprising behavior
- May not match user intent (e.g., testing save but depends on publish)
- Hard to debug when auto-detection chooses wrong task

### Solution C: Explicit Strategy Parameter
```gradle
requiresImage 'webApp', task: 'build'    // → dockerBuildWebApp
requiresImage 'postgres', task: 'save'   // → dockerSavePostgres
requiresImage 'redis'                     // No task = compose pulls
```
**Issue:** User still needs to understand which task to use; complexity not reduced enough.

### Solution D: Smart Compose File Parsing
Parse compose files to detect image references and auto-wire to matching `docker.images` definitions.

**Issues:**
- Must parse YAML (with variable substitution, env files, etc.)
- Must handle `image:` vs `build:` directives
- Must match image names (registry, namespace, tag variations)
- Must handle cross-project image references
- Extremely complex for marginal benefit

## Decision: Defer Indefinitely

After analyzing the complexities, the benefits do not justify the implementation cost and potential for surprising
behavior.

### Reasons for Deferral

1. **Edge cases dominate** - SourceRef, external images, multiple tasks per image make auto-detection unreliable
2. **User intent varies** - Cannot reliably determine which task to depend on
3. **Manual wiring is explicit** - 3-5 lines of clear code vs. complex auto-detection
4. **Low frequency** - Most projects have 1-3 images; boilerplate is manageable
5. **Proposal 1 addresses primary pain** - Auto-wiring `usesCompose()` eliminates most boilerplate

### Alternative: Document Manual Wiring Pattern

Instead of `requiresImage`, provide clear documentation on manual wiring patterns.

## Documentation Plan

Update `docs/usage/usage-docker-orch.md` to include a section on wiring image dependencies.

### New Section: "Wiring Image Dependencies"

**Location:** After "Understanding usesCompose() Configuration" section

**Content outline:**

1. **Why Manual Wiring is Needed**
   - Compose needs images to exist before containers start
   - Plugin cannot auto-detect all scenarios reliably
   - Explicit wiring is clear and predictable

2. **Common Patterns**

   **Pattern 1: Local build (has context)**
   ```gradle
   docker.images {
       webApp {
           context.set(file('src/main/docker'))
       }
   }

   afterEvaluate {
       tasks.named('composeUpWebAppTest') {
           dependsOn tasks.named('dockerBuildWebApp')
       }
   }
   ```

   **Pattern 2: SourceRef with tags (pulled and tagged)**
   ```gradle
   docker.images {
       postgres {
           sourceRef {
               imageName = 'postgres'
               tag = '15'
           }
           tags.set(['latest'])
       }
   }

   afterEvaluate {
       tasks.named('composeUpTest') {
           dependsOn tasks.named('dockerTagPostgres')  // Pulls if needed, then tags
       }
   }
   ```

   **Pattern 3: SourceRef only (no docker tasks)**
   ```gradle
   docker.images {
       redis {
           sourceRef {
               imageName = 'redis'
               tag = '7'
           }
       }
   }

   // No wiring needed - Docker Compose pulls automatically
   ```

   **Pattern 4: External images (not in docker DSL)**
   ```yaml
   # compose.yml
   services:
     nginx:
       image: nginx:alpine
   ```
   ```gradle
   // No docker.images definition needed
   // No wiring needed - Docker Compose pulls automatically
   ```

   **Pattern 5: Multiple images**
   ```gradle
   afterEvaluate {
       tasks.named('composeUpAppTest') {
           dependsOn tasks.named('dockerBuildWebApp')
           dependsOn tasks.named('dockerBuildWorker')
           // Don't depend on postgres - compose pulls automatically
       }
   }
   ```

3. **Decision Guide**

   | Image Source | Has docker.images? | Has context? | Has tags? | Depend On | Reason |
   |--------------|-------------------|--------------|-----------|-----------|---------|
   | Built locally | Yes | Yes | No | `dockerBuild*` | Must build before compose |
   | Built + tagged | Yes | Yes | Yes | `dockerTag*` | Build is implicit dependency of tag |
   | SourceRef + tags | Yes | No (sourceRef) | Yes | `dockerTag*` | Tag task pulls if needed |
   | SourceRef only | Yes | No | No | **None** | Compose pulls automatically |
   | External | No | N/A | N/A | **None** | Compose pulls automatically |

4. **Common Mistakes**

   **Mistake 1: Depending on wrong task**
   ```gradle
   // WRONG - save task doesn't prepare image for compose
   tasks.named('composeUpTest') {
       dependsOn tasks.named('dockerSavePostgres')  // ❌
   }

   // CORRECT - tag task (or build task) prepares image
   tasks.named('composeUpTest') {
       dependsOn tasks.named('dockerTagPostgres')   // ✅
   }
   ```

   **Mistake 2: Depending on sourceRef-only image**
   ```gradle
   docker.images {
       postgres {
           sourceRef { /* ... */ }
           // No tags, save, or publish
       }
   }

   // WRONG - no tasks exist for this image
   tasks.named('composeUpTest') {
       dependsOn tasks.named('dockerTagPostgres')  // ❌ Task doesn't exist!
   }

   // CORRECT - compose pulls automatically
   tasks.named('composeUpTest') {
       // No dependency needed  ✅
   }
   ```

   **Mistake 3: Forgetting dependency entirely**
   ```gradle
   docker.images {
       webApp {
           context.set(file('src/main/docker'))
       }
   }

   tasks.named('composeUpTest') {
       // ❌ Missing: dependsOn tasks.named('dockerBuildWebApp')
   }
   // Result: Compose may fail if image doesn't exist or is stale
   ```

5. **Verification**

   How to verify correct wiring:
   ```bash
   # Check task dependencies
   ./gradlew composeUpWebAppTest --dry-run

   # Should show: dockerBuildWebApp → composeUpWebAppTest
   ```

6. **Why No Auto-Wiring?**

   Brief explanation:
   - Images can come from many sources (built, pulled, external registries)
   - Task creation depends on DSL configuration (context, sourceRef, tags, save, publish)
   - Multiple valid tasks per image (build, tag, save, publish)
   - User intent varies (testing build vs. published image)
   - Explicit wiring is clearer and more predictable

## Benefits of This Approach

1. **Explicit is better than implicit** - Clear intent, no surprises
2. **Works for all cases** - No edge cases where auto-detection fails
3. **Easy to debug** - Users can see exactly what depends on what
4. **Low implementation cost** - Documentation only, no code changes
5. **Future-proof** - No complex auto-detection to maintain

## Future Considerations

If auto-wiring is reconsidered in the future, require:

1. **Comprehensive compose file parsing** - Handle all YAML features
2. **Image name matching** - Handle registry/namespace/tag variations
3. **User override mechanism** - Allow explicit control when auto-detection is wrong
4. **Clear error messages** - When auto-detection cannot determine dependency
5. **Opt-in behavior** - Default to manual wiring; users enable auto-wiring explicitly

Until these can be implemented reliably, manual wiring is the better approach.

## Related Documents

- Proposal 1 (implemented): `autowire-test-task-dependencies.md` - Auto-wires `usesCompose()` dependencies
- Usage documentation: `docs/usage/usage-docker-orch.md` - Will be updated with manual wiring patterns
