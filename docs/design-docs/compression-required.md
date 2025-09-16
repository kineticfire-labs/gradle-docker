# Plan: Make Compression Parameter Required for Save Configuration

## Overview

This document outlines the plan to make the `compression` parameter required for Docker image save configurations, eliminating the current default of `'gzip'` to ensure explicit user control over output format.

## Current Implementation Analysis

### 1. **Current Default Behavior**
- **SaveSpec.groovy**: Line 37 sets `compression.convention("gzip")`
- **DockerSaveTask.groovy**: Line 40 sets `compression.convention(CompressionType.GZIP)`
- **Test Evidence**: ImageSpecTest.groovy line 190 verifies `compression.get() == 'gzip' // default`

### 2. **Key Files Identified**
From grep analysis, these files contain compression-related code:
- `SaveSpec.groovy` - Main spec class with default
- `DockerSaveTask.groovy` - Task implementation with default
- `CompressionType.groovy` - Enum with supported types
- `DockerService.groovy` & `DockerServiceImpl.groovy` - Service interfaces
- `GradleDockerPlugin.groovy` - Plugin configuration
- Multiple test files

### 3. **Breaking Change Policy**
**✅ No Backward Compatibility Required** - This project is in early development stage with:
- No published releases
- No external users
- Internal development only
- Integration tests under our control

## Rationale

### **Problem with Current Defaults**
1. **Unexpected Behavior**: Users specify no compression but get gzipped files
2. **File Format Confusion**: Saved `.tar` files are actually `.tar.gz` compressed
3. **Debugging Issues**: `docker load` fails on compressed files when users expect uncompressed
4. **Implicit Behavior**: No explicit choice about space vs. compatibility tradeoff

### **Benefits of Required Parameter**
1. **Explicit is Better Than Implicit**: Forces deliberate choice about compression
2. **Prevents User Frustration**: No surprising compressed files
3. **Clear File Formats**: Output filename matches actual content
4. **Docker Ecosystem Consistency**: Most Docker tools require explicit compression

## Implementation Plan

### **Phase 1: Core Model Changes**

#### **A. SaveSpec.groovy**
**Current:** Line 37 `compression.convention("gzip")`
**Change:** Remove default completely, add validation
```groovy
// Remove: compression.convention("gzip")
// Remove: pullIfMissing.convention(false) // can stay as reasonable default
// Add validation method to check if compression is present
```

#### **B. DockerSaveTask.groovy**
**Current:** Line 40 `compression.convention(CompressionType.GZIP)`
**Change:** Remove default, add validation in task action
```groovy
// Remove: compression.convention(CompressionType.GZIP)
// Keep: pullIfMissing.convention(false) // reasonable default
// Add validation in saveImage() method before line 67
```

#### **C. DockerExtension.groovy**
**New:** Add validation method for save configurations
```groovy
// Add: validateSaveSpec(SaveSpec saveSpec) method
// Validate compression is present when save block is used
```

### **Phase 2: Unit Test Updates**

#### **A. SaveSpecTest.groovy** (New Tests Needed)
- ✅ Add: `"compression is required when not set"`
- ✅ Add: `"compression validation with valid values"`
- ✅ Add: `"compression validation with invalid values"`
- 🔄 Update existing tests to explicitly set compression

#### **B. ImageSpecTest.groovy** (Existing Tests to Fix)
- 🔄 **Line 178 test**: `"save with defaults"` → **Delete this test entirely** (no defaults anymore)
- 🔄 **Line 144-148**: Add explicit `compression.set('gzip')`
- 🔄 **Line 183-185**: Add explicit `compression.set('none')` or similar
- ✅ **Add new test**: `"save configuration requires compression parameter"`

#### **C. DockerSaveTaskTest.groovy** (Existing Tests to Fix)
- 🔄 Update all existing tests to set compression explicitly
- ✅ Add validation tests for missing compression
- 🔄 **Remove any tests that rely on default compression behavior**

#### **D. CompressionTypeTest.groovy** (Likely Minimal Changes)
- 🔍 Review if any tests depend on defaults (likely none)

### **Phase 3: Validation Logic**

#### **A. DockerExtension.groovy**
```groovy
void validateSaveSpec(SaveSpec saveSpec) {
    if (!saveSpec.compression.present) {
        throw new GradleException(
            "compression parameter is required for save configuration. " +
            "Available options: 'none', 'gzip', 'bzip2', 'xz', 'zip'"
        )
    }

    // Validate compression type is supported
    try {
        CompressionType.fromString(saveSpec.compression.get())
    } catch (Exception e) {
        throw new GradleException(
            "Invalid compression type: '${saveSpec.compression.get()}'. " +
            "Available options: 'none', 'gzip', 'bzip2', 'xz', 'zip'"
        )
    }
}
```

