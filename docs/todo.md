# todo

**REMAINING WORK:**
Phase 5:
24 +  The following integration tests in `plugin-integration-test/dockerProject/` were NOT implemented and need to be added:
25 +  1. `scenario-1-build-mode/` - Basic build mode: jarFrom, test, additionalTags - DONE
26 +  2. `scenario-2-sourceref-mode/` - SourceRef mode with component properties - DONE
27 +  3. `scenario-3-save-publish/` - Save and publish on success
28 +  4. `scenario-4-method-l` - Method lifecycle mode
      5. `scenario-5-contextdir-mode/` - Build mode using contextDir instead of jarFrom
   30 +  6. `README.md` - Documentation of scenarios


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