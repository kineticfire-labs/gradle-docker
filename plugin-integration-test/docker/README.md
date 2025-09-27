# Integration Test Coverage

This document provides a matrix to track features against integration test coverage.

Note that these tests run in parallel, so operations based on image or file names require unique ones per test.


## Build Mode

| # | Tested Feature                                         | Test Scenario                                   | 
|---|--------------------------------------------------------|-------------------------------------------------|
| 2 | number of images = 1                                   | <ol><li>scenario-1</li><li>scenario-2</li></ol> |
| 2 | number of images > 1                                   | todo                                            |
| 2 | build image only                                       | <ol><li>scenario-1</li></ol>                    |
| 2 | build image + follow-on save and/or publish            | <ol><li>scenario-2</li></ol>                    |
| 2 | Image Name Mode: registry, namespace, imageName, 1 tag | todo                                            |
| 2 | Image Name Mode: namespace, imageName, 2 tags          | <ol><li>scenario-2</li></ol>                    |
| 2 | Image Name Mode: imageName, tag(s)                     | <ol><li>scenario-1</li></ol>                    |
| 2 | Repository Mode: registry, repository, 1 tag           | todo                                            |
| 2 | Repository Mode: repository, 2 tags                    | todo                                            |
| 3 | number of build args = none                            | todo                                            |
| 4 | number of build args = 1                               | <ol><li>scenario-2</li></ol>                    |
| 5 | number of build args > 1                               | <ol><li>scenario-1</li></ol>                    |
| 4 | number of build args w/ put = 1                        | <ol><li>scenario-2</li></ol>                    |
| 4 | number of build args w/ put > 1                        | <ol><li>scenario-1</li></ol>                    |
| 4 | number of build args w/ providers.provider = 1         | todo                                            |
| 4 | number of build args w/ providers.provider > 1         | todo                                            |
| 4 | number of build args w/ put & providers.provider > 1   | todo                                            |
| 3 | number of labels = none                                | todo                                            |
| 4 | number of labels = 1                                   | <ol><li>scenario-2</li></ol>                    |
| 5 | number of labels > 1                                   | <ol><li>scenario-1</li></ol>                    |
| 4 | number of labels w/ put = 1                            | <ol><li>scenario-2</li></ol>                    |
| 4 | number of labels w/ put > 1                            | <ol><li>scenario-1</li></ol>                    |
| 4 | number of labels w/ providers.provider = 1             | todo                                            |
| 4 | number of labels w/ providers.provider > 1             | todo                                            |
| 4 | number of labels w/ put & providers.provider > 1       | todo                                            |
| 6 | dockerfile = default                                   | <ol><li>scenario-1</li></ol>                    |
| 7 | dockerfile = specify by name                           | todo                                            |
| 8 | dockerfile = specify by path/name                      | todo                                            |



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
| 2  | Image Name Mode: registry, namespace, imageName, 1 tag              | todo                         |
| 2  | Image Name Mode: namespace, imageName, 2 tags                       | todo                         |
| 2  | Image Name Mode: imageName, tag(s)                                  | <ol><li>scenario-2</li></ol> |
| 2  | Repository Mode: registry, repository, 1 tag                        | todo                         |
| 2  | Repository Mode: repository, 2 tags                                 | todo                         |
