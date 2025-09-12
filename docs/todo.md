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

## DX
1. All projects/sub-projects should accept a 'version' property for 'gradlew ...', IFF they require it
2. Figure out the build dirs under plugin-integration-test/app-image/build/ ... these right?


## UX / Feature
1. 'dockerBuild' should assemble src to its own temp folder (added to .gitignore)
4. docker publish to public registry (e.g., Docker Hub, GitHub Package Registry, etc.)
5. use TestContainers


## Documentation

- concise description
- marketing statements / value / benefits (succinct)
   - gradle controls image actions like build, tag (retag), save, and publish
   - test the actual image that will be delivered, deployed, and used!  don't just assume the image will work because the 
   app inside it was tested.  there's a lot that can cause issues with a docker image/container. 
      - strategic foundation with "test what you ship" philosophy
   - Strategic Positioning: "Gradle-native Docker testing solution" with performance and developer experience advantages 
   over generic alternatives.

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