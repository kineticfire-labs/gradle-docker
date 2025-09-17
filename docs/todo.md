# todo

## Clean-up
1. remove temp/debug/backup files/dirs
2. Look for deprecated methods
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

### Other

- don't change license section