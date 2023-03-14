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

import java.util.List;

/** Interface describing a general contract for any tokenization algorithm. */
public interface TransformFn {

  /**
   * Returns Deidentified/Encrypted list of strings for the provided list of messages using the
   * specific tokenization technique. The order of output should be the same as the order of input.
   *
   * @param rows UTF-8 encoded message strings
   * @return the deidentified list of messages in the same order as input.
   * @throws Exception when any exception occours in tokenization.
   */
  List<String> deidentify(List<List<Object>> rows) throws Exception;

  /**
   * Returns Reidentified/Decrypted list of strings for the provided list of encrypted messages
   * using the specific tokenization technique. The order of output should be the same as the order
   * of input.
   *
   * @param rows UTF-8 encoded encrypted message strings
   * @return the ReIdentified/Decrypted list of messages in the same order as input.
   * @throws Exception when any exception occurs or the input messages are not in the same
   *     encryption format.
   */
  List<String> reidentify(List<List<Object>> rows) throws Exception;

  /**
   * Returns the name of the encryption algorithm implemented. Needs to be unique for all loaded
   * function classes.
   */
  String getName();
}
