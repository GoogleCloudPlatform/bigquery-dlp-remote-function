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

package com.google.cloud.solutions.bqremoteencryptionfn.testing;

import static com.google.cloud.solutions.bqremoteencryptionfn.testing.JsonMapper.toJson;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.cloud.solutions.bqremoteencryptionfn.BigQueryRemoteFnRequest;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SimpleBigQueryRemoteFnRequestMaker {

  public static String testRequest(Map<String, String> options, List<?>... calls) {
    var objCalls =
        Arrays.stream(calls)
            .map(l -> (List<Object>) l.stream().map(x -> (Object) x).collect(toImmutableList()))
            .collect(toImmutableList());

    return toJson(
        new BigQueryRemoteFnRequest(
            "testRequestId",
            "testCallerId",
            "testSessionUser@somedomain.com",
            ImmutableMap.copyOf(options),
            objCalls));
  }
}
