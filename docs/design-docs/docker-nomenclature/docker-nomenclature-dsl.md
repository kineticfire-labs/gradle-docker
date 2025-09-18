# Docker DSL Nomenclature


## Build: Image Naming/Referencing

Images are named/referenced as below:
- `registry` = host and optional port, e.g. `ghcr.io`, `example.com:5000`
- Choose *ONE* of these:
   - `repository`: 
      - repository = the path only consisting of namespace + name (no host, no tag), e.g. `kineticfire/stuff/my-app`
   - namespace + name (together define the repository path) 
      - `namespace` e.g., `kineticfire/stuff/`
      - `name` e.g., `my-app`
- `tag` = `1.0.0`, `latest`, etc.

- Resulting full reference:
   - `[registry/]<repository>:<tag>`
   - `[registry/]<namespace + name>:<tag>`

Minimum required is either `repository` or `name`.

Enforce validation rule: if either 'namespace' or 'name' is set AND 'repository' is set, then throw an error that they
cannot be set together.  Use one method or the other.

Using registry/namespace/name:
```groovy
registry  = "kineticfire.com:5000"
namespace = "kineticfire/stuff"
name      = "my-app"
tag       = "1.0.0"

// full reference:
"kineticfire.com:5000/kineticfire/stuff/my-app:1.0.0"
```

Using registry/repository:
```groovy
registry   = "kineticfire.com:5000"
repository = "kineticfire/stuff/my-app"
tag        = "1.0.0"

// full reference:
"kineticfire.com:5000/kineticfire/stuff/my-app:1.0.0"
```

If not building a new image and, rather, using one already built, then define `sourceRef`.  If 'sourceRef' and either one of
'contextTask', 'buildArgs', 'labels', 'dockerfile', 'namespace', 'name', OR 'repository' is set; then error. 
- `sourceRef = 'kineticfire/blah/time-server:test'`

## Build: Build Args

Model `buildArgs` the Gradle way:
- What: Declare them as MapProperty<String, String> on your extension and tasks. This makes values lazy, trackable, and config-cache friendly.
- API rules:
    - put(k, v) / putAll(map) → merge into the current map (last write for a key wins).
    - Overloads should accept Provider<String> / Provider<Map<...>> for laziness.
    - set(map) → replace the entire map.
    - Optionally call finalizeValueOnRead() or disallowChanges() after configuration.
- Consumption: Only call .get() inside @TaskAction so Providers evaluate at execution time.

```groovy
// Literal value
buildArg("BUILD_VERSION", version.toString())

// From another provider (Jar task’s archive name)
def jarName = tasks.named(":app:jar", Jar).flatMap { it.archiveFileName }
buildArg("JAR_FILE", jarName)

// Bulk put with a map
buildArgs([
        "APP_NAME": name,                            // Property<String> resolves lazily
        "GIT_SHA" : providers.gradleProperty("GIT_SHA").orElse("unknown")
])
```

Don't use `'BUILD_TIME': new Date().format('yyyy-MM-dd HH:mm:ss')`, it's not lazy evaluated!  Instead use lazy statement 
like `'BUILD_TIME': java.time.Instant.now().toString()`.


## Build: Labels

Model `labels` the Gradle way:
- What: Declare them as MapProperty<String, String> on your extension and tasks. This makes values lazy, trackable, and config-cache friendly.
- API rules:
   - put(k, v) / putAll(map) → merge into the current map (last write for a key wins).
   - Overloads should accept Provider<String> / Provider<Map<...>> for laziness.
   - set(map) → replace the entire map.
   - Optionally call finalizeValueOnRead() or disallowChanges() after configuration.
- Consumption: Only call .get() inside @TaskAction so Providers evaluate at execution time.

```groovy
// Individual values
label "org.opencontainers.image.revision", gitSha
label "org.opencontainers.image.title", "time-server"


// Bulk merge
labels([
        "org.opencontainers.image.source": "https://github.com/acme/time-server",
        "org.opencontainers.image.licenses": "Apache-2.0"
])
```

