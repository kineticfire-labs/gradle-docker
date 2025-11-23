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

- [Gradle 9 and 10 Compatibility Practices](gradle-9-and-10-compatibility-practices.md) - Full compatibility guide
- [Docker DSL Usage](usage-docker.md) - Docker image operations
- [Docker Orchestration Usage](usage-docker-orch.md) - Testing with Docker Compose
- [Gradle Provider API Documentation](https://docs.gradle.org/current/userguide/lazy_configuration.html) - Official
  Gradle docs
