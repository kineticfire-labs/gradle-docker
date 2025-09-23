# Test Multiple Containers with Different Statuses

An integration test is needed that, when running integration tests with `composeUp`, shows waiting on multiple
containers to each reach a different status before proceeding with tests:
- container #1 must reach the status of 'ready'
- container #2 must reach the status of 'healthy'

To perform this test:
- containers #2 must have a health check

Before proceeding, check if an integration test that meets these requirements is already met.  If so, then no need to
implement a new test and mark this as 'done'.

## Status

Pending.
