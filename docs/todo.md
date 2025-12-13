# todo

**REMAINING WORK:**
Phase 5:
24 +  The following integration tests in `plugin-integration-test/dockerProject/` were NOT implemented and need to be added:
25 +  1. `scenario-1-build-mode/` - Basic build mode: jarFrom, test, additionalTags - DONE
26 +  2. `scenario-2-sourceref-mode/` - SourceRef mode with component properties - DONE
27 +  3. `scenario-3-save-publish/` - Save and publish on success - DONE
28 +  4. `scenario-4-method-lifecycle/` - Method lifecycle mode - DONE
      5. `scenario-5-contextdir-mode/` - Build mode using contextDir instead of jarFrom - DONE
   30 +  6. `README.md` - Documentation of scenarios

User DSL:
dockerProject {
    image {
        name.set('my-app')
        tags.set(['latest', '1.0.0'])
        jarFrom.set(':app:jar')
        buildArgs.put('VERSION', '1.0.0')
        labels.put('title', 'My App')
    }

    test {
        compose.set('src/integrationTest/resources/compose/app.yml')
        waitForHealthy.set(['app'])
        timeoutSeconds.set(60)
        testTaskName.set('integrationTest')
    }

    onSuccess {
        additionalTags.set(['tested', 'stable'])
        saveFile.set('build/images/my-app.tar.gz')
        publishRegistry.set('localhost:5000')
        publishNamespace.set('myorg')
        publishTags.set(['latest', 'tested'])
    }
}


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





2. rename dockerTest to dockerTest?
Phases 1 - 8
4


Starting a Gradle Daemon, 1 incompatible and 5 stopped Daemons could not be reused, use --status for details
Calculating task graph as no cached configuration is available for tasks: cleanAll integrationTest
Caught exception: Already watching path: /home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/dockerTest/examples/web-app-junit/app



> Task :docker:scenario-4:cleanDockerImages
Docker build: [Warning] One or more build-args [JAR_FILE] were not consumed



> Task :dockerWorkflows:scenario-3-failed-tests:app-image:verifyFailedPipeline
==========================================
Step 0: Build the Docker image first
==========================================
Starting a Gradle Daemon, 1 busy and 1 incompatible and 5 stopped Daemons could not be reused, use --status for details
Calculating task graph as configuration cache cannot be reused because file 'settings.gradle' has changed.
Caught exception: Already watching path: /home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/dockerTest/examples/web-app-junit/app







> Task :dockerWorkflows:scenario-3-failed-tests:app-image:dockerBuildFailedTestApp
Docker client connected to: unix:///var/run/docker.sock
DockerService initialized successfully
Step 1: Run the pipeline (expecting failure)
==========================================
Calculating task graph as no cached configuration is available for tasks: :dockerWorkflows:scenario-3-failed-tests:app-image:runFailingPipeline
Caught exception: Already watching path: /home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/dockerTest/examples/web-app-junit/app






> Task :dockerWorkflows:scenario-3-failed-tests:app-image:runFailingPipeline
State file generated: /home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/dockerWorkflows/scenario-3-failed-tests/app-image/build/compose-state/failedTest-state.json
ComposeUp task composeUpFailedTest completed successfully
Test task failed with exception: There are test sources present and no filters are applied, but the test task did not discover any tests to execute. This is likely due to a misconfiguration. Please check your test configuration. If this is not a misconfiguration, this error can be disabled by setting the 'failOnNoDiscoveredTests' property to false.
Stopping Docker Compose stack: failedTest (project: workflow-scenario3-test)
Successfully stopped compose stack 'failedTest'
ComposeDown task composeDownFailedTest completed successfully
Pipeline failingPipeline failed: Test execution failed: There are test sources present and no filters are applied, but the test task did not discover any tests to execute. This is likely due to a misconfiguration. Please check your test configuration. If this is not a misconfiguration, this error can be disabled by setting the 'failOnNoDiscoveredTests' property to false.
Executing always (cleanup) step
Executing cleanup for pipeline: failingPipeline
Cleanup completed for pipeline: failingPipeline

> Task :dockerWorkflows:scenario-3-failed-tests:app-image:runFailingPipeline FAILED

