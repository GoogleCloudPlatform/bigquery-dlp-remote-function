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

package com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.dlp;

import com.google.api.gax.rpc.ApiCallContext;
import com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.BaseUnaryApiFuture;
import com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.BaseUnaryApiFuture.ApiFutureFactory;
import com.google.common.truth.extensions.proto.ProtoTruth;
import com.google.privacy.dlp.v2.DeidentifyContentRequest;
import com.google.privacy.dlp.v2.DeidentifyContentResponse;
import java.util.concurrent.ExecutionException;

public class VerifyingDeidentifyCallerFactory
    extends ApiFutureFactory<DeidentifyContentRequest, DeidentifyContentResponse> {

  private final DeidentifyContentRequest expectedRequest;
  private final ApiFutureFactory<DeidentifyContentRequest, DeidentifyContentResponse> deidFactory;

  public VerifyingDeidentifyCallerFactory(
      DeidentifyContentRequest expectedRequest,
      ApiFutureFactory<DeidentifyContentRequest, DeidentifyContentResponse> deidFactory) {
    super(DeidentifyContentRequest.class, DeidentifyContentResponse.class);
    this.expectedRequest = expectedRequest;
    this.deidFactory = deidFactory;
  }

  @Override
  public BaseUnaryApiFuture<DeidentifyContentResponse> create(
      DeidentifyContentRequest request, ApiCallContext context) {
    return new BaseUnaryApiFuture<DeidentifyContentResponse>() {
      @Override
      public DeidentifyContentResponse get() throws InterruptedException, ExecutionException {
        ProtoTruth.assertThat(request)
            .ignoringFields(DeidentifyContentRequest.ITEM_FIELD_NUMBER)
            .isEqualTo(expectedRequest);

        return deidFactory.create(request, context).get();
      }
    };
  }
}
