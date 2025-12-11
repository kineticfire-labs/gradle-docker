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

# Simple Java application image for dockerProject integration testing
# This Dockerfile demonstrates the method lifecycle scenario
FROM eclipse-temurin:21-jre-alpine

# Build argument for version info
ARG BUILD_VERSION=unknown

# Create app user
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

# Copy the JAR (prepared by dockerProject jarFrom property)
COPY app.jar /app/app.jar

# Set ownership
RUN chown -R app:app /app

USER app

EXPOSE 8080

# Health check for the time server app
HEALTHCHECK --interval=5s --timeout=3s --start-period=10s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
