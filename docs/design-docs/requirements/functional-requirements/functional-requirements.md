# Functional Requirements Document (FRD)

A Functional Requirements Document (FRD) provides a detailed guide that specifies what a software or system must do by 
describing its features, functions, and user interactions. It acts as a blueprint, explaining the system's purpose and 
how users will interact with it, ensuring that the final product meets user needs and business goals.

## List of Functional Requirements

| Use Case ID | Requirement ID | Description                                                                                                                                                                                                                 | Status |
|-------------|----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|
| uc-6        | fr-1           | The plugin shall provide a Gradle task to clean the temporary Docker context directory, used to build a Docker image.  The result of this task is the temporary directory for the build context is either removed or empty. | Draft  |
| uc-6        | fr-2           | The plugin shall provide a Gradle task configuration to configure the build for zero or more images                                                                                                                         | Draft  |
| uc-6        | fr-2.1         | The plugin shall provide, for the task configuration block to configure the build of images, configuration to set the Docker build context consisting of 0 or more directories (if none, defaults to `src/main/docker`      | Draft  |


