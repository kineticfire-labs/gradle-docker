import com.kineticfire.gradle.docker.spec.*
import org.gradle.testfixtures.ProjectBuilder

def project = ProjectBuilder.builder().build()
def publishSpec = project.objects.newInstance(PublishSpec, project.objects)

// Test direct target creation
def target = project.objects.newInstance(PublishTarget, "test", project.objects)
target.publishTags.set(["test:latest"])
println "Direct target publishTags: ${target.publishTags.get()}"

// Test through publishSpec.to() method
publishSpec.to("dockerhub") {
    publishTags.set(["docker.io/myuser/app:latest"])
}

def dockerhubTarget = publishSpec.to.getByName("dockerhub")
println "PublishSpec target publishTags: ${dockerhubTarget.publishTags.get()}"
