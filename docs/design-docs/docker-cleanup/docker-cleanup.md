# Docker Plugin Cleanup & Enhancement Plan

**Doc meta**
- Owner: Development Team
- Status: Approved
- Version: 1.0.0
- Last updated: 2025-01-15
- Comment: Cleanup serverAddress inconsistencies and implement explicit registry validation

## **Overview**

This plan addresses critical inconsistencies in the Docker plugin implementation and enhances user experience through explicit registry validation and improved error handling.

### **Key Issues Addressed**
1. **ServerAddress Property Inconsistency**: AuthSpec contains serverAddress property that conflicts with v3 implementation plan
2. **Missing Registry Validation**: No explicit registry requirement causes confusion with Docker Hub defaults
3. **Poor Error Messages**: Environment variable resolution failures provide generic error messages
4. **Registry Conflicts**: No detection of conflicting registry specifications

## **Implementation Plan**

### **Phase 1: Remove ServerAddress Property**

#### **Step 1.1: Remove serverAddress from AuthSpec**
- **File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/AuthSpec.groovy`
- **Action**: Remove the `serverAddress` property and related methods
- **Impact**: Clean up API to match v3 plan specification

#### **Step 1.2: Search and Remove All ServerAddress References**
- **Search Pattern**: `serverAddress` (case-insensitive)
- **Target Areas**:
  - All spec files (`*.groovy`)
  - All task implementations
  - All service implementations
  - All test files
  - All documentation files
- **Action**: Remove all references and update any dependent logic

### **Phase 2: Implement Explicit Registry Logic**

#### **Step 2.1: Update PublishTarget Registry Validation**
- **File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/PublishTarget.groovy`
- **Logic**: Add validation to require explicit registry:
```groovy
void validateRegistry() {
    def registryValue = registry.getOrElse("")
    def repositoryValue = repository.getOrElse("")

    // Check if repository is fully qualified (basic detection)
    def isFullyQualified = repositoryValue.contains("/") &&
                          (repositoryValue.contains(".") || repositoryValue.contains(":"))

    if (registryValue.isEmpty() && !isFullyQualified) {
        throw new GradleException(
            "Registry must be explicitly specified for publish target '${name}'. " +
            "Use registry.set('docker.io') for Docker Hub, registry.set('localhost:5000') for local registry, " +
            "or registry.set('<other-target-registry>') for other registries."
        )
    }

    // No registry format validation - allow any string to support various registry formats
    // Registry formats vary widely (hostnames, IPs, ports, paths) and validation would be complex
    // Let Docker daemon handle registry validation at runtime for better error messages
}
```

