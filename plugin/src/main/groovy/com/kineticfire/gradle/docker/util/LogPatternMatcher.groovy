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

package com.kineticfire.gradle.docker.util

import com.google.common.annotations.VisibleForTesting

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Pure utility class for log pattern matching.
 *
 * <p>This class contains no state and all methods are pure functions, making it
 * 100% unit testable without mocks.</p>
 */
class LogPatternMatcher {

    /**
     * Result of checking reject patterns against log lines.
     *
     * <p>This simple data class replaces Tuple2 for type safety and clarity.</p>
     */
    static class RejectCheckResult {
        final String patternString
        final String matchingLogLine

        RejectCheckResult(String patternString, String matchingLogLine) {
            this.patternString = patternString
            this.matchingLogLine = matchingLogLine
        }
    }

    private LogPatternMatcher() {
        // Utility class - prevent instantiation
    }

    /**
     * Compile a regex pattern string with optional case-insensitivity.
     *
     * @param patternString The regex pattern
     * @param caseInsensitive Whether to compile with CASE_INSENSITIVE flag
     * @return Compiled Pattern
     * @throws PatternSyntaxException if pattern is invalid
     */
    @VisibleForTesting
    static Pattern compilePattern(String patternString, boolean caseInsensitive) {
        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0
        return Pattern.compile(patternString, flags)
    }

    /**
     * Compile all patterns for all services.
     *
     * @param servicePatterns Map of service -> pattern strings
     * @param caseInsensitive Whether to use case-insensitive matching
     * @return Map of service -> compiled patterns
     * @throws IllegalArgumentException if any pattern is invalid (wraps PatternSyntaxException)
     */
    static Map<String, List<Pattern>> compilePatterns(
            Map<String, List<String>> servicePatterns,
            boolean caseInsensitive) {
        def result = [:]
        servicePatterns.each { serviceName, patterns ->
            try {
                result[serviceName] = patterns.collect { patternString ->
                    compilePattern(patternString, caseInsensitive)
                }
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException(
                    "Invalid regex pattern for service '${serviceName}': ${e.pattern}\n" +
                    "Error: ${e.description}\n" +
                    "Hint: Escape special regex characters with \\\\ (e.g., \\\\[ to match literal [)",
                    e
                )
            }
        }
        return result
    }

    /**
     * Check if a pattern matches any line in the log output.
     *
     * @param pattern Compiled regex pattern
     * @param logLines List of log lines to search
     * @return true if pattern matches any line
     */
    @VisibleForTesting
    static boolean matchesAnyLine(Pattern pattern, List<String> logLines) {
        return logLines.any { line ->
            pattern.matcher(line).find()
        }
    }

    /**
     * Find the first log line matching a pattern.
     *
     * @param pattern Compiled regex pattern
     * @param logLines List of log lines to search
     * @return The matching log line, or null if no match
     */
    @VisibleForTesting
    static String findMatchingLine(Pattern pattern, List<String> logLines) {
        return logLines.find { line ->
            pattern.matcher(line).find()
        }
    }

    /**
     * Check for reject pattern matches in log output.
     *
     * @param rejectPatterns List of reject patterns to check
     * @param logLines Log lines to search
     * @return RejectCheckResult with matched pattern and log line, or null if no match
     */
    static RejectCheckResult checkRejectPatterns(
            List<Pattern> rejectPatterns,
            List<String> logLines) {
        for (Pattern pattern : rejectPatterns) {
            def matchingLine = findMatchingLine(pattern, logLines)
            if (matchingLine != null) {
                return new RejectCheckResult(pattern.pattern(), matchingLine)
            }
        }
        return null
    }

    /**
     * Update match state for a service based on new log output.
     *
     * <p>This method modifies the provided collections in place for efficiency.</p>
     *
     * @param patterns Patterns to match (must not be null or empty)
     * @param matchedPatterns Set of already-matched pattern indices (modified in place, must not be null)
     * @param logLines New log lines to check (must not be null, may be empty)
     * @param elapsedSeconds Current elapsed time for recording match time
     * @param matchTimes Map of pattern index to match time (modified in place, must not be null)
     * @return Number of newly matched patterns
     * @throws NullPointerException if any required parameter is null
     */
    static int updateMatches(
            List<Pattern> patterns,
            Set<Integer> matchedPatterns,
            List<String> logLines,
            long elapsedSeconds,
            Map<Integer, Long> matchTimes) {
        // Null safety - fail fast with clear error messages
        if (patterns == null) {
            throw new NullPointerException("patterns cannot be null")
        }
        if (matchedPatterns == null) {
            throw new NullPointerException("matchedPatterns cannot be null")
        }
        if (logLines == null) {
            throw new NullPointerException("logLines cannot be null")
        }
        if (matchTimes == null) {
            throw new NullPointerException("matchTimes cannot be null")
        }

        int newMatches = 0
        patterns.eachWithIndex { pattern, index ->
            if (!matchedPatterns.contains(index) && matchesAnyLine(pattern, logLines)) {
                matchedPatterns.add(index)
                matchTimes[index] = elapsedSeconds
                newMatches++
            }
        }
        return newMatches
    }
}
