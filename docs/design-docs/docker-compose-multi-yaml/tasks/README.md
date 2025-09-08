# Docker Compose Multi-YAML Implementation Tasks

## Overview
This directory contains bite-sized tasks for implementing multi-file Docker Compose support in the Gradle Docker Plugin. Each task follows a structured approach with code implementation, review, testing, and final review phases.

## Task Structure

### Task Areas
The implementation is divided into the following areas:

1. **ComposeStackSpec Enhancement** - DSL and property updates
2. **Task Configuration Logic** - Plugin configuration and task setup  
3. **Integration Tests** - End-to-end testing with real Docker environment

### Task Types
Each area follows this pattern:
1. **Code Implementation** - Implement the functionality
2. **Code Review** - Review implementation for quality and compliance
3. **Unit Tests** - Write comprehensive unit tests (100% coverage goal)
4. **Unit Test Review** - Review and improve unit tests
5. **Functional Tests** - Write functional tests (may be disabled due to TestKit issues)
6. **Functional Test Review** - Review functional tests and TestKit compatibility
7. **Final Code Review** - Comprehensive review of all code and tests

## Task Execution Order

Execute tasks in the following order:

### Phase 1: ComposeStackSpec Enhancement
```
01-compose-stack-spec/01-code-add-multi-file-properties.md
01-compose-stack-spec/02-review-multi-file-properties.md
01-compose-stack-spec/03-unit-test-compose-stack-spec.md
01-compose-stack-spec/04-review-unit-tests.md
01-compose-stack-spec/05-functional-test-compose-stack-spec.md
01-compose-stack-spec/06-review-functional-tests.md
01-compose-stack-spec/07-final-code-review.md
```

### Phase 2: Task Configuration Logic
```
02-task-configuration/01-code-task-configuration-logic.md
02-task-configuration/02-review-task-configuration.md
02-task-configuration/03-unit-test-task-configuration.md
02-task-configuration/04-review-unit-tests.md
02-task-configuration/05-functional-test-task-configuration.md
02-task-configuration/06-review-functional-tests.md
02-task-configuration/07-final-code-review.md
```

### Phase 3: Integration Tests
```
03-integration-tests/01-integration-test-multi-file-compose.md
03-integration-tests/02-review-integration-tests.md
```

## Key Context for All Tasks

### Expert Context
All tasks assume you are a Principal Software Engineer and expert at:
- Java, Gradle, custom Gradle plugins
- Groovy language and DSL development
- Docker and Docker Compose
- Testing frameworks (Spock, JUnit 5)

### Important Guidelines Referenced

#### Gradle 9 Configuration Cache Guidance
All tasks must follow: `@docs/design-docs/gradle-9-configuration-cache-guidance.md`
- Use `Provider<T>` and `Property<T>` for all dynamic values
- Never call `.get()` on providers during configuration
- Use `.map()`, `.flatMap()`, `.zip()` for provider transformations
- Ensure all properties are serializable

#### TestKit Compatibility Issues
Be aware of: `@docs/design-docs/functional-test-testkit-gradle-issue.md`
- Functional tests may need to be disabled due to Gradle 9 TestKit issues
- `withPluginClasspath()` causes `InvalidPluginMetadataException`
- 18/20 existing functional tests are currently disabled
- Code should be configuration cache compatible

### Build Commands
Standard commands used throughout:
- `./gradlew clean build` - Full build and test
- `./gradlew clean test` - Unit tests only
- `./gradlew clean functionalTest` - Functional tests (may be disabled)
- `./gradlew jacocoTestReport` - Coverage reporting
- `./gradlew clean fullTest` - Integration tests (from plugin-integration-test/)

### Success Criteria
Each task area should achieve:
- 100% unit test coverage for new code
- Build success with `./gradlew clean build`
- Configuration cache compatibility
- Backward compatibility with existing single-file configurations
- Integration with existing plugin architecture

## Important Notes

### Functional Test Status
Due to known Gradle 9 TestKit compatibility issues, functional tests may need to be documented and disabled rather than implemented. This is acceptable and documented in the TestKit issue guidance.

### Configuration Cache Priority
All implementations must be compatible with Gradle 9 configuration cache requirements. This takes precedence over other considerations.

### Backward Compatibility
All changes must maintain full backward compatibility with existing single-file compose configurations.

## Task Dependencies

- **ComposeStackSpec tasks** are independent and can be completed first
- **Task Configuration tasks** depend on ComposeStackSpec completion
- **Integration Test tasks** depend on both previous areas being complete

## Implementation Strategy

1. **Complete one area at a time** before moving to the next
2. **Run builds frequently** to catch issues early
3. **Maintain 100% test coverage** throughout implementation
4. **Test configuration cache compatibility** at each major milestone
5. **Document any functional test limitations** due to TestKit issues