@Grab('org.spockframework:spock-core:2.3-groovy-4.0')

import spock.lang.Specification
import com.kineticfire.gradle.docker.extension.DockerExtension

class DebugTest {
    static void main(String[] args) {
        def extension = new DockerExtension()
        
        def testCases = [
            'myapp:latest',
            'registry.example.com/myapp:1.0.0',
            'user/repo:tag-123',
            'my-app:1.2.3-alpha',
            'repo.domain.com/namespace/app:stable',
            'a:b',
            '1app:1tag',
            'private-registry.company.com/team/app:1.0.0',
            'gcr.io/project-id/app:latest',
            'docker.io/library/nginx:alpine'
        ]
        
        testCases.each { imageRef ->
            boolean result = extension.isValidImageReference(imageRef)
            println "$imageRef -> $result"
        }
    }
}