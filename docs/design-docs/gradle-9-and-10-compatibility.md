# Gradle 9 and 10 Compatibility Instructions (for AI Coding Agents)

Goal: Produce build scripts and plugins that are **configuration-cache friendly on Gradle 9** and **forward-compatible 
with Gradle 10** _and_ fully leverage the **build cache** (local/remote). Keep configuration pure & lazy; make tasks 
declarative, deterministic, relocatable, and cacheable.

# 1) Golden Rules (apply everywhere)

- Provider API only
   - Model all values with `Property<T>`, `ListProperty<T>`, `MapProperty<K,V>`, `RegularFileProperty`, 
     `DirectoryProperty`.
   - Compose with `map/flatMap/zip`. Never call `.get()` during configuration.
- Register, don’t create
   - Use `tasks.register("X", Type)`; configure via `.configure { … }`; avoid `tasks.create`.
- Pure configuration
    - No I/O, env/sys-prop reads, clocks, network, or external processes during configuration.
  - Use `ProviderFactory`, `ValueSource`, `BuildService`, injected Gradle services.
- No `Project` at execution time
    - Inside `@TaskAction`, never call `task.project` or any `Project` APIs. Inject services instead.
- No `afterEvaluate` and no cross-task mutation at execution
    - Do not configure task B from task A’s `@TaskAction`. Wire via inputs/providers and dependencies.
- Defaults vs overrides
    - Defaults with `convention(...)`; user overrides with `.set(...)`. Lock with `finalizeValueOnRead()` / 
      `disallowChanges()`.

## 2) Configuration Cache (Gradle 9 & 10)

- All dynamic values are **Providers**; do not resolve them in configuration.
- Do not read environment/system properties in configuration; wrap in ValueSource or resolve at execution through Providers.
- Do not use `doFirst`/`doLast` for behavior that should be part of the task action or configuration.
- Do not access `Project`, `Gradle`, or global mutable state in `@TaskAction`; inject services (`ExecOperations`, 
  `FileSystemOperations`, etc.).
- Avoid `afterEvaluate`; prefer provider-based lazy wiring.

Quick test

```bash
./gradlew <tasks> --configuration-cache
./gradlew <tasks> --configuration-cache  # should reuse on second run
```

## 3) Build Cache Configuration (local & remote)

**Enable in** `gradle.properties`:

```properties
org.gradle.caching=true
```

**Optional CI defaults**:
```properties
org.gradle.configuration-cache=true
org.gradle.configuration-cache.problems=warn
org.gradle.warning.mode=all
```

**Remote cache (Groovy DSL — `settings.gradle`)**:
```groovy
buildCache {
    local { enabled = true } // keep local cache for dev
    remote(HttpBuildCache) {
        url = uri("https://cache.example.com/cache/")
        push = System.getenv("CI") == "true"  // push only from CI
        allowUntrustedServer = false
    }
}
```

**Relocatability (crucial for cache hits across machines/paths):**

- Use `@PathSensitive(PathSensitivity.RELATIVE)` on `@InputFiles`/`@InputDirectory`.
- Avoid absolute paths in `@Input` strings; model paths as `RegularFileProperty` / `DirectoryProperty`.
- Compute paths via `layout.*` providers (`layout.buildDirectory.file/dir`, `layout.projectDirectory.file/dir`).
- Do not bake machine-specific values (user dirs, temp paths) into inputs or outputs.

- **What not to cache:**
- Tasks with unavoidable external side effects (publishing, pushing Docker images, starting daemons): mark
```groovy
@DisableCachingByDefault("External side effects")
```
- If a task can be pure/deterministic, prefer @CacheableTask and declare inputs/outputs precisely.

## 4) Task Design (deterministic & cacheable)

- Inputs/Outputs
   - Annotate: `@Input`, `@Optional`, `@Nested`, `@Internal`, `@InputFile`/`@InputFiles`, `@OutputFile`/`@OutputDirectory`.
   - Add `@PathSensitive(PathSensitivity.RELATIVE)` to file inputs unless classpath semantics are required.
   - Use `@Classpath` only for true classpaths.
- Cacheability
   - Deterministic tasks → `@CacheableTask`.
   - Side-effecting tasks → `@DisableCachingByDefault("reason")`.
- Incremental work
   - Support incremental inputs with `@TaskAction` parameter `InputChanges` when appropriate.

## 5) External Processes, Env & Services (Docker/CLI-heavy plugins)

- Inject and use **services**, not `project.exec`:
```groovy
@javax.inject.Inject abstract ExecOperations getExecOps()
execOps.exec { commandLine "docker", "build", … }
```
- Wrap unstable/external lookups as `ValueSource` inputs (env vars, git info, short probes) for consistent cache keys.
- Use a **Build Service** for shared mutable state (thread-safe, lifecycle-managed).

## 6) Reproducible Outputs

