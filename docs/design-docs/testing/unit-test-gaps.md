# Unit Test Coverage Gaps

This document tracks code that cannot be unit tested due to technical limitations, along with justification and
alternative testing strategies.

## Purpose

Per the project's 100% unit test coverage requirement, this document explicitly identifies and justifies any code that
cannot achieve full unit test coverage. Each gap must include:
- Description of the untested code
- Root cause of the testing limitation
- Justification for why the gap is acceptable
- Alternative testing strategies (if any)
- Potential solutions for future resolution

---

## Active Gaps

### 1. DockerServiceImpl - Docker Java API Mocking Limitation

**Status**: DOCUMENTED GAP - Tests exist but disabled
**Affected Package**: `com.kineticfire.gradle.docker.service`
**Coverage Impact**: ~2,500 instructions (~46% of service package coverage gap)

#### Description

DockerServiceImpl uses the Docker Java Client library to interact with the Docker daemon. The implementation includes
methods for:
- `buildImage()`: Building Docker images from Dockerfiles
- `tagImage()`: Applying tags to Docker images
- `saveImage()`: Saving images to tar files with various compression formats
- `pushImage()`: Publishing images to Docker registries
- `pullImage()`: Pulling images from Docker registries
- `imageExists()`: Checking if an image exists
- `close()`: Cleanup of Docker client and executor resources

#### Root Cause

The Spock testing framework cannot create mocks for certain Docker Java API command classes due to bytecode
compatibility issues. When attempting to mock classes like `BuildImageCmd`, `TagImageCmd`, `SaveImageCmd`, etc.,
Spock throws:

```
org.spockframework.mock.CannotCreateMockException: Cannot create mock for class ...
```

This is a known limitation when mocking certain Java library classes with Spock's bytecode-based mocking approach.

#### Current Test Status

A comprehensive test suite exists at:
`plugin/src/test/groovy/com/kineticfire/gradle/docker/service/DockerServiceImplComprehensiveTest.groovy`

The test file contains 24 well-written test cases covering:
- ✓ buildImage with all nomenclature parameters
- ✓ buildImage with multiple tags
- ✓ buildImage with labels (applied and empty)
- ✓ buildImage error handling
- ✓ tagImage with multiple tags
- ✓ tagImage error handling
- ✓ saveImage with all compression types (NONE, GZIP, ZIP, BZIP2, XZ)
- ✓ saveImage error handling
- ✓ pushImage with/without authentication
- ✓ pushImage error handling
- ✓ pullImage with/without authentication
- ✓ pullImage error handling
- ✓ imageExists for all scenarios (found, not found, errors)
- ✓ close with executor shutdown handling

**All tests are disabled with:**
```groovy
@spock.lang.Ignore("Spock CannotCreateMockException - see DockerServiceLabelsTest for label tests")
```

#### Justification

This gap is acceptable because:

1. **Comprehensive tests exist**: The test suite is complete and ready to run if the mocking issue is resolved
2. **Alternative coverage exists**: DockerServiceLabelsTest provides partial coverage for label functionality
3. **Integration tests provide validation**: The plugin includes comprehensive integration tests that exercise
   DockerServiceImpl against a real Docker daemon
4. **External library limitation**: The gap is caused by a third-party library limitation, not design issues in our
   code
5. **Well-documented**: The test file clearly documents the issue and provides a reference to the alternative test

#### Alternative Testing Strategies

1. **Integration Tests**: DockerServiceImpl is fully exercised by integration tests in
   `plugin-integration-test/docker/scenario-*/` which test against a real Docker daemon
2. **Partial Unit Tests**: DockerServiceLabelsTest provides unit test coverage for BuildContext label functionality
3. **Manual Testing**: The plugin is tested manually during development with actual Docker operations

#### Potential Solutions

Future approaches to resolve this gap:

1. **Alternative Mocking Framework**: Investigate Mockito or PowerMock as alternatives to Spock's mocking
2. **Bytecode Mock Maker**: Research Spock mock-maker configuration options for better library compatibility
3. **Refactoring**: Further abstract Docker Java API interactions behind simpler interfaces that can be mocked
4. **Upgrade Libraries**: Monitor Spock and Docker Java Client updates for compatibility improvements

#### File References

- Implementation: `plugin/src/main/groovy/com/kineticfire/gradle/docker/service/DockerServiceImpl.groovy`
- Disabled Tests:
  `plugin/src/test/groovy/com/kineticfire/gradle/docker/service/DockerServiceImplComprehensiveTest.groovy`
- Partial Tests: `plugin/src/test/groovy/com/kineticfire/gradle/docker/service/DockerServiceLabelsTest.groovy`
- Integration Tests: `plugin-integration-test/docker/scenario-*/`

---

## Coverage Statistics

### Service Package Coverage (com.kineticfire.gradle.docker.service)

**Current Coverage**: 54.0% instruction, 66.5% branch

**Breakdown by Class**:
- ✅ ExecLibraryComposeService: 89% instruction, 93% branch (67 unit tests, all passing)
- ✅ JsonServiceImpl: 99% instruction, 100% branch
- ✅ DefaultServiceLogger: 100% instruction
- ✅ ProcessResult: 100% instruction, 100% branch
- ⚠️ DefaultCommandValidator: 95% instruction, 90% branch (minor gaps)
- ⚠️ DefaultProcessExecutor: 77% instruction, 66% branch (minor gaps)
- ❌ DockerServiceImpl: 30% instruction, 0% branch (documented gap - tests exist but disabled)
- ❌ DockerServiceImpl closures: 0% coverage (dependent on main class tests)

**If DockerServiceImpl gap were resolved**: Service package would achieve ~95% instruction coverage, ~98% branch
coverage

---

## Review Schedule

This document should be reviewed:
- When upgrading Spock framework versions
- When upgrading Docker Java Client versions
- When investigating alternative mocking frameworks
- Quarterly during project maintenance reviews

---

## Document History

- **2025-10-13**: Initial documentation of DockerServiceImpl gap. Comprehensive unit test suite exists (24 tests) but
  disabled due to Spock CannotCreateMockException with Docker Java API classes. Integration test coverage provides
  validation.
