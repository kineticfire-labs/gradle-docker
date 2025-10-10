# Integration Test Coverage - 'docker' Task

This document provides a matrix to track features against integration test coverage for the `docker` task.


## De-conflict Integration Tests

Integration tests run in parallel, including those from `compose`.  Shared resources must be manually de-conflicted:
- Docker images must have unique names per integration test, such as `scenario-<number>-<image name>` or
  `scenario-<number>/<image name>`
- Ports must be unique, such as those used for exposed services (e.g., the `TimeServer`) or for Docker private
  registries


## Build Mode

| # | Tested Feature                                         | Test Scenario                                                                                                               | 
|---|--------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| 1 | number of images = 1                                   | <ol><li>scenario-1</li><li>scenario-2</li><li>scenario-3</li><li>scenario-4</li><li>scenario-6</li><li>scenario-7</li></ol> |
| 2 | number of images > 1                                   | <ol><li>scenario-8</li></ol>                                                                                                |
| 3 | build image only                                       | <ol><li>scenario-1</li></ol>                                                                                                |
| 4 | build image + follow-on save and/or publish            | <ol><li>scenario-2</li><li>scenario-3</li><li>scenario-4</li><li>scenario-6</li><li>scenario-7</li><li>scenario-8</li></ol> |
| 5 | Image Name Mode: registry, namespace, imageName, 1 tag | <ol><li>scenario-3</li></ol>                                                                                                |
| 6 | Image Name Mode: namespace, imageName, 2 tags          | <ol><li>scenario-2</li></ol>                                                                                                |
| 2 | Image Name Mode: namespace, imageName, 1 tag           | <ol><li>scenario-4</li></ol>                                                                                                |
| 2 | Image Name Mode: imageName, tag(s)                     | <ol><li>scenario-1</li><li>scenario-7</li></ol>                                                                             |
| 2 | Repository Mode: registry, repository, 1 tag           | <ol><li>scenario-8</li></ol>                                                                                                |
| 2 | Repository Mode: repository, 2 tags                    | <ol><li>scenario-6</li></ol>                                                                                                |
| 3 | number of build args = none                            | <ol><li>scenario-3</li><li>scenario-7</li><li>scenario-8</li></ol>                                                          |
| 4 | number of build args = 1                               | <ol><li>scenario-2</li><li>scenario-4</li></ol>                                                                             |
| 5 | number of build args > 1                               | <ol><li>scenario-1</li><li>scenario-6</li><li>scenario-8</li></ol>                                                          |
| 4 | number of build args w/ put = 1                        | <ol><li>scenario-2</li></ol>                                                                                                |
| 4 | number of build args w/ put > 1                        | <ol><li>scenario-1</li><li>scenario-8</li></ol>                                                                             |
| 4 | number of build args w/ putAll = 1                     | <ol><li>scenario-4</li></ol>                                                                                                |
| 4 | number of build args w/ putAll > 1                     | <ol><li>scenario-6</li></ol>                                                                                                |
| 3 | number of labels = none                                | <ol><li>scenario-3</li><li>scenario-4</li><li>scenario-7</li><li>scenario-8</li></ol>                                       |
| 4 | number of labels = 1                                   | <ol><li>scenario-2</li><li>scenario-4</li><li>scenario-8</li></ol>                                                          |
| 5 | number of labels > 1                                   | <ol><li>scenario-1</li><li>scenario-6</li></ol>                                                                             |
| 4 | number of labels w/ put = 1                            | <ol><li>scenario-2</li><li>scenario-8</li></ol>                                                                             |
| 4 | number of labels w/ put > 1                            | <ol><li>scenario-1</li></ol>                                                                                                |
| 4 | number of labels w/ putAll = 1                         | <ol><li>scenario-4</li></ol>                                                                                                |
| 4 | number of labels w/ putAll > 1                         | <ol><li>scenario-6</li></ol>                                                                                                |
| 6 | dockerfile = default                                   | <ol><li>scenario-1</li><li>scenario-4</li><li>scenario-6</li><li>scenario-7</li><li>scenario-8</li></ol>                    |
| 7 | dockerfile = specify by name                           | <ol><li>scenario-2</li></ol>                                                                                                |
| 8 | dockerfile = specify by path/name                      | <ol><li>scenario-3</li></ol>                                                                                                |



## Source Ref Mode

