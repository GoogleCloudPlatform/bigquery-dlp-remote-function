/*
 * Copyright 2023 Google LLC
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

package com.google.cloud.solutions.bqremoteencryptionfn.fns.dlp;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.skyscreamer.jsonassert.JSONAssert;

@RunWith(JUnit4.class)
public class DlpConfigTest {

  @Test
  public void fromJson_map_valid() throws JSONException {
    var testJson = ImmutableMap.of("dlp-deid-template", "my-template-id");
    assertThat(DlpConfig.fromJson(testJson)).isEqualTo(new DlpConfig("my-template-id"));
  }

  @Test
  public void fromJson_string_valid() throws JSONException {
    assertThat(DlpConfig.fromJson("{\"dlp-deid-template\": \"my-template-id\"}"))
        .isEqualTo(new DlpConfig("my-template-id"));
  }

  @Test
  public void toJson_valid() throws JSONException {
    var json = new DlpConfig("my-template-id").toJson();
    JSONAssert.assertEquals("{\"dlp-deid-template\": \"my-template-id\"}", json, true);
  }
}