#### **Step 2.2: Add Registry Conflict Detection**
- **File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/spec/PublishTarget.groovy`
- **Logic**: Detect conflicting registry specifications:
```groovy
void validateRegistryConsistency() {
    def registryValue = registry.getOrElse("")
    def repositoryValue = repository.getOrElse("")

    if (!registryValue.isEmpty() && repositoryValue.contains("/")) {
        // Extract potential registry from repository (before first slash)
        def potentialRegistry = repositoryValue.split("/")[0]

        // If the repository part looks like a registry (contains . or :) and differs from registry property
        if ((potentialRegistry.contains(".") || potentialRegistry.contains(":")) &&
            !potentialRegistry.equals(registryValue)) {
            throw new GradleException(
                "Registry conflict in publish target '${name}': " +
                "registry.set('${registryValue}') conflicts with repository '${repositoryValue}'. " +
                "Use either registry property OR fully qualified repository, not both."
            )
        }
    }
}
```

#### **Step 2.3: Implement Environment Variable Error Handling in Plugin**
- **File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerPublishTask.groovy`
- **Logic**: Add validation method for authentication credentials with registry-specific suggestions:
```groovy
void validateAuthenticationCredentials(PublishTarget target, AuthSpec authSpec) {
    if (authSpec == null) return

    def registryName = target.getEffectiveRegistry()
    def exampleVars = getExampleEnvironmentVariables(registryName)

    // Validate username
    if (authSpec.username.isPresent()) {
        try {
            def username = authSpec.username.get()
            if (username == null || username.trim().isEmpty()) {
                throw new GradleException(
                    "Authentication username is empty for registry '${registryName}' in target '${target.name}'. " +
                    "Ensure your username environment variable contains a valid value."
                )
            }
        } catch (IllegalStateException e) {
            if (e.message?.contains("environment variable") || e.message?.contains("provider")) {
                throw new GradleException(
                    "Authentication username environment variable is not set for registry '${registryName}' in target '${target.name}'. " +
                    "Ensure your username environment variable is set. ${exampleVars.username}"
                )
            }
            throw e
        }
    }

    // Validate password/token
    if (authSpec.password.isPresent()) {
        try {
            def password = authSpec.password.get()
            if (password == null || password.trim().isEmpty()) {
                throw new GradleException(
                    "Authentication password/token is empty for registry '${registryName}' in target '${target.name}'. " +
                    "Ensure your password/token environment variable contains a valid value."
                )
            }
        } catch (IllegalStateException e) {
            if (e.message?.contains("environment variable") || e.message?.contains("provider")) {
                throw new GradleException(
                    "Authentication password/token environment variable is not set for registry '${registryName}' in target '${target.name}'. " +
                    "Ensure your password/token environment variable is set. ${exampleVars.password}"
                )
            }
            throw e
        }
    }
}

private Map<String, String> getExampleEnvironmentVariables(String registryName) {
    switch (registryName.toLowerCase()) {
        case "docker.io":
            return [
                username: "Common examples: DOCKERHUB_USERNAME, DOCKER_USERNAME",
                password: "Common examples: DOCKERHUB_TOKEN, DOCKER_TOKEN"
            ]
        case "ghcr.io":
            return [
                username: "Common examples: GHCR_USERNAME, GITHUB_USERNAME",
                password: "Common examples: GHCR_TOKEN, GITHUB_TOKEN"
            ]
        case { it.contains("localhost") }:
            return [
                username: "Common examples: REGISTRY_USERNAME, LOCAL_USERNAME",
                password: "Common examples: REGISTRY_PASSWORD, LOCAL_PASSWORD"
            ]
        default:
            return [
                username: "Common examples: REGISTRY_USERNAME, ${registryName.toUpperCase().replaceAll(/[^A-Z0-9]/, '_')}_USERNAME",
                password: "Common examples: REGISTRY_TOKEN, ${registryName.toUpperCase().replaceAll(/[^A-Z0-9]/, '_')}_TOKEN"
            ]
    }
}
```