## Save

Make 'compression' a property AND an enum:
```groovy
enum SaveCompression {
  NONE, GZIP, ZIP, BZIP2, XZ
}
```

## Use Gradle Properties

Use Gradle Property types everywhere to keep config-cache compatible and play nice with Kotlin DSL.  Examples:
```
name: Property<String> 
namespace: Property<String> 
version: Property<String> (default from project.version) 
tags: ListProperty<String> 
labels: MapProperty<String, String> 
sourceRef: Property<String> 
publish.publishTags: ListProperty<String> 
save.outputFile: RegularFileProperty
```

The above isn't an exhaustive list!  Should be like this (using properties) as possible!

### Concept plugin code

Concept plugin code for using Gradle properties to keep config-cache compatible.

Notes
- All fields use Property/ListProperty/MapProperty/RegularFileProperty for laziness + config-cache compatibility.
- Call .get() only inside @TaskAction to evaluate Providers at execution time.
- You can add finalizeValueOnRead() or disallowChanges() in your plugin after configuration if you want to lock values.

Example `DockerImageSpec.groovy`:
```groovy
import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.*
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested

import javax.inject.Inject

abstract class DockerImageSpec {

    @Input abstract Property<String> getName()          // leaf image name
    @Input abstract Property<String> getNamespace()     // optional path prefix
    @Input abstract Property<String> getVersion()       // defaults from project.version
    @Input abstract ListProperty<String> getTags()
    @Input abstract MapProperty<String, String> getLabels()
    @Input abstract Property<String> getSourceRef()     // if set, skip build

    @Nested final PublishSpec publish
    @Nested final SaveSpec save

    @Inject
    DockerImageSpec(ObjectFactory objects, ProjectLayout layout, ProviderFactory providers, Project project) {
        // nested specs
        this.publish = objects.newInstance(PublishSpec)
        this.save    = objects.newInstance(SaveSpec, layout)

        // sensible conventions
        namespace.convention("")                                      // no namespace by default
        version.convention(providers.provider { project.version.toString() })
        tags.convention(version.map { v -> [v] })                     // default tag = version
        labels.convention([:])                                        // empty map
        sourceRef.convention("")                                      // empty = build locally
    }

    // ergonomic helpers (optional)
    void label(String k, String v)                { labels.put(k, v) }
    void label(String k, Provider<String> v)      { labels.put(k, v) }
    void labels(Map<String,String> m)             { labels.putAll(m) }
    void labels(Provider<? extends Map<String,String>> pm) { labels.putAll(pm) }
}
```

Example `PublishSpec.groovy`:
```groovy
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input

import javax.inject.Inject

abstract class PublishSpec {
  @Input abstract ListProperty<String> getPublishTags()

  @Inject
  PublishSpec(ObjectFactory objects) {
    publishTags.convention([])
  }
}
```

Example `SaveSpec.groovy`:
```groovy
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile

import javax.inject.Inject

abstract class SaveSpec {
  @OutputFile abstract RegularFileProperty getOutputFile()

  @Inject
  SaveSpec(ProjectLayout layout) {
    outputFile.convention(layout.buildDirectory.file("docker-images/image.tar"))
  }
}
```

