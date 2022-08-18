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
import javax.annotation.Nullable;

/** Factory interface for creating objects of a {@link TokenizeFn} implementation. */
public interface TokenizeFnFactory<T extends TokenizeFn> {

  /**
   * Returns an instance of {@link TokenizeFn} implementation.
   *
   * @param options the implementation specific configuration
   */
  T createFn(@Nullable Map<String, String> options);

  /**
   * Returns the name of the encrption algorithm implemented. Needs to be unique for all loaded
   * function classes.
   */
  String getFnName();
}
