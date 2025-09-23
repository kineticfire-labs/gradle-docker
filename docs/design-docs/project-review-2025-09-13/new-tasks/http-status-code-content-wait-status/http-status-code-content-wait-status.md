# HTTP Status Code and Content Check for Container Status

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
   [Log Check for Container Status](../log-check-container-status/log-check-container-status.md);
   be sure to read its detailed tasks in `../log-check-container-status/tasks/`.
4. Plan the error message
5. Determine the logic to identify these states which won't allow the container to reach 'healthy' or 'running' status
    1. How to detect container states reliably?
6. Determine how to implement the logic: what code is affected and what new code is needed?
7. Plan the implementation of the logic for this new feature in `plugin/src/main`
8. Plan the unit tests for this new feature in `plugin/src/test`
9. Plan the functional tests for this new feature in `plugin/src/functionalTest`
10. Plan the integration tests for this new feature in `plugin-integration-test/app-image/src/integrationTest`


## Status

Pending.
