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

package com.google.cloud.solutions.bqremoteencryptionfn.fns;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.cloud.solutions.bqremoteencryptionfn.TransformFnFactory;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Implementation to convert a given input to Base64 encoded String using {@link
 * java.util.Base64.Encoder}
 */
public final class Base64Fn extends UnaryStringArgFn {

  public static final String FN_NAME = "base64";

  @Component
  public static class Base64TransformFnFactory implements TransformFnFactory<Base64Fn> {
    @Override
    public Base64Fn createFn(Map<String, String> options) {
      return new Base64Fn();
    }

    @Override
    public String getFnName() {
      return FN_NAME;
    }
  }

  @Override
  public String getName() {
    return FN_NAME;
  }

  @Override
  public ImmutableList<String> deidentifyUnaryRow(List<String> rows) {
    var encoder = Base64.getEncoder();

    return rows.stream()
        .map(row -> row.getBytes(StandardCharsets.UTF_8))
        .map(encoder::encodeToString)
        .collect(toImmutableList());
  }

  @Override
  public ImmutableList<String> reidentifyUnaryRow(List<String> rows) {
    var decoder = Base64.getDecoder();

    return rows.stream()
        .map(decoder::decode)
        .map(b -> new String(b, StandardCharsets.UTF_8))
        .collect(toImmutableList());
  }
}
