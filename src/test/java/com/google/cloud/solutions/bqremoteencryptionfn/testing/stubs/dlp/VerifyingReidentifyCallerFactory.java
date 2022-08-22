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


import com.google.api.gax.rpc.ApiCallContext;
import com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.BaseUnaryApiFuture;
import com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.BaseUnaryApiFuture.ApiFutureFactory;
import com.google.common.truth.extensions.proto.ProtoTruth;
import com.google.privacy.dlp.v2.ReidentifyContentRequest;
import com.google.privacy.dlp.v2.ReidentifyContentResponse;
import java.util.concurrent.ExecutionException;

public class VerifyingReidentifyCallerFactory
    extends ApiFutureFactory<ReidentifyContentRequest, ReidentifyContentResponse> {

  private final ReidentifyContentRequest expectedRequest;
  private final ApiFutureFactory<ReidentifyContentRequest, ReidentifyContentResponse> reidFactory;

  private VerifyingReidentifyCallerFactory(
      ReidentifyContentRequest expectedRequest,
      ApiFutureFactory<ReidentifyContentRequest, ReidentifyContentResponse> reidFactory) {
    super(ReidentifyContentRequest.class, ReidentifyContentResponse.class);
    this.expectedRequest = expectedRequest;
    this.reidFactory = reidFactory;
  }

  public static VerifyingReidentifyCallerFactory withExpectedRequest(
      ReidentifyContentRequest expectedRequest) {
    return new VerifyingReidentifyCallerFactory(expectedRequest, null);
  }

  public VerifyingReidentifyCallerFactory withReidFactory(
      ApiFutureFactory<ReidentifyContentRequest, ReidentifyContentResponse> reidFactory) {
    return new VerifyingReidentifyCallerFactory(expectedRequest, reidFactory);
  }

  @Override
  public BaseUnaryApiFuture<ReidentifyContentResponse> create(
      ReidentifyContentRequest request, ApiCallContext context) {
    return new BaseUnaryApiFuture<>() {
      @Override
      public ReidentifyContentResponse get() throws InterruptedException, ExecutionException {
        ProtoTruth.assertThat(request)
            .ignoringFields(ReidentifyContentRequest.ITEM_FIELD_NUMBER)
            .isEqualTo(expectedRequest);

        return reidFactory.create(request, context).get();
      }
    };
  }
}
