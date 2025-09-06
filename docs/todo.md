# todo

## Clean-up
1. Look for deprecated methods
2. Be sure the lifecycle methods really are doing the appropriate lifecycle
3. Function tests
   1. Try to re-enable functional tests?
   2. Need to add any, even if disabled? 
4. Look for "todo" and "skip"
5. plugin-integration-test has "src.backup"
6. symlink .gradlew for plugin-integration-test subprojects
7. Re-organize documents:
   1. plugin/docs/claude/status... needed?  teh whole folder?
   2. plugin/docs/{functional-test..., gradle-9...} these are more decisions
   3. plugin-integration-test/docs ... move to plugin/docs?


## DX
1. All projects/sub-projects should accept a 'version' property for 'gradlew ...', IFF they require it
2. 'composeUp' should accept multiple compose files


## UX / Feature
1. 'dockerBuild' should assemble src to its own temp folder (added to .gitignore)
2. the 'dockerBuild' task should accept a copy/copySpec
3. add 'rar' compression type
4. docker publish to public registry (e.g., Docker Hub, GitHub Package Registry, etc.)


## Documentation

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

- multi compose files
- lifecycle: suite, class, method

### Developer stuff

- build plugin: g clean build <version prop: todo>
   - goes to  ?
- run integration tests... lifecycle tests