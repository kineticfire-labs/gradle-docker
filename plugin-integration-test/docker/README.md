# Integration Test Coverage - 'docker' Task

This document provides a matrix to track features against integration test coverage for the `docker` task.


## De-conflict Integration Tests

Integration tests run in parallel, including those from `compose`.  Shared resources must be manually de-conflicted:
- Docker images must have unique names per integration test, such as `scenario-<number>-<image name>` or
  `scenario-<number>/<image name>`
- Ports must be unique, such as those used for exposed services (e.g., the `TimeServer`) or for Docker private
  registries


## Build Mode

| # | Tested Feature                                         | Test Scenario                                                                         | 
|---|--------------------------------------------------------|---------------------------------------------------------------------------------------|
| 2 | number of images = 1                                   | <ol><li>scenario-1</li><li>scenario-2</li><li>scenario-3</li><li>scenario-4</li></ol> |
| 2 | number of images > 1                                   | todo                                                                                  |
| 2 | build image only                                       | <ol><li>scenario-1</li></ol>                                                          |
| 2 | build image + follow-on save and/or publish            | <ol><li>scenario-2</li><li>scenario-3</li><li>scenario-4</li></ol>                    |
| 2 | Image Name Mode: registry, namespace, imageName, 1 tag | <ol><li>scenario-3</li></ol>                                                          |
| 2 | Image Name Mode: namespace, imageName, 2 tags          | <ol><li>scenario-2</li></ol>                                                          |
| 2 | Image Name Mode: namespace, imageName, 1 tag           | <ol><li>scenario-4</li></ol>                                                          |
| 2 | Image Name Mode: imageName, tag(s)                     | <ol><li>scenario-1</li></ol>                                                          |
| 2 | Repository Mode: registry, repository, 1 tag           | todo                                                                                  |
| 2 | Repository Mode: repository, 2 tags                    | todo                                                                                  |
| 3 | number of build args = none                            | <ol><li>scenario-3</li><li>scenario-4</li></ol>                                       |
| 4 | number of build args = 1                               | <ol><li>scenario-2</li></ol>                                                          |
| 5 | number of build args > 1                               | <ol><li>scenario-1</li></ol>                                                          |
| 4 | number of build args w/ put = 1                        | <ol><li>scenario-2</li></ol>                                                          |
| 4 | number of build args w/ put > 1                        | <ol><li>scenario-1</li></ol>                                                          |
| 4 | number of build args w/ providers.provider = 1         | todo                                                                                  |
| 4 | number of build args w/ providers.provider > 1         | todo                                                                                  |
| 4 | number of build args w/ put & providers.provider > 1   | todo                                                                                  |
| 3 | number of labels = none                                | <ol><li>scenario-3</li><li>scenario-4</li></ol>                                       |
| 4 | number of labels = 1                                   | <ol><li>scenario-2</li></ol>                                                          |
| 5 | number of labels > 1                                   | <ol><li>scenario-1</li></ol>                                                          |
| 4 | number of labels w/ put = 1                            | <ol><li>scenario-2</li></ol>                                                          |
| 4 | number of labels w/ put > 1                            | <ol><li>scenario-1</li></ol>                                                          |
| 4 | number of labels w/ providers.provider = 1             | todo                                                                                  |
| 4 | number of labels w/ providers.provider > 1             | todo                                                                                  |
| 4 | number of labels w/ put & providers.provider > 1       | todo                                                                                  |
| 6 | dockerfile = default                                   | <ol><li>scenario-1</li><li>scenario-4</li></ol>                                       |
| 7 | dockerfile = specify by name                           | <ol><li>scenario-2</li></ol>                                                          |
| 8 | dockerfile = specify by path/name                      | <ol><li>scenario-3</li></ol>                                                          |



## Source Ref Mode

