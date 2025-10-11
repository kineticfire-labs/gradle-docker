# Scenario 99: Docker Hub Publishing Integration Test

Tests publishing Docker images to Docker Hub public registry with authentication and timestamp-based tags for
rerunnability.

## ⚠️ PREREQUISITES

### 1. Create Private Repository

**IMPORTANT:** Create the repository as **PRIVATE** to avoid polluting the public Docker Hub namespace with test images.

1. Visit https://hub.docker.com
2. Click "Create Repository"
3. Repository name: `gradle-docker-test`
4. Namespace/Username: `kineticfire`
5. Visibility: **Private**
6. Click "Create"

**Final repository:** `kineticfire/gradle-docker-test`

### 2. Get Personal Access Token

Personal Access Tokens (PATs) are more secure than using your password.

1. Visit https://hub.docker.com/settings/security
2. Click "New Access Token"
3. Access token description: `gradle-docker-test-integration`
4. Access permissions: **Read & Write** (required for push)
5. Click "Generate"
6. **Copy the token immediately** (you won't see it again!)

### 3. Set Environment Variables

Set these environment variables before running the test:

```bash
export DOCKERHUB_USERNAME='your-username'
export DOCKERHUB_TOKEN='your-personal-access-token'
```

**Security Notes:**
- Never commit these values to git
- Use different tokens for different purposes
- Rotate tokens periodically
- Revoke tokens when no longer needed

## 🚀 RUNNING THE TEST

### From plugin-integration-test directory

```bash
# Set credentials first
export DOCKERHUB_USERNAME='your-username'
export DOCKERHUB_TOKEN='your-access-token'

# Run the test (replace 1.0.0 with your plugin version)
./gradlew -Pplugin_version=1.0.0 docker:scenario-99:dockerHubIntegrationTest -PenableDockerHubTests=true
```

**Note:** The `plugin_version` parameter is required and must match the version you published to Maven local.

### What the -PenableDockerHubTests flag does

- **Required:** Prevents accidental execution
- **Safety:** Guards against running without credentials
- **Isolation:** Keeps this test separate from regular integration tests

## 🔍 WHAT GETS TESTED

The test executes this workflow:

1. **Build** - Builds TimeServer Docker image with timestamp-based tag
   - Tag format: `test-2025-10-10T14-30-45Z`
   - Uses gradle-docker plugin's build functionality

2. **Publish** - Pushes image to Docker Hub
   - Registry: `docker.io`
   - Repository: `kineticfire/gradle-docker-test`
   - Uses credentials from environment variables

3. **Verify Local** - Confirms local build succeeded
   - Checks image exists in local Docker image store

4. **Verify Remote** - Pulls from Docker Hub to verify publish
   - Removes local copy first (ensures fresh pull)
   - Authenticates with Docker Hub
   - Pulls image back from Docker Hub
   - **This proves the image was successfully published**

5. **Cleanup** - Removes local test images
   - Cleans build image
   - Cleans pulled image
   - **Remote image requires manual cleanup** (see below)

## 🏷️ TAG STRATEGY

### Why Timestamp-Based Tags?

Using fixed tags (like `latest` or `1.0.0`) would fail on subsequent runs because the image already exists on Docker Hub.

**Solution:** Dynamic timestamp tags

- Format: `test-2025-10-10T14-30-45Z`
- **Unique per run** - No conflicts, test can be rerun unlimited times
- **Chronologically sortable** - Easy to find latest test runs
- **Human readable** - Can manually verify on Docker Hub web UI
- **Config cache compatible** - Uses Gradle ValueSource API

### Tag Lifecycle

1. **Created** - During Gradle configuration phase
2. **Build** - Applied to local image
3. **Publish** - Pushed to Docker Hub with same tag
4. **Verify** - Pulled back from Docker Hub
5. **Cleanup** - Local images deleted; remote requires manual cleanup

## 🧹 CLEANUP

### Automatic Cleanup (Local)

Local images are cleaned automatically after the test:
- Build image: `scenario99-time-server:test-{timestamp}`
- Pulled image: `docker.io/kineticfire/gradle-docker-test:test-{timestamp}`

### Manual Cleanup (Remote) - REQUIRED!

**Docker Hub images must be deleted manually to avoid clutter.**

#### How to Clean Up Remote Images

1. Visit https://hub.docker.com/r/kineticfire/gradle-docker-test/tags
2. Log in if needed
3. Find tags starting with `test-`
4. Click the **three dots** (⋮) next to each tag
5. Select **Delete**
6. Confirm deletion

#### Cleanup Schedule

**Recommendation:** Clean up weekly to avoid accumulating test images

- Keep last 5-10 test runs for debugging
- Delete older test tags
- Watch repository storage quota

#### Why Not Automatic?

- Docker Hub API requires separate authentication setup
- Adds complexity and dependencies
- Manual cleanup is simple and sufficient for integration tests
- Allows inspection of published images if needed

## ❓ TROUBLESHOOTING

### "Docker Hub credentials not found"

**Symptom:** Error message about missing DOCKERHUB_USERNAME or DOCKERHUB_TOKEN

**Solution:**
```bash
# Check if variables are set
echo $DOCKERHUB_USERNAME
echo $DOCKERHUB_TOKEN

# Set them if missing
export DOCKERHUB_USERNAME='your-username'
export DOCKERHUB_TOKEN='your-token'
```

**Common Issues:**
- Variable names are case-sensitive
- Variables only persist in current terminal session
- Add to shell profile (.bashrc, .zshrc) for persistence

### "unauthorized: authentication required"

**Symptom:** Docker push or pull fails with authentication error

**Solutions:**
1. Verify token has **Read & Write** permissions
2. Try regenerating token at https://hub.docker.com/settings/security
3. Check token hasn't expired
4. Verify username matches repository namespace

### "repository does not exist"

**Symptom:** Push fails with "repository not found"

**Solutions:**
1. Create repository at https://hub.docker.com
2. Verify repository name: `kineticfire/gradle-docker-test`
3. Check repository is **private** (recommended)
4. Ensure you have access to the namespace

### Pull failed during verification

**Symptom:** Cannot pull image from Docker Hub in verification step

**Solutions:**
1. Check repository is accessible with your credentials
2. Verify image was actually pushed (check Docker Hub web UI)
3. Check network connectivity
4. Try manual pull: `docker pull docker.io/kineticfire/gradle-docker-test:test-{timestamp}`

### "Cannot query the value of this provider" / plugin_version error

**Symptom:** Error about missing `plugin_version` Gradle property

```
Cannot query the value of this provider because it has no value available.
  The value of this provider is derived from:
    - Gradle property 'plugin_version'
```

**Solution:**
```bash
# Add -Pplugin_version=<version> to the command
./gradlew -Pplugin_version=1.0.0 docker:scenario-99:dockerHubIntegrationTest -PenableDockerHubTests=true
```

**Why this is required:**
- All integration tests need to know which plugin version to use
- The plugin must be built and published to Maven local first
- Use the same version you published: `./gradlew -Pplugin_version=1.0.0 build publishToMavenLocal`

### Task disabled / Test doesn't run

**Symptom:** Task shows as disabled or doesn't execute

**Solution:**
```bash
# Make sure to include BOTH property flags
./gradlew -Pplugin_version=1.0.0 docker:scenario-99:dockerHubIntegrationTest -PenableDockerHubTests=true
```

### "Integration test must be run from top-level directory"

**Symptom:** Error about running from wrong directory

**Solution:**
```bash
# Must run from plugin-integration-test directory
cd plugin-integration-test
./gradlew -Pplugin_version=1.0.0 docker:scenario-99:dockerHubIntegrationTest -PenableDockerHubTests=true
```

## 🔒 SECURITY BEST PRACTICES

### Environment Variables

✅ **DO:**
- Use environment variables for credentials
- Set variables in shell session or CI/CD secrets
- Clear variables when done: `unset DOCKERHUB_USERNAME DOCKERHUB_TOKEN`

❌ **DON'T:**
- Commit credentials to git
- Hard-code credentials in build files
- Share tokens publicly
- Use your password instead of token

### Token Management

✅ **DO:**
- Create separate tokens for different purposes
- Use descriptive token names
- Rotate tokens periodically (every 90 days)
- Revoke tokens when no longer needed
- Use minimal required permissions (Read & Write only)

❌ **DON'T:**
- Reuse tokens across projects
- Give tokens admin permissions
- Share tokens between team members
- Leave unused tokens active

### Repository Privacy

✅ **DO:**
- Keep test repositories **private**
- Label test images clearly (test.scenario label)
- Clean up old test images regularly

❌ **DON'T:**
- Use public repositories for tests
- Leave test images published indefinitely
- Use production repository names

## 🔄 REUSABLE PATTERN FOR OTHER REGISTRIES

This scenario demonstrates a pattern that can be replicated for other public registries:

| Registry | Scenario | Registry URL | Auth Variables |
|----------|----------|--------------|----------------|
| **Docker Hub** | **99** | **docker.io** | **DOCKERHUB_USERNAME**, **DOCKERHUB_TOKEN** |
| GitHub Container Registry (GHCR) | 100 | ghcr.io | GITHUB_USERNAME, GITHUB_TOKEN |
| Google Artifact Registry (GAR) | 101 | REGION-docker.pkg.dev | GOOGLE_PROJECT, GOOGLE_SA_KEY |
| Amazon ECR | 102 | ACCOUNT.dkr.ecr.REGION.amazonaws.com | AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY |
| Azure Container Registry (ACR) | 103 | REGISTRY.azurecr.io | AZURE_USERNAME, AZURE_PASSWORD |
| GitLab Container Registry | 104 | registry.gitlab.com | GITLAB_USERNAME, GITLAB_TOKEN |

### Common Pattern Elements

All registry integration tests should follow this pattern:

1. ✅ **Timestamp-based tags** for rerunnability
2. ✅ **Environment variable credentials** for security
3. ✅ **Separate task group** for isolation
4. ✅ **Property-gated execution** to prevent accidents
5. ✅ **Pull-based verification** to prove publish worked
6. ✅ **Clear documentation** with prerequisites
7. ✅ **Manual cleanup instructions** for remote resources

## 📊 TEST COVERAGE

This scenario tests:

- ✅ Publishing to public registry (Docker Hub)
- ✅ Registry authentication with environment variables
- ✅ Timestamp-based tag generation for rerunnability
- ✅ Repository mode: registry + repository + tag
- ✅ Build args with timestamp
- ✅ Docker labels for test identification
- ✅ Pull-based verification (proves image exists remotely)
- ✅ Automatic local cleanup
- ✅ Configuration cache compatibility
- ✅ Isolated execution (doesn't run with regular tests)

## 📝 NOTES

### Why This Test is Separate

This test does NOT run as part of regular integration tests because:

1. **Requires credentials** - Not available in all environments
2. **Publishes externally** - Pushes to real Docker Hub
3. **Requires manual setup** - Repository must be created first
4. **Requires manual cleanup** - Remote images need deletion
5. **Slower execution** - Network I/O to Docker Hub
6. **Not deterministic** - Depends on external service availability

### Integration Test vs Regular Tests

**Regular integration tests (scenarios 1-13):**
- Run automatically with `./gradlew integrationTest`
- Use local Docker registries
- No external dependencies
- Fully automated cleanup
- Fast execution

**Public registry tests (scenario 99+):**
- Require explicit execution with flag
- Use real external registries
- Require credentials and setup
- Partial manual cleanup
- Slower execution (network I/O)

## 🎯 ACCEPTANCE CRITERIA

Test passes when:

- ✅ Image builds successfully with timestamp tag
- ✅ Image publishes to Docker Hub successfully
- ✅ Local image verification succeeds
- ✅ Remote pull verification succeeds (proves publish)
- ✅ Local cleanup completes successfully
- ✅ No errors or warnings during execution
- ✅ Manual cleanup instructions displayed

## 📚 REFERENCES

- Docker Hub: https://hub.docker.com
- Docker Hub Security: https://hub.docker.com/settings/security
- Gradle Configuration Cache: https://docs.gradle.org/current/userguide/configuration_cache.html
- Docker Registry API: https://docs.docker.com/registry/spec/api/
