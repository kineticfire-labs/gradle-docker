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

package com.kineticfire.gradle.docker.workflow

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.testing.Test
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Unit tests for TestResultCapture
 */
class TestResultCaptureTest extends Specification {

    @TempDir
    Path tempDir

    Project project
    TestResultCapture capture

    def setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
        capture = new TestResultCapture()
    }

    /**
     * Helper to configure a Test task's junitXml output location to point to the conventional directory.
     * This is needed because ProjectBuilder doesn't automatically configure the junitXml report location.
     * We use project.layout.dir() to properly wrap the File as a Directory provider.
     */
    private void configureJUnitXmlOutputLocation(Test testTask, File reportsDir) {
        testTask.reports.junitXml.outputLocation.set(project.layout.dir(project.provider { reportsDir }))
    }

    // ===== CAPTURE FROM TASK TESTS =====

    def "captureFromTask returns fallback result for non-Test task"() {
        given:
        def task = project.tasks.create('myTask')

        when:
        def result = capture.captureFromTask(task)

        then:
        result.success
        result.executed == 1
        result.totalCount == 1
        result.failureCount == 0
    }

    def "captureFromTask returns fallback result when Test task has no reports"() {
        given:
        def testTask = project.tasks.create('test', Test)

        when:
        def result = capture.captureFromTask(testTask)

        then:
        result.success
        result.executed == 1
        result.totalCount == 1
    }

    def "captureFromTask returns JUnit XML result when available"() {
        given:
        def testTask = project.tasks.create('test', Test)
        def reportsDir = new File(project.layout.buildDirectory.asFile.get(), "test-results/test")
        reportsDir.mkdirs()
        configureJUnitXmlOutputLocation(testTask, reportsDir)

        def xmlContent = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="10" failures="2" errors="1" skipped="1">
</testsuite>'''
        new File(reportsDir, 'TEST-com.example.MyTest.xml').text = xmlContent

        when:
        def result = capture.captureFromTask(testTask)

        then:
        !result.success
        result.totalCount == 10
        result.failureCount == 3  // failures + errors
        result.skipped == 1
        result.executed == 9  // total - skipped
    }

    // ===== CAPTURE FAILURE TESTS =====

    def "captureFailure returns failure result with JUnit data when available"() {
        given:
        def testTask = project.tasks.create('test', Test)
        def reportsDir = new File(project.layout.buildDirectory.asFile.get(), "test-results/test")
        reportsDir.mkdirs()
        configureJUnitXmlOutputLocation(testTask, reportsDir)

        def xmlContent = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="5" failures="2" errors="0" skipped="0">
</testsuite>'''
        new File(reportsDir, 'TEST-com.example.MyTest.xml').text = xmlContent

        def exception = new RuntimeException('Test failed')

        when:
        def result = capture.captureFailure(testTask, exception)

        then:
        !result.success
        result.totalCount == 5
        result.failureCount == 2
    }

    def "captureFailure returns minimal failure result when no JUnit data"() {
        given:
        def task = project.tasks.create('myTask')
        def exception = new RuntimeException('Test failed')

        when:
        def result = capture.captureFailure(task, exception)

        then:
        !result.success
        result.failureCount == 1
        result.executed == 0
        result.totalCount == 0
    }

    // ===== FIND JUNIT REPORTS DIR TESTS =====

    def "findJUnitReportsDir returns standard directory when exists"() {
        given:
        def testTask = project.tasks.create('test', Test)
        def reportsDir = new File(project.layout.buildDirectory.asFile.get(), "test-results/test")
        reportsDir.mkdirs()
        configureJUnitXmlOutputLocation(testTask, reportsDir)

        when:
        def result = capture.findJUnitReportsDir(testTask)

        then:
        result == reportsDir
    }

    def "findJUnitReportsDir returns null when directory does not exist"() {
        given:
        def testTask = project.tasks.create('test', Test)

        when:
        def result = capture.findJUnitReportsDir(testTask)

        then:
        result == null
    }

    def "findJUnitReportsDir uses task name for path"() {
        given:
        def testTask = project.tasks.create('integrationTest', Test)
        def reportsDir = new File(project.layout.buildDirectory.asFile.get(), "test-results/integrationTest")
        reportsDir.mkdirs()
        configureJUnitXmlOutputLocation(testTask, reportsDir)

        when:
        def result = capture.findJUnitReportsDir(testTask)

        then:
        result == reportsDir
    }

    // ===== CAPTURE FROM JUNIT XML TESTS =====

    def "captureFromJUnitXml returns null when no reports directory"() {
        given:
        def testTask = project.tasks.create('test', Test)

        when:
        def result = capture.captureFromJUnitXml(testTask)

        then:
        result == null
    }

    def "captureFromJUnitXml returns null when directory is empty"() {
        given:
        def testTask = project.tasks.create('test', Test)
        def reportsDir = new File(project.layout.buildDirectory.asFile.get(), "test-results/test")
        reportsDir.mkdirs()

        when:
        def result = capture.captureFromJUnitXml(testTask)

        then:
        result == null
    }

    def "captureFromJUnitXml aggregates results from multiple XML files"() {
        given:
        def testTask = project.tasks.create('test', Test)
        def reportsDir = new File(project.layout.buildDirectory.asFile.get(), "test-results/test")
        reportsDir.mkdirs()
        configureJUnitXmlOutputLocation(testTask, reportsDir)

        def xml1 = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="5" failures="1" errors="0" skipped="0">
</testsuite>'''
        new File(reportsDir, 'TEST-Test1.xml').text = xml1

        def xml2 = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="3" failures="0" errors="1" skipped="1">
</testsuite>'''
        new File(reportsDir, 'TEST-Test2.xml').text = xml2

        when:
        def result = capture.captureFromJUnitXml(testTask)

        then:
        result.totalCount == 8
        result.failureCount == 2  // 1 failure + 1 error
        result.skipped == 1
        result.executed == 7  // 8 - 1 skipped
    }

    // ===== PARSE JUNIT XML FILES TESTS =====

    def "parseJUnitXmlFiles aggregates counts correctly"() {
        given:
        def file1 = File.createTempFile('test1', '.xml')
        file1.text = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="10" failures="2" errors="1" skipped="1">
</testsuite>'''
        def file2 = File.createTempFile('test2', '.xml')
        file2.text = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="5" failures="0" errors="0" skipped="2">
</testsuite>'''

        when:
        def result = capture.parseJUnitXmlFiles([file1, file2])

        then:
        result.totalCount == 15
        result.failureCount == 3  // 2 + 1
        result.skipped == 3  // 1 + 2
        result.executed == 12  // 15 - 3
        !result.success

        cleanup:
        file1?.delete()
        file2?.delete()
    }

    def "parseJUnitXmlFiles returns success when no failures"() {
        given:
        def file = File.createTempFile('test', '.xml')
        file.text = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="10" failures="0" errors="0" skipped="0">
</testsuite>'''

        when:
        def result = capture.parseJUnitXmlFiles([file])

        then:
        result.success
        result.failureCount == 0

        cleanup:
        file?.delete()
    }

    def "parseJUnitXmlFiles handles malformed XML gracefully"() {
        given:
        def goodFile = File.createTempFile('good', '.xml')
        goodFile.text = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="5" failures="1" errors="0" skipped="0">
</testsuite>'''
        def badFile = File.createTempFile('bad', '.xml')
        badFile.text = 'not valid xml'

        when:
        def result = capture.parseJUnitXmlFiles([goodFile, badFile])

        then:
        result.totalCount == 5
        result.failureCount == 1

        cleanup:
        goodFile?.delete()
        badFile?.delete()
    }

    // ===== PARSE JUNIT XML FILE TESTS =====

    def "parseJUnitXmlFile extracts counts correctly"() {
        given:
        def file = File.createTempFile('test', '.xml')
        file.text = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="10" failures="2" errors="3" skipped="1">
</testsuite>'''

        when:
        def result = capture.parseJUnitXmlFile(file)

        then:
        result.tests == 10
        result.failures == 2
        result.errors == 3
        result.skipped == 1

        cleanup:
        file?.delete()
    }

    def "parseJUnitXmlFile handles missing attributes"() {
        given:
        def file = File.createTempFile('test', '.xml')
        file.text = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="5">
</testsuite>'''

        when:
        def result = capture.parseJUnitXmlFile(file)

        then:
        result.tests == 5
        result.failures == 0
        result.errors == 0
        result.skipped == 0

        cleanup:
        file?.delete()
    }

    def "parseJUnitXmlFile handles empty attributes"() {
        given:
        def file = File.createTempFile('test', '.xml')
        file.text = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="" failures="" errors="" skipped="">
</testsuite>'''

        when:
        def result = capture.parseJUnitXmlFile(file)

        then:
        result.tests == 0
        result.failures == 0
        result.errors == 0
        result.skipped == 0

        cleanup:
        file?.delete()
    }

    // ===== SAFE PARSE INT TESTS =====

    def "safeParseInt returns parsed value for valid input"() {
        expect:
        capture.safeParseInt(input, 0) == expected

        where:
        input   | expected
        '10'    | 10
        '0'     | 0
        '999'   | 999
        '-5'    | -5
    }

    def "safeParseInt returns default for null input"() {
        expect:
        capture.safeParseInt(null, 42) == 42
    }

    def "safeParseInt returns default for empty input"() {
        expect:
        capture.safeParseInt('', 42) == 42
    }

    def "safeParseInt returns default for invalid input"() {
        expect:
        capture.safeParseInt(input, 42) == 42

        where:
        input << ['abc', '1.5', 'ten', '1a']
    }

    // ===== CAPTURE FROM TASK STATE TESTS =====

    def "captureFromTaskState returns success result"() {
        given:
        def task = project.tasks.create('myTask')

        when:
        def result = capture.captureFromTaskState(task)

        then:
        result.success
        result.executed == 1
        result.totalCount == 1
        result.failureCount == 0
    }

    // ===== CREATE SUCCESS/FAILURE RESULT TESTS =====

    def "createSuccessResult returns success with default counts"() {
        when:
        def result = capture.createSuccessResult()

        then:
        result.success
        result.executed == 1
        result.totalCount == 1
        result.failureCount == 0
    }

    def "createFailureResult returns failure with default counts"() {
        when:
        def result = capture.createFailureResult()

        then:
        !result.success
        result.executed == 0
        result.totalCount == 1
        result.failureCount == 1
    }

    // ===== EDGE CASES =====

    def "captureFromJUnitXml ignores non-XML files"() {
        given:
        def testTask = project.tasks.create('test', Test)
        def reportsDir = new File(project.layout.buildDirectory.asFile.get(), "test-results/test")
        reportsDir.mkdirs()
        configureJUnitXmlOutputLocation(testTask, reportsDir)

        def xmlFile = new File(reportsDir, 'TEST-Test.xml')
        xmlFile.text = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="5" failures="1" errors="0" skipped="0">
</testsuite>'''
        def txtFile = new File(reportsDir, 'readme.txt')
        txtFile.text = 'This is not an XML file'

        when:
        def result = capture.captureFromJUnitXml(testTask)

        then:
        result.totalCount == 5
        result.failureCount == 1
    }

    def "parseJUnitXmlFiles handles empty list"() {
        when:
        def result = capture.parseJUnitXmlFiles([])

        then:
        result.success
        result.totalCount == 0
        result.executed == 0
        result.failureCount == 0
    }

    // ===== ADDITIONAL EDGE CASE TESTS =====

    def "captureFromTask falls back to task state when Test task has no XML reports"() {
        given:
        def testTask = project.tasks.create('testNoXml', Test)
        // Don't create any reports directory

        when:
        def result = capture.captureFromTask(testTask)

        then:
        result.success
        result.executed == 1
        result.totalCount == 1
    }

    def "captureFailure falls back for Test task without XML reports"() {
        given:
        def testTask = project.tasks.create('testNoXml', Test)
        // No reports directory
        def exception = new RuntimeException('Test failed')

        when:
        def result = capture.captureFailure(testTask, exception)

        then:
        !result.success
        result.failureCount == 1
    }

    def "findJUnitReportsDir returns null when outputLocation is not set"() {
        given:
        def testTask = project.tasks.create('testNoOutputLocation', Test)
        // outputLocation is not explicitly set and no directory exists

        when:
        def result = capture.findJUnitReportsDir(testTask)

        then:
        result == null
    }

    def "parseJUnitXmlFile handles file with no attributes"() {
        given:
        def file = File.createTempFile('test', '.xml')
        file.text = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite>
</testsuite>'''

        when:
        def result = capture.parseJUnitXmlFile(file)

        then:
        result.tests == 0
        result.failures == 0
        result.errors == 0
        result.skipped == 0

        cleanup:
        file?.delete()
    }

    def "parseJUnitXmlFile handles nested testsuite element"() {
        given:
        def file = File.createTempFile('test', '.xml')
        file.text = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="5" failures="1" errors="0" skipped="1">
    <testcase classname="com.example.Test" name="test1"/>
    <testcase classname="com.example.Test" name="test2"/>
</testsuite>'''

        when:
        def result = capture.parseJUnitXmlFile(file)

        then:
        result.tests == 5
        result.failures == 1
        result.errors == 0
        result.skipped == 1

        cleanup:
        file?.delete()
    }

    def "safeParseInt handles whitespace input"() {
        expect:
        capture.safeParseInt('  ', 42) == 42
    }

    def "safeParseInt handles very large numbers"() {
        expect:
        capture.safeParseInt('2147483647', 0) == Integer.MAX_VALUE
    }

    def "safeParseInt handles integer overflow gracefully"() {
        expect:
        capture.safeParseInt('9999999999999', 0) == 0
    }

    def "captureFromJUnitXml skips non-XML files without error"() {
        given:
        def testTask = project.tasks.create('test', Test)
        def reportsDir = new File(project.layout.buildDirectory.asFile.get(), "test-results/test")
        reportsDir.mkdirs()
        configureJUnitXmlOutputLocation(testTask, reportsDir)

        // Create only non-XML files
        new File(reportsDir, 'readme.txt').text = 'not an xml file'
        new File(reportsDir, 'data.json').text = '{"key": "value"}'

        when:
        def result = capture.captureFromJUnitXml(testTask)

        then:
        result == null
    }

    def "parseJUnitXmlFiles handles all files being malformed"() {
        given:
        def badFile1 = File.createTempFile('bad1', '.xml')
        badFile1.text = 'not valid xml'
        def badFile2 = File.createTempFile('bad2', '.xml')
        badFile2.text = '<unclosed'

        when:
        def result = capture.parseJUnitXmlFiles([badFile1, badFile2])

        then:
        result.success
        result.totalCount == 0

        cleanup:
        badFile1?.delete()
        badFile2?.delete()
    }

    def "captureFromTask works with custom Test task name"() {
        given:
        def testTask = project.tasks.create('integrationTest', Test)
        def reportsDir = new File(project.layout.buildDirectory.asFile.get(), "test-results/integrationTest")
        reportsDir.mkdirs()
        configureJUnitXmlOutputLocation(testTask, reportsDir)

        def xmlContent = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="7" failures="0" errors="0" skipped="0">
</testsuite>'''
        new File(reportsDir, 'TEST-integration.xml').text = xmlContent

        when:
        def result = capture.captureFromTask(testTask)

        then:
        result.success
        result.totalCount == 7
        result.failureCount == 0
    }

    def "captureFailure uses JUnit data when task is Test with reports"() {
        given:
        def testTask = project.tasks.create('testWithReports', Test)
        def reportsDir = new File(project.layout.buildDirectory.asFile.get(), "test-results/testWithReports")
        reportsDir.mkdirs()
        configureJUnitXmlOutputLocation(testTask, reportsDir)

        def xmlContent = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="10" failures="3" errors="1" skipped="0">
</testsuite>'''
        new File(reportsDir, 'TEST-failure.xml').text = xmlContent

        def exception = new RuntimeException('Some test failed')

        when:
        def result = capture.captureFailure(testTask, exception)

        then:
        !result.success
        result.totalCount == 10
        result.failureCount == 4  // failures + errors
    }

    def "captureFromJUnitXml handles reports directory with subdirectories"() {
        given:
        def testTask = project.tasks.create('test', Test)
        def reportsDir = new File(project.layout.buildDirectory.asFile.get(), "test-results/test")
        reportsDir.mkdirs()
        configureJUnitXmlOutputLocation(testTask, reportsDir)

        // Create a subdirectory (should be ignored by listFiles filter)
        new File(reportsDir, 'subdir').mkdirs()

        def xmlContent = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="5" failures="0" errors="0" skipped="0">
</testsuite>'''
        new File(reportsDir, 'TEST-Test.xml').text = xmlContent

        when:
        def result = capture.captureFromJUnitXml(testTask)

        then:
        result.totalCount == 5
    }

    // ===== EXCEPTION PATH TESTS =====

    def "captureFromJUnitXml returns null when reportsDir exists but has no readable files"() {
        given:
        def testTask = project.tasks.create('testReadable', Test)
        def reportsDir = new File(project.layout.buildDirectory.asFile.get(), "test-results/testReadable")
        reportsDir.mkdirs()
        configureJUnitXmlOutputLocation(testTask, reportsDir)

        // Create directory but no files at all
        // The findAll will return empty list

        when:
        def result = capture.captureFromJUnitXml(testTask)

        then:
        result == null
    }

    def "findJUnitReportsDir returns null when reports.junitXml directory doesn't exist"() {
        given:
        def testTask = project.tasks.create('testNonExistent', Test)
        // Configure output location to a directory that doesn't exist
        def nonExistentDir = new File(tempDir.toFile(), "non-existent-reports")
        configureJUnitXmlOutputLocation(testTask, nonExistentDir)

        when:
        def result = capture.findJUnitReportsDir(testTask)

        then:
        result == null
    }

    def "parseJUnitXmlFile handles zero values"() {
        given:
        def file = File.createTempFile('test', '.xml')
        file.text = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="0" failures="0" errors="0" skipped="0">
</testsuite>'''

        when:
        def result = capture.parseJUnitXmlFile(file)

        then:
        result.tests == 0
        result.failures == 0
        result.errors == 0
        result.skipped == 0

        cleanup:
        file?.delete()
    }

    def "parseJUnitXmlFiles handles single file"() {
        given:
        def file = File.createTempFile('single', '.xml')
        file.text = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="3" failures="1" errors="0" skipped="0">
</testsuite>'''

        when:
        def result = capture.parseJUnitXmlFiles([file])

        then:
        result.totalCount == 3
        result.failureCount == 1
        result.executed == 3
        !result.success

        cleanup:
        file?.delete()
    }

    def "captureFromTask handles Test task with empty reports directory"() {
        given:
        def testTask = project.tasks.create('testEmpty', Test)
        def reportsDir = new File(project.layout.buildDirectory.asFile.get(), "test-results/testEmpty")
        reportsDir.mkdirs()
        configureJUnitXmlOutputLocation(testTask, reportsDir)
        // Directory exists but is empty

        when:
        def result = capture.captureFromTask(testTask)

        then:
        result.success
        result.executed == 1
    }

    def "safeParseInt handles positive integer string"() {
        expect:
        capture.safeParseInt('42', 0) == 42
    }

    def "safeParseInt handles zero string"() {
        expect:
        capture.safeParseInt('0', -1) == 0
    }

    def "safeParseInt handles negative integer string"() {
        expect:
        capture.safeParseInt('-10', 0) == -10
    }

    def "parseJUnitXmlFiles calculates success based on failure count"() {
        given:
        def successFile = File.createTempFile('success', '.xml')
        successFile.text = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="10" failures="0" errors="0" skipped="2">
</testsuite>'''

        when:
        def result = capture.parseJUnitXmlFiles([successFile])

        then:
        result.success
        result.totalCount == 10
        result.failureCount == 0
        result.skipped == 2
        result.executed == 8

        cleanup:
        successFile?.delete()
    }

    def "captureFromJUnitXml returns null when reportsDir is configured but never created"() {
        given:
        def testTask = project.tasks.create('testNotCreated', Test)
        // Don't configure output location - use default which won't exist

        when:
        def result = capture.captureFromJUnitXml(testTask)

        then:
        result == null
    }
}
