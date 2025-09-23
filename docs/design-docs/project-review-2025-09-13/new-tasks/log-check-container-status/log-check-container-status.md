# Log Check for Container Status

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
   [HTTP Status Code and Content Check for Container Status](../http-status-code-content-wait-status/http-status-code-content-wait-status.md);
   be sure to read its detailed tasks in `../http-status-code-content-wait-status/tasks/`.
3. Plan the error messages
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
