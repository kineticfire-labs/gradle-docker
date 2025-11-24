# gradle-docker
[![Powered by KineticFire Labs](https://img.shields.io/badge/Powered_by-KineticFire_Labs-CDA519?link=https%3A%2F%2Flabs.kineticfire.com%2F)](https://labs.kineticfire.com/)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

<p></p>

`gradle-docker` is a Gradle plugin for building and testing Docker-based projects.

## Table of Contents

todo


## Key Capabilities

## Usage

## Detailed Documentation

See ??? for `docker` DSL to build a new image or reference a previously built image then optionally tag, save, and 
publish an image.

See ??? for `dockerOrch` DSL to test an image.

## Installation

todo after the plugin is proven in a separate project and published to Gradle Plugin Portal

## Building

### Prerequisites
- Java 21 or later
- Gradle 9.0 or later (wrapper included)

### Testing

#### Unit and Functional tests 

Run the unit and functional tests from the directory `plugin/`:

```shell
./gradlew -Pplugin_version=1.0.0 clean build publishToMavenLocal
```

#### Integration Tests

Integration tests require that the public was built using targets `build` and `publishToMavenLocal`.

##### Integration Tests that do NOT Publish to a Public Image Registry

Run the integration tests that do NOT publish to a public image registry.  From the directory 
`plugin-integration-test/`, run the command:

```shell
./gradlew -Pplugin_version=1.0.0 cleanAll integrationTest
```

##### Integration Tests that DO Publish to a Public Image Registry

Run the integration tests that publish to a public image registry.

Set these environment variables:
- `DOCKERHUB_USERNAME`: Docker Hub username
- `DOCKERHUB_TOKEN`: Docker Hub personal access token

From the directory `plugin-integration-test/`, run the command:

```shell
./gradlew -Pplugin_version=1.0.0 docker:scenario-99:dockerHubIntegrationTest -PenableDockerHubTests=true
```

#### Building

From the directory `plugin/`, build the plugin by running the command:

```shell
./gradlew -Pplugin_version=1.0.0 clean build
```

#### Publishing

todo after the plugin is proven in a separate project and published to Gradle Plugin Portal

## License
The *gradle-docker* project is released under [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