- Archives with stable content:
```groovy
tasks.withType(AbstractArchiveTask).configureEach {
preserveFileTimestamps = false
reproducibleFileOrder  = true
}
```
- Avoid timestamps, random IDs, or non-deterministic ordering in generated files. If unavoidable, declare them as inputs 
  so cache keys change appropriately (or disable caching for that task).

## 7) Plugin Patterns (Groovy/Java DSL)

**Extension**
```groovy
class DockerExtension {
    final Property<String> registry
    final Property<String> namespace
    @javax.inject.Inject DockerExtension(ObjectFactory objects) {
        registry  = objects.property(String)
        namespace = objects.property(String)
    }
}
```

**Task**
```groovy
@CacheableTask
abstract class DockerBuildTask extends DefaultTask {
    @Input    abstract Property<String> getImageName()
    @InputFile abstract RegularFileProperty getDockerfile()
    @OutputFile abstract RegularFileProperty getImageId()

    @javax.inject.Inject abstract ExecOperations getExecOps()

    @TaskAction
    void run() {
        def df   = dockerfile.get().asFile
        def name = imageName.get()
        execOps.exec { commandLine "docker", "build", "-f", df, "-t", name, df.parent }
        imageId.get().asFile.text = name
    }
}
```

**Wiring**

```groovy
def ext = extensions.create("docker", DockerExtension, objects)
tasks.register("dockerBuild", DockerBuildTask) {
    imageName.convention(providers.provider { "${project.name}:${project.version}" })
    dockerfile.convention(layout.projectDirectory.file("Dockerfile"))
    imageId.convention(layout.buildDirectory.file("docker/image.txt"))
}
```

**Archive properties — setter style**

```groovy
tasks.named("jar", Jar).configure {
    archiveBaseName.set("gradle-docker")
// Prefer .set(...) for clarity and future parity with Kotlin DSL
}
```


## 8) Build Script Hygiene

- Paths via `layout.*` providers; no string-concat of `build/...`.
- Don’t probe FS in configuration; wire via other task outputs/providers.
- Avoid `doFirst`/`doLast` that mutate tasks or touch `project`; configure via task APIs/providers.

## 9) Testing Plugins (TestKit)

```groovy
plugins { id("java-gradle-plugin") }

dependencies {
    testImplementation(gradleTestKit())  // functional tests via GradleRunner
// `java-gradle-plugin` already adds `gradleApi()` to implementation
}
```

- Consider a functionalTest source set with its own GradleRunner tests.

## 10) Java Toolchains (Java 21)

```groovy
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
```

## 11) Verification & CI Guardrails

```bash
# Config cache store + reuse
./gradlew <tasks> --configuration-cache
./gradlew <tasks> --configuration-cache

# Show all deprecations + traces
./gradlew <tasks> --warning-mode=all --stacktrace -Dorg.gradle.deprecation.trace=true

# Build cache on/off (if not set in properties)
./gradlew <tasks> --build-cache
```

**Recommended `gradle.properties` (CI)**

```properties
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.configuration-cache.problems=warn
org.gradle.warning.mode=all
```


## 12) 9→10 Migration Checklist

- [ ] No `Project` access inside `@TaskAction`; required services injected.
- [ ] No `afterEvaluate`; no task configuration from other tasks during execution.
- [ ] All dynamic values are Providers; no eager `.get()` in configuration.
- [ ] Tasks declare inputs/outputs; deterministic tasks are `@CacheableTask`; side-effecting tasks disabled from caching.
- [ ] File inputs use `PathSensitivity.RELATIVE`; no absolute paths in inputs.
- [ ] Reproducible archives configured (or defaults verified).
- [ ] No APIs flagged by `--warning-mode=all` (fix warnings proactively).
- [ ] TestKit functional tests pass; second run with `--configuration-cache` reuses cache.

## 13) Quick Gotchas (fix on sight)

- `project.exec { … }` / `Runtime.exec(...)` in actions → inject and use `ExecOperations`.
- `afterEvaluate { … }` orchestration → replace with providers + task registration.
- Reading env/sysprops in configuration → move to `ValueSource` or provider.
- `doFirst`/`doLast` that mutate inputs/other tasks → configure via task APIs/providers instead.
- Strings holding file paths as `@Input` → switch to `@InputFile`/`@InputFiles` with proper path sensitivity.
- Groovy direct property assignment on archives → prefer `.set(...)`.

## 14) Provider Patterns (copy/paste)

**Lazy file under build dir**

```groovy
def rel    = name.map { n -> "out/${n}.txt" }
def output = layout.buildDirectory.file(rel) // Provider<RegularFile>
```

**Combine two values**

```groovy
def path = providers.zip(name, version) { n, v -> "dist/${n}-${v}.tar" }
```

**Consume another task’s output**

```groovy
def jarTask = tasks.named("jar", Jar)
from(jarTask.flatMap { it.archiveFile })
```

**Environment & properties**

```groovy
def token    = providers.gradleProperty("TOKEN").orElse("")
def epochIso = providers.environmentVariable("SOURCE_DATE_EPOCH")
.map { java.time.Instant.ofEpochSecond(it.toLong()).toString() }
```
