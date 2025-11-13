# Task Graph Calculation Message for Functional Tests

## Observed Message

When running the full build command that includes `functionalTest`:

```bash
./gradlew -Pplugin_version=1.0.0 clean functionalTest build publishToMavenLocal
```

Gradle displays:

```
Calculating task graph as no cached configuration is available for tasks: clean functionalTest build publishToMavenLocal
```

## Why This is Normal

This message is **expected behavior** and **not an error**. The `functionalTest` task is explicitly marked as
incompatible with Gradle's configuration cache due to TestKit limitations.

### Root Cause

- **TestKit Incompatibility**: Gradle TestKit (used by functional tests) has known conflicts with configuration cache
  in Gradle 9 (GitHub issue #34505)
- **Correct Configuration**: The `functionalTest` task in `build.gradle` (lines 328-331) is correctly marked with:
  ```groovy
  notCompatibleWithConfigurationCache("TestKit service cleanup conflicts with configuration cache")
  ```
- **Isolated Impact**: This only affects commands that include `functionalTest`. The main build works fine with
  configuration cache.

### Verification

Configuration cache **does work** when running without `functionalTest`:

```bash
./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal
# Result: "Configuration cache entry reused" ✓
```

## Recommendations

### Option 1: Run Tests Separately (Recommended)
Get fast cached builds while still running all tests:

```bash
# Fast build with configuration cache
./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal

# Then run functional tests
./gradlew -Pplugin_version=1.0.0 functionalTest
```

### Option 2: Accept the Warning
Run everything together - the build works correctly, it just recalculates the task graph:

```bash
./gradlew -Pplugin_version=1.0.0 clean functionalTest build publishToMavenLocal
# Warning appears but build succeeds normally
```

## Summary

The "Calculating task graph as no cached configuration is available" message is **working as designed**. It cannot be
eliminated when including `functionalTest` in the command due to TestKit's genuine incompatibility with configuration
cache. This is harmless and does not indicate a problem with the build.
