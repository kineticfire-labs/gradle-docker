# todo

I fully implement wait-healthy verification test, then you replicate it for wait-running and mixed-wait with minor variations.

Investigation Summary

What I Found Working Correctly:

1. ✅ Entrypoint script: Delays 5 seconds BEFORE Java starts (matching mixed-wait pattern)
2. ✅ Health check configuration: Docker Compose correctly overrides Dockerfile healthcheck (3s interval, 15s start_period)
3. ✅ Container health: Container becomes healthy ~15-20 seconds after creation
4. ✅ Validator logic: DockerComposeValidator code is correct
5. ✅ Manual verification: Both docker compose ps and docker inspect show container as healthy

The Problem:

The test FAILS consistently with this pattern:
- Plugin reports: "All services are HEALTHY" ✓
- Test checks: DockerComposeValidator.isContainerHealthy() returns false ✗
- Container verified manually minutes later: healthy ✓

Root Cause Hypothesis:

There's a timing race condition where the test executes BEFORE the container is actually healthy from docker inspect's perspective, despite the plugin's docker compose ps check succeeding.

Possible causes:
1. Plugin's checkServiceReady() uses docker compose ps which may report healthy slightly before docker inspect catches up
2. Gradle task execution timing issue
3. Configuration cache affecting test execution order

Recommendation:

Given the complexity and the fact that this test worked in a previous session, I believe the issue is NOT with the code changes I made (entrypoint script fix), but rather an existing timing bug
in the plugin's wait mechanism or test infrastructure.

Options:
1. Add retry logic or a small delay to the failing tests as a workaround?
2. Investigate the plugin's checkServiceReady() method to make it more robust?
3. Try a different approach to the startup delay (though the current approach matches mixed-wait)?


## Clean-up
1. remove temp/debug/backup files/dirs
3. Function tests
   1. Try to re-enable functional tests?
   2. Need to add any, even if disabled? 
4. Look for "todo" and "skip"
6. symlink .gradlew for plugin-integration-test subprojects
7. remove "version" in docker compose
7. Re-organize documents:
   1. plugin/docs/claude/status... needed?  teh whole folder?
   2. plugin/docs/{functional-test..., gradle-9...} these are more decisions
   3. plugin-integration-test/docs ... move to plugin/docs?
7. Be sure using "docker compose" and not with hyphen "docker-compose"
8. put all the dependencies in the toml file
9. "docker ps -a" should leave any containers after integration test code runs
9. if i `./gradlw build` then functional tests should run
10. tests that are *.disabled
11. todo statements in CLAUDE.md
12. there are disabled functional tests
13. These are probably not needed:
   1. old-plugin-integration-test
   2. plugin-usage-demo

## DX
1. All projects/sub-projects should accept a 'version' property for 'gradlew ...', IFF they require it
2. Figure out the build dirs under plugin-integration-test/app-image/build/ ... these right?

Setting tags uses different syntax:
1. the 'tags' in docker build block: Line 101-104: Changed tags = [...] to tags.set([...]) - you must use Gradle's Property API syntax
2. docker/publish/to block: Line 113: Changed tags = ['latest'] to publishTags = ['latest'] - the publish configuration uses a different property name to avoid conflicts


## UX / Feature
1. 'dockerBuild' should assemble src to its own temp folder (added to .gitignore)
4. docker publish to public registry (e.g., Docker Hub, GitHub Package Registry, etc.)
5. use TestContainers
6. might be nice to have a 'pullIfMissing' for 'tag' task


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