Example `DockerBuild.groovy` (task consuming the properties):
```groovy
import org.gradle.api.DefaultTask
import org.gradle.api.provider.*
import org.gradle.api.tasks.*

abstract class DockerBuild extends DefaultTask {

  @Input abstract Property<String> getName()
  @Input abstract Property<String> getNamespace()
  @Input abstract Property<String> getVersion()
  @Input abstract ListProperty<String> getTags()
  @Input abstract MapProperty<String, String> getLabels()

  @InputDirectory abstract DirectoryProperty getContextDir()
  @InputFile      abstract RegularFileProperty getDockerfile()

  @TaskAction
  void run() {
    // compute repository = [namespace + "/"] + name (no registry for local build)
    def repo = (namespace.getOrElse("")?.trim()) ? "${namespace.get().trimEnd('/')}/${name.get()}" : name.get()

    // primary ref from version; you could also loop all tags here if you tag locally
    def primaryRef = "${repo}:${version.get()}"

    def args = ["build", "-f", dockerfile.get().asFile.absolutePath, "-t", primaryRef]
    labels.get().each { k, v -> args += ["--label", "${k}=${v}"] }
    args += contextDir.get().asFile.absolutePath

    logger.lifecycle("docker ${args.join(' ')}")
    project.exec { it.commandLine("docker", args) }
  }
}
```

Example DSL usage (Groovy):
```groovy
docker {
  images {
    timeServer {
      namespace.set("kineticfire/blah")
      name.set("my-app")
      version.set(project.version.toString())
      tags.set([version.get(), "latest"])

      label "org.opencontainers.image.title", name
      labels([
        "org.opencontainers.image.version": version.get(),
        "org.opencontainers.image.source" : "https://github.com/acme/time-server"
      ])

      // save/publish examples
      save.outputFile.set(layout.buildDirectory.file("docker-images/${name.get()}-${version.get()}.tar.gz"))
      publish.publishTags.convention(tags)  // inherit image-level tags
    }
  }
}
```


## Example DSL

Uses properties and lazy configuration, keeping config-cache compatible.

