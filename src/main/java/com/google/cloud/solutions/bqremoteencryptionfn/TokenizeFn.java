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
public interface TokenizeFn {

  /**
   * Returns Tokenized/Encrypted list of strings for the provided list of messages using the
   * specific tokenization technique. The order of output should be the same as the order of input.
   *
   * @param rows UTF-8 encoded message strings
   * @return the tokenized list of messages in the same order as input.
   * @throws Exception when any exception occours in tokenization.
   */
  List<String> tokenize(List<List<Object>> rows) throws Exception;

  /**
   * Returns ReIdentified/Decrypted list of strings for the provided list of encrypted messages
   * using the specific tokenization technique. The order of output should be the same as the order
   * of input.
   *
   * @param rows UTF-8 encoded encryted message strings
   * @return the ReIdentified/Decrypted list of messages in the same order as input.
   * @throws Exception when any exception occours or the input messages are not in the same
   *     encryption format.
   */
  List<String> reIdentify(List<List<Object>> rows) throws Exception;

  /**
   * Returns the name of the encrption algorithm implemented. Needs to be unique for all loaded
   * function classes.
   */
  String getName();
}
