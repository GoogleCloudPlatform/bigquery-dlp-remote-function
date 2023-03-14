/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.solutions.bqremoteencryptionfn;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

/**
 * BigQuery Remote Function Response data model
 *
 * <p>
 *
 * @see <a
 *     href="https://cloud.google.com/bigquery/docs/reference/standard-sql/remote-functions#output_format">
 *     Output Format</a>
 */
public record BigQueryRemoteFnResponse(List<?> replies, String errorMessage) {

  public static BigQueryRemoteFnResponse withReplies(List<?> replies) {
    return new BigQueryRemoteFnResponse(checkNotNull(replies), null);
  }

  public static BigQueryRemoteFnResponse withErrorMessage(String errorMessage) {
    return new BigQueryRemoteFnResponse(null, errorMessage);
  }
}
