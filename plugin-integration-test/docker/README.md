# Integration Test Coverage

Note that these tasks run in parallel, so operations based on image or file names require unique ones per test.

| Scenario   | Type  | 
|------------|-------|
| scenario-1 | build |
| scenario-2 | build |


| # | Tested Feature                | Scenario                     | 
|---|-------------------------------|------------------------------|
| 1 | build w/ images = 1           | <ol><li>scenario-1</li></ol> |
| 2 | build w/ dockerfile = default | <ol><li>scenario-1</li></ol> |
| 3 | build w/ build args = 3       | <ol><li>scenario-1</li></ol> |
| 4 | build w/ tags = 2             | <ol><li>scenario-1</li></ol> |
| 5 | build w/ save = none          | <ol><li>scenario-1</li></ol> |
| 6 | build w/ publish = none       | <ol><li>scenario-1</li></ol> |


