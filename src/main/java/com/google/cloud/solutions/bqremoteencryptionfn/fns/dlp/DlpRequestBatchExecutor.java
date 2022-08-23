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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toList;

import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.cloud.dlp.v2.DlpServiceClient;
import com.google.cloud.solutions.bqremoteencryptionfn.fns.dlp.DlpFn.DlpClientFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.GoogleLogger;
import com.google.privacy.dlp.v2.FieldId;
import com.google.privacy.dlp.v2.Table;
import com.google.privacy.dlp.v2.Table.Row;
import com.google.privacy.dlp.v2.Value;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Standard execution pattern for batching requests as per DLP request size limits.
 *
 * @see <a href="https://cloud.google.com/dlp/limits#content-redaction-limits">Redaction Limits</a>.
 * @see <a href="https://cloud.google.com/dlp/limits#content-limits">Content Limits</a>
 */
public class DlpRequestBatchExecutor<DlpRequestT, DlpResponseT> {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  public static final int REQUEST_MAX_CELL_COUNT = 50000;

  public static final int REQUEST_MAX_BYTES = 500000;

  private final String dlpColumnName;
  private final DlpClientFactory dlpClientFactory;

  private final Function<DlpServiceClient, Function<DlpRequestT, DlpResponseT>> dlpCallFnFactory;
  private final Function<DlpServiceClient, Function<Table, DlpRequestT>> tableToDlpRequestFnFactory;

  private final Function<DlpRequestT, Table> dlpRequestToTableFn;
  private final Function<DlpResponseT, Table> dlpResponseToTableFn;

  public DlpRequestBatchExecutor(
      String dlpColumnName,
      DlpClientFactory dlpClientFactory,
      Function<DlpServiceClient, Function<DlpRequestT, DlpResponseT>> dlpCallFnFactory,
      Function<DlpServiceClient, Function<Table, DlpRequestT>> tableToDlpRequestFnFactory,
      Function<DlpRequestT, Table> dlpRequestToTableFn,
      Function<DlpResponseT, Table> dlpResponseToTableFn) {
    this.dlpColumnName = dlpColumnName;
    this.dlpClientFactory = dlpClientFactory;
    this.dlpCallFnFactory = dlpCallFnFactory;
    this.tableToDlpRequestFnFactory = tableToDlpRequestFnFactory;
    this.dlpRequestToTableFn = dlpRequestToTableFn;
    this.dlpResponseToTableFn = dlpResponseToTableFn;
  }

  public List<String> process(List<String> rows) throws Exception {

    try (var dlpClient = dlpClientFactory.newClient()) {

      var requestMaker = tableToDlpRequestFnFactory.apply(dlpClient);
      var rowToTableFn = new RowsToTableFn(dlpColumnName);
      var tableToRowsFn = new TableToRowsFn();

      return rowToTableFn.apply(rows).stream()
          .map(requestMaker)
          .map(new RetryingDlpCaller(dlpClient))
          .flatMap(List::stream)
          .map(dlpResponseToTableFn)
          .map(tableToRowsFn)
          .flatMap(List::stream)
          .collect(toList());
    }
  }

  /**
   * Implements exponential down-sizing of request payload when DLP content API requests to send
   * smaller requests by throwing {@link InvalidArgumentException}.
   */
  private class RetryingDlpCaller implements Function<DlpRequestT, List<DlpResponseT>> {

    private final DlpServiceClient dlpClient;

    public RetryingDlpCaller(DlpServiceClient dlpClient) {
      this.dlpClient = dlpClient;
    }

