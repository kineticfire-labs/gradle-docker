# Example Integration Tests

These demonstrate how to use the dockerOrch plugin for testing your applications.

## Purpose

Example tests show **real-world usage** of the dockerOrch plugin:

- ✅ Test application business logic
- ✅ Validate API contracts
- ✅ Check database operations
- ✅ Test service-to-service communication
- ✅ Verify event processing
- ✅ Test scheduled jobs

## 📋 Copy and Adapt These!

**These examples are designed to be copied and adapted for your own projects.**

Each example includes:
- Full application source code
- Typical dockerOrch configuration
- Integration tests using standard libraries
- Detailed comments explaining the approach

## Testing Approach

Examples use **standard testing libraries** that real users would use:

- **RestAssured** - REST API testing
- **JDBC / JPA** - Database operations
- **Kafka Client** - Event streaming
- **Spring Batch** - Batch processing

These are the same tools you would use in your own projects.

## Example Scenarios

All examples use **SUITE lifecycle** (setupSpec/cleanupSpec):
- Containers start once before all tests
- All tests run against the same container environment
- Containers stop once after all tests
- Most efficient for integration testing

### Web App (`web-app/`)

**Use Case**: Testing REST API endpoints

**Lifecycle**: SUITE

**Stack**: Spring Boot + REST endpoints

**Tests**:
- Health check endpoint
- Root endpoint with version info
- Concurrent request handling
- Response time validation

**Libraries**: RestAssured, HTTP client

**Configuration**:
```groovy
dockerOrch {
    composeStacks {
        webApp {
            files.from('compose.yml')
            projectName = "example-web-app"
            waitForHealthy {
                waitForServices.set(['web-app'])
                timeoutSeconds.set(60)
            }
        }
    }
}
```

---

### Database App (`database-app/`)

**Use Case**: Testing application with database persistence

**Lifecycle**: SUITE

**Stack**: Spring Boot + PostgreSQL

**Tests**:
- Database connection
- CRUD operations
- Transactions (commit/rollback)
- Data persistence across requests
- Migration validation

**Libraries**: JDBC, JPA, Spring Data

**Configuration**:
```groovy
dockerOrch {
    composeStacks {
        appStack {
            files.from('compose.yml')
            projectName = "example-database-app"
            waitForHealthy {
                waitForServices.set(['app'])
            }
            waitForRunning {
                waitForServices.set(['postgres'])
            }
        }
    }
}
```

---

### Microservices (`microservices/`)

**Use Case**: Testing multiple interacting services

**Lifecycle**: SUITE

**Stack**: Frontend (React) + Backend (Spring Boot) + Auth Service (Spring Boot)

**Tests**:
- Service-to-service communication
- Authentication/authorization flow
- API gateway routing
- Circuit breaker behavior
- Service discovery

**Libraries**: RestAssured, HTTP client

**Configuration**:
```groovy
dockerOrch {
    composeStacks {
        microservices {
            files.from('compose.yml')
            projectName = "example-microservices"
            waitForHealthy {
                waitForServices.set(['frontend', 'backend', 'auth'])
            }
        }
    }
}
```

---

### Kafka App (`kafka-app/`)

**Use Case**: Testing event-driven applications

**Lifecycle**: SUITE

**Stack**: Spring Boot + Kafka + Zookeeper

**Tests**:
- Produce messages to topic
- Consume messages from topic
- Message ordering
- Consumer groups
- Error handling (dead letter queue)

**Libraries**: Kafka client, TestProducer, TestConsumer

**Configuration**:
```groovy
dockerOrch {
    composeStacks {
        kafkaApp {
            files.from('compose.yml')
            projectName = "example-kafka-app"
            waitForHealthy {
                waitForServices.set(['app'])
            }
            waitForRunning {
                waitForServices.set(['kafka', 'zookeeper'])
            }
        }
    }
}
```

---

### Batch Job (`batch-job/`)

**Use Case**: Testing scheduled/batch processing

**Lifecycle**: SUITE

**Stack**: Spring Boot + Spring Batch + PostgreSQL

**Tests**:
- Trigger batch job via API
- Validate job execution status
- Verify processed data in database
- Test job failure/retry logic
- Schedule-based execution

**Libraries**: Spring Batch, JDBC, RestAssured

**Configuration**:
```groovy
dockerOrch {
    composeStacks {
        batchJob {
            files.from('compose.yml')
            projectName = "example-batch-job"
            waitForHealthy {
                waitForServices.set(['app'])
            }
            waitForRunning {
                waitForServices.set(['postgres'])
            }
        }
    }
}
```

## Running Examples

From `plugin-integration-test/` directory:

```bash
# Run all examples
./gradlew dockerOrch:examples:integrationTest

# Run specific example
./gradlew dockerOrch:examples:web-app:integrationTest
./gradlew dockerOrch:examples:database-app:integrationTest
```

## Example Test Structure

```groovy
class WebAppExampleIT extends Specification {

    static String baseUrl

    def setupSpec() {
        // Read state file generated by dockerOrch plugin
        def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))
        def stateData = new JsonSlurper().parse(stateFile)

        // Get published port
        def port = stateData.services['web-app'].publishedPorts[0].host
        baseUrl = "http://localhost:${port}"

        // Configure RestAssured
        RestAssured.baseURI = baseUrl
    }

    def "should respond to health check endpoint"() {
        when:
        def response = given().get("/health")

        then:
        response.statusCode() == 200
        response.jsonPath().getString("status") == "UP"
    }
}
```

## Key Characteristics

1. **Uses standard libraries** - RestAssured, JDBC, Kafka client, etc.
2. **Tests application logic** - Not plugin mechanics
3. **Real-world scenarios** - Patterns users actually need
4. **Copy-paste ready** - Detailed comments and explanations
5. **For users** - Shows how to use the plugin effectively

## How to Adapt for Your Project

1. **Choose the example closest to your use case**
2. **Copy the directory structure** (app/, app-image/, build files)
3. **Replace the application** with your own code
4. **Update Docker Compose file** with your service definitions
5. **Modify the tests** to validate your business logic
6. **Adjust wait configurations** based on your startup times

## Common Patterns

### Reading Port Mapping

```groovy
def stateFile = new File(System.getProperty('COMPOSE_STATE_FILE'))
def stateData = new JsonSlurper().parse(stateFile)
def port = stateData.services['service-name'].publishedPorts[0].host
```

### Waiting for Healthy Service

```groovy
dockerOrch {
    composeStacks {
        myStack {
            waitForHealthy {
                waitForServices.set(['app'])
                timeoutSeconds.set(60)
                pollSeconds.set(2)
            }
        }
    }
}
```

### Waiting for Running Service (No Health Check)

```groovy
dockerOrch {
    composeStacks {
        myStack {
            waitForRunning {
                waitForServices.set(['database'])
                timeoutSeconds.set(30)
            }
        }
    }
}
```

### Full Test Workflow

```groovy
tasks.register('runIntegrationTest') {
    description = 'Full integration test: build -> up -> test -> down'
    group = 'verification'

    dependsOn tasks.named('dockerBuildMyApp')
    dependsOn tasks.named('composeUpMyStack')
    dependsOn tasks.named('integrationTest')
    finalizedBy tasks.named('composeDownMyStack')
}
```

---

**For plugin verification tests**, see [`../verification/README.md`](../verification/README.md)
