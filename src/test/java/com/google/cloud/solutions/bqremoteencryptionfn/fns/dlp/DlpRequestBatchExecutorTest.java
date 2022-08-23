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

import com.google.cloud.dlp.v2.DlpServiceClient;
import com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.dlp.Base64EncodingDlpStub;
import com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.dlp.PatchyDlpStub;
import com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.dlp.RequestSizeLimitingDeidentifyFactory;
import com.google.common.collect.ImmutableSet;
import com.google.privacy.dlp.v2.ContentItem;
import com.google.privacy.dlp.v2.DeidentifyContentRequest;
import com.google.privacy.dlp.v2.DeidentifyContentResponse;
import com.google.privacy.dlp.v2.ReidentifyContentRequest;
import com.google.privacy.dlp.v2.ReidentifyContentResponse;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DlpRequestBatchExecutorTest {

  @Test
  public void process_correctlyCreatedDlpSizedTables_valid() throws Exception {

    var testRows = makeRows("TwentyStringers", 60000);
    var expectedTableRequests = 4;

    var base64EncodingStub =
        new Base64EncodingDlpStub(ImmutableSet.of("bqfnvalue"), "test-project-id", "global");

    var dlpServiceClient =
        DlpServiceClient.create(
            PatchyDlpStub.using(
                List.of(
                    base64EncodingStub.deidentifyFactory(),
                    base64EncodingStub.reidentifyFactory())));

    RequestMeasuringDlpCaller<DeidentifyContentRequest, DeidentifyContentResponse>
        deidRequestMeasuringDlpCaller =
            new RequestMeasuringDlpCaller<>(dlpClient -> dlpClient::deidentifyContent);

    RequestMeasuringDlpCaller<ReidentifyContentRequest, ReidentifyContentResponse>
        reidRequestMeasuringDlpCaller =
            new RequestMeasuringDlpCaller<>(dlpClient -> dlpClient::reidentifyContent);

    var deidRows =
        new DlpRequestBatchExecutor<>(
                "dlpColumnName",
                () -> dlpServiceClient,
                deidRequestMeasuringDlpCaller,
                dlpClient ->
                    table ->
                        DeidentifyContentRequest.newBuilder()
                            .setParent("projects/test-project-id")
                            .setItem(ContentItem.newBuilder().setTable(table))
                            .build(),
                deidRequest -> deidRequest.getItem().getTable(),
                deidResponse -> deidResponse.getItem().getTable())
            .process(testRows);

    var reidRows =
        new DlpRequestBatchExecutor<>(
                "dlpColumnName",
                () -> dlpServiceClient,
                reidRequestMeasuringDlpCaller,
                dlpClient ->
                    table ->
                        ReidentifyContentRequest.newBuilder()
                            .setParent("projects/test-project-id")
                            .setItem(ContentItem.newBuilder().setTable(table))
                            .build(),
                reidRequest -> reidRequest.getItem().getTable(),
                reidResponse -> reidResponse.getItem().getTable())
            .process(deidRows);

    assertThat(deidRows).hasSize(testRows.size());
    assertThat(reidRows).containsAtLeastElementsIn(testRows).inOrder();
    assertThat(deidRequestMeasuringDlpCaller.getRequestCalls()).isEqualTo(expectedTableRequests);
    assertThat(reidRequestMeasuringDlpCaller.getRequestCalls()).isEqualTo(expectedTableRequests);
  }

  @Test
  public void process_splitsTheTableWhenRequested_valid() throws Exception {

    var testRows = makeRows("SplittingTest", 1000);
    var expectedTableRequests = 3; // 1st = 1000 rows, 2,3 = 500 rows (1000/2)

    var dlpServiceClient =
        DlpServiceClient.create(
            PatchyDlpStub.using(List.of(new RequestSizeLimitingDeidentifyFactory(600))));

    RequestMeasuringDlpCaller<DeidentifyContentRequest, DeidentifyContentResponse>
        deidRequestMeasuringDlpCaller =
            new RequestMeasuringDlpCaller<>(dlpClient -> dlpClient::deidentifyContent);

    var deidRows =
        new DlpRequestBatchExecutor<>(
                "dlpColumnName",
                () -> dlpServiceClient,
                deidRequestMeasuringDlpCaller,
                dlpClient ->
                    table ->
                        DeidentifyContentRequest.newBuilder()
                            .setParent("projects/test-project-id")
                            .setItem(ContentItem.newBuilder().setTable(table))
                            .build(),
                deidRequest -> deidRequest.getItem().getTable(),
                deidResponse -> deidResponse.getItem().getTable())
            .process(testRows);

    assertThat(deidRows).hasSize(testRows.size());
    assertThat(deidRequestMeasuringDlpCaller.getRequestCalls()).isEqualTo(expectedTableRequests);
  }

  private static List<String> makeRows(String base, int rowCount) {
    return IntStream.range(0, rowCount)
        .boxed()
        .map(i -> String.format("%s %06d", base, i))
        .collect(toImmutableList());
  }

  public static final class RequestMeasuringDlpCaller<DlpRequestT, DlpResponseT>
      implements Function<DlpServiceClient, Function<DlpRequestT, DlpResponseT>> {

    private int requestCount;

    private final Function<DlpServiceClient, Function<DlpRequestT, DlpResponseT>> actualFn;

    public RequestMeasuringDlpCaller(
        Function<DlpServiceClient, Function<DlpRequestT, DlpResponseT>> actualFn) {
      this.requestCount = 0;
      this.actualFn = actualFn;
    }

    @Override
    public Function<DlpRequestT, DlpResponseT> apply(DlpServiceClient dlpServiceClient) {
      return dlpRequestT -> {
        requestCount++;
        return actualFn.apply(dlpServiceClient).apply(dlpRequestT);
      };
    }

    public int getRequestCalls() {
      return requestCount;
    }
  }
}
