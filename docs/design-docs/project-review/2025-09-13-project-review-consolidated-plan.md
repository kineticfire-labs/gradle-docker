# Project Review Consolidated Plan

This document defines the consolidated action plan from the first project review at 
`docs/design-docs/project-review/2025-09-09-project-review.md` and the updated review at 
`docs/design-docs/project-review/2025-09-10-project-review-v2.md`.

This plan:
- selects and reorganizes priority items from the two plans
- reinforces the 'v2' plan's perspective that TestContainers is a complementary project and **NOT** a competitor

## Use This Plan

- Read and understand this plan.
- When a work item has been completed:
  - Check the box under [Understand and Update Contents & Status](#understand-and-update-contents--status) 
  - Update the "Status" section under that work item 

## Understand and Update Contents & Status

todo: be sure all are represented!

- [ ] [1. Test Multiple Containers with Different Statuses](#1-test-multiple-containers-with-different-statuses)
- [ ] [2. Early State Failure Detection](#2-early-state-failure-detection)
- [ ] [3. HTTP Status Code and Content Check for Container Status](#3-http-status-code-and-content-check-for-container-status)
- [ ] [4. Log Check for Container Status](#4-log-check-for-container-status)


## 1. Test Multiple Containers with Different Statuses

An integration test is needed that, when running integration tests with `composeUp`, shows waiting on multiple 
containers to each reach a different status before proceeding with tests:
- container #1 must reach the status of 'ready'
- container #2 must reach the status of 'healthy'

To perform this test:
- containers #2 must have a health check

Before proceeding, check if an integration test that meets these requirements is already met.  If so, then no need to
implement a new test and mark this as 'done'.

### Status

Pending.

## 2. Early State Failure Detection

A new feature is needed that, when running integration tests with `composeUp`, the logic detects a container reaching a 
state that would not allow it to reach the target state and, when such a state is reached, (1) fail early and (2) 
include in the error message that the container reached a state (and name the state) that would not allow it to reach 
the target state of <name of state>.  Otherwise, the user would need to wait on the timeout to occur, which could be a 
minute or more.

Use this approach when planning this feature:
1. check that this functionality is not already implemented.
2. identify container states that would not allow the container to reach the supported target states of 'healthy' or 
   'running'.  
   1. Initial investigation shows these container states would not allow a container to reach 'healthy' or 'running':
      1. `stopped`
      2. `exited`
      3. `dead`
3. Plan the error message
4. Determine the logic to identify these states which won't allow the container to reach 'healthy' or 'running' status
   1. How to detect container states reliably?
5. Determine how to implement the logic: what code is affected and what new code is needed? 
6. Plan the implementation of the logic for this new feature in `plugin/src/main`
7. Plan the unit tests for this new feature in `plugin/src/test`
8. Plan the functional tests for this new feature in `plugin/src/functionalTest`
9. Plan the integration tests for this new feature in `plugin-integration-test/app-image/src/integrationTest`

### Status

Pending.

### 3. HTTP Status Code and Content Check for Container Status

A new feature is needed that, when running integration tests with `composeUp`, the user can use the Gradle DSL task
configuration to configure a status check on the container for a web request to the container that checks the (1)
HTTP status response code and (2) HTTP response content (optional) to determine the containers status, just like the 
already supported statuses of 'running' and 'healthy'.  This feature is useful for a container that does not have a
healthcheck, or where content to check can be dynamic.

Use this approach to plan the new feature:
1. Define the Gradle task configuration DSL that a user would write to configure this check
   1. The user can define a required HTTP response status code as a number to indicate **SUCCESS**; if none is given, 
      default to '200'.  The user can define:
      1. A number, e.g. '200'
      2. A range of numbers, e.g. '200-206'
      3. A wildcard for the last two digits, e.g. '2xx'
      4. A list of two or more of the above, separated with commas
   2. The user can define a list to indicate immediate **FAILURE**, which allows the status check to fail-fast without
      waiting for a timeout:
      1. A number, e.g. '404'
      2. A range of numbers, e.g. '400-451'
      3. A wildcard for the last two digits, e.g. '4xx'
      4. A list of two or more of the above, separated with commas
   3. The user can define one or more required HTTP response content (string) which can be an exact match or a regex to
      indicate **SUCCESS**.
   4. The user can define one or more HTTP response content (string) which can be an exact match or a regex to indicate
      **FAILURE**.  This allows the status check to fail-fast without waiting for the timeout.
   5. The user can configure an interval at which the HTTP requests are made.  If the user does not set this, then use
      a sensible default like 2 seconds.
   6. Interaction of the HTTP response status code and the HTTP response content:
      1. At least one of the **SUCCESSFUL** response status code or the **SUCCESSFUL** response content must be defined;
         if not, then throw an exception with an informative error message
      2. If both a **SUCCESSFUL** response status code or the **SUCCESSFUL** response content are defined, then **BOTH**
         must be satisfied as true.
      3. If any one of the **FAILURE** conditions for the response status code or the response content is met, then the
         container is determined to have failed
   7. Determine the name for the task configuration block (`waitForHttp`?), and how to allow the user to define the 
      settings discussed above.  The current task configuration for waiting on container status of 'running' and 
      'healthy' appears below. This will require some design to have good user experience (UX) to fit this model and 
      also allow the settings to be given.
```groovy
waitForRunning {
    services = ["database", "my-container"]
    timeoutSeconds = 45
}

waitForHealthy {
   services = ["time-server", "other-container"]
   timeoutSeconds = 45
}
```
3. The design for the DSL, error messages, and logic must be consistent with 
  [4. Log Check for Container Status](#4-log-check-for-container-status)
4. Plan the error message
5. Determine the logic to identify these states which won't allow the container to reach 'healthy' or 'running' status
    1. How to detect container states reliably?
6. Determine how to implement the logic: what code is affected and what new code is needed?
7. Plan the implementation of the logic for this new feature in `plugin/src/main`
8. Plan the unit tests for this new feature in `plugin/src/test`
9. Plan the functional tests for this new feature in `plugin/src/functionalTest`
10. Plan the integration tests for this new feature in `plugin-integration-test/app-image/src/integrationTest`


### Status

Pending.


### 4. Log Check for Container Status

A new feature is needed that, when running integration tests with `composeUp`, the user can use the Gradle DSL task
configuration to configure a status check on the container's logs to determine if the container is ready.

Use this approach to plan the new feature:
1. Define the Gradle task configuration DSL that a user would write to configure this check
   1. A user **MUST** define one or more regexes that, if satisfied, indicate **SUCCESS**.  So at least one is required.
   2. A user can define one or more regexes that, if satisfied, indicate **FAILURE**.  So these are optional.
   3. Determine the name for the task configuration block (`waitForLogs`?), and how to allow the user to define the
      settings discussed above.  The current task configuration for waiting on container status of 'running' and
      'healthy' appears below. This will require some design to have good user experience (UX) to fit this model and
      also allow the settings to be given.
```groovy
waitForRunning {
    services = ["database", "my-container"]
    timeoutSeconds = 45
}

waitForHealthy {
   services = ["time-server", "other-container"]
   timeoutSeconds = 45
}
```
2. The design for the DSL, error messages, and logic must be consistent with
   [3. HTTP Status Code and Content Check for Container Status](#3-http-status-code-and-content-check-for-container-status)
3. Plan the error message
4. Determine the logic to identify these states which won't allow the container to reach 'healthy' or 'running' status
    1. How to detect container states reliably?
5. Determine how to implement the logic: what code is affected and what new code is needed?
6. Plan the implementation of the logic for this new feature in `plugin/src/main`
7. Plan the unit tests for this new feature in `plugin/src/test`
8. Plan the functional tests for this new feature in `plugin/src/functionalTest`
9. Plan the integration tests for this new feature in `plugin-integration-test/app-image/src/integrationTest`
10. Determine the most efficient, non-blocking way to read container logs.  It may be periodically checking a container's
   logs with the Docker Client's equivalent of `docker logs <container reference>`.  If polling or similar, then the 
   user should be able to configure this, but have a sensible default like 2 seconds.

### Status

Pending.


## todo

- logs for container failure
- container cleanup w/ verification!
- Port Management: Automatic port allocation to prevent conflicts
- Phase 2: TestContainers Integration & Synergy
