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

import com.google.common.base.Strings;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.util.Map;

/** Configuration Model for request options in a BigQuery remote function call. */
public record DlpConfig(String deidTemplate, String inspectTemplate) {

  private static final Gson jsonMapper =
      new Gson()
          .newBuilder()
          .setFieldNamingStrategy(
              (Field f) -> "dlp-" + FieldNamingPolicy.LOWER_CASE_WITH_DASHES.translateName(f))
          .create();

  public static DlpConfig fromJson(Map<String, String> nodeTree) {
    return jsonMapper.fromJson(jsonMapper.toJsonTree(nodeTree), DlpConfig.class);
  }

  public static DlpConfig fromJson(String json) {
    return jsonMapper.fromJson(json, DlpConfig.class);
  }

  public boolean hasInspectTemplate() {
    return !Strings.isNullOrEmpty(inspectTemplate);
  }

  public boolean hasDlpDeidTemplate() {
    return !Strings.isNullOrEmpty(deidTemplate);
  }

  public String toJson() {
    return jsonMapper.toJson(this);
  }
}
