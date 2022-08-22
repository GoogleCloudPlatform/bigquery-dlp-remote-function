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

import com.google.cloud.solutions.bqremoteencryptionfn.fns.IdentityFn.IdentityTransformFnFactory;
import com.google.common.flogger.GoogleLogger;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The main REST Controller that provides BigQuery Remote function compliant endpoint
 *
 * @see <a
 *     href="https://cloud.google.com/bigquery/docs/reference/standard-sql/remote-functions#create_a_http_endpoint_in_or>Remote
 *     function HTTP Endpoint</a>
 */
@RestController
public class BigQueryFnCallController {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  public static final String CALL_MODE_KEY = "mode";
  public static final String TRANSFORM_ALGO_KEY = "algo";

  @Autowired private List<TransformFnFactory<? extends TransformFn>> transformFnFactories;

  @PostMapping("/")
  public BigQueryRemoteFnResponse process(@RequestBody BigQueryRemoteFnRequest request) {
    try {
      var options =
          checkNotNull(request.userDefinedContext(), "userDefinedContext is required. Found null.");
      var callMode = identifyCallMode(options);
      var algo = checkNotNull(options.get(TRANSFORM_ALGO_KEY), "Invalid Algorithm. Found null");

      var transformFn =
          transformFnFactories.stream()
              .filter(factory -> factory.getFnName().equals(algo))
              .findFirst()
              .orElseGet(IdentityTransformFnFactory::new)
              .createFn(options);

      var replies =
          switch (callMode) {
            case DEIDENTIFY -> transformFn.deidentify(request.calls());
            case REIDENTIFY -> transformFn.reidentify(request.calls());
          };

      return BigQueryRemoteFnResponse.withReplies(replies);
    } catch (Exception exp) {
      logger.atInfo().withCause(exp).log("error processing request");
      return BigQueryRemoteFnResponse.withErrorMessage(exp.getMessage());
    }
  }

  private static CallMode identifyCallMode(Map<String, String> userContext) {
    var callMode = userContext.get(CALL_MODE_KEY);
    return callMode == null ? CallMode.DEIDENTIFY : CallMode.valueOf(callMode.toUpperCase());
  }

  public enum CallMode {
    DEIDENTIFY,
    REIDENTIFY
  }
}