| # | Tested Feature                                                                           | Test Scenario                | 
|---|------------------------------------------------------------------------------------------|------------------------------|
| 2 | sourceRef                                                                                | todo                         |
| 2 | Image Name Mode: sourceRefImageName, sourceRefTag                                        | todo                         |
| 2 | Image Name Mode: sourceRefNamespace, sourceRefImageName, sourceRefTag                    | todo                         |
| 2 | Image Name Mode: sourceRefRegistry, sourceRefNamespace, sourceRefImageName, sourceRefTag | <ol><li>scenario-5</li></ol> |
| 2 | Repository Mode: sourceRefRepository, sourceRefTag                                       | todo                         |
| 2 | Repository Mode: sourceRefRegistry, sourceRefRepository, sourceRefTag                    | todo                         |
| 2 | pullIfMissing = not set && image IS local                                                | <ol><li>scenario-5</li></ol> |
| 2 | pullIfMissing = false && image IS local                                                  | todo                         |
| 2 | pullIfMissing = true && image IS local so no pull                                        | todo                         |
| 2 | pullIfMissing = true && image is NOT local so must pull                                  | todo                         |
| 2 | with 'save'                                                                              | todo                         |
| 2 | with 'publish'                                                                           | todo                         |


## Tag Features

| # | Tested Feature                                          | Test Scenario                                                      | 
|---|---------------------------------------------------------|--------------------------------------------------------------------|
| 2 | number of tags = none (using 'sourceRef' or components) | <ol><li>scenario-5</li></ol>                                       |
| 1 | number of tags = 1                                      | <ol><li>scenario-2</li><li>scenario-3</li><li>scenario-4</li></ol> |
| 2 | number of tags > 1                                      | <ol><li>scenario-1</li></ol>                                       |


## Save Features

| # | Tested Feature                   | Test Scenario                                                      | 
|---|----------------------------------|--------------------------------------------------------------------|
| 1 | save = none                      | <ol><li>scenario-1</li><li>scenario-4</li><li>scenario-5</li></ol> |
| 2 | save w/ compression type = none  | <ol><li>scenario-2</li></ol>                                       |
| 3 | save w/ compression type = gzip  | <ol><li>scenario-3</li></ol>                                       |
| 4 | save w/ compression type = bzip2 | todo                                                               |
| 5 | save w/ compression type = xz    | todo                                                               |
| 6 | save w/ compression type = zip   | todo                                                               |


## Publish Features

| #  | Tested Feature                                                      | Test Scenario                                                                         | 
|----|---------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| 1  | publish = none                                                      | <ol><li>scenario-1</li></ol>                                                          |
| 2  | publish = 1 tag                                                     | <ol><li>scenario-2</li><li>scenario-4</li><li>scenario-5</li></ol>                    |
| 3  | publish same image > 1 tags, same registry                          | <ol><li>scenario-3</li></ol>                                                          |
| 4  | publish same image > 1 tags, different registries                   | todo                                                                                  |
| 5  | publish different images > 1 tags, same registry                    | todo                                                                                  |
| 6  | publish different images > 1 tags, different registries             | todo                                                                                  |
| 7  | publish to private registry without authentication                  | <ol><li>scenario-2</li><li>scenario-3</li><li>scenario-4</li><li>scenario-5</li></ol> |
| 8  | publish to private registry w/ authentication                       | todo                                                                                  |
| 9  | publish to public registry: Docker Hub                              | todo                                                                                  |
| 10 | publish to public registry: Amazon Elastic Container Registry (ECR) | todo                                                                                  |
| 11 | publish to public registry: Azure Container Registry (ACR)          | todo                                                                                  |
| 12 | publish to public registry: Google Artifact Registry (formerly GCR) | todo                                                                                  |
| 13 | publish to public registry: GitHub Container Registry               | todo                                                                                  |
| 14 | publish to public registry: GitLab Container Registry               | todo                                                                                  |
| 2  | inherit all from build                                              | <ol><li>scenario-4</li></ol>                                                          |
| 2  | inherit all from sourceRef                                          | todo                                                                                  |
| 2  | inherit sourceRef: namespace, imageName                             | <ol><li>scenario-5</li></ol>                                                          |
| 2  | Image Name Mode: registry, namespace, imageName, 1 tag              | todo                                                                                  |
| 2  | Image Name Mode: namespace, imageName, 2 tags                       | <ol><li>scenario-3</li></ol>                                                          |
| 2  | Image Name Mode: registry, (inherit namespace), imageName, tag(s)   | <ol><li>scenario-2</li></ol>                                                          |
| 2  | Repository Mode: registry, repository, 1 tag                        | todo                                                                                  |
| 2  | Repository Mode: repository, 2 tags                                 | todo                                                                                  |
