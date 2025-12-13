# Plugin Usage Documentation

This directory contains user-facing documentation for the gradle-docker plugin.

## Quick Start Guides

### [Docker DSL Guide](usage-docker.md)
Complete guide to building, tagging, saving, and publishing Docker images using the `docker` DSL.

**Use this when:** You need to build Docker images as part of your Gradle build.

**Key topics:**
- Building images from Dockerfiles
- Multi-stage builds and build contexts
- Tagging and publishing to registries
- Saving images to files
- Multi-project JAR packaging

**Quick example:**
```groovy
docker {
    images {
        myApp {
            imageName = 'my-app'
            tags = ['latest', '1.0.0']
            contextTask = tasks.register('prepareContext', Copy) {
                into layout.buildDirectory.dir('docker-context')
                from('src/main/docker')
            }
        }
    }
}
```

---

### [Docker Orchestration Guide](usage-docker-orch.md)
Complete guide to testing Docker images using Docker Compose orchestration with the `dockerTest` DSL.

**Use this when:** You need to run integration tests against Docker containers.

**Key topics:**
- Docker Compose orchestration for tests
- Container lifecycle management (class, method)
- Health checks and readiness waiting
- Spock and JUnit 5 test framework integration
- Integration test source set convention

**Quick example:**
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

tasks.named('integrationTest') {
    usesCompose(stack: "myTest", lifecycle: "class")
}
```

---

## Reference Documentation

### [Gradle 9 and 10 Compatibility Practices](gradle-9-and-10-compatibility-practices.md)
Best practices for writing configuration-cache compatible build scripts with this plugin.

**Use this when:**
- Setting up new projects with the plugin
- Migrating from older Gradle versions
- Debugging configuration cache issues
- Learning Provider API patterns

**Key topics:**
- Provider API usage patterns
- Configuration cache requirements
- Task dependency wiring
- afterEvaluate acceptable usage
- Common pitfalls and solutions

---

### [Provider API Patterns](provider-patterns.md)
Copy-paste patterns for common Provider API scenarios (configuration cache safe).

**Use this when:** You need specific examples of Provider API usage.

**Covered patterns:**
- Task outputs as providers
- Environment variables
- Project properties
- File paths with layout API
- Provider composition and transformation
- Anti-patterns (what NOT to do)

---

### [Spock and JUnit Test Extensions Guide](spock-junit-test-extensions.md)
Detailed guide to using test framework extensions for Docker Compose lifecycle management.

**Use this when:**
- Writing integration tests with Spock or JUnit 5
- Need fine-grained control over container lifecycle
- Implementing class vs method lifecycle patterns

**Key topics:**
- `@ComposeUp` annotation (Spock)
- `@ExtendWith` extensions (JUnit 5)
- CLASS vs METHOD lifecycle comparison
- State file access patterns
- Test isolation strategies

---

## Documentation Map

```
User Journey Flow:

1. Building Images
   └─> usage-docker.md
       ├─> provider-patterns.md (if needed)
       └─> gradle-9-and-10-compatibility-practices.md (reference)

2. Testing Images
   └─> usage-docker-orch.md
       ├─> spock-junit-test-extensions.md (detailed extension usage)
       ├─> provider-patterns.md (if needed)
       └─> gradle-9-and-10-compatibility-practices.md (reference)

3. Troubleshooting
   ├─> usage-docker.md#troubleshooting-guide
   ├─> usage-docker-orch.md#troubleshooting-guide
   └─> gradle-9-and-10-compatibility-practices.md
```

---

## Related Documentation

- **Design documentation:** `../design-docs/` - Architecture, implementation details
- **Project standards:** `../project-standards/` - Testing, style, philosophies
- **Integration test examples:** `../../plugin-integration-test/` - Working code examples

---

## Getting Help

1. **Quick questions:** Check troubleshooting sections in relevant usage guides
2. **Configuration cache issues:** See [gradle-9-and-10-compatibility-practices.md](gradle-9-and-10-compatibility-practices.md)
3. **Examples:** Browse integration test examples in `plugin-integration-test/`
4. **Issues:** Report at project repository
