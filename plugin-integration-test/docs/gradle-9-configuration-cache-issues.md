# Information on Gradle Configuration Cache Error

## Error response

When running './gradlew clean fullTest' from 'plugin-integration-test/', get this error:

> Task :app-image:smokeTest FAILED

> Task :app-image:composeDownSmokeTest
Stopping Docker Compose stack: smokeTest (project: smoke-app-image)
Successfully stopped compose stack 'smokeTest'

1 problem was found storing the configuration cache.
- Build file 'app-image/build.gradle': invocation of 'Task.project' at execution time is unsupported with the configuration cache.
  See https://docs.gradle.org/9.0.0/userguide/configuration_cache_requirements.html#config_cache:requirements:use_project_during_execution

See the complete report at file:///home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/configuration-cache/acsfkefldeucrm15t0ubsn8ue/5jg8zan9jp6qihsw4xoro2bk8/configuration-cache-report.html

[Incubating] Problems report is available at: file:///home/user/kf/repos/github-repos/gradle-docker/plugin-integration-test/build/reports/problems/problems-report.html

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app-image:smokeTest'.
> Invocation of 'Task.project' by task ':app-image:smokeTest' at execution time is unsupported with the configuration cache.



## Information Source 1
The error "invocation of 'Task.project' at execution time is unsupported with the configuration cache" in Gradle indicates that a task is attempting to access the Project object during its execution phase while the Configuration Cache is enabled. This is problematic because the Project instance, which contains build configuration information, is not available or cannot be reliably serialized and deserialized by the Configuration Cache when accessed at execution time.
Explanation:
Configuration Cache:
This Gradle feature aims to speed up builds by caching the result of the configuration phase. It stores the configured state of tasks and projects, allowing subsequent builds to skip re-running the configuration if no relevant build files have changed.
Execution Time vs. Configuration Time:
Configuration Time: This is when Gradle evaluates build scripts and defines tasks, their inputs, and outputs. Accessing Project properties and methods during this phase is generally acceptable.
Execution Time: This is when tasks actually perform their work (e.g., compile code, run tests). When the Configuration Cache is enabled, the Project object is not guaranteed to be available or in a serializable state during this phase.
Resolution:
To resolve this error, you must refactor your build logic to ensure that any necessary information from the Project object is captured and stored in a way that is compatible with the Configuration Cache before the execution phase. This typically involves:
Capturing values at configuration time:
Retrieve the required Project properties or values during the configuration phase and store them in Property<T> or Provider<T> instances within your task. These types are designed to be properly serialized by the Configuration Cache.
Using ProviderFactory:
For more complex scenarios where you need to compute values based on project properties, use ProviderFactory to create Provider instances that will resolve the values when needed, in a cache-compatible manner.
Avoiding direct Project access in task actions:
Do not directly reference task.project or project within doLast or doFirst blocks, or any other task action. Instead, use the properties you captured at configuration time.
Example of a fix (conceptual):

Instead of:
```
tasks.register('myTask') {
    doLast {
        println "Project name: ${project.name}" // Problematic
    }
}
```

Refactor to:
```
tasks.register('myTask') {
    def projectName = project.name // Capture at configuration time
    doLast {
        println "Project name: ${projectName}" // Use captured value
    }
}
```

## Information Source 2

The error you're encountering is caused by Gradle's configuration cache and how your :app-image:smokeTest task is implemented.

🔍 What the error means

This part:

Invocation of 'Task.project' by task ':app-image:smokeTest' at execution time is unsupported with the configuration cache.


...means that inside your smokeTest task, you're accessing project during task execution, and that breaks Gradle's configuration cache requirements in version 9.0.0 and above.

Gradle 9 is stricter with what tasks can do during execution when configuration caching is enabled. Some things that used to be fine are now disallowed because they make caching unreliable.

✅ Solution: Refactor the task to avoid project access during execution

You need to ensure all configuration logic happens during the configuration phase, not during task execution.

Here’s how you can fix this:

1. Check your smokeTest task code (likely in app-image/build.gradle)

You're probably doing something like this:

task smokeTest {
    doLast {
        def someValue = project.someProperty  // ❌ Not allowed with config cache
        println "Testing with $someValue"
    }
}


This is not allowed because project is being accessed during doLast, which runs at execution time.

✅ Correct way (move it to configuration phase):
def someValue = project.someProperty  // ✅ This happens during configuration

task smokeTest {
    doLast {
        println "Testing with $someValue"  // Only using pre-computed values here
    }
}

🔧 General Guidance

Avoid accessing project, tasks, configurations, etc. inside doLast or doFirst.

Compute all necessary values at configuration time, and use final variables inside doLast.

If you're using custom tasks (e.g., extending DefaultTask), make sure:

You use @Input, @OutputDirectory, etc.

You avoid lazy project access in @TaskAction.


## Suggestions!

In 'plugin-integration-test/app-image/build.gradle', the task at error is:
```
// SMOKE TESTS: Quick validation using suite lifecycle (fastest)
tasks.register('smokeTest', Test) {
    description = 'Smoke tests with suite lifecycle - quick Docker image validation'
    group = 'verification'
    testClassesDirs = sourceSets.functionalTest.output.classesDirs
    classpath = sourceSets.functionalTest.runtimeClasspath
    useJUnitPlatform()

    // DEMONSTRATE: Suite lifecycle - compose up once for all tests (fastest)
    usesCompose stack: "smokeTest", lifecycle: "suite"
    systemProperty "COMPOSE_STATE_FILE", composeStateFileFor("smokeTest")

    dependsOn 'dockerBuildTimeServer'
}
```

Suggested refactoring to fix the issue:
```
def composeStateFile = composeStateFileFor("smokeTest")  // ✅ evaluated during configuration

tasks.register('smokeTest', Test) {
    description = 'Smoke tests with suite lifecycle - quick Docker image validation'
    group = 'verification'
    testClassesDirs = sourceSets.functionalTest.output.classesDirs
    classpath = sourceSets.functionalTest.runtimeClasspath
    useJUnitPlatform()

    // Still may need a fix depending on implementation of `usesCompose`
    usesCompose stack: "smokeTest", lifecycle: "suite"

    systemProperty "COMPOSE_STATE_FILE", composeStateFile  // ✅ using precomputed value

    dependsOn 'dockerBuildTimeServer'
}
```

Caution!
- If usesCompose is a custom method from a plugin (e.g. gradle-docker or testcontainers), make sure it also doesn't call into project during task execution.
- If usesCompose works like test.useJUnitPlatform() and is part of the test framework setup during configuration, it should be fine. But if you're seeing issues around it in your stack trace, you may need to trace that call too.