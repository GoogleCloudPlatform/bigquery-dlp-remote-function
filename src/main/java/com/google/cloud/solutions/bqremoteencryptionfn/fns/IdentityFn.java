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

import com.google.cloud.solutions.bqremoteencryptionfn.TransformFnFactory;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

/** Simple Pass-through function, does not transform the input. */
public final class IdentityFn extends UnaryStringArgFn {

  public static final String FN_NAME = "identity";

  @Component
  public static class IdentityTransformFnFactory implements TransformFnFactory<IdentityFn> {
    @Override
    public IdentityFn createFn(@Nonnull Map<String, String> options) {
      return new IdentityFn();
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
  public List<String> deidentifyUnaryRow(List<String> rows) {
    return rows;
  }

  @Override
  public List<String> reidentifyUnaryRow(List<String> rows) {
    return rows;
  }
}