3 problems were found storing the configuration cache.
- Plugin class 'org.gradle.api.plugins.GroovyBasePlugin': execution of task ':dockerWorkflows:scenario-3-failed-tests:app-image:runFailingPipeline' caused invocation of 'Task.project' in other task at execution time which is unsupported with the configuration cache.
  See https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution
- Plugin class 'org.gradle.api.plugins.JavaBasePlugin': execution of task ':dockerWorkflows:scenario-3-failed-tests:app-image:runFailingPipeline' caused invocation of 'Task.project' in other task at execution time which is unsupported with the configuration cache.
  See https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution
- Task `:dockerWorkflows:scenario-3-failed-tests:app-image:runFailingPipeline` of type `com.kineticfire.gradle.docker.task.PipelineRunTask`: execution of task ':dockerWorkflows:scenario-3-failed-tests:app-image:runFailingPipeline' caused invocation of 'Task.project' in other task at execution time which is unsupported with the configuration cache.
  See https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution

See the complete report at file:///home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/configuration-cache/55xvxtljzt2yjyqspeb446lap/aao4032fwbknjsnltjkjv98pt/configuration-cache-report.html

[Incubating] Problems report is available at: file:///home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/problems/problems-report.html

FAILURE: Build failed with an exception.

* What went wrong:
  Execution failed for task ':dockerWorkflows:scenario-3-failed-tests:app-image:runFailingPipeline'.
> Pipeline 'failingPipeline' failed: Test execution failed: There are test sources present and no filters are applied, but the test task did not discover any tests to execute. This is likely due to a misconfiguration. Please check your test configuration. If this is not a misconfiguration, this error can be disabled by setting the 'failOnNoDiscoveredTests' property to false.







> Task :dockerWorkflows:scenario-4-multiple-pipelines:app-image:dockerBuildMultiPipelineApp
Compiling integration test classes...
Calculating task graph as configuration cache cannot be reused because file 'settings.gradle' has changed.
Caught exception: Already watching path: /home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/dockerTest/examples/web-app-junit/app



> Task :dockerWorkflows:scenario-4-multiple-pipelines:app-image:runDevPipeline
3 problems were found storing the configuration cache.
- Plugin class 'org.gradle.api.plugins.GroovyBasePlugin': execution of task ':dockerWorkflows:scenario-4-multiple-pipelines:app-image:runDevPipeline' caused invocation of 'Task.project' in other task at execution time which is unsupported with the configuration cache.
  See https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution
- Plugin class 'org.gradle.api.plugins.JavaBasePlugin': execution of task ':dockerWorkflows:scenario-4-multiple-pipelines:app-image:runDevPipeline' caused invocation of 'Task.project' in other task at execution time which is unsupported with the configuration cache.
  See https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution
- Task `:dockerWorkflows:scenario-4-multiple-pipelines:app-image:runDevPipeline` of type `com.kineticfire.gradle.docker.task.PipelineRunTask`: execution of task ':dockerWorkflows:scenario-4-multiple-pipelines:app-image:runDevPipeline' caused invocation of 'Task.project' in other task at execution time which is unsupported with the configuration cache.
  See https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution

See the complete report at file:///home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/configuration-cache/cwcf1443r8a0na7vof3xydo6v/2t9xeo7u68k6ako3gvquxu6kf/configuration-cache-report.html

[Incubating] Problems report is available at: file:///home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/problems/problems-report.html





> Task :dockerWorkflows:scenario-4-multiple-pipelines:app-image:runStagingPipeline
Successfully applied tags: [staging]
Success path completed for pipeline: stagingPipeline
Executing always (cleanup) step
Executing cleanup for pipeline: stagingPipeline
Cleanup completed for pipeline: stagingPipeline
Pipeline stagingPipeline completed successfully

3 problems were found storing the configuration cache.
- Plugin class 'org.gradle.api.plugins.GroovyBasePlugin': execution of task ':dockerWorkflows:scenario-4-multiple-pipelines:app-image:runStagingPipeline' caused invocation of 'Task.project' in other task at execution time which is unsupported with the configuration cache.
  See https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution
