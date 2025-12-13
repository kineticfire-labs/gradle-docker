#!/bin/sh
# (c) Copyright 2023-2025 gradle-docker Contributors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -e

echo "Starting Wait-Healthy Verification App..."
echo "Java Version:"
java -version

# Handle startup delay (simulates slow application initialization)
# This is configured via STARTUP_DELAY_MS environment variable
DELAY_MS="${STARTUP_DELAY_MS:-5000}"

if [ "$DELAY_MS" -gt 0 ]; then
    # Convert milliseconds to seconds for sleep command
    DELAY_SEC=$(awk "BEGIN {printf \"%.3f\", $DELAY_MS/1000}")
    echo "Simulating startup delay of ${DELAY_MS}ms (${DELAY_SEC}s)..."
    sleep "$DELAY_SEC"
    echo "Startup delay complete, starting application..."
fi

exec java -jar /app/app.jar
