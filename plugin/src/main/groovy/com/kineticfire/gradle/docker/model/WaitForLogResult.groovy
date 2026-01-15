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

package com.kineticfire.gradle.docker.model

/**
 * Result of a wait-for-log operation for a single service.
 *
 * <p>Tracks which patterns have matched and when, enabling detailed progress reporting
 * and timeout diagnostics.</p>
 */
class WaitForLogResult {

    /**
     * Status of an individual pattern match.
     */
    static class PatternMatch {
        final String pattern
        final boolean matched
        final Long matchedAtSeconds  // null if not matched

        PatternMatch(String pattern, boolean matched, Long matchedAtSeconds) {
            this.pattern = pattern
            this.matched = matched
            this.matchedAtSeconds = matchedAtSeconds
        }
    }

    final String serviceName
    final List<PatternMatch> patternMatches
    final boolean ready
    final boolean rejected
    final String rejectPattern  // null if not rejected
    final String rejectLogLine  // null if not rejected

    WaitForLogResult(String serviceName, List<PatternMatch> patternMatches, boolean ready) {
        this.serviceName = serviceName
        this.patternMatches = Collections.unmodifiableList(patternMatches)
        this.ready = ready
        this.rejected = false
        this.rejectPattern = null
        this.rejectLogLine = null
    }

    WaitForLogResult(String serviceName, List<PatternMatch> patternMatches,
                     String rejectPattern, String rejectLogLine) {
        this.serviceName = serviceName
        this.patternMatches = Collections.unmodifiableList(patternMatches)
        this.ready = false
        this.rejected = true
        this.rejectPattern = rejectPattern
        this.rejectLogLine = rejectLogLine
    }

    int getMatchedCount() {
        return patternMatches.count { it.matched }
    }

    int getTotalPatterns() {
        return patternMatches.size()
    }
}