#### **B. Integration with Main Validation**
- Add call to `validateSaveSpec()` in main validation flow
- Ensure it's called during configuration phase

### **Phase 4: Documentation Updates**

#### **A. docs/design-docs/usage.md**
**Current Examples:** Show compression as optional with defaults
**New Examples:** Show compression as required
```groovy
// Before:
save {
    outputFile = file('my-app.tar')  // compression defaulted to gzip
}

// After:
save {
    outputFile = file('my-app.tar')
    compression = 'none'  // Required - no default
}
```

**Add Note:**
> **Breaking Change:** `compression` parameter is now required for save configuration. This ensures explicit control over output format and prevents unexpected compressed files. Since this project is in early development with no external users, no backward compatibility is provided.

## Test Coverage Plan

### **Unit Test Requirements (100% Coverage)**

1. **Validation Tests (New)**:
   - SaveSpec without compression → GradleException
   - SaveSpec with invalid compression → GradleException
   - SaveSpec with each valid compression type → Success

2. **Integration Tests (Updated)**:
   - **All existing save tests updated** to include explicit compression
   - **Remove tests that relied on defaults**
   - New error message validation tests

3. **Regression Tests (Updated)**:
   - ImageSpecTest save examples updated to be explicit
   - DockerSaveTaskTest examples updated to be explicit

### **Error Messages Testing**
- Verify exact error message format
- Test with each invalid compression value
- Test with missing compression

## Migration Path

### **For Internal Integration Tests**
**Simple Direct Update** - No compatibility layer needed:
```groovy
// Old (will now fail):
save {
    outputFile = file('backup.tar')
}

// New (required):
save {
    outputFile = file('backup.tar')
    compression = 'none'  // Explicit choice required
}
```

**Update Strategy:**
1. Update all integration test save configurations in one commit
2. No gradual migration needed since no external users
3. Fix all usage in project simultaneously

## Risk Assessment

### **Low Risk Changes** ✅
- Adding validation logic
- Updating internal test files
- Documentation updates
- **No external impact** since no published releases

### **No Risk of User Impact** ✅
- **No external users to break**
- **No published versions to maintain compatibility with**
- **All usage is under our control**

### **Medium Risk Changes** ⚠️
- Integration test updates (many files to update)
- Ensuring all save configurations are updated

### **Mitigation**
- Comprehensive unit test coverage ensures validation works correctly
- Clear error messages guide developers to correct configuration
- **Batch update all internal usage simultaneously**
- Run full integration test suite to verify all configurations updated

## Implementation Benefits

### **Simplified Implementation**
- **No compatibility layer needed** - clean removal of defaults
- **No migration period** - immediate switch to required parameter
- **Cleaner codebase** - no legacy default handling code
- **Simpler testing** - no need to test both old and new behaviors

### **Development Advantages**
- **Faster implementation** - no backward compatibility complexity
- **Cleaner API** - explicit from day one
- **Better error messages** - no confusion about defaults
- **Future-proof design** - explicit configuration philosophy

## Expected Behavior After Implementation

### **Valid Configuration Examples**
```groovy
// Uncompressed tar file
save {
    outputFile = file('my-app.tar')
    compression = 'none'
}

// Gzip compressed (space efficient)
save {
    outputFile = file('my-app.tar.gz')
    compression = 'gzip'
}

// Other formats
save {
    outputFile = file('my-app.tar.bz2')
    compression = 'bzip2'
}
```

### **Error Cases**
```groovy
// This will now fail with clear error message:
save {
    outputFile = file('my-app.tar')
    // Missing compression parameter!
}
```

**Error Message:**
```
compression parameter is required for save configuration. Available options: 'none', 'gzip', 'bzip2', 'xz', 'zip'
```

## Summary

This plan converts compression from a defaulted optional parameter to a required explicit parameter with **no backward compatibility concerns**. Since the project has no external users and no published releases, we can make a clean breaking change that results in a better, more explicit API. The comprehensive test updates ensure 100% unit test coverage of the new validation logic while removing all default-dependent test cases.

The change eliminates user confusion about file formats while maintaining full flexibility for choosing the appropriate compression method for each use case.