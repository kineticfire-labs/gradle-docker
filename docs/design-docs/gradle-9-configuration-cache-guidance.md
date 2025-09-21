# Gradle 9 Configuration & Build Cache Guide (for AI Coding Agents)

## Goal

Write Gradle builds/plugins that reuse the configuration cache and benefit from the build cache. Keep configuration pure 
& lazy; make tasks declarative & deterministic.

## Core Rules (do these every time)

- Model values with Properties/Providers: use `Property<T>`, `ListProperty<T>`, `MapProperty<K,V>`,
  `RegularFileProperty`, `DirectoryProperty`.
- Be lazy: compose values via `map/flatMap/zip`; do not call `.get()` during configuration.
- Register tasks: `tasks.register("X", Type)`; configure via `.configure {};` avoid `tasks.create`.
- No side effects in configuration: no I/O, no network, no system time, no environment reads. Use `ProviderFactory` 
  instead.
- No Project access during execution: don’t call `task.project/project.*` inside `@TaskAction`.
- Defaults via `convention(...)`, overrides via `set(...)`.
- Lock when ready: `finalizeValueOnRead()` or `disallowChanges()` after configuration.

## Task Design (make caching work)

- Declare inputs/outputs precisely:
   - `@Input`, `@Optional`, `@Nested`, `@Internal`
   - `@InputFile/@InputFiles` with `@PathSensitive(PathSensitivity.RELATIVE)`
   - `@OutputFile/@OutputDirectory`
- Cacheability:
   - Pure, deterministic tasks → `@CacheableTask` 
   - External/mutable side effects (network/daemons/CLIs) → `@DisableCachingByDefault("…reason…")`
- Evaluate lazily: only read Properties/Providers inside `@TaskAction`.

## Provider Patterns (copy/paste)

Derive a file under build dir (lazy):
```groovy
def rel = name.map { n -> "out/${n}.txt" }
def output = layout.buildDirectory.file(rel) // Provider<RegularFile>
```

Combine two values (lazy):
```groovy
def path = providers.zip(name, version) { n, v -> "dist/${n}-${v}.tar" }
```

Consume another task’s output (lazy):
```groovy
def jarTask = tasks.named("jar", Jar)
from(jarTask.flatMap { it.archiveFile })
```

Environment & properties (lazy):
```groovy
def token = providers.gradleProperty("TOKEN").orElse("")
def epochIso = providers.environmentVariable("SOURCE_DATE_EPOCH")
  .map { java.time.Instant.ofEpochSecond(it.toLong()).toString() }
```

## File & Path Hygiene
- Compute paths with `ProjectLayout` (`layout.buildDirectory.file/dir`).
- Never hardcode `build/...` strings directly when a Provider is available.
- Avoid filesystem probing in configuration; wire via task outputs/providers.

Avoid These (they break config cache)
- Calling `.get()` on Providers during configuration.
- `new Date()/UUID.random()` or reading env/system props in configuration.
- `doFirst/doLast` that touch `project` or do I/O.
- Accessing `task.project/project.*` inside `@TaskAction`.

## Build Services & External Data
- Use **Build Services** for shared, mutable state across tasks (thread-safe).
- Use **ValueSource** for reproducible, cacheable external lookups.

## Testing & Verification

```bash
# Store config cache
./gradlew <tasks> --configuration-cache

# Reuse config cache
./gradlew <tasks> --configuration-cache

# Inspect problems
./gradlew <tasks> --configuration-cache --configuration-cache-problems=warn
```

**Expect**: “Configuration cache entry stored.” then “Reusing configuration cache.”

**Common errors to fix:**
- `invocation of 'Task.project' at execution time is unsupported`
- Providers resolved at configuration (look for `.get()` in config code)

## Mini Checklist (before you ship)

- [ ] All dynamic values are Properties/Providers (no eager .get()).
- [ ] Tasks registered, not created; no side effects in configuration.
- [ ] Inputs/outputs annotated; cacheability annotated correctly.
- [ ] Paths via layout.*; other task outputs via tasks.named(...).flatMap.
- [ ] Defaults via convention; user overrides via set.
- [ ] Runs clean with --configuration-cache and reuses on second run.