#
# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

FROM gradle:7.4.2-jdk17-alpine
COPY . /bigquery-dlp-remote-function-src
WORKDIR /bigquery-dlp-remote-function-src
RUN gradle clean test assemble

FROM eclipse-temurin:17-jre-focal
COPY --from=0 /bigquery-dlp-remote-function-src/build/libs/bigquery-dlp-remote-function-0.0.1-SNAPSHOT.jar .
# Run the web service on container startup.
RUN groupadd -r apprunner && useradd -rm -g apprunner apprunner
RUN chown apprunner ./bigquery-dlp-remote-function-0.0.1-SNAPSHOT.jar
USER apprunner
CMD ["java", "-jar", "bigquery-dlp-remote-function-0.0.1-SNAPSHOT.jar"]