# CLAUDE.md
This document provides guidance to Claude Code when working in this source code repository.

## PURPOSE
The purpose of the `gradle-docker` project is to create and publish a Gradle plugin to interact with Docker to support:
- the build, tagging, and publication of Linux (Docker) images
- the control of Docker Compose (e.g., `up` and `down`) to support testing of Docker images or systems composed of 
Docker images
- functionality to wait for a Docker image or group of Docker images, run either with `docker` or `docker compose` to be
ready for testing, indicated by the container or group of containers in states of `RUNNING` or `HEALTHY`

## TECH STACK
- **Language**: Groovy (for the plugin) and Java (for the integration test application)
- **Build and Dependency Management**: Gradle
- **Testing and Specification Framework**: JUnit and Spock
- **Test DSL**: Groovy
- **Version Control:** Git
- **AI-assisted Infrastructure-as-Code & DevOps:** Claude Code

## PROJECT STRUCTURE
- `plugin/`: the subproject to implement the plugin
   - `plugin/src/main`: the source code for the plugin
   - `plugin/src/test`: unit tests for the plugin
   - `plugin/src/functionalTest`: functional tests for the plugin
- `plugin-integration-test/`: the subproject to implement the plugin's integration test, by using a `build.gradle` that
  uses the plugin to build a Docker image with a Java application
   - `plugin-integration-test/src/`: the application's source code
   - `plugin-integration-test/test/`: unit tests for the application
   - `plugin-integration-test/build.gradle`: the Gradle build for the test application that uses the plugin
- `docs/`: documentation
   - `docs/development-philosophies/development-philosophies.md`: guiding development philosophies 
   - `docs/style/`: style guide 
- `README.md`: project overview

### Commands
1. Run unit and functional tests
   1. From the `plugin/` directory, run `./gradlew clean test` 
1. Build the project
   1. From the `plugin/` directory, run `./gradlew clean build`
      1. Runs both unit tests and functional tests, then builds the plugin to 
      `plugin/build/libs/example-gradle-plugin-<version>.jar` 
1. Run integration tests
   1. From the `plugin-integration-test/` directory, run `./gradlew -PpluginVersion=1.0.0 clean build` where the 
   property `PpluginVersion` is the plugin's version 
   1. Note: must first build the plugin to `plugin/build/libs/example-gradle-plugin-<version>.jar` by running 
   `./gradlew clean build` from the `plugin/` directory

### Searching the Code Base
1. Don't use grep `grep -r "pattern" .`, and instead use rg `rg "pattern"`.
1. Don't use find with name `find . -name "*.clj`, and instead use rg with file filtering as either `rg --files | rg 
   "\.clj$" or rg --files -g "*.clj"`