```groovy
docker {
    images {
        timeServer {
            
            // this section builds a new image
            contextTask = tasks.register('prepareTimeServerContext', Copy) {
                group = 'docker'
                description = 'Prepare Docker build context for time server image'

                // build/docker-context/<imageName>
                into(layout.buildDirectory.dir(name.map { n -> "docker-context/${n}" }))

                // Static build context files
                from(layout.projectDirectory.dir('src/main/docker'))

                // Pull the JAR produced by :app:jar lazily
                def jarTask = tasks.named(':app:jar', Jar)
                dependsOn(jarTask)

                // Copy the jar into the context, renaming to app-<version>.jar at execution time
                from(jarTask.flatMap { it.archiveFile }) {
                    rename { "app-${version.get()}.jar" }
                }
            }
            buildArg "myarg", "myvalue"
            buildArgs = ([
                    'JAR_FILE': "app-${version}.jar",
                    'BUILD_VERSION': version,
                    'BUILD_TIME': java.time.Instant.now().toString()
            ])
            //CHANGE HERE! with 'labels'.  these are baked into the image.  add with "build --label key=value".  support any key-value pair appearing in 'labels'.
            label "org.opencontainers.image.revision", gitSha
            labels([
                    "org.opencontainers.image.title": "time-server",
                    "org.opencontainers.image.source": "https://github.com/acme/time-server"
                    ])
            // dockerfile will default to 'build/docker-context/timeServer/Dockerfile' based on image name
            namespace.set("team")         // CHANGE HERE!; optional; can be "team" or "my-repo/team" etc. or not defined; namespace can end with a '/' or not
            name.set("time-server")       // CHANGE HERE!; required; leaf only, e.g. the specific image name without registry, namespace, or tag
            // repository = "team/time-server"  // CHANGE HERE!; required; could use 'repository' instead of 'namespace' + 'name'
            // END build a new image
            
            // If not building a new image and, rather, using one already built, then define `sourceRef`.  If 'sourceRef' and either one of 
            // 'contextTask', 'buildArgs', 'labels', 'dockerfile', 'namespace', 'name', OR 'repository' is set; then error.
            //
            // sourceRef = 'kineticfire/blah/time-server:test'

            version.set(project.version.toString())
            tags.set([  // CHANGE HERE!; required; 1 or more tags
                        "${version}",
                        "latest"
            ])
            
            // resulting local images:
            //   - if from build:
            //     - team/time-server:1.0.0
            //     - team/time-server:latest
            //   - if from existing image:
            //     - 'kineticfire/blah/time-server:1.0.0'
            //     - 'kineticfire/blah/time-server:latest'

            save {
                // Lazily compute the tar path from name + version
                def tarName = providers.zip(name, version) { n, v -> "docker-images/${n}-${v}.tar.gz" }
                outputFile.set(layout.buildDirectory.file(tarName))    // RegularFileProperty
                compression.set(SaveCompression.GZIP)                  // enum
                pullIfMissing.set(false)                               // if true, source ref must include registry/namespace

                // Authentication (choose ONE; only needed when pullIfMissing = true)
                // auth.basic {
                //   username.set(providers.gradleProperty("REG_USER"))
                //   password.set(providers.gradleProperty("REG_PASS"))
                // }

                // auth.token {
                //   value.set(providers.gradleProperty("REG_TOKEN"))
                // }

                // auth.helper {
                //   name.set("docker-credential-ecr-login")
                // }

            }

            publish {
                // If unset, inherit from the image's root tags (ListProperty<String>)
                publishTags.convention(tags)

                // Override at the publish block level (applies to all targets unless they override)
                // publishTags.set(["1.0.0", "edge"])

                to("localRegistry") {
                    registry.set("localhost:25000")          // Property<String>
                    namespace.set("kineticfire/blah")        // Property<String>
                    // get the image name from the 'name' property in the build section or extract it from 'sourceRef'
                    publishTags.set(["latest", "edge"])      // ListProperty<String> (overrides parent)

                    // Choose ONE auth mode (optional)
                    auth {
                        basic {
                            username.set(providers.gradleProperty("LOCAL_REG_USER"))
                            password.set(providers.gradleProperty("LOCAL_REG_PASS"))
                        }
                        // token { value.set(providers.gradleProperty("LOCAL_REG_TOKEN")) }
                        // helper { name.set("docker-credential-ecr-login") }
                    }

                    // publishes:
                    //   localhost:25000/kineticfire/blah/<name>:latest
                    //   localhost:25000/kineticfire/blah/<name>:edge
                }

                to("dockerHub") {
                    registry.set("docker.io") // Be explicit: Docker Hub
                    namespace.set("kineticfire/blah")
                    name.set("diff-image") // override 'name'
                    // not setting publishTags here → so inherits publish.publishTags (which defaults to root tags)

                    // Inherit publish tags from parent unless you override with .set([...]), so inherits publish.publishTags (which defaults to root tags)
                    // publishTags.set(["1.0.0", "edge"])
                    
                    auth {
                        basic {
                            username.set(providers.gradleProperty("DOCKERHUB_USER"))
                            password.set(providers.gradleProperty("DOCKERHUB_TOKEN"))
                        }
                        // or: token { value.set(providers.gradleProperty("DOCKERHUB_TOKEN")) }
                        // or: helper { name.set("docker-credential-docker-hub") }
                    }

                    // publishes (assuming root tags e.g. ["1.0.0","latest"]):
                    //   dockerhub.io/kineticfire/blah/diff-image:1.0.0
                    //   dockerhub.io/kineticfire/blah/diff-image:latest
                }

                to("ghcr") {
                    registry.set("ghcr.io")
                    repository.set("kineticfire/blah/diff-image") // set 'namespace' and override 'name'

                    // Inherit publish tags from parent unless you override with .set([...]), so inherits publish.publishTags (which defaults to root tags)
                    // publishTags.set(["1.0.0", "edge"])

                    auth {
                        token { value.set(providers.gradleProperty("GHCR_TOKEN")) }
                        // or: basic { username.set(...); password.set(...) }
                        // or: helper { name.set("docker-credential-ghcr") }
                    }

                    // publishes (assuming root tags e.g. ["1.0.0","latest"]):
                    //   ghcr.io/kineticfire/blah/<name>:1.0.0
                    //   ghcr.io/kineticfire/blah/<name>:latest
                }
            }
        }
    }
}
```