# IDE Templates for gradle-docker Plugin

This directory contains IDE templates to help you quickly create test classes with the required Docker Compose
annotations.

## IntelliJ IDEA Live Templates

### Installation

1. **Locate your IntelliJ IDEA configuration directory:**
   - **macOS**: `~/Library/Application Support/JetBrains/IntelliJIdea<version>/templates/`
   - **Linux**: `~/.config/JetBrains/IntelliJIdea<version>/templates/`
   - **Windows**: `%APPDATA%\JetBrains\IntelliJIdea<version>\templates\`

   For Community Edition, replace `IntelliJIdea` with `IdeaIC`.

2. **Copy the template file:**
   ```bash
   # macOS example
   cp gradle-docker-templates.xml ~/Library/Application\ Support/JetBrains/IntelliJIdea2024.1/templates/

   # Linux example
   cp gradle-docker-templates.xml ~/.config/JetBrains/IntelliJIdea2024.1/templates/
   ```

3. **Restart IntelliJ IDEA**

4. **Verify installation:**
   - Go to `Settings/Preferences` → `Editor` → `Live Templates`
   - Look for the "Gradle Docker" group

### Available Templates

| Abbreviation | Description | Language |
|--------------|-------------|----------|
| `composeup` | Spock test class with `@ComposeUp` | Groovy |
| `spockintegration` | Complete Spock integration test | Groovy |
| `junit5class` | JUnit 5 test with CLASS lifecycle | Java |
| `junit5method` | JUnit 5 test with METHOD lifecycle | Java |
| `junit5integration` | Complete JUnit 5 integration test | Java |
| `dockerorch` | dockerOrch DSL configuration | Gradle |

### Usage

1. Create a new file (or open existing)
2. Type the abbreviation (e.g., `composeup`)
3. Press `Tab` to expand the template
4. Fill in the placeholder values using `Tab` to navigate between them
5. Press `Enter` to complete

### Template Details

#### `composeup` - Quick Spock Test

Creates a minimal Spock test class with `@ComposeUp`:

```groovy
@ComposeUp
class MyServiceIT extends Specification {
    // cursor here
}
```

#### `spockintegration` - Full Spock Integration Test

Creates a complete Spock integration test with:
- Package declaration
- Required imports
- `@ComposeUp` annotation
- `@Shared` base URL
- `setupSpec()` that reads compose state
- Sample test method

#### `junit5class` / `junit5method` - Quick JUnit 5 Tests

Creates a minimal JUnit 5 test class with the appropriate extension:

```java
@ExtendWith(DockerComposeClassExtension.class)  // or DockerComposeMethodExtension
class MyServiceIT {
    // cursor here
}
```

#### `junit5integration` - Full JUnit 5 Integration Test

Creates a complete JUnit 5 integration test with:
- Package declaration
- Required imports
- `@ExtendWith` annotation
- Static `baseUrl` field
- `@BeforeAll` setup method
- Sample test method

#### `dockerorch` - Gradle Configuration

Creates the complete `dockerOrch` DSL configuration with `usesCompose()`:

```groovy
dockerOrch {
    composeStacks {
        myTest {
            files.from('src/integrationTest/resources/compose/app.yml')
            waitForHealthy {
                waitForServices.set(['my-service'])
                timeoutSeconds.set(60)
            }
        }
    }
}

tasks.named('integrationTest') {
    usesCompose(stack: "myTest", lifecycle: "class")
}
```

## VS Code Snippets

Coming soon.

## Other IDEs

Pull requests welcome for additional IDE support.

## Troubleshooting

### Templates not appearing

1. Verify the file is in the correct `templates/` directory
2. Restart IntelliJ IDEA completely
3. Check `Settings` → `Editor` → `Live Templates` for the "Gradle Docker" group
4. Ensure the file has `.xml` extension

### Template not expanding

1. Verify cursor is in the correct context (Groovy for Spock, Java for JUnit 5)
2. Check that code completion is enabled
3. Try typing the full abbreviation and pressing `Tab`

### Wrong context

Templates are context-sensitive:
- `composeup`, `spockintegration` work in Groovy files
- `junit5class`, `junit5method`, `junit5integration` work in Java files
- `dockerorch` works in Gradle build files
