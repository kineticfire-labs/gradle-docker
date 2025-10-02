/*
 * (c) Copyright 2023-2025 gradle-docker Contributors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kineticfire.gradle.docker.spec

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class PublishTargetValidationTest extends Specification {

    Project project
    ObjectFactory objectFactory

    def setup() {
        project = ProjectBuilder.builder().build()
        objectFactory = project.objects
    }

    def "validateRegistry should allow completely empty target"() {
        given:
        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        // All properties empty

        when:
        target.validateRegistry()

        then:
        noExceptionThrown()
    }

    def "validateRegistry should require registry for non-empty target without qualified repository"() {
        given:
        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.imageName.set("myapp")  // Has some property but no registry

        when:
        target.validateRegistry()

        then:
        def exception = thrown(GradleException)
        exception.message.contains("Registry must be explicitly specified")
        exception.message.contains("leave the target completely empty to inherit")
    }

    def "validateRegistry should allow target with explicit registry"() {
        given:
        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.registry.set("docker.io")
        target.imageName.set("myapp")
        target.publishTags(["latest"])

        when:
        target.validateRegistry()

        then:
        noExceptionThrown()
    }

    def "validateRegistry should allow target with fully qualified repository"() {
        given:
        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.repository.set("docker.io/company/app")  // Fully qualified
        target.publishTags(["latest"])

        when:
        target.validateRegistry()

        then:
        noExceptionThrown()
    }

    def "validateRegistry should detect empty target with only publishTags"() {
        given:
        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.publishTags(["latest"])  // Only publishTags set

        when:
        target.validateRegistry()

        then:
        def exception = thrown(GradleException)
        exception.message.contains("Registry must be explicitly specified")
    }

    def "validateRegistry should detect empty target with only namespace"() {
        given:
        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.namespace.set("company")  // Only namespace set

        when:
        target.validateRegistry()

        then:
        def exception = thrown(GradleException)
        exception.message.contains("Registry must be explicitly specified")
    }

    def "validateRegistry should allow target with registry and namespace only"() {
        given:
        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.registry.set("localhost:5000")
        target.namespace.set("project")
        // imageName and publishTags will be inherited

        when:
        target.validateRegistry()

        then:
        noExceptionThrown()
    }

    def "validateRegistry should handle repository with port but no dots"() {
        given:
        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.repository.set("localhost:5000/app")  // Has port, should be qualified
        target.publishTags(["test"])

        when:
        target.validateRegistry()

        then:
        noExceptionThrown()
    }

    def "validateRegistry should require registry for repository without qualified parts"() {
        given:
        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.repository.set("company/app")  // Not fully qualified
        target.publishTags(["test"])

        when:
        target.validateRegistry()

        then:
        def exception = thrown(GradleException)
        exception.message.contains("Registry must be explicitly specified")
    }

    def "validateRegistryConsistency should detect conflicts"() {
        given:
        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.registry.set("docker.io")
        target.repository.set("ghcr.io/company/app")  // Conflicts with registry

        when:
        target.validateRegistryConsistency()

        then:
        def exception = thrown(GradleException)
        exception.message.contains("Registry conflict")
        exception.message.contains("docker.io")
        exception.message.contains("ghcr.io/company/app")
    }

    def "validateRegistryConsistency should allow consistent registry and repository"() {
        given:
        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.registry.set("docker.io")
        target.repository.set("docker.io/company/app")  // Consistent

        when:
        target.validateRegistryConsistency()

        then:
        noExceptionThrown()
    }

    def "validateRegistryConsistency should allow registry with non-qualified repository"() {
        given:
        def target = objectFactory.newInstance(PublishTarget, "test", objectFactory)
        target.registry.set("docker.io")
        target.repository.set("company/app")  // Not qualified, no conflict

        when:
        target.validateRegistryConsistency()

        then:
        noExceptionThrown()
    }
}