    @Override
    public List<DlpResponseT> apply(DlpRequestT dlpRequest) {
      var dlpCallFn = dlpCallFnFactory.apply(dlpClient);
      var retries = 0;

      var requestsToSend = List.of(dlpRequest);

      do {
        try {

          logger.atInfo().log("Sending Try(%s): ", retries);

          return requestsToSend.stream().map(dlpCallFn).toList();

        } catch (InvalidArgumentException invalidArgumentException) {

          logger.atWarning().log(
              "DLP Caller InvalidArgument: msg: %s, retryable: %s",
              invalidArgumentException.getMessage(), invalidArgumentException.isRetryable());

          if (!invalidArgumentException.isRetryable()
              || !invalidArgumentException
                  .getMessage()
                  .toLowerCase()
                  .contains("retry with a smaller request")) {
            throw invalidArgumentException;
          }
        }

        requestsToSend =
            requestsToSend.stream().map(this::splitRowsToHalf).flatMap(List::stream).toList();

      } while (retries++ < 10);

      throw new RuntimeException("unable to receive DLP Response after retries");
    }

    private List<DlpRequestT> splitRowsToHalf(DlpRequestT dlpRequest) {

      var table = dlpRequestToTableFn.apply(dlpRequest);

      var rows = table.getRowsList();
      var rowCount = table.getRowsCount();
      var splitPoint = rowCount / 2;

      logger.atInfo().log("Splitting Table: new RowSize: %s", splitPoint);

      return Stream.of(
              table.toBuilder().clearRows().addAllRows(rows.subList(0, splitPoint)).build(),
              table.toBuilder().clearRows().addAllRows(rows.subList(splitPoint, rowCount)).build())
          .map(subTable -> tableToDlpRequestFnFactory.apply(dlpClient).apply(subTable))
          .toList();
    }
  }

  @VisibleForTesting
  static final class RowsToTableFn implements Function<List<String>, List<Table>> {

    private final String dlpColumnName;
    private final int maxCellCount;
    private final int maxBytes;

    @VisibleForTesting
    RowsToTableFn(String dlpColumnName, int maxCellCount, int maxBytes) {
      this.dlpColumnName = dlpColumnName;
      this.maxCellCount = maxCellCount;
      this.maxBytes = maxBytes;
    }

    RowsToTableFn(String dlpColumnName) {
      this(dlpColumnName, REQUEST_MAX_CELL_COUNT, REQUEST_MAX_BYTES);
    }

    @Override
    public List<Table> apply(List<String> rows) {
      var requestTableBuilder = ImmutableList.<Table>builder();

      var accTable = newTable();

      for (var stringRow : rows) {
        var tableRow = convertStringToRow(stringRow);

        if (tableRow.getSerializedSize() >= maxBytes) {
          throw new RuntimeException(
              String.format(
                  "Single Row size greater than DLP limit. Found %s bytes",
                  tableRow.getSerializedSize()));
        }

        if (accTable.getRowsCount() + 1 > maxCellCount
            || accTable.getSerializedSize() + tableRow.getSerializedSize() >= maxBytes) {

          requestTableBuilder.add(accTable);
          accTable = newTable();
        }

        accTable = accTable.toBuilder().addRows(tableRow).build();
      }

      var requestTables = requestTableBuilder.add(accTable).build();
      logger.atInfo().log("Created %s tables from %s rows", requestTables.size(), rows.size());
      return requestTables;
    }

    private Row convertStringToRow(String value) {
      return Row.newBuilder().addValues(Value.newBuilder().setStringValue(value)).build();
    }

    private Table newTable() {
      return Table.newBuilder().addHeaders(FieldId.newBuilder().setName(dlpColumnName)).build();
    }
  }

  private final class TableToRowsFn implements Function<Table, List<String>> {
    private int getHeaderIndex(List<FieldId> headers) {
      int headerIndex = 0;
      for (; headerIndex < headers.size(); headerIndex++) {
        if (headers.get(headerIndex).getName().equals(dlpColumnName)) {
          return headerIndex;
        }
      }

      throw new RuntimeException(
          String.format("required Table header (%s) not found in: %s", dlpColumnName, headers));
    }

    @Override
    public List<String> apply(Table table) {
      final int headerIndex = getHeaderIndex(table.getHeadersList());

      return table.getRowsList().stream()
          .map(r -> r.getValues(headerIndex))
          .map(com.google.privacy.dlp.v2.Value::getStringValue)
          .collect(toImmutableList());
    }
  }
}
