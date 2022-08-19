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

package com.google.cloud.solutions.bqremoteencryptionfn.fns;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toList;

import com.google.cloud.dlp.v2.DlpServiceClient;
import com.google.cloud.solutions.bqremoteencryptionfn.TokenizeFn;
import com.google.cloud.solutions.bqremoteencryptionfn.TokenizeFnFactory;
import com.google.common.flogger.GoogleLogger;
import com.google.privacy.dlp.v2.ContentItem;
import com.google.privacy.dlp.v2.DeidentifyContentRequest;
import com.google.privacy.dlp.v2.FieldId;
import com.google.privacy.dlp.v2.Table;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * Implements the Google Cloud DLP based tokenization using the provided DeIdenitfy Templates. The
 * Function does not manage DLP batch sizes, which can potentially throw an error.
 *
 * <p>
 *
 * @see <a href="https://cloud.google.com/dlp/docs/creating-templates-deid">DeIdentify Templates</a>
 */
public final class DlpFn extends UnaryStringArgFn {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  public static final String FN_NAME = "dlp";

  public static final String DLP_DEID_TEMPLATE_KEY = "dlp-deid-template";

  private static final Pattern TEMPLATE_LOCATION_REGEX =
      Pattern.compile("^projects/(?<project>[^/]+)/locations/(?<location>[^/]+)");

  /** Factory Interface to manage client creation for Unit Testing. */
  public interface DlpClientFactory {
    DlpServiceClient newClient() throws Exception;
  }

  @Component
  @PropertySource("classpath:dlp.properties")
  public static class DlpTokenizeFnFactory implements TokenizeFnFactory<TokenizeFn> {
    private final String dlpColName;
    private final DlpClientFactory dlpClientFactory;

    public DlpTokenizeFnFactory(
        @Value("${dlp.valueColName}") String dlpColName, DlpClientFactory dlpClientFactory) {
      this.dlpColName = dlpColName;
      this.dlpClientFactory = dlpClientFactory;
    }

    @Override
    public TokenizeFn createFn(Map<String, String> options) {
      var deidTemplateName = options.get(DLP_DEID_TEMPLATE_KEY);
      return new DlpFn(dlpColName, deidTemplateName, dlpClientFactory);
    }

    @Override
    public String getFnName() {
      return FN_NAME;
    }
  }

  private final String deidTemplateName;
  private final RowsToTableFn tableFn;
  private final TableToRowsFn rowsFn;
  private final DlpClientFactory dlpClientFactory;

  public DlpFn(String dlpColName, String deidTemplateName, DlpClientFactory dlpClientFactory) {
    this.deidTemplateName = deidTemplateName;
    this.tableFn = new RowsToTableFn(dlpColName);
    this.rowsFn = new TableToRowsFn();
    this.dlpClientFactory = dlpClientFactory;
  }

  @Override
  public List<String> tokenizeUnaryRow(List<String> rows) throws Exception {
    try (var dlpClient = dlpClientFactory.newClient()) {
      var table = tableFn.apply(rows);

      var request =
          DeidentifyContentRequest.newBuilder()
              .setParent(extractDlpParent(deidTemplateName))
              .setDeidentifyTemplateName(deidTemplateName)
              .setItem(ContentItem.newBuilder().setTable(table).build())
              .build();

      logger.atInfo().log("tokenize %s rows, %s bytes", rows.size(), request.getSerializedSize());

      var tokenizedValues = dlpClient.deidentifyContent(request);

      return rowsFn.apply(tokenizedValues.getItem().getTable());
    }
  }

  @Override
  public List<String> reIdentifyUnaryRow(List<String> rows) throws Exception {
    try (var dlpClient = dlpClientFactory.newClient()) {

      var table = tableFn.apply(rows);

      var deidentifyConfig =
          dlpClient.getDeidentifyTemplate(deidTemplateName).getDeidentifyConfig();

      var reidRequest =
          DlpReIdRequestMaker.forConfig(deidentifyConfig)
              .makeRequest(ContentItem.newBuilder().setTable(table))
              .toBuilder()
              .setParent(extractDlpParent(deidTemplateName))
              .build();

      logger.atInfo().log(
          "reidentify %s rows, %s bytes", rows.size(), reidRequest.getSerializedSize());

      var reIdentifiedValues = dlpClient.reidentifyContent(reidRequest);

      return rowsFn.apply(reIdentifiedValues.getItem().getTable());
    }
  }

  @Override
  public String getName() {
    return FN_NAME;
  }

  private static class RowsToTableFn implements Function<List<String>, Table> {

    private final FieldId dlpColName;

    public RowsToTableFn(String dlpColName) {
      this.dlpColName = FieldId.newBuilder().setName(dlpColName).build();
    }

    @Override
    public Table apply(List<String> rows) {
      return Table.newBuilder()
          .addHeaders(dlpColName)
          .addAllRows(
              rows.stream()
                  .map(e -> com.google.privacy.dlp.v2.Value.newBuilder().setStringValue(e).build())
                  .map(v -> Table.Row.newBuilder().addValues(v).build())
                  .collect(toList()))
          .build();
    }
  }

  private static class TableToRowsFn implements Function<Table, List<String>> {

    @Override
    public List<String> apply(Table table) {
      return table.getRowsList().stream()
          .map(r -> r.getValues(0))
          .map(com.google.privacy.dlp.v2.Value::getStringValue)
          .collect(toImmutableList());
    }
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
