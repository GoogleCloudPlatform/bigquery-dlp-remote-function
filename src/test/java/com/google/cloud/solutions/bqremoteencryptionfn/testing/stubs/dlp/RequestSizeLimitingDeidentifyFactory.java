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
import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.BaseUnaryApiFuture;
import com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.BaseUnaryApiFuture.ApiFutureFactory;
import com.google.privacy.dlp.v2.ContentItem;
import com.google.privacy.dlp.v2.DeidentifyContentRequest;
import com.google.privacy.dlp.v2.DeidentifyContentResponse;

public class RequestSizeLimitingDeidentifyFactory
    extends ApiFutureFactory<DeidentifyContentRequest, DeidentifyContentResponse> {

  private final int expectedRowCount;

  public RequestSizeLimitingDeidentifyFactory(int expectedRowCount) {
    super(DeidentifyContentRequest.class, DeidentifyContentResponse.class);
    this.expectedRowCount = expectedRowCount;
  }

  @Override
  public BaseUnaryApiFuture<DeidentifyContentResponse> create(
      DeidentifyContentRequest request, ApiCallContext context) {
    return new BaseUnaryApiFuture<>() {
      @Override
      public DeidentifyContentResponse get() {

        if (request.getItem().getTable().getRowsCount() > expectedRowCount) {
          throw new InvalidArgumentException(
              new RuntimeException(
                  "Too many findings to de-identify. Retry with a smaller request."),
              new StatusCode() {
                @Override
                public Code getCode() {
                  return Code.INVALID_ARGUMENT;
                }

                @Override
                public Object getTransportCode() {
                  return Code.INVALID_ARGUMENT.getHttpStatusCode();
                }
              },
              true);
        }

        return DeidentifyContentResponse.newBuilder()
            .setItem(ContentItem.newBuilder().setTable(request.getItem().getTable()))
            .build();
      }
    };
  }
}