#### **Step 2.4: Update Publish Task Implementation**
- **File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/task/DockerPublishTask.groovy`
- **Logic**: Use explicit registry resolution without defaults
- **Validation**: Call validation methods during task execution:
```groovy
@TaskAction
void publish() {
    // Validate each publish target
    publishTargets.each { target ->
        target.validateRegistry()
        target.validateRegistryConsistency()

        if (target.auth.isPresent()) {
            validateAuthenticationCredentials(target, target.auth.get())
        }

        // Proceed with publish operation...
    }
}
```

#### **Step 2.5: Update Service Layer**
- **File**: `plugin/src/main/groovy/com/kineticfire/gradle/docker/service/DockerServiceImpl.groovy`
- **Logic**: Handle explicit registry specification in publish operations

### **Phase 3: Documentation Updates**

#### **Step 3.1: Remove ServerAddress References**
- **File**: `docs/design-docs/usage-docker.md`
- **Action**: Remove all `serverAddress` mentions from auth examples
- **Add**: Comment explaining automatic extraction from image references

#### **Step 3.2: Add Comprehensive Publish Examples (Restructured)**

**Basic Registry Examples:**

**A) Docker Hub Publishing:**
```groovy
publish {
    to('dockerhub') {
        registry.set("docker.io")  // Explicit Docker Hub registry
        repository.set("username/myapp")
        publishTags.set(["latest", "v1.0"])

        auth {
            username.set(providers.environmentVariable("DOCKERHUB_USERNAME"))
            // Use Personal Access Token (preferred) instead of password
            password.set(providers.environmentVariable("DOCKERHUB_TOKEN"))
            // serverAddress automatically extracted as docker.io
        }
    }
}
```

**B) GitHub Container Registry:**
```groovy
publish {
    to('ghcr') {
        registry.set("ghcr.io")
        repository.set("username/myapp")
        publishTags.set(["latest"])

        auth {
            username.set(providers.environmentVariable("GHCR_USERNAME"))
            password.set(providers.environmentVariable("GHCR_PASSWORD"))
            // serverAddress automatically extracted as ghcr.io
        }
    }
}
```

**Registry Authentication Examples:**

**C) Private Registry WITHOUT Authentication:**
```groovy
publish {
    to('privateRegistry') {
        registry.set("my-company.com:5000")
        repository.set("team/myapp")
        publishTags.set(["latest", "beta"])
        // No auth block - registry allows anonymous push
    }
}
```

**D) Private Registry WITH Authentication:**
```groovy
publish {
    to('authenticatedRegistry') {
        registry.set("secure-registry.company.com")
        namespace.set("engineering")
        imageName.set("myapp")
        publishTags.set(["latest", "v2.1"])

        auth {
            username.set(providers.environmentVariable("REGISTRY_USERNAME"))
            password.set(providers.environmentVariable("REGISTRY_PASSWORD"))
            // serverAddress automatically extracted as secure-registry.company.com
        }
    }
}
```

**Local Development Example:**

**E) Local Development Registry:**
```groovy
publish {
    to('localDev') {
        registry.set("localhost:5000")  // Example for local dev registry
        repository.set("myapp")
        publishTags.set(["dev", "latest"])
        // No auth block - local registry typically runs without authentication
    }
}
```

**Note**: Environment variables are validated automatically by the plugin with helpful error messages if missing or empty, including registry-specific suggestions for common variable names.

#### **Step 3.3: Add Complete Context Examples**
- Direct `context` property usage
- Inline `context {}` DSL block
- Multiple context configuration approaches

#### **Step 3.4: Add Label Helper Method Examples**
- Single `label(key, value)` usage
- Bulk `labels(map)` usage
- Provider-based label examples

### **Phase 4: Validation & Testing**

#### **Step 4.1: Update Unit Tests**
- Remove serverAddress-related test cases
- Add explicit registry validation test cases
- Add registry conflict detection test cases
- Add environment variable validation test cases with registry-specific error messages
- Test auth resolution with explicit registries

#### **Step 4.2: Update Functional Tests**
- Ensure TestKit compatibility with registry validation
- Test DSL validation with explicit registry requirement
- Test environment variable error handling scenarios

## **Expected Error Messages**

```bash
# Missing username
> Task :dockerPublishMyApp FAILED
Authentication username environment variable is not set for registry 'docker.io' in target 'dockerhub'.
Ensure your username environment variable is set. Common examples: DOCKERHUB_USERNAME, DOCKER_USERNAME

# Empty token
> Task :dockerPublishMyApp FAILED
Authentication password/token is empty for registry 'ghcr.io' in target 'ghcr'.
Ensure your password/token environment variable contains a valid value.

# Generic registry
> Task :dockerPublishMyApp FAILED
Authentication username environment variable is not set for registry 'my-company.com' in target 'corporate'.
Ensure your username environment variable is set. Common examples: REGISTRY_USERNAME, MY_COMPANY_COM_USERNAME

# Registry conflict
> Task :dockerPublishMyApp FAILED
Registry conflict in publish target 'production': registry.set('docker.io') conflicts with repository 'ghcr.io/user/app'.
Use either registry property OR fully qualified repository, not both.
```

## **Success Criteria**

1. **ServerAddress Property Removed**: All references eliminated from codebase
2. **Explicit Registry Validation**: All publish targets require explicit registry specification
3. **Registry Conflict Detection**: Conflicts between registry and repository properties are caught
4. **Enhanced Error Messages**: Environment variable issues provide helpful, registry-specific suggestions
5. **Updated Documentation**: Usage examples reflect new requirements and best practices
6. **All Tests Pass**: Unit and functional tests updated and passing

## **Benefits**

1. **API Consistency**: Aligns implementation with v3 specification
2. **User Safety**: Prevents accidental public pushes to Docker Hub
3. **Clear Intent**: Explicit registry requirements eliminate ambiguity
4. **Better UX**: Helpful error messages guide users to solutions
5. **Maintainability**: Centralized validation logic