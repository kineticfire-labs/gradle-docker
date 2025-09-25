# Integration Test Coverage

This document provides a matrix to track features against integration test coverage.

Note that these tests run in parallel, so operations based on image or file names require unique ones per test.


## Build Mode

- Image Name Mode: registry, namespace, imageName, tags
   - registry, namespace, imageName, 1 tag
   - namespace, imageName, 2 tags
   - imageName, tag(s)
- Repository Mode: registry, repository, tags
   - registry, repository, 1 tag
   - repository, 2 tags
- Number of images
   - 1
   - `> 1`
- Number of build args
   - none
   - 1
   - `> 1`
- Number of build labels
  - none
  - 1
  - `> 1`
- Dockerfile
   - default
   - specify by name
   - specify by path/name
- build only
- build, save, publish



| # | Tested Feature                    | Test Scenario                                   | 
|---|-----------------------------------|-------------------------------------------------|
| 1 | number of images = 1              | <ol><li>scenario-1</li><li>scenario-2</li></ol> |
| 2 | number of images > 1              | todo                                            |
| 3 | number of build args = none       | todo                                            |
| 4 | number of build args = 1          | <ol><li>scenario-2</li></ol>                    |
| 5 | number of build args > 1          | <ol><li>scenario-1</li></ol>                    |
| 6 | dockerfile = default              | <ol><li>scenario-1</li></ol>                    |
| 7 | dockerfile = specify by name      | <ol><li>scenario-2</li></ol>                    |
| 8 | dockerfile = specify by path/name | todo                                            |


## Source Ref Mode

- complete sourceRef
- Image Name Mode: registry, namespace, imageName, tag
  - registry, namespace, imageName, tag
  - namespace, imageName, tag
  - imageName, tag
- Repository Mode: registry, repository, tag
  - registry, repository, tag
  - repository, tag
- pull if missing
   - pullIfMissing not set
   - pullIfMissing=false
   - pullIfMissing=true
      - image is local, no pull
      - must pull image
- save
- publish

todo




## Tag Features

| # | Tested Feature     | Test Scenario                | 
|---|--------------------|------------------------------|
| 1 | number of tags = 1 | <ol><li>scenario-2</li></ol> |
| 2 | number of tags > 1 | <ol><li>scenario-1</li></ol> |


## Save Features

| # | Tested Feature                                              | Test Scenario                | 
|---|-------------------------------------------------------------|------------------------------|
| 1 | save = none                                                 | <ol><li>scenario-1</li></ol> |
| 2 | save w/ compression type = none                             | <ol><li>scenario-2</li></ol> |
| 3 | save w/ compression type = gzip                             | todo                         |
| 4 | save w/ compression type = bzip2                            | todo                         |
| 5 | save w/ compression type = xz                               | todo                         |
| 6 | save w/ compression type = zip                              | todo                         |
| 7 | save w/ no pullIfMissing                                    | <ol><li>scenario-2</li></ol> |
| 8 | save w/ pullIfMissing = false                               | todo                         |
| 9 | save w/ pullIfMissing = true                                | todo                         |


## Publish Features

| #  | Tested Feature                                                      | Test Scenario                | 
|----|---------------------------------------------------------------------|------------------------------|
| 1  | publish = none                                                      | <ol><li>scenario-1</li></ol> |
| 2  | publish = 1 tag                                                     | <ol><li>scenario-2</li></ol> |
| 3  | publish same image > 1 tags, same registry                          | todo                         |
| 4  | publish same image > 1 tags, different registries                   | todo                         |
| 5  | publish different images > 1 tags, same registry                    | todo                         |
| 6  | publish different images > 1 tags, different registries             | todo                         |
| 7  | publish to private registry without authentication                  | <ol><li>scenario-2</li></ol> |
| 8  | publish to private registry w/ authentication                       | todo                         |
| 9  | publish to public registry: Docker Hub                              | todo                         |
| 10 | publish to public registry: Amazon Elastic Container Registry (ECR) | todo                         |
| 11 | publish to public registry: Azure Container Registry (ACR)          | todo                         |
| 12 | publish to public registry: Google Artifact Registry (formerly GCR) | todo                         |
| 13 | publish to public registry: GitHub Container Registry               | todo                         |
| 14 | publish to public registry: GitLab Container Registry               | todo                         |


4.1: Multi-Registry Test Scenarios
// New integration test scenarios:
- scenario-3-multi-registry-publish    // Publish to 2+ registries
- scenario-4-authenticated-publish     // Authenticated registry publish
- scenario-5-save-with-pull-auth      // Save with pullIfMissing + auth
- scenario-6-mixed-auth-workflow      // Mixed authenticated/unauthenticated