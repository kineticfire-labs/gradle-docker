# Decision: Do Not Implement Phase 2 (Auto-Detection Enhancement)

**Status:** DECIDED - Do Not Implement
**Date:** 2025-12-06
**Author:** Development Team
**Related Documents:**
- [Add Method Workflow Analysis](add-method-workflow-analysis.md) (contains original Phase 2 plan)

---

## Executive Summary

After thorough analysis, Phase 2 (Auto-Detection Enhancement) **should not be implemented**. The marginal user value
(eliminating one annotation per test class) does not justify the implementation risk and the JVM-wide auto-detection
implications for JUnit 5 users.

Phase 1 is complete and functional. The annotation requirement (`@ComposeUp` for Spock, `@ExtendWith` for JUnit 5) is
a minor inconvenience, not a blocking issue. Users are better served by improved documentation and error messages.

---

## What Phase 2 Would Have Done

Phase 2 aimed to eliminate the annotation requirement by implementing global test framework extensions that
auto-detect Docker Compose configuration from system properties set by the pipeline or `usesCompose()`.

**Before (Phase 1 - Current):**
```groovy
@ComposeUp  // Required annotation
class MyIntegrationTest extends Specification {
    def "test something"() { /* ... */ }
}
```

**After (Phase 2 - Not Implementing):**
```groovy
// No annotation needed - auto-detected from system properties
class MyIntegrationTest extends Specification {
    def "test something"() { /* ... */ }
}
```

---

## Reasons for Not Implementing

### 1. Marginal User Value

The annotation requirement adds:
- 1 annotation line per test class
- 1 import statement per test class

For a typical project with 10 integration test classes, this is 20 lines of code total. This is not significant
friction, and the annotations serve a documentation purpose—they make it immediately clear that a test class uses
Docker Compose.

### 2. JVM-Wide Auto-Detection Risk (JUnit 5)

JUnit 5's extension auto-detection is a **JVM-wide setting**:

```properties
junit.jupiter.extensions.autodetection.enabled=true
```

When enabled, this activates **ALL** extensions on the classpath registered via
`META-INF/services/org.junit.jupiter.api.extension.Extension`—not just the Docker Compose extension.

**Real-world implications:**

| Scenario | Risk Level | Consequence |
|----------|------------|-------------|
| User has Testcontainers on classpath | Medium | Testcontainers extensions may activate |
| User has Spring Boot Test on classpath | Medium-High | Spring's extensions may interact unexpectedly |
| User has custom company extensions | High | Unknown behavior changes |
| User adds new dependency with hidden extension | High | Silent behavior change in future |

This risk is **unique to JUnit 5**. Spock's `IGlobalExtension` is always active, so users already accept that risk
when using Spock. However, forcing JUnit 5 users to enable JVM-wide auto-detection to save one annotation is not a
reasonable trade-off.

### 3. Framework Asymmetry

- **Spock:** Auto-detection is always enabled (no opt-in required)
- **JUnit 5:** Requires explicit opt-in via properties file or system property

This asymmetry creates two different user experiences for the same feature, leading to confusion and inconsistent
documentation.

### 4. Implementation Risk

| Risk | Probability | Impact |
|------|-------------|--------|
| Both extensions fire (annotation + global) | Medium | High |
| JUnit 5 extension ordering causes conflicts | Medium | Medium |
| Hardcoded defaults override system properties | High (requires Step 1 fix) | High |
| ServiceLoader discovery issues | Low | High |

While these risks are manageable, they represent significant implementation and testing effort for marginal benefit.

### 5. Maintenance Burden

Phase 2 would add:
- 2 new global extension classes (Spock + JUnit 5)
- 2 new META-INF/services files
- Complex conflict detection logic
- Ongoing maintenance for two test framework integrations

### 6. Phase 1 Already Works

Phase 1 is complete and functional. The annotation requirement is a minor inconvenience that:
- Guides users to the correct setup
- Serves as documentation
- Creates no risk of unexpected behavior
- Works consistently across both test frameworks

---

## Cost-Benefit Analysis

| Factor | Phase 2 Full | Phase 2 Spock-Only | Improve Phase 1 |
|--------|--------------|-------------------|-----------------|
| User value | Medium | Low-Medium | Low |
| Implementation effort | 26-28 hours | 12-15 hours | 4-8 hours |
| Implementation risk | Medium-High | Low-Medium | Very Low |
| JVM-wide risk | High (JUnit 5) | None | None |
| Maintenance burden | High | Medium | Low |
| Breaking change risk | Medium | Low | None |

---

## Alternative: Improve Phase 1 Instead

Rather than implementing Phase 2, improve the Phase 1 user experience:

### 1. Better Error Messages (Recommended)

When a test runs without the required annotation, provide a clear error:

```
ERROR: Test class 'MyIntegrationTest' is configured with lifecycle=METHOD but missing required annotation.

Add one of:
  Spock:   @ComposeUp
  JUnit 5: @ExtendWith(DockerComposeMethodExtension.class)

This annotation is required to enable per-method container lifecycle management.
```

**Effort:** 2-4 hours
**Risk:** None
**Value:** Guides users to correct solution immediately

### 2. IDE Templates

Provide IntelliJ IDEA live templates for common patterns:

```groovy
// Template: Spock integration test with compose
@ComposeUp
class $NAME$ extends Specification {
    def "$TEST_NAME$"() {
        $END$
    }
}
```

**Effort:** 4-6 hours (documentation + templates)
**Risk:** None

### 3. Documentation Improvements

- Explain **why** the annotation is required (test framework lifecycle hooks)
- Provide copy-paste examples for both Spock and JUnit 5
- Add troubleshooting section for common errors

**Effort:** 2-3 hours
**Risk:** None

---

## If Auto-Detection Is Reconsidered in the Future

If the decision is revisited, consider:

1. **Spock-only implementation:** Eliminates JVM-wide risk since Spock auto-detection is always enabled anyway.
   Effort: 12-15 hours.

2. **Explicit opt-in property:** Add `docker.compose.autoDetect=true` system property as an explicit signal,
   rather than relying on presence of other properties. This makes activation intentional.

3. **Build-time validation:** Instead of runtime auto-detection, validate at build time that test classes have
   the required annotations. Provide a Gradle task that reports which classes need annotations.

---

## Conclusion

**Phase 2 should not be implemented.** The annotation requirement in Phase 1 is a minor papercut, not a wound.
The cure (Phase 2 auto-detection) carries more risk than the disease:

- JVM-wide auto-detection implications for JUnit 5 users
- Framework asymmetry between Spock and JUnit 5
- Implementation risk and maintenance burden
- Marginal user value (1 annotation per test class)

Users are better served by clear documentation, helpful error messages, and IDE templates than by complex
auto-detection machinery that introduces new risks.

**Recommended action:** Improve Phase 1 documentation and error messages (4-8 hours effort, zero risk).
