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

import com.google.cloud.solutions.bqremoteencryptionfn.fns.IdentityFn;
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
public class BqFnCallController {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  public static final String CALL_MODE_KEY = "mode";
  public static final String TOKENIZE_ALGO_KEY = "algo";

  @Autowired private List<TokenizeFnFactory<TokenizeFn>> tokenizeFnFactories;

  @PostMapping("/")
  public BigQueryRemoteFnResponse process(@RequestBody BigQueryRemoteFnRequest request) {
    try {
      var options =
          checkNotNull(request.userDefinedContext(), "userDefinedContext is required. Found null.");
      var callMode = identifyCallMode(options);
      var algo = checkNotNull(options.get(TOKENIZE_ALGO_KEY), "Invalid Algorithm. Found null");

      var tokenizeFn =
          tokenizeFnFactories.stream()
              .filter(factory -> factory.getFnName().equals(algo))
              .findFirst()
              .orElseGet(IdentityFn.IdentityTokenizeFnFactory::new)
              .createFn(options);

      var replies =
          switch (callMode) {
            case TOKENIZE -> tokenizeFn.tokenize(request.calls());
            case REIDENTIFY -> tokenizeFn.reIdentify(request.calls());
          };

      return BigQueryRemoteFnResponse.withReplies(replies);
    } catch (Exception exp) {
      logger.atInfo().withCause(exp).log("error processing request");
      return BigQueryRemoteFnResponse.withErrorMessage(exp.getMessage());
    }
  }

  private static CallMode identifyCallMode(Map<String, String> userContext) {
    var callMode = userContext.get(CALL_MODE_KEY);
    return callMode == null ? CallMode.TOKENIZE : CallMode.valueOf(callMode.toUpperCase());
  }

  public enum CallMode {
    TOKENIZE,
    REIDENTIFY
  }
}
