# Technical Debt: Functional Tests Configuration Cache Disabled

## Summary

Configuration cache has been disabled for functional tests to work around Gradle TestKit service cleanup issues that cause test execution to hang with "Could not stop all services" errors.

## Details

### Problem Description

- **Issue**: Functional tests using Gradle TestKit were failing to complete with service cleanup errors
- **Error Pattern**: `DefaultMultiCauseException: Could not stop all services` with extensive stack traces
- **Impact**: Tests would hang indefinitely during shutdown, preventing CI/CD completion
- **Manifestation**: 1 functional test showing as "failed to execute tests" due to service cleanup conflicts

### Root Cause

This is a known incompatibility between:
- **Gradle TestKit**: Creates isolated test environments with their own service contexts
- **Gradle Configuration Cache**: Caches build configuration state including service registrations
- **Service Cleanup**: Complex interdependent service shutdown sequences conflict when cached state doesn't match runtime state

The specific issue occurs when TestKit tries to clean up services that were initialized with different configuration cache states, leading to deadlocks in the service registry shutdown process.

### Workaround Implemented

**Location**: `plugin/build.gradle` - `functionalTest` task configuration

```groovy
tasks.register('functionalTest', Test) {
    // ... other configuration ...

    // Disable configuration cache for functional tests to work around TestKit service cleanup issues
    doNotTrackState("Functional tests use TestKit which has configuration cache conflicts")
    notCompatibleWithConfigurationCache("TestKit service cleanup conflicts with configuration cache")
}
```

**Effect**:
- Functional tests run without configuration cache optimization
- Service cleanup completes successfully
- No impact on main build performance (only affects test execution)

## Technical Debt Assessment

### Debt Category
**Infrastructure/Tooling** - External dependency limitation

### Impact Level
**Low** - Affects only test execution performance, not production functionality

### Mitigation Strategy
**Acceptable Long-term** - This is a targeted workaround for a known Gradle ecosystem issue

## Future Resolution Options

### Option 1: Gradle Version Upgrade
- **Timeline**: Check with each major Gradle release
- **Effort**: Low - version bump and testing
- **Likelihood**: High - Gradle team is aware of TestKit configuration cache issues

### Option 2: Alternative Test Framework
- **Timeline**: Future major refactoring
- **Effort**: High - would require rewriting all functional tests
- **Likelihood**: Low - TestKit is the standard for Gradle plugin testing

### Option 3: Test Structure Changes
- **Timeline**: If test performance becomes critical
- **Effort**: Medium - restructure tests to avoid problematic patterns
- **Likelihood**: Low - current test structure is appropriate

## Monitoring and Review

### Review Triggers
1. **Gradle Major Version Releases**: Test if workaround is still needed
2. **Performance Issues**: If functional test execution becomes too slow
3. **CI/CD Problems**: If test execution time impacts build pipelines

### Success Metrics
- Functional tests complete successfully without hanging
- Test execution time remains acceptable (< 5 minutes total)
- No service cleanup errors in CI logs

## References

- **Gradle Issue Tracker**: Similar issues reported with TestKit + Configuration Cache
- **Implementation**: See `plugin/build.gradle` lines 283-286
- **Related Documentation**: `docs/design-docs/functional-test-testkit-gradle-issue.md`

---

**Created**: 2025-09-25
**Status**: Active
**Priority**: Low
**Review Date**: Next Gradle major release