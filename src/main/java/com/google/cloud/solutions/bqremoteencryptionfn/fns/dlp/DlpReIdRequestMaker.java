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

package com.google.cloud.solutions.bqremoteencryptionfn.fns.dlp;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;

import com.google.privacy.dlp.v2.ContentItem;
import com.google.privacy.dlp.v2.CustomInfoType;
import com.google.privacy.dlp.v2.CustomInfoType.SurrogateType;
import com.google.privacy.dlp.v2.DeidentifyConfig;
import com.google.privacy.dlp.v2.FieldTransformation;
import com.google.privacy.dlp.v2.InfoType;
import com.google.privacy.dlp.v2.InfoTypeTransformations;
import com.google.privacy.dlp.v2.InfoTypeTransformations.InfoTypeTransformation;
import com.google.privacy.dlp.v2.InspectConfig;
import com.google.privacy.dlp.v2.PrimitiveTransformation;
import com.google.privacy.dlp.v2.PrimitiveTransformation.TransformationCase;
import com.google.privacy.dlp.v2.RecordTransformations;
import com.google.privacy.dlp.v2.ReidentifyContentRequest;
import com.google.privacy.dlp.v2.TransformationErrorHandling;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Transform a given {@link DeidentifyConfig} to {@link ReidentifyContentRequest}.
 *
 * <p>The surrogateType information from the DeIdentifyConfig is used to construct a CustomInfoTypes
 * list for inspection and the InfoTypeTransformations' InfoType selectors from DeidentifyConfig are
 * modified to match the surrogate values.
 *
 * @see <a
 *     href="https://cloud.google.com/dlp/docs/inspect-sensitive-text-de-identify#step_5_send_a_re-identify_request_to_the>ReIdentify
 *     Request</a>
 */
public final class DlpReIdRequestMaker {

  private final DeidentifyConfig deidentifyConfig;

  private DlpReIdRequestMaker(DeidentifyConfig deidentifyConfig) {
    this.deidentifyConfig = deidentifyConfig;
  }

  public static DlpReIdRequestMaker forConfig(DeidentifyConfig deidentifyConfig) {
    return new DlpReIdRequestMaker(deidentifyConfig);
  }

  public ReidentifyContentRequest makeRequest(ContentItem.Builder itemBuilder) {
    var requestBuilder =
        ReidentifyContentRequest.newBuilder()
            .setItem(itemBuilder)
            .setReidentifyConfig(makeReIdentifyConfig());

    var customInfoTypes = extractSurrogatesAsCustomInfoTypes();

    if (customInfoTypes.size() > 0) {
      requestBuilder.setInspectConfig(
          InspectConfig.newBuilder().addAllCustomInfoTypes(customInfoTypes));
    }

    return requestBuilder.build();
  }

  private List<CustomInfoType> extractSurrogatesAsCustomInfoTypes() {

    Function<String, CustomInfoType> makeCustomInfoType =
        (name) ->
            CustomInfoType.newBuilder()
                .setInfoType(InfoType.newBuilder().setName(name))
                .setSurrogateType(SurrogateType.newBuilder())
                .build();

    Function<FieldTransformation, List<String>> extractSurrogateFromField =
        (fieldTransformation) ->
            switch (fieldTransformation.getTransformationCase()) {
              case PRIMITIVE_TRANSFORMATION -> List.of(
                  getSurrogateInfoType(fieldTransformation.getPrimitiveTransformation()).getName());
              case INFO_TYPE_TRANSFORMATIONS -> extractSurrogateName(
                  fieldTransformation.getInfoTypeTransformations());
              case TRANSFORMATION_NOT_SET -> throwUnknownTransformationException();
            };

    Stream<String> surrogateNamesStream =
        switch (deidentifyConfig.getTransformationCase()) {
          case INFO_TYPE_TRANSFORMATIONS -> extractSurrogateName(
              deidentifyConfig.getInfoTypeTransformations())
              .stream();

          case RECORD_TRANSFORMATIONS -> deidentifyConfig
              .getRecordTransformations()
              .getFieldTransformationsList()
              .stream()
              .map(extractSurrogateFromField)
              .flatMap(List::stream);

          case TRANSFORMATION_NOT_SET -> throwUnknownTransformationException();
        };

    return surrogateNamesStream
        .distinct()
        .filter(name -> (name != null && !name.isBlank()))
        .map(makeCustomInfoType)
        .collect(toList());
  }

  private List<String> extractSurrogateName(InfoTypeTransformations infoTypeTransformations) {
    return infoTypeTransformations.getTransformationsList().stream()
        .map(InfoTypeTransformation::getPrimitiveTransformation)
        .map(this::getSurrogateInfoType)
        .map(InfoType::getName)
        .collect(toList());
  }

  private DeidentifyConfig makeReIdentifyConfig() {
    var reIdConfigBuilder =
        DeidentifyConfig.newBuilder()
            .setTransformationErrorHandling(
                TransformationErrorHandling.newBuilder()
                    .setLeaveUntransformed(
                        TransformationErrorHandling.LeaveUntransformed.newBuilder()));

    switch (deidentifyConfig.getTransformationCase()) {
      case RECORD_TRANSFORMATIONS -> reIdConfigBuilder.setRecordTransformations(
          RecordTransformations.newBuilder()
              .addAllFieldTransformations(
                  deidentifyConfig.getRecordTransformations().getFieldTransformationsList().stream()
                      .map(
                          fieldTransformation -> {
                            var reIdFieldTransformationBuilder =
                                FieldTransformation.newBuilder(fieldTransformation);

                            switch (fieldTransformation.getTransformationCase()) {
                              case PRIMITIVE_TRANSFORMATION -> {
                                checkNotNull(
                                    getSurrogateInfoType(
                                        fieldTransformation.getPrimitiveTransformation()));

                                reIdFieldTransformationBuilder.setPrimitiveTransformation(
                                    fieldTransformation.getPrimitiveTransformation());
                              }

                              case INFO_TYPE_TRANSFORMATIONS -> reIdFieldTransformationBuilder
                                  .setInfoTypeTransformations(
                                      rewriteForReId(
                                          fieldTransformation.getInfoTypeTransformations()));

                              case TRANSFORMATION_NOT_SET -> throwUnknownTransformationException();
                            }

                            return reIdFieldTransformationBuilder.build();
                          })
                      .collect(toList())));

      case INFO_TYPE_TRANSFORMATIONS -> reIdConfigBuilder.setInfoTypeTransformations(
          rewriteForReId(deidentifyConfig.getInfoTypeTransformations()));

      case TRANSFORMATION_NOT_SET -> {}
    }
    return reIdConfigBuilder.build();
  }

  private InfoTypeTransformations rewriteForReId(
      InfoTypeTransformations deidInfoTypeTransformations) {
    return InfoTypeTransformations.newBuilder()
        .addAllTransformations(
            deidInfoTypeTransformations.getTransformationsList().stream()
                .map(
                    infoTransform ->
                        InfoTypeTransformation.newBuilder()
                            .addInfoTypes(
                                getSurrogateInfoType(infoTransform.getPrimitiveTransformation()))
                            .setPrimitiveTransformation(infoTransform.getPrimitiveTransformation())
                            .build())
                .collect(toList()))
        .build();
  }

  private InfoType getSurrogateInfoType(PrimitiveTransformation primitiveTransformation) {

    if (!primitiveTransformation
        .getTransformationCase()
        .equals(TransformationCase.CRYPTO_DETERMINISTIC_CONFIG)) {
      throw new RuntimeException(
          String.format(
              "Unsupported ReId Primitive Transform %s",
              primitiveTransformation.getTransformationCase()));
    }

    return primitiveTransformation.getCryptoDeterministicConfig().getSurrogateInfoType();
  }

  private static <T> T throwUnknownTransformationException() {
    throw new RuntimeException("Unknown Transform Exception");
  }
}
