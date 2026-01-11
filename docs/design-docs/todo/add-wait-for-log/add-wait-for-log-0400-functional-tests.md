# Design Document: Add `waitForLog` Block to `dockerTest` DSL -- Functional Tests

## Prerequisites

Read these documents first:
- `add-wait-for-log-0000-overview.md` - Feature overview and context
- `add-wait-for-log-0200-implementation.md` - Implementation details and component structure
- `add-wait-for-log-0300-unit-tests.md` - Unit test specifications

Reference project testing standards:
- `docs/project-standards/testing/functional-testing.md`

## Purpose

Define functional test specifications for the `waitForLog` feature implementation. Functional tests verify:
- Gradle plugin DSL configuration
- Task registration and wiring
- Validation error messages
- Configuration cache compatibility
- Property propagation without actual Docker execution

This document should contain functional tests ONLY. Unit tests are in `add-wait-for-log-0300-unit-tests.md`,
integration tests are in `add-wait-for-log-0500-integration-tests.md`.

## Checklist

- [ ] Functional tests for `waitForLog` DSL configuration
- [ ] Functional tests for validation error messages
- [ ] Functional tests for property wiring
- [ ] Functional tests for `waitForLog` with `Lifecycle.CLASS`
- [ ] Functional tests for `waitForLog` with `Lifecycle.METHOD`
- [ ] Configuration cache verification tests (Phase 4)
- [ ] Verify all functional tests pass

### Final Verification
- [ ] All functional tests pass
- [ ] No compilation warnings

---

## Notes from Implementation

The following content was extracted from the implementation document (`add-wait-for-log-0200-implementation.md`)
and should be incorporated into the functional test specifications below.

### Configuration Cache Verification (Phase 4 from Implementation)

These verification steps should be implemented as functional tests:

- [ ] **MapProperty<String, List<String>> serialization verification** (CRITICAL):
  - [ ] Create functional test with `waitForLog` DSL containing multiple services
  - [ ] Run with `--configuration-cache` flag (first run)
  - [ ] Run with `--configuration-cache` flag again - **MUST say "Reusing configuration cache"**
  - [ ] If second run says "Calculating task graph", serialization has failed - implement fallback
- [ ] **Pattern content verification**:
  - [ ] Test with simple patterns: `['Started Application']`
  - [ ] Test with regex special characters: `['\\[INFO\\].*started', 'port:\\s+\\d+']`
  - [ ] Test with escape sequences: `['message: \\"ready\\"', 'path\\\\to\\\\file']`
  - [ ] Test with case-insensitive flag patterns: `['(?i)ready', '(?i)started']`
  - [ ] Verify patterns match correctly after cache restore (values not corrupted)
- [ ] **Multi-service verification**:
  - [ ] Test with 3+ services in `waitForServices` map
  - [ ] Test with services having different numbers of patterns (1, 3, 5 patterns)
  - [ ] Test with `rejectPatterns` populated for some but not all services
- [ ] **Edge case verification**:
  - [ ] Test with empty `rejectPatterns` (should serialize as empty map `{}`)
  - [ ] Test with very long pattern strings (500+ characters)
  - [ ] Test with Unicode characters in patterns
- [ ] **If MapProperty serialization fails**:
  - [ ] Implement JSON String fallback per Section 17 of implementation doc
  - [ ] Re-run all above tests with fallback implementation
  - [ ] Document the limitation in release notes

### Testing Consideration from Implementation

Include functional tests with patterns containing:
- Backslashes: `'\\n'`, `'path\\to\\file'`
- Quotes: `'"value"'`, `"'value'"`
- Special regex chars: `'.*?'`, `'\\d+'`, `'(?i)pattern'`

---

## Functional Test Specifications

### 1. DSL Configuration Tests

**File**: `plugin/src/functionalTest/groovy/com/kineticfire/gradle/docker/WaitForLogDslFunctionalTest.groovy`

TODO: Define test specifications

### 2. Validation Error Tests

TODO: Define test specifications for validation error messages

### 3. Configuration Cache Tests

TODO: Define test specifications based on the notes from implementation above

### 4. Property Wiring Tests

TODO: Define test specifications

---

## Test Execution Commands

```bash
# Run functional tests only
cd plugin && ./gradlew clean functionalTest

# Run functional tests with configuration cache
cd plugin && ./gradlew clean functionalTest --configuration-cache
```
