# todo

# Clean-up
1. Look for deprecated methods
2. Be sure the lifecycle methods really are doing the appropriate lifecycle
3. Function tests
   1. Try to re-enable functional tests?
   2. Need to add any, even if disabled? 
4. Look for "todo" and "skip"
5. plugin-integration-test has "src.backup"
6. symlink .gradlew for plugin-integration-test subprojects


# DX
1. All projects/sub-projects should accept a 'version' property for 'gradlew ...', IFF they require it
2. 'composeUp' should accept multiple compose files


# UX / Feature
1. 'dockerBuild' should assemble src to its own temp folder (added to .gitignore)
2. the 'dockerBuild' task should accept a copy/copySpec
3. add 'rar' compression type
