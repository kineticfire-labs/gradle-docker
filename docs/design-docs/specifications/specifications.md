# Specifications Document (SD)

This document provides specifications for implementation this project.

A Specifications Document (SD) details how requirements will be achieved, providing technical and 
implementation-specific information for developers.  Specifications documents describe the solution to the problem.

A Requirements Document (RD) is used to develop specifications documents.  A SD is used by  developers to implement the
software.

## Glossary

| Term              | Definition                                                     |
|-------------------|----------------------------------------------------------------|
| Docker Compose v2 | docker compose (not legacy docker-compose, e.g. v1)            |
| Compose           | will mean 'Docker Compose v2' for the purposes of this project |
| Functional test   | runs Gradle build + plugin in an isolated temp project         |
| Integration test  | creates real containers via Compose, asserts behavior          |

## Actors

| Actor             | Description                                                                                                                                                                                           |
|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Plugin Developer  | An individual who is using the `docker-gradle` plugin as part of their project, e.g. a consumer or customer of this project                                                                           |
| Project Developer | An individual who is contributing to the `docker-gradle` project such as creating/modifying source code, tests, documentation, etc.; building the project, and publishing to the Gradle Plugin Portal |

## Function Specifications Document (SPD)
The [Function Specifications Document (SPD)](functional-specifications), which is formed from the sum of individual use 
cases at `./functional-specifications/`, defines the functional specifications.

## Non-Functional Specifications Document (NFSD)
The [Non-Functional Specifications Document (NFSD)](non-functional-specifications), which is formed from the sum of 
individual non-functional specifications at `./non-functional-specifications/`, defines the non-functional 
specifications.

## Technical Specifications Document (TSD)
The [Technical Specifications Document (TSD)](technical-specifications), which is formed from the sum of individual
technical specifications at `./technical-specifications/`, defines the technical specifications.

