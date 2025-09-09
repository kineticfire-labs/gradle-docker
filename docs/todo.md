# todo

## Clean-up
1. Look for deprecated methods
2. Be sure the lifecycle methods really are doing the appropriate lifecycle
3. Function tests
   1. Try to re-enable functional tests?
   2. Need to add any, even if disabled? 
4. Look for "todo" and "skip"
5. look for ".backup" files/dirs
6. symlink .gradlew for plugin-integration-test subprojects
7. remove "version" in docker compose
7. Re-organize documents:
   1. plugin/docs/claude/status... needed?  teh whole folder?
   2. plugin/docs/{functional-test..., gradle-9...} these are more decisions
   3. plugin-integration-test/docs ... move to plugin/docs?
7. Be sure using "docker compose" and not with hyphen "docker-compose"
8. put all the dependencies in the toml file
9. "docker ps -a" should leave any containers after integration test code runs

## DX
1. All projects/sub-projects should accept a 'version' property for 'gradlew ...', IFF they require it


## UX / Feature
1. 'dockerBuild' should assemble src to its own temp folder (added to .gitignore)
2. the 'dockerBuild' task should accept a copy/copySpec
3. docker compose accept multi file
4. docker publish to public registry (e.g., Docker Hub, GitHub Package Registry, etc.)


## Documentation

- concise description
- marketing statements / value / benefits (succinct)

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
- lifecycle: suite, class, method

### Developer stuff

- build plugin: g clean build <version prop: todo>
   - goes to  ?
- run integration tests... lifecycle tests
- when i build plugin, where does it go (like a jar would go to build/libs/)?  do i need to publishToMavenLocal?
- for running the integration tests, must the plugin have been published to maven local?

### Other

- don't change license section