# Functional Requirements Document (FRD)

A Functional Requirements Document (FRD) provides a detailed guide that specifies what a software or system must do by 
describing its features, functions, and user interactions. It acts as a blueprint, explaining the system's purpose and 
how users will interact with it, ensuring that the final product meets user needs and business goals.

## List of Functional Requirements

| Use Case ID | Requirement ID | Description                                                                                                              | Status           |
|-------------|----------------|--------------------------------------------------------------------------------------------------------------------------|------------------|
| uc-1        | fr-1           | The plugin shall enforce minimum Java version 21 during build                                                            | Complete (fs-1)  |
| uc-1        | fr-2           | The plugin shall enforce minimum Gradle version 9.0.0 during build                                                       | Complete (fs-2)  |
| uc-1        | fr-3           | The plugin shall enforce minimum Groovy version 4.0 during build                                                         | Complete (fs-3)  |
| uc-1        | fr-4           | The plugin shall validate compatibility across supported version matrix through automated testing                        | Complete (fs-4)  |
| uc-2        | fr-5           | The plugin shall support Gradle Configuration Cache                                                                      | Complete (fs-5)  |
| uc-2        | fr-6           | The plugin shall use Provider API for lazy evaluation                                                                    | Complete (fs-6)  |
| uc-2        | fr-7           | The plugin shall display clear error messages for version compatibility issues                                           | Complete (fs-7)  |
| uc-3        | fr-8           | The plugin shall use Docker Java Client library for Docker daemon operations (build, tag, push, save)                    | Complete (fs-8)  |
| uc-3        | fr-9           | The plugin shall use Spock Framework for BDD-style testing                                                               | Complete (fs-9)  |
| uc-3        | fr-10          | The plugin shall use Jackson library for JSON parsing                                                                    | Complete (fs-10) |
| uc-6        | fr-11          | The plugin shall provide a `dockerBuild` task to build Docker images                                                     | Complete (fs-11) |
| uc-6        | fr-12          | The plugin shall provide a `dockerSave` task to save Docker images to tar.gz files                                       | Complete (fs-12) |
| uc-6        | fr-13          | The plugin shall provide a `dockerTag` task to tag Docker images                                                         | Complete (fs-13) |
| uc-6        | fr-14          | The plugin shall provide a `dockerPublish` task to publish Docker images to registries                                   | Complete (fs-14) |
| uc-6        | fr-15          | The plugin shall support multiple Docker images per project                                                              | Complete (fs-15) |
| uc-6        | fr-16          | The plugin shall generate per-image tasks (e.g., `dockerBuildAlpine`, `dockerSaveUbuntu`)                                | Complete (fs-16) |
| uc-6        | fr-17          | The plugin shall support Docker build context configuration with fallback to `src/main/docker`                           | Complete (fs-17) |
| uc-6        | fr-18          | The plugin shall support Docker build arguments configuration                                                            | Complete (fs-18) |
| uc-6        | fr-19          | The plugin shall support multiple image tags per image                                                                   | Complete (fs-19) |
| uc-6        | fr-20          | The plugin shall support multiple registry publishing targets per image                                                  | Complete (fs-20) |
| uc-6        | fr-21          | The plugin shall support registry authentication (username/password)                                                     | Complete (fs-21) |
| uc-6        | fr-22          | The plugin shall support operations on pre-existing images using sourceRef                                               | Complete (fs-22) |
| uc-7        | fr-23          | The plugin shall provide a `composeUp` task to start Docker Compose services                                             | Complete (fs-23) |
| uc-7        | fr-24          | The plugin shall provide a `composeDown` task to stop Docker Compose services                                            | Complete (fs-24) |
| uc-7        | fr-25          | The plugin shall support multiple Docker Compose stack definitions                                                       | Complete (fs-25) |
| uc-7        | fr-26          | The plugin shall wait for services to reach `running` state with configurable timeout                                    | Complete (fs-26) |
| uc-7        | fr-27          | The plugin shall wait for services to reach `healthy` state with configurable timeout                                    | Complete (fs-27) |
| uc-7        | fr-28          | The plugin shall generate JSON state file with service connectivity information                                          | Complete (fs-28) |
| uc-7        | fr-29          | The plugin shall integrate with Gradle Test tasks using `usesCompose` configuration                                      | Complete (fs-29) |
| uc-7        | fr-30          | The plugin shall support test lifecycle management (suite, class, method levels)                                         | Complete (fs-30) |
| uc-7        | fr-31          | The plugin shall capture and store Docker Compose logs                                                                   | Complete (fs-31) |
| uc-3, uc-7  | fr-32          | The plugin shall use the exec library from https://github.com/kineticfire-labs/exec for Docker Compose command execution | Complete (fs-32) |


