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
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.cloud.solutions.bqremoteencryptionfn.fns.dlp.DlpRequestBatchExecutor.RowsToTableFn;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.GoogleLogger;
import com.google.common.truth.extensions.proto.ProtoTruth;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Enclosed.class)
public final class RowsToTableFnTest {

  @RunWith(Parameterized.class)
  public static final class ParameterizedTests {

    private final int maxCellCount;
    private final int maxBytes;
    private final List<String> testRows;

    private final int expectedTablesCount;

    public ParameterizedTests(
        String testCaseName,
        int maxCellCount,
        int maxBytes,
        List<String> testRows,
        int expectedTablesCount) {
      this.maxCellCount = maxCellCount;
      this.maxBytes = maxBytes;
      this.testRows = testRows;
      this.expectedTablesCount = expectedTablesCount;

      GoogleLogger.forEnclosingClass().atInfo().log("testCase: %s", testCaseName);
    }

    @Test
    public void apply_valid() {

      var tables = new RowsToTableFn("testDlpColumn", maxCellCount, maxBytes).apply(testRows);

      assertThat(tables).hasSize(expectedTablesCount);
      tables.forEach(
          table -> {
            assertThat(table.getHeadersCount()).isEqualTo(1);
            assertThat(table.getHeadersList().get(0).getName()).isEqualTo("testDlpColumn");
            ProtoTruth.assertThat(table).serializedSize().isLessThan(maxBytes);
          });
    }

    @Parameters(name = "{0}")
    public static ImmutableList<Object[]> testingParameters() {
      return ImmutableList.<Object[]>builder()
          .add(
              new Object[] {
                /*testCaseName=*/ "Exact row split",
                /*maxCellCount=*/ 10,
                /*maxBytes=*/ DlpRequestBatchExecutor.REQUEST_MAX_BYTES,
                /*testRows=*/ makeRows("Some String", 50),
                /*expectedTablesCount=*/ 5
              })
          .add(
              new Object[] {
                /*testCaseName=*/ "Extra table, with remainder of rows",
                /*maxCellCount=*/ 20,
                /*maxBytes=*/ DlpRequestBatchExecutor.REQUEST_MAX_BYTES,
                /*testRows=*/ makeRows("TwentyStringers", 58),
                /*expectedTablesCount=*/ 3
              })
          .add(
              new Object[] {
                /*testCaseName=*/ "Tables split for maxBytes",
                /*maxCellCount=*/ DlpRequestBatchExecutor.REQUEST_MAX_CELL_COUNT,
                /*maxBytes=*/ 100,
                /*testRows=*/ makeRows("iBaseStringToMakeFiftyBytesSizeOfStringWhyHard", 10),
                /*expectedTablesCount=*/ 10
              })
          .build();
    }

    private static List<String> makeRows(String base, int rowCount) {
      return IntStream.range(0, rowCount)
          .boxed()
          .map(i -> String.format("%s %03d", base, i))
          .collect(toImmutableList());
    }
  }

  @RunWith(JUnit4.class)
  public static final class ExceptionTests {

    @Test
    public void singleElementMoreThanMaxBytes_throwsRuntimeException() {

      var fn =
          new RowsToTableFn(
              /*dlpColumnName=*/ "testDlpCol", /*maxCellCount=*/ 10000, /*maxBytes=*/ 50);

      var runtimeException =
          assertThrows(
              RuntimeException.class,
              () ->
                  fn.apply(
                      List.of(
                          "iBaseStringToMakeFiftyBytesSizeOfStringWhyHardiBaseStringToMakeFiftyBytesSizeOfStringWhyHard")));

      assertThat(runtimeException)
          .hasMessageThat()
          .startsWith("Single Row size greater than DLP limit.");
    }
  }
}
