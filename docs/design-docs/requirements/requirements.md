# Requirements Document (RD)

This document provides requirements for implementation this project.

A Requirements Document (RD) outlines what a project or system needs to do, focusing on stakeholder needs and desired 
outcomes in a user-friendly, business-oriented language.  Requirements documents define the problem to be solved.

A RD is used to develop specifications documents.  A Specification Document (SD) is used by  developers to implement the 
software.

## Purpose

Provide a Gradle plugin with tasks and DSL to execute Docker and Docker Compose commands in a repeatable, testable, 
CI-friendly way; and provide a library to help with build and testing workflows.

## Goals
- A Gradle plugin with:
   - Support key Docker commands--`build`, `tag`, `push`--as Gradle tasks, with conventions for DX and allowing 
   configuration for customization
   - Support key Docker Compose commands--`up`, `down`--as Gradle tasks, with conventions for DX and allowing 
   configuration  for customization
   - One-line DX: `./gradlew dockerBuild`, `./gradlew composeUp`, etc.
   - Library of functions to support build and testing workflows with Docker and Docker Compose.  Example: wait for a 
   set of containers to reach `RUNNING` or `HEALTHY` state.
   - Deterministic runs across dev/CI with clear logs and error handling.
   - Tests (unit, functional, integration) prove plugin correctness and safety.

## Scope

| In/Out Scope? | Description                                                                                                             |
|---------------|-------------------------------------------------------------------------------------------------------------------------|
| In-scope      | Docker CLI orchestration, Docker Compose v2 orchestration, task DSL, logging, exit-code mapping, basic caching hooks    |
| Out-of-scope  | Docker Compose v1, Podman/Buildah, Kubernetes, remote Docker Engine auth flows, image signing, SBOM generation (future) |

## Glossary

| Term              | Definition                                                     |
|-------------------|----------------------------------------------------------------|
| Docker Compose v2 | docker compose (not legacy docker-compose, e.g. v1)            |
| Compose           | will mean 'Docker Compose v2' for the purposes of this project |
| Functional test   | runs Gradle build + plugin in an isolated temp project         |
| Integration test  | creates real containers via Compose, asserts behavior          |

## Actors

| Actor             | Description                                                                                                                                                                                            |
|-------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Plugin Developer  | An individual who is contributing to this `docker-gradle` project such as creating/modifying source code, tests, documentation, etc.; building the project, and publishing to the Gradle Plugin Portal |
| Project Developer | An individual who is using this `docker-gradle` plugin as part of their project, e.g. a consumer or customer of this project                                                                           |

## Use Case Document (UCD)
The [Use Case Document (UCD)](use-cases), which is formed from the sum of individual use cases at `./use-cases/`, 
defines the use cases.

## Functional Requirements Document (FRD)
The [Functional Requirements Document (FRD)](functional-requirements), which is formed from the sum of individual 
functional requirements at `./functional-requirements/`, defines the functional requirements.

## Non-Functional Requirements Document (NFRD)
The [Non-Functional Requirements Document (NFRD)](non-functional-requirements), which is formed from the sum of 
individual non-functional requirements at `./non-functional-requirements/`, defines the non-functional requirements.


todo:
## Document Metadata

| Key     | Value      |
|---------|------------|
| Status  | Draft      |
| Version | 0.0.1      |
| Updated | 2025-08-30 |