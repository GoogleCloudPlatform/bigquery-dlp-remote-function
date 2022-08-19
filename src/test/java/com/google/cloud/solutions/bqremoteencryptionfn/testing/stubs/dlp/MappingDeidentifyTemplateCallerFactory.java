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

package com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.dlp;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.gax.rpc.ApiCallContext;
import com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.BaseUnaryApiFuture;
import com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.BaseUnaryApiFuture.ApiFutureFactory;
import com.google.common.collect.ImmutableMap;
import com.google.privacy.dlp.v2.DeidentifyTemplate;
import com.google.privacy.dlp.v2.GetDeidentifyTemplateRequest;
import java.util.Map;

public class MappingDeidentifyTemplateCallerFactory
    extends ApiFutureFactory<GetDeidentifyTemplateRequest, DeidentifyTemplate> {

  private final ImmutableMap<String, DeidentifyTemplate> nameTemplateMap;

  public MappingDeidentifyTemplateCallerFactory(Map<String, DeidentifyTemplate> nameTemplateMap) {
    super(GetDeidentifyTemplateRequest.class, DeidentifyTemplate.class);
    this.nameTemplateMap = ImmutableMap.copyOf(nameTemplateMap);
  }

  public static MappingDeidentifyTemplateCallerFactory using(
      Map<String, DeidentifyTemplate> nameTemplateMap) {
    return new MappingDeidentifyTemplateCallerFactory(nameTemplateMap);
  }

  @Override
  public BaseUnaryApiFuture<DeidentifyTemplate> create(
      GetDeidentifyTemplateRequest request, ApiCallContext context) {
    return new BaseUnaryApiFuture<>() {
      @Override
      public DeidentifyTemplate get() {
        assertThat(nameTemplateMap).containsKey(request.getName());
        return nameTemplateMap.get(request.getName());
      }
    };
  }
}
