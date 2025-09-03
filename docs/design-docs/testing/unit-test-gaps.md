# Unit Test Gaps

This document captures gaps in unit testing.

## Gap Entry Template

Unit test gaps must be recorded with this template.

```md
### <module>/<path>: <ClassOrNs>#<method|function>  <!-- GAP-ID: TG-YYYYMMDD-XXX -->
- Extent: <lines/branches uncovered, %>
- Reason: <why unit coverage is impractical or unsafe>
- Compensating tests: <links to FT/IT cases or plan>
- Owner: <name> | Target removal date: <YYYY-MM-DD>
```

## Unit Test Gaps

### plugin/com.kineticfire.gradle.docker.service: DockerServiceImpl#buildImage  <!-- GAP-ID: TG-20250902-001 -->
- Extent: 95% of service package instructions, 95% branches
- Reason: Requires live Docker daemon connection, process execution, and external system state
- Compensating tests: Integration tests in `plugin-integration-test/` cover Docker build workflows with real Docker daemon
- Owner: Claude Code | Target removal date: N/A (Architecture decision - external dependency)

### plugin/com.kineticfire.gradle.docker.service: DockerServiceImpl#tagImage  <!-- GAP-ID: TG-20250902-002 -->
- Extent: Docker tag operations (~30 instructions, multiple branches)
- Reason: Requires Docker daemon interaction and image repository state validation
- Compensating tests: Integration tests verify tag operations with actual images
- Owner: Claude Code | Target removal date: N/A (Architecture decision - external dependency)

### plugin/com.kineticfire.gradle.docker.service: DockerServiceImpl#pushImage  <!-- GAP-ID: TG-20250902-003 -->
- Extent: Docker registry push operations (~40 instructions, auth branches)
- Reason: Requires Docker registry authentication, network connectivity, and external registry state
- Compensating tests: Integration tests with test registries and mock authentication scenarios
- Owner: Claude Code | Target removal date: N/A (Architecture decision - external dependency)

### plugin/com.kineticfire.gradle.docker.service: ComposeServiceImpl#upStack  <!-- GAP-ID: TG-20250902-004 -->
- Extent: Compose orchestration (~60 instructions, health check branches)
- Reason: Requires Docker Compose binary, multi-container coordination, and service health monitoring
- Compensating tests: Functional tests with real Compose stacks in `plugin-integration-test/`
- Owner: Claude Code | Target removal date: N/A (Architecture decision - external dependency)

### plugin/com.kineticfire.gradle.docker.service: ComposeServiceImpl#downStack  <!-- GAP-ID: TG-20250902-005 -->
- Extent: Compose teardown operations (~25 instructions, cleanup branches)
- Reason: Requires Docker Compose binary and container lifecycle management
- Compensating tests: Integration tests verify complete stack teardown scenarios
- Owner: Claude Code | Target removal date: N/A (Architecture decision - external dependency)

### plugin/com.kineticfire.gradle.docker.service: ComposeServiceImpl#getServiceState  <!-- GAP-ID: TG-20250902-006 -->
- Extent: Service state monitoring (~35 instructions, status parsing branches)
- Reason: Requires running containers and Compose service inspection commands
- Compensating tests: Integration tests monitor actual service states during stack operations
- Owner: Claude Code | Target removal date: N/A (Architecture decision - external dependency)

### plugin/com.kineticfire.gradle.docker.service: *ServiceImpl#processExecution  <!-- GAP-ID: TG-20250902-007 -->
- Extent: Process execution and output parsing (~50 instructions across all services)
- Reason: Platform-specific process management, output stream handling, and timeout management
- Compensating tests: Integration tests exercise real command execution with various scenarios
- Owner: Claude Code | Target removal date: N/A (Architecture decision - system integration)

## Architecture Decision: Service Package Coverage Strategy

**Decision**: Service package maintains 5-10% unit test coverage by design

**Rationale**:
- Docker operations are inherently integration-dependent
- Unit testing Docker daemon interactions requires complex mocking that doesn't provide meaningful validation
- Real Docker behavior varies by platform, version, and configuration
- Process execution and system command invocation are platform-specific

**Compensating Testing Strategy**:
1. **Unit Tests** (5-10% coverage): Focus on input validation, parameter parsing, and error message formatting
2. **Integration Tests** (90%+ operational coverage): Exercise real Docker operations with actual daemon
3. **Functional Tests** (End-to-end coverage): Complete plugin workflows with real build scenarios

**Success Metrics**:
- Unit tests: Validate all input processing and error handling logic
- Integration tests: 90%+ coverage of Docker operation scenarios  
- Functional tests: Complete plugin lifecycle validation
- Combined: 100% validation of plugin functionality across appropriate test layers

## Coverage Targets by Package

| Package | Unit Test Target | Integration Test Coverage | Rationale |
|---------|------------------|---------------------------|-----------|
| **Exception** | 100% | N/A | Pure logic, no external dependencies |
| **Model** | 95%+ | N/A | Data classes with minimal external dependencies |
| **Extension** | 90%+ | Configuration validation | DSL parsing with Gradle integration |
| **Task** | 85%+ | Task execution scenarios | Task logic minus service calls |
| **Main Plugin** | 80%+ | Plugin lifecycle tests | Gradle integration points |
| **Service** | 5-10% | 90%+ operational coverage | External Docker dependencies |
| **Specification** | 80%+ | Configuration scenarios | Specification validation logic |

**Overall Strategy**: Achieve comprehensive testing through appropriate test layer selection based on dependency characteristics and testability constraints.