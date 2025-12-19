# todo


Help me understand the overall usage for our proposed 'dockerProject' DSL.  Start with this outline:
dockerProject {

      image {
          ...
      }

      test {
          ...
      }

      onSuccess {
          ...
      }
}

And the purpose would be show normal usage examples and add comments for options.

Taking these one block at time:

dockerProject.image:
- can use a simple 'name' for image name, or Image Name Mode (registry, namespace, imageName, tags), or Repository Mode (registry, repository, tags)
- for a java project
    - if just need a jar:  would use jarFrom.  and optionally rename with jarName.
    - if need jar and other files, would use contextTask
    - if a non-java project like a python ML project, then would use contextTask.  and would depend on if there is a dependent subproject on if that dependency is needed.
- the dockerfile is assumed to be a path and name by convention: if a different name, use dockerfileName.set("CustomDockerFile") and if a different path use dockerfile.set(file("path/to/CustomDockerfile"))
- optional buildArgs and labels
- and if the image is already available locally, then don't need to build it.  just sourceRef or its component forms.







1. integration tests:
   2. verifyFailedPipeline
   3. publish to public repo




## Clean-up

### Phase 1 Improvements (Recommended)

To improve the Phase 1 user experience without the risks of auto-detection:

1. **Better Error Messages:** When tests fail due to missing annotation, provide clear guidance:
   ERROR: Test class 'MyIntegrationTest' configured with lifecycle=METHOD but missing annotation.
   Add: @ComposeUp (Spock) or @ExtendWith(DockerComposeMethodExtension.class) (JUnit 5)

2. **Documentation:** Update usage docs to explain why the annotation is required and provide copy-paste examples.

3. **IDE Templates:** Provide IntelliJ IDEA live templates for quick test class creation.


## Documentation

- concise description
- marketing statements / value / benefits (succinct)
   - gradle controls image actions like build, tag (retag), save, and publish
   - test the actual image that will be delivered, deployed, and used!  don't just assume the image will work because the 
   app inside it was tested.  there's a lot that can cause issues with a docker image/container. 
      - strategic foundation with "test what you ship" philosophy
   - Strategic Positioning: "Gradle-native Docker testing solution" with performance and developer experience advantages 
   over generic alternatives.
  
### Overall

- opinionated with Docker image build and tests in a subproject peer to the one that builds the application to be put 
  into a Docker container.  separation of concerns.
- Java 21
- Gradle 9 (esp. build cache configuration compliant)

### docker stuff

- dir layout

- all-in-one: build (multi-tag), re-tag (multi), save (multi), publish (multi images, multi repos)
  - assume image already exists
     - locally
     - remote, and pull
     - then: re-tag, save, publish
  - invoke task to do all or to do one by name

- build image
   - copy files w/ some change on copy
   - show source folder
  - invoke task to do all or to do one by name

- 'publish' block takes a 'tags' block, which can re-tag the image to publish to the correct registry
```groovy
Publish Tags vs Build Tags

  Analysis: Yes, the publish tags block can re-tag images during publish. Here's how it works:

  - Line 58: tags.set(["scenario2-time-server:latest"]) - Creates local image with this tag
  - Line 66: tags(['localhost:5200/scenario2-time-server:latest']) - Re-tags for registry push

  Behavior: The DockerPublishTask takes the locally built image scenario2-time-server:latest and tags it as localhost:5200/scenario2-time-server:latest
```

### test (compose) stuff

- single vs. multi compose files (they have different syntax)
- composeDown will automatically use the yaml files used by composeUp, but can override with different files if desired
- lifecycle to optimize (isolation vs speed) tests: suite, class, method
- wait for container healthy/running

### Developer stuff

- build plugin: g clean build <version prop: todo>
   - goes to  ?
- run integration tests... lifecycle tests
- when i build plugin, where does it go (like a jar would go to build/libs/)?  do i need to publishToMavenLocal?
- for running the integration tests, must the plugin have been published to maven local?
- takes about 8 to 12 minutes to run unit tests.  limit tests to what you changed, then run tests for all.
- takes about 28 minutes to run integration tests (not including tests with public registries).  limit tests to what you changed, then run tests for all.
   -  tests to public registries must be run separate

### Other

- don't change license section