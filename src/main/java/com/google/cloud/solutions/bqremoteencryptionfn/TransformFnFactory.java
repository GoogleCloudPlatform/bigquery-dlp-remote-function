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

import java.util.Map;
import javax.annotation.Nonnull;

/** Factory interface for creating objects of a {@link TransformFn} implementation. */
public interface TransformFnFactory<T extends TransformFn> {

  /**
   * Returns an instance of {@link TransformFn} implementation.
   *
   * @param options the implementation specific configuration
   */
  T createFn(@Nonnull Map<String, String> options);

  /**
   * Returns the name of the encryption algorithm implemented. Needs to be unique for all loaded
   * function classes.
   */
  String getFnName();
}