- Plugin class 'org.gradle.api.plugins.JavaBasePlugin': execution of task ':dockerWorkflows:scenario-4-multiple-pipelines:app-image:runStagingPipeline' caused invocation of 'Task.project' in other task at execution time which is unsupported with the configuration cache.
  See https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution
- Task `:dockerWorkflows:scenario-4-multiple-pipelines:app-image:runStagingPipeline` of type `com.kineticfire.gradle.docker.task.PipelineRunTask`: execution of task ':dockerWorkflows:scenario-4-multiple-pipelines:app-image:runStagingPipeline' caused invocation of 'Task.project' in other task at execution time which is unsupported with the configuration cache.
  See https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution

See the complete report at file:///home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/configuration-cache/f1ms6bolx16nk908t98s6qqki/2gzv3kbo9e01ogle9lq2r97y/configuration-cache-report.html

[Incubating] Problems report is available at: file:///home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/problems/problems-report.html




==========================================
TEST 3: Production Pipeline (adds 'prod' and 'release' tags)
==========================================
Calculating task graph as no cached configuration is available for tasks: :dockerWorkflows:scenario-4-multiple-pipelines:app-image:runProdPipeline
Caught exception: Already watching path: /home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/dockerTest/examples/web-app-junit/app








> Task :dockerWorkflows:scenario-4-multiple-pipelines:app-image:runProdPipeline
Successfully applied tags: [prod, release]
Success path completed for pipeline: prodPipeline
Executing always (cleanup) step
Executing cleanup for pipeline: prodPipeline
Cleanup completed for pipeline: prodPipeline
Pipeline prodPipeline completed successfully

3 problems were found storing the configuration cache.
- Plugin class 'org.gradle.api.plugins.GroovyBasePlugin': execution of task ':dockerWorkflows:scenario-4-multiple-pipelines:app-image:runProdPipeline' caused invocation of 'Task.project' in other task at execution time which is unsupported with the configuration cache.
  See https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution
- Plugin class 'org.gradle.api.plugins.JavaBasePlugin': execution of task ':dockerWorkflows:scenario-4-multiple-pipelines:app-image:runProdPipeline' caused invocation of 'Task.project' in other task at execution time which is unsupported with the configuration cache.
  See https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution
- Task `:dockerWorkflows:scenario-4-multiple-pipelines:app-image:runProdPipeline` of type `com.kineticfire.gradle.docker.task.PipelineRunTask`: execution of task ':dockerWorkflows:scenario-4-multiple-pipelines:app-image:runProdPipeline' caused invocation of 'Task.project' in other task at execution time which is unsupported with the configuration cache.
  See https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution

See the complete report at file:///home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/configuration-cache/8n5kvq9wy3hcs8sfd1d0kl27j/8wtv10tdrqn26jvy2dwb09j1b/configuration-cache-report.html

[Incubating] Problems report is available at: file:///home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/problems/problems-report.html









> Task :dockerWorkflows:scenario-5-complex-success:app-image:runComplexSuccessPipeline
Successfully applied tags: [verified, stable]
Success path completed for pipeline: complexSuccessPipeline
Executing always (cleanup) step
Executing cleanup for pipeline: complexSuccessPipeline
Cleanup completed for pipeline: complexSuccessPipeline
Pipeline complexSuccessPipeline completed successfully

1 problem was found storing the configuration cache.
- Task `:dockerWorkflows:scenario-5-complex-success:app-image:runComplexSuccessPipeline` of type `com.kineticfire.gradle.docker.task.PipelineRunTask`: execution of task ':dockerWorkflows:scenario-5-complex-success:app-image:runComplexSuccessPipeline' caused invocation of 'Task.project' in other task at execution time which is unsupported with the configuration cache.
  See https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution

See the complete report at file:///home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/configuration-cache/agl36opz42tmzkvp68hl0r8qt/54rvi5drokvn76sg87rld1kzk/configuration-cache-report.html

[Incubating] Problems report is available at: file:///home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/problems/problems-report.html



==========================================
RUNNING: Hooks Pipeline
==========================================
Calculating task graph as no cached configuration is available for tasks: :dockerWorkflows:scenario-6-hooks:app-image:runHooksPipeline
Caught exception: Already watching path: /home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/dockerTest/examples/web-app-junit/app