| #  | Tested Feature                                                                           | Test Scenario                | 
|----|------------------------------------------------------------------------------------------|------------------------------|
| 1  | sourceRef                                                                                | <ol><li>scenario-9</li></ol> |
| 2  | Image Name Mode: sourceRefImageName, sourceRefTag                                        | todo                         |
| 3  | Image Name Mode: sourceRefNamespace, sourceRefImageName, sourceRefTag                    | todo                         |
| 4  | Image Name Mode: sourceRefRegistry, sourceRefNamespace, sourceRefImageName, sourceRefTag | <ol><li>scenario-5</li></ol> |
| 5  | Repository Mode: sourceRefRepository, sourceRefTag                                       | todo                         |
| 6  | Repository Mode: sourceRefRegistry, sourceRefRepository, sourceRefTag                    | todo                         |
| 7  | pullIfMissing = not set && image IS local                                                | <ol><li>scenario-5</li></ol> |
| 8  | pullIfMissing = false && image IS local                                                  | <ol><li>scenario-9</li></ol> |
| 9  | pullIfMissing = true && image IS local so no pull                                        | todo                         |
| 10 | pullIfMissing = true && image is NOT local so must pull                                  | todo                         |
| 11 | without 'save' or 'publish'                                                              | <ol><li>scenario-5</li></ol> |
| 12 | with 'save'                                                                              | <ol><li>scenario-9</li></ol> |
| 13 | with 'publish'                                                                           | <ol><li>scenario-9</li></ol> |


## Tag Features

| # | Tested Feature        | Test Scenario                                                                         | 
|---|-----------------------|---------------------------------------------------------------------------------------|
| 1 | number of tags = none | <ol><li>scenario-5</li><li>scenario-8</li></ol>                                       |
| 2 | number of tags = 1    | <ol><li>scenario-2</li><li>scenario-3</li><li>scenario-4</li><li>scenario-7</li></ol> |
| 3 | number of tags > 1    | <ol><li>scenario-1</li><li>scenario-6</li></ol>                                       |


## Save Features

| # | Tested Feature                   | Test Scenario                                   |
|---|----------------------------------|-------------------------------------------------|
| 1 | save = none                      | <ol><li>scenario-1</li><li>scenario-5</li></ol> |
| 2 | save w/ compression type = none  | <ol><li>scenario-2</li></ol>                    |
| 3 | save w/ compression type = gzip  | <ol><li>scenario-3</li><li>scenario-8</li></ol> |
| 4 | save w/ compression type = bzip2 | <ol><li>scenario-4</li><li>scenario-9</li></ol> |
| 5 | save w/ compression type = xz    | <ol><li>scenario-6</li></ol>                    |
| 6 | save w/ compression type = zip   | <ol><li>scenario-7</li></ol>                    |


## Publish Features

| #  | Tested Feature                                                      | Test Scenario                                                                                                               | 
|----|---------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| 1  | publish = none                                                      | <ol><li>scenario-1</li></ol>                                                                                                |
| 2  | publish = 1 tag                                                     | <ol><li>scenario-2</li><li>scenario-4</li><li>scenario-5</li></ol>                                                          |
| 3  | publish same image > 1 tags, same registry                          | <ol><li>scenario-3</li><li>scenario-6</li><li>scenario-7</li></ol>                                                          |
| 4  | publish same image > 1 tags, different registries                   | <ol><li>scenario-7</li><li>scenario-9</li></ol>                                                                             |
| 5  | publish different images > 1 tags, same registry                    | <ol><li>scenario-8</li></ol>                                                                                                |
| 6  | publish different images > 1 tags, different registries             | <ol><li>scenario-9</li></ol>                                                                                                |
| 7  | publish to private registry without authentication                  | <ol><li>scenario-2</li><li>scenario-3</li><li>scenario-4</li><li>scenario-5</li><li>scenario-7</li><li>scenario-8</li><li>scenario-9</li></ol> |
| 8  | publish to private registry w/ authentication                       | <ol><li>scenario-6</li></ol>                                                                                                |
| 9  | publish to public registry: Docker Hub                              | todo                                                                                                                        |
| 10 | publish to public registry: Amazon Elastic Container Registry (ECR) | todo                                                                                                                        |
| 11 | publish to public registry: Azure Container Registry (ACR)          | todo                                                                                                                        |
| 12 | publish to public registry: Google Artifact Registry (formerly GCR) | todo                                                                                                                        |
| 13 | publish to public registry: GitHub Container Registry               | todo                                                                                                                        |
| 14 | publish to public registry: GitLab Container Registry               | todo                                                                                                                        |
| 15 | inherit all from build                                              | <ol><li>scenario-4</li></ol>                                                                                                |
| 16 | inherit all from sourceRef                                          | todo                                                                                                                        |
| 17 | inherit sourceRef: namespace, imageName                             | <ol><li>scenario-5</li></ol>                                                                                                |
| 18 | Image Name Mode: registry, namespace, imageName, tag(s)             | todo                                                                                                                        |
| 19 | Image Name Mode: namespace, imageName, 2 tags                       | <ol><li>scenario-3</li></ol>                                                                                                |
| 20 | Image Name Mode: registry, (inherit namespace), imageName, tag(s)   | <ol><li>scenario-2</li></ol>                                                                                                |
| 21 | Repository Mode: registry, repository, 2 tags                       | <ol><li>scenario-6</li><li>scenario-8</li></ol>                                                                             |
| 22 | Repository Mode: repository, 1 tag                                  | todo                                                                                                                        |
