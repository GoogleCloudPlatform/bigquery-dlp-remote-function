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

import com.google.cloud.solutions.bqremoteencryptionfn.TransformFn;
import java.util.List;

/**
 * Base class for transform functions that take a single argument for deidentify and reidentify
 * operations.
 */
public abstract class UnaryStringArgFn implements TransformFn {

  @Override
  public final List<String> deidentify(List<List<Object>> rows) throws Exception {
    return deidentifyUnaryRow(makeUnaryArgumentRow(rows));
  }

  @Override
  public final List<String> reidentify(List<List<Object>> rows) throws Exception {
    return reidentifyUnaryRow(makeUnaryArgumentRow(rows));
  }

  private List<String> makeUnaryArgumentRow(List<List<Object>> calledRows) {
    return calledRows.stream().map(r -> r.get(0)).map(Object::toString).collect(toImmutableList());
  }

  protected abstract List<String> deidentifyUnaryRow(List<String> rows) throws Exception;

  protected abstract List<String> reidentifyUnaryRow(List<String> rows) throws Exception;
}
