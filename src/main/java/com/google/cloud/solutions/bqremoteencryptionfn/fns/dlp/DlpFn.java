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

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.cloud.dlp.v2.DlpServiceClient;
import com.google.cloud.solutions.bqremoteencryptionfn.TransformFnFactory;
import com.google.cloud.solutions.bqremoteencryptionfn.fns.UnaryStringArgFn;
import com.google.privacy.dlp.v2.*;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * Implements the Google Cloud DLP based tokenization using the provided Deidentify Templates. The
 * Function does not manage DLP batch sizes, which can potentially throw an error.
 *
 * <p>
 *
 * @see <a href="https://cloud.google.com/dlp/docs/creating-templates-deid">Deidentify Templates</a>
 */
public final class DlpFn extends UnaryStringArgFn {
  public static final String FN_NAME = "dlp";

  private static final Pattern TEMPLATE_LOCATION_REGEX =
      Pattern.compile("^projects/(?<project>[^/]+)/locations/(?<location>[^/]+)");

  /** Factory Interface to manage client creation. */
  public interface DlpClientFactory {
    DlpServiceClient newClient() throws Exception;
  }

  @Component
  @PropertySource("classpath:dlp.properties")
  public static class DlpTransformFnFactory implements TransformFnFactory<DlpFn> {
    private final String dlpColName;

    private final int requestCellCount;

    private final int requestBytes;
    private final DlpClientFactory dlpClientFactory;

    public DlpTransformFnFactory(
        @Value("${dlp.valueColName}") String dlpColName,
        @Value("${dlp.requestCellCount}") int requestCellCount,
        @Value("${dlp.requestBytes}") int requestBytes,
        DlpClientFactory dlpClientFactory) {
      this.dlpColName = dlpColName;
      this.requestCellCount = requestCellCount;
      this.requestBytes = requestBytes;
      this.dlpClientFactory = dlpClientFactory;
    }

    @Override
    public DlpFn createFn(@Nonnull Map<String, String> options) {
      return new DlpFn(
          requestCellCount,
          requestBytes,
          dlpColName,
          DlpConfig.fromJson(options),
          dlpClientFactory);
    }

    @Override
    public String getFnName() {
      return FN_NAME;
    }
  }

  private final String dlpColName;

  private final int requestCellCount;

  private final int requestBytes;
  private final DlpConfig dlpConfig;
  private final DlpClientFactory dlpClientFactory;

  private DlpFn(
      int requestCellCount,
      int requestBytes,
      String dlpColName,
      DlpConfig dlpConfig,
      DlpClientFactory dlpClientFactory) {
    this.requestCellCount = requestCellCount;
    this.requestBytes = requestBytes;
    this.dlpColName = dlpColName;
    this.dlpConfig = dlpConfig;
    this.dlpClientFactory = dlpClientFactory;
  }

  @Override
  public List<String> deidentifyUnaryRow(List<String> rows) throws Exception {
    return DlpRequestBatchExecutor.<DeidentifyContentRequest, DeidentifyContentResponse>builder()
        .setDlpColumnName(dlpColName)
        .setRequestCellCount(requestCellCount)
        .setRequestMaxBytes(requestBytes)
        .setDlpClientFactory(dlpClientFactory)
        .setDlpCallFnFactory(dlpClient -> dlpClient::deidentifyContent)
        .setTableToDlpRequestFnFactory(
            dlpClient ->
                table ->
                    DeidentifyContentRequest.newBuilder()
                        .setParent(extractDlpParent(dlpConfig.deidTemplate()))
                        .setDeidentifyTemplateName(dlpConfig.deidTemplate())
                        .setInspectTemplateName(
                            dlpConfig.hasInspectTemplate() ? dlpConfig.inspectTemplate() : "")
                        .setItem(ContentItem.newBuilder().setTable(table).build())
                        .build())
        .setDlpRequestToTableFn(deidRequest -> deidRequest.getItem().getTable())
        .setDlpResponseToTableFn(deidResponse -> deidResponse.getItem().getTable())
        .build()
        .process(rows);
  }

  @Override
  public List<String> reidentifyUnaryRow(List<String> rows) throws Exception {
    return DlpRequestBatchExecutor.<ReidentifyContentRequest, ReidentifyContentResponse>builder()
        .setDlpColumnName(dlpColName)
        .setRequestCellCount(requestCellCount)
        .setRequestMaxBytes(requestBytes)
        .setDlpClientFactory(dlpClientFactory)
        .setDlpCallFnFactory(dlpClient -> dlpClient::reidentifyContent)
        .setTableToDlpRequestFnFactory(
            dlpClient -> {
              var deidentifyConfig =
                  dlpClient.getDeidentifyTemplate(dlpConfig.deidTemplate()).getDeidentifyConfig();
              return (table) ->
                  DlpReIdRequestMaker.forConfig(deidentifyConfig)
                      .makeRequest(ContentItem.newBuilder().setTable(table))
                      .toBuilder()
                      .setParent(extractDlpParent(dlpConfig.deidTemplate()))
                      .setInspectTemplateName(
                          dlpConfig.hasInspectTemplate() ? dlpConfig.inspectTemplate() : "")
                      .build();
            })
        .setDlpRequestToTableFn(reidRequest -> reidRequest.getItem().getTable())
        .setDlpResponseToTableFn(reidResponse -> reidResponse.getItem().getTable())
        .build()
        .process(rows);
  }

  @Override
  public String getName() {
    return FN_NAME;
  }

  private static String extractDlpParent(String dlpTemplateName) {

    var matcher = TEMPLATE_LOCATION_REGEX.matcher(dlpTemplateName);
    if (!matcher.find()) {
      throw new RuntimeException("Invalid DLP Template name");
    }

    var location = matcher.group("location");

    var parentBuilder = new StringBuilder().append("projects/").append(matcher.group("project"));

    if (isNullOrEmpty(location) || !location.equals("global")) {
      parentBuilder.append("/locations/").append(location);
    }

    return parentBuilder.toString();
  }
}
