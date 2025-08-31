# Non-Functional Requirements Document (NFRD)

A Non-Functional Requirements Document (NFRD) outlines the quality attributes and constraints of a system, describing 
how the system should work rather than what it should do. These documents are critical for setting measurable goals for 
performance, security, usability, reliability, and maintainability, and they are essential for guiding development, 
ensuring user satisfaction, and achieving project success.

## List of Non-Functional Requirements

| Use Case ID | Requirement ID | Description                                                                                                                                                                        | Status |
|-------------|----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|
| uc-1, uc-2  | nfr-1          | The project shall use Gradle version 9.0.0 or above                                                                                                                                | Complete (nfs-1) |
| uc-1, uc-2  | nfr-2          | The project shall use Groovy version 4.0.0 or above                                                                                                                                | Complete (nfs-2) |
| uc-1, uc-2  | nfr-3          | The project shall use Java version 21 or above                                                                                                                                     | Complete (nfs-3) |
| uc-1, uc-2  | nfr-4          | Any dependency not specified with a version should shall use the highest version available and compatible with the specified version dependencies                                  | Complete (nfs-4) |
| uc-1        | nfr-5          | The plugin shall be tested against minimum supported versions (Java 21, Gradle 9.0.0, Groovy 4.0)                                                                                  | Complete (nfs-5) |
| uc-1        | nfr-6          | The plugin shall be tested against current stable versions to ensure forward compatibility                                                                                         | Complete (nfs-6) |
| uc-2        | nfr-7          | The plugin shall provide clear migration guide for users upgrading from older versions                                                                                             | Complete (nfs-7) |
| uc-2        | nfr-8          | The plugin shall publish version compatibility matrix in documentation                                                                                                             | Complete (nfs-8) |
| uc-3        | nfr-9          | The plugin shall use Docker Java Client library for Docker daemon operations (build, tag, push, save)                                                                              | Complete (nfs-9) |
| uc-3        | nfr-10         | The plugin shall isolate library dependencies through abstraction layers                                                                                                           | Complete (nfs-10) |
| uc-3        | nfr-11         | The plugin shall document rationale for each major library choice                                                                                                                  | Complete (nfs-11) |
| uc-4        | nfr-12         | The plugin shall achieve minimum 90% line coverage and 85% branch coverage                                                                                                         | Complete (nfs-12) |
| uc-4        | nfr-13         | The plugin shall be tested with real Docker daemon and containers                                                                                                                  | Complete (nfs-13) |
| uc-4        | nfr-14         | The plugin shall be tested across platforms (Linux, macOS, Windows WSL2)                                                                                                           | Complete (nfs-14) |
| uc-4        | nfr-15         | The plugin shall be tested across Docker CE versions (stable and previous stable)                                                                                                  | Complete (nfs-15) |
| uc-4        | nfr-16         | The plugin shall test failure scenarios (Docker daemon down, network issues, registry unavailable)                                                                                 | Complete (nfs-16) |
| uc-5        | nfr-17         | Clean build shall complete in less than 60 seconds including tests                                                                                                                 | Complete (nfs-17) |
| uc-5        | nfr-18         | Incremental build shall complete in less than 10 seconds for code changes                                                                                                          | Complete (nfs-18) |
| uc-5        | nfr-19         | Unit tests shall execute in less than 30 seconds                                                                                                                                   | Complete (nfs-19) |
| uc-5        | nfr-20         | Functional tests shall execute in less than 2 minutes                                                                                                                              | Complete (nfs-20) |
| uc-5        | nfr-21         | The plugin shall provide precise error locations with actionable suggestions                                                                                                       | Complete (nfs-21) |
| uc-5        | nfr-22         | The plugin shall integrate seamlessly with IntelliJ IDEA and VS Code                                                                                                               | Complete (nfs-22) |
| uc-6, uc-7  | nfr-23         | The plugin shall handle Docker daemon unavailability gracefully with clear error messages                                                                                          | Complete (nfs-23) |
| uc-6, uc-7  | nfr-24         | The plugin shall handle network connectivity issues during registry operations gracefully                                                                                          | Complete (nfs-24) |
| uc-6, uc-7  | nfr-25         | The plugin shall validate all configuration parameters and provide helpful error messages                                                                                          | Complete (nfs-25) |
| uc-7        | nfr-26         | The plugin shall fail fast on service health check timeouts with clear indication of which services failed                                                                         | Complete (nfs-26) |
| uc-8        | nfr-27         | Documentation shall be comprehensive covering all plugin features                                                                                                                  | Complete (nfs-27) |
| uc-8        | nfr-28         | Documentation shall provide tutorials for beginner, intermediate, and advanced users                                                                                               | Complete (nfs-28) |
| uc-8        | nfr-29         | All documentation examples shall be tested with current plugin version                                                                                                             | Complete (nfs-29) |
| uc-8        | nfr-30         | Documentation shall be searchable and well-structured                                                                                                                              | Complete (nfs-30) |
| uc-3, uc-7  | nfr-31         | The plugin shall use the exec library from https://github.com/kineticfire-labs/exec for Docker Compose operations; NOTE: exec library currently supports Linux/Unix platforms only | Complete (nfs-31) |

