# Use Case - 1 - Plugin Developer Use Newest Features of Languages

## Document Metadata

| Key     | Value       |
|---------|-------------|
| Status  | Implemented |
| Version | 1.0.0       |
| Updated | 2025-08-31  |

## Definition

**Actor**: Plugin Developer

**Goal**: Use modern language features and maintain compatibility across supported versions in plugin development 
to ensure the `gradle-docker` plugin leverages current best practices and provides robust functionality

**Preconditions**: Development environment with Java 21+, Gradle 9.0.0+, and modern tooling installed

**Post conditions**: Plugin built using modern language features while maintaining compatibility across supported 
version ranges, enabling efficient development and reliable functionality

**Steps by Actor to achieve goal**:
1. Plugin Developer establishes minimum supported versions: Java 21, Gradle 9.0.0, Groovy 4.x
1. Plugin Developer configures build to use Java 21+ toolchain features (records, pattern matching, enhanced switch)
1. Plugin Developer leverages modern Gradle APIs (Provider API, Configuration Cache, lazy configuration)
1. Plugin Developer uses current Groovy 4.x features (improved type checking, static compilation)
1. Plugin Developer validates compatibility across supported version matrix through automated testing
1. Plugin Developer maintains compatibility matrix documentation for supported tool versions

## Technology Requirements

**Minimum Supported Versions**:
- Java: 21 (LTS)
- Gradle: 9.0.0
- Groovy: 4.0

**Modern Features to Leverage**:
- Java 21+: Records, pattern matching, sealed classes, text blocks
- Gradle: Provider API, Configuration Cache, lazy task configuration, modern plugin development practices
- Groovy 4.x: Enhanced static type checking, improved performance, better Java integration

**Compatibility Testing Matrix**:
- Test against minimum versions (Java 21, Gradle 9.0.0)
- Test against current stable versions
- Ensure forward compatibility with reasonable version ranges

**Derived functional requirements**: fr-1, fr-2, fr-3, fr-4

**Derived non-functional requirements**: nfr-1, nfr-2, nfr-3, nfr-4, nfr-5, nfr-6
