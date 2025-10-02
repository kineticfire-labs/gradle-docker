# Fix Integration Test Publish Tagging Mismatch

## Problem Statement

Integration test scenario-3 fails during the publish phase due to image tagging inconsistency between build and publish
configurations. The built image has a different tag structure than what the publish operation expects.

### Current Behavior
- **Build Phase Creates**: `localhost:5000/scenario3/scenario3-time-server:latest`
- **Publish Phase Expects**: `localhost:5031/scenario3-time-server:latest` (missing namespace)
- **Error**: `Push failed: An image does not exist locally with the tag: localhost:5031/scenario3-time-server`

### Configuration Analysis
```groovy
// In scenario-3/build.gradle
docker {
    images {
        timeServer {
            registry.set('localhost:5000')
            namespace.set('scenario3')
            imageName.set('scenario3-time-server')
            tags.set(["latest"])

            publish {
                to('testRegistry') {
                    registry.set('localhost:5031')
                    imageName.set('scenario3-time-server')
                    publishTags(['latest', '1.0.0'])
                }
            }
        }
    }
}
```

## Investigation Plan

### Phase 1: Understand Current Tag Resolution Logic
1. **Analyze `DockerServiceImpl.buildImage()`**
   - Review how build tags are constructed from registry/namespace/imageName
   - Identify where `localhost:5000/scenario3/scenario3-time-server:latest` gets created

2. **Analyze `DockerServiceImpl.pushImage()`**
   - Review how publish expects to find existing images
   - Identify where `localhost:5031/scenario3-time-server:latest` tag expectation comes from

3. **Analyze `ImageRefParts.parse()`**
   - Review image reference parsing logic
   - Understand how registry/namespace/imageName gets combined

4. **Review Publish Configuration Logic**
   - Examine how `PublishTarget` configurations are processed
   - Identify where namespace gets lost in publish tag construction

### Phase 2: Identify Root Cause
1. **Tag Construction Inconsistency**
   - Compare build vs publish tag construction logic
   - Identify if namespace is being included consistently
   - Check if `ImageRefParts` handles namespace properly for publish targets

2. **Configuration Precedence Issues**
   - Verify if `PublishTarget.imageName` overrides base `ImageSpec.namespace`
   - Check if `PublishTarget` should inherit namespace from base configuration
   - Analyze if publish tags should include namespace automatically

3. **Image Tagging vs Publishing Mismatch**
   - Determine if built images need additional tags for publish targets
   - Check if publish should reference built images by their actual tags
   - Analyze if intermediate tagging step is needed

### Phase 3: Design Solution Options

#### Option A: Inherit Namespace in Publish Target
- Modify publish tag construction to include namespace from base ImageSpec
- Ensure `localhost:5031/scenario3/scenario3-time-server:latest` is the expected tag
- Update tag resolution logic in publish operations

#### Option B: Tag Built Images for Publish Targets
- Add intermediate tagging step before publish
- Tag `localhost:5000/scenario3/scenario3-time-server:latest` as `localhost:5031/scenario3-time-server:latest`
- Keep current publish logic unchanged

#### Option C: Unified Tag Resolution
- Create consistent tag resolution logic for both build and publish
- Ensure namespace handling is identical across operations
- Update `ImageRefParts` to handle all scenarios consistently

### Phase 4: Implementation Strategy
1. **Code Analysis Files to Review**:
   - `plugin/src/main/groovy/com/kineticfire/gradle/docker/service/DockerServiceImpl.groovy`
   - `plugin/src/main/groovy/com/kineticfire/gradle/docker/model/ImageRefParts.groovy`
   - `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerPublishTask.groovy`
   - `plugin/src/main/groovy/com/kineticfire/gradle/docker/extension/PublishTarget.groovy`

2. **Test Strategy**:
   - Add unit tests for tag resolution consistency
   - Verify scenario-1 and scenario-2 still work after changes
   - Ensure scenario-3 publish completes successfully
   - Test with different namespace/registry combinations

3. **Validation Approach**:
   - Run `docker images` to verify built image tags
   - Run `docker images` after publish to verify expected tags exist
   - Test full integration workflow: build → tag → publish → verify

### Phase 5: Verification Plan
1. **Unit Test Coverage**:
   - Test tag construction logic for build operations
   - Test tag construction logic for publish operations
   - Test namespace inheritance in publish targets
   - Test ImageRefParts parsing for all scenarios

2. **Integration Test Validation**:
   - Run scenario-3 full integration test successfully
   - Verify scenario-1 and scenario-2 remain unaffected
   - Test publish to both localhost:5000 and localhost:5031 registries
   - Verify saved images and registry images as expected

3. **Edge Case Testing**:
   - Test publish without namespace in base configuration
   - Test publish with multiple publish targets
   - Test publish with different registry/namespace combinations

## Success Criteria
- [ ] scenario-3 integration test completes successfully without publish errors
- [ ] scenario-1 and scenario-2 continue to work unchanged
- [ ] Built image tags match expected publish target tags
- [ ] All unit tests pass with tag resolution logic
- [ ] Integration tests verify end-to-end publish workflow
- [ ] No regression in existing functionality

## Implementation Files to Modify
- Tag resolution logic in service layer
- Publish target configuration processing
- Unit tests for tag construction
- Integration test validation

## Estimated Effort
- **Investigation**: 2-3 hours
- **Implementation**: 3-4 hours
- **Testing**: 2-3 hours
- **Total**: 7-10 hours

## Dependencies
- Requires understanding of current Docker service implementation
- Requires access to integration test environment with registries
- May require updates to project documentation if tag resolution changes