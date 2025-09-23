# Early State Failure Detection

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

## Status

Pending.