> Task :dockerWorkflows:scenario-6-hooks:app-image:runHooksPipeline
Successfully applied tags: [hooks-verified]
HOOK: afterSuccess executed - marker created
Success path completed for pipeline: hooksPipeline
Executing always (cleanup) step
Executing cleanup for pipeline: hooksPipeline
Cleanup completed for pipeline: hooksPipeline
Pipeline hooksPipeline completed successfully

1 problem was found storing the configuration cache.
- Task `:dockerWorkflows:scenario-6-hooks:app-image:runHooksPipeline` of type `com.kineticfire.gradle.docker.task.PipelineRunTask`: execution of task ':dockerWorkflows:scenario-6-hooks:app-image:runHooksPipeline' caused invocation of 'Task.project' in other task at execution time which is unsupported with the configuration cache.
  See https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution

See the complete report at file:///home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/configuration-cache/85m3p6i069i6z87ntu2ef5kka/7kcfo8b1aexd494pihxr4hnvm/configuration-cache-report.html

[Incubating] Problems report is available at: file:///home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/problems/problems-report.html




> Configure project :dockerProject:scenario-4-method-lifecycle:app-image
Integration test convention applied: source set, configurations, and task created
dockerProject: Using METHOD lifecycle for pipeline 'projectscenario4appPipeline'. Ensure test task 'integrationTest' has maxParallelForks = 1 and test classes use @ComposeUp (Spock) or @ExtendWith(DockerComposeMethodExtension.class) (JUnit 5).
dockerProject: Configured image 'project-scenario4-app' with pipeline 'projectscenario4appPipeline'
Pipeline 'projectscenario4appPipeline' has delegateStackManagement=true but also sets stack='projectscenario4appTest'. The stack property will be ignored since testIntegration manages the compose lifecycle. Consider removing the stack configuration to avoid confusion.



> Task :dockerWorkflows:scenario-7-save-publish:app-image:runSavePublishPipeline
Successfully saved image to: /home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/dockerWorkflows/scenario-7-save-publish/app-image/build/images/workflow-scenario7-app.tar.gz
Success path completed for pipeline: savePublishPipeline
Executing always (cleanup) step
Executing cleanup for pipeline: savePublishPipeline
Cleanup completed for pipeline: savePublishPipeline
Pipeline savePublishPipeline completed successfully

1 problem was found storing the configuration cache.
- Task `:dockerWorkflows:scenario-7-save-publish:app-image:runSavePublishPipeline` of type `com.kineticfire.gradle.docker.task.PipelineRunTask`: execution of task ':dockerWorkflows:scenario-7-save-publish:app-image:runSavePublishPipeline' caused invocation of 'Task.project' in other task at execution time which is unsupported with the configuration cache.
  See https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution

See the complete report at file:///home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/configuration-cache/x83kvek5l592yvl2q10xc7h4/2uz8bh03f5u73fwfompi5jc0z/configuration-cache-report.html

[Incubating] Problems report is available at: file:///home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/problems/problems-report.html





2 problems were found storing the configuration cache.
- Task `:dockerProject:scenario-4-method-lifecycle:app-image:runProjectscenario4appPipeline` of type `com.kineticfire.gradle.docker.task.PipelineRunTask`: execution of task ':dockerProject:scenario-4-method-lifecycle:app-image:runProjectscenario4appPipeline' caused invocation of 'Task.project' in other task at execution time which is unsupported with the configuration cache.
  See https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution
- Task `:dockerWorkflows:scenario-8-method-lifecycle:app-image:runMethodLifecyclePipeline` of type `com.kineticfire.gradle.docker.task.PipelineRunTask`: execution of task ':dockerWorkflows:scenario-8-method-lifecycle:app-image:runMethodLifecyclePipeline' caused invocation of 'Task.project' in other task at execution time which is unsupported with the configuration cache.
  See https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution

See the complete report at file:///home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/configuration-cache/8krf09rpiilg3f8k24u02d0k2/avw97jj7y9np2dbb0oilr8qq4/configuration-cache-report.html

[Incubating] Problems report is available at: file:///home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/problems/problems-report.html







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