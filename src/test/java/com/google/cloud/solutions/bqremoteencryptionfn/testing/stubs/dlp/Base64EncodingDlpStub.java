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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.api.gax.rpc.ApiCallContext;
import com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.BaseUnaryApiFuture;
import com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.BaseUnaryApiFuture.ApiFutureFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.privacy.dlp.v2.ContentItem;
import com.google.privacy.dlp.v2.DeidentifyContentRequest;
import com.google.privacy.dlp.v2.DeidentifyContentResponse;
import com.google.privacy.dlp.v2.ReidentifyContentRequest;
import com.google.privacy.dlp.v2.ReidentifyContentResponse;
import com.google.privacy.dlp.v2.Table;
import com.google.privacy.dlp.v2.Value;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Set;
import java.util.function.Function;

public class Base64EncodingDlpStub implements Serializable {
  private final String projectId;
  private final String location;
  private final Set<String> transformColumnIds;

  public Base64EncodingDlpStub(Set<String> transformColumnIds, String projectId, String location) {
    this.transformColumnIds = transformColumnIds;
    this.projectId = projectId;
    this.location = location;
  }

  public ApiFutureFactory<DeidentifyContentRequest, DeidentifyContentResponse> deidentifyFactory() {

    return new ApiFutureFactory<>(DeidentifyContentRequest.class, DeidentifyContentResponse.class) {
      @Override
      public BaseUnaryApiFuture<DeidentifyContentResponse> create(
          DeidentifyContentRequest request, ApiCallContext context) {
        return new BaseUnaryApiFuture<>() {
          @Override
          public DeidentifyContentResponse get() {
            var actioner =
                new Base64Actioner(Base64EncodingDlpStub::encodeBase64Value, request.getParent());

            return DeidentifyContentResponse.newBuilder()
                .setItem(
                    ContentItem.newBuilder()
                        .setTable(actioner.checkAndtransformRows(request.getItem().getTable())))
                .build();
          }
        };
      }
    };
  }

  public ApiFutureFactory<ReidentifyContentRequest, ReidentifyContentResponse> reidentifyFactory() {

    return new ApiFutureFactory<>(ReidentifyContentRequest.class, ReidentifyContentResponse.class) {
      @Override
      public BaseUnaryApiFuture<ReidentifyContentResponse> create(
          ReidentifyContentRequest request, ApiCallContext context) {
        return new BaseUnaryApiFuture<>() {
          @Override
          public ReidentifyContentResponse get() {
            var actioner =
                new Base64Actioner(Base64EncodingDlpStub::decodeBase64String, request.getParent());

            return ReidentifyContentResponse.newBuilder()
                .setItem(
                    ContentItem.newBuilder()
                        .setTable(actioner.checkAndtransformRows(request.getItem().getTable())))
                .build();
          }
        };
      }
    };
  }

  private final class Base64Actioner {

    private final Function<Value, Value> elementTransformer;
    private final String callParent;

    public Base64Actioner(Function<Value, Value> elementTransformer, String callParent) {
      this.elementTransformer = elementTransformer;
      this.callParent = callParent;
    }

    private void checkCallParent() {
      assertThat(callParent).startsWith(String.format("projects/%s", projectId));

      if (!location.equals("global")) {
        assertThat(callParent)
            .isEqualTo(String.format("projects/%s/locations/%s", projectId, location));
      }
    }

    private Table checkAndtransformRows(Table table) {

      checkCallParent();

      var headers = table.getHeadersList();

      var updatedRows =
          table.getRowsList().stream()
              .map(
                  row -> {
                    //noinspection UnstableApiUsage
                    ImmutableList<Value> updatedValues =
                        Streams.zip(
                                headers.stream(),
                                row.getValuesList().stream(),
                                (header, value) -> {
                                  if (transformColumnIds.contains(header.getName())) {
                                    return elementTransformer.apply(value);
                                  }
                                  return value;
                                })
                            .collect(toImmutableList());

                    return row.toBuilder().clearValues().addAllValues(updatedValues).build();
                  })
              .collect(toImmutableList());

      return Table.newBuilder().addAllHeaders(headers).addAllRows(updatedRows).build();
    }
  }

  private static Value encodeBase64Value(Value value) {

    byte[] bytes = null;

    switch (value.getTypeCase()) {
      case INTEGER_VALUE:
        bytes = ByteBuffer.allocate(Long.BYTES).putLong(value.getIntegerValue()).array();
        break;
      case FLOAT_VALUE:
        bytes = ByteBuffer.allocate(Double.BYTES).putDouble(value.getIntegerValue()).array();
        break;
      case STRING_VALUE:
        bytes = value.getStringValue().getBytes();
        break;
      case BOOLEAN_VALUE:
        bytes = ByteBuffer.allocate(Integer.BYTES).putInt(value.getBooleanValue() ? 1 : 0).array();
        break;
      case TIMESTAMP_VALUE:
      case TIME_VALUE:
      case DATE_VALUE:
      case DAY_OF_WEEK_VALUE:
      case TYPE_NOT_SET:
        return Value.getDefaultInstance();
    }

    return Value.newBuilder().setStringValue(Base64.getEncoder().encodeToString(bytes)).build();
  }

  private static Value decodeBase64String(Value value) {

    if (!value.getTypeCase().equals(Value.TypeCase.STRING_VALUE)) {
      throw new RuntimeException("non-string value not expected");
    }

    return Value.newBuilder()
        .setStringValue(new String(Base64.getDecoder().decode(value.getStringValue())))
        .build();
  }
}
