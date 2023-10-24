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

package com.google.cloud.solutions.bqremoteencryptionfn;

import static com.google.cloud.solutions.bqremoteencryptionfn.testing.JsonMapper.fromJson;
import static com.google.cloud.solutions.bqremoteencryptionfn.testing.JsonMapper.jsonToProto;
import static com.google.cloud.solutions.bqremoteencryptionfn.testing.SimpleBigQueryRemoteFnRequestMaker.testRequest;
import static com.google.common.truth.Truth.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.google.cloud.dlp.v2.DlpServiceClient;
import com.google.cloud.solutions.bqremoteencryptionfn.fns.dlp.DlpFn.DlpClientFactory;
import com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.BaseUnaryApiFuture.ApiFutureFactory;
import com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.dlp.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.GoogleLogger;
import com.google.common.io.Resources;
import com.google.privacy.dlp.v2.DeidentifyContentRequest;
import com.google.privacy.dlp.v2.DeidentifyTemplate;
import com.google.privacy.dlp.v2.ReidentifyContentRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@RunWith(Parameterized.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(BqTransformFnAppTest.TestDlpClientFactoryConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
public final class BqTransformFnAppTest {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  static {
    System.setProperty("AES_KEY", "2lDNBd0hHgCZ+1/P+fWO+g==");
    System.setProperty("AES_IV_PARAMETER_BASE64", "/t2/6YFewDgoHeQM1QBZdw==");
  }

  @ClassRule public static final SpringClassRule SPRING_CLASS_RULE = new SpringClassRule();
  @Rule public final SpringMethodRule springMethodRule = new SpringMethodRule();
  @Autowired MockMvc mockMvc;

  @Autowired TestDlpClientFactoryConfiguration dlpClientFactoryConfiguration;

  private final String testRequestJson;
  private final BigQueryRemoteFnResponse expectedResult;

  private final List<ApiFutureFactory<?, ?>> factories;

  public BqTransformFnAppTest(
      String testCaseName,
      String testRequestJson,
      BigQueryRemoteFnResponse expectedResult,
      List<ApiFutureFactory<?, ?>> factories) {
    this.testRequestJson = testRequestJson;
    this.expectedResult = expectedResult;
    this.factories = factories;

    logger.atInfo().log("Starting Testcase: %s", testCaseName);
  }

  @Before
  public void setApiFactories() {
    dlpClientFactoryConfiguration.factories = this.factories;
  }

  @Test
  public void operation_valid() throws Exception {
    mockMvc
        .perform(post("/").contentType("application/json").content(testRequestJson))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(
            result ->
                assertThat(
                        fromJson(
                            result.getResponse().getContentAsString(),
                            BigQueryRemoteFnResponse.class))
                    .isEqualTo(expectedResult));
  }

  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> testParameters() throws IOException {

    var base64Stub =
        new Base64EncodingDlpStub(ImmutableSet.of("bqfnvalue"), "test-project-id", "test-region1");

    return ImmutableList.<Object[]>builder()
        .add(
            new Object[] {
              /* testName= */ "No-Op Deidentify",
              /* testRequestJson= */ testRequest(
                  Map.of("mode", "deidentify", "algo", "identity"),
                  List.of("Anant"),
                  List.of("Damle")),
              /* expectedResult= */ new BigQueryRemoteFnResponse(List.of("Anant", "Damle"), null),
              /* factories= */ List.of()
            })
        .add(
            new Object[] {
              /* testName= */ "Identity ReIdenitfy",
              /* testRequestJson= */ testRequest(
                  Map.of("mode", "reidentify", "algo", "identity"),
                  List.of("Anant"),
                  List.of("Damle")),
              /* expectedResult= */ new BigQueryRemoteFnResponse(List.of("Anant", "Damle"), null),
              /* factories= */ List.of()
            })
        .add(
            new Object[] {
              /* testName= */ "Base64 Deidentify",
              /* testRequestJson= */ testRequest(
                  Map.of("mode", "deidentify", "algo", "base64"),
                  List.of("Anant"),
                  List.of("Damle")),
              /* expectedResult= */ new BigQueryRemoteFnResponse(
                  List.of("QW5hbnQ=", "RGFtbGU="), null),
              /* factories= */ List.of()
            })
        .add(
            new Object[] {
              /* testName= */ "Base64 ReIdentify",
              /* testRequestJson= */ testRequest(
                  Map.of("mode", "reidentify", "algo", "base64"),
                  List.of("QW5hbnQ="),
                  List.of("RGFtbGU=")),
              /* expectedResult= */ new BigQueryRemoteFnResponse(List.of("Anant", "Damle"), null),
              /* factories= */ List.of()
            })
        .add(
            new Object[] {
              /* testName= */ "AES128-ECB Deidentify",
              /* testRequestJson= */ testRequest(
                  Map.of(
                      "mode", "deidentify",
                      "algo", "aes",
                      "aes-cipher-type", "AES/ECB/PKCS5PADDING"),
                  List.of("Anant"),
                  List.of("Damle")),
              /* expectedResult= */ new BigQueryRemoteFnResponse(
                  List.of("nrUwN61laFc115jyyQHmng==", "JCKtXkM8spJLyZdAqZKf/g=="), null),
              /* factories= */ List.of()
            })
        .add(
            new Object[] {
              /* testName= */ "AES128-ECB ReIdentify",
              /* testRequestJson= */ testRequest(
                  Map.of(
                      "mode",
                      "reidentify",
                      "algo",
                      "aes",
                      "aes-cipher-type",
                      "AES/ECB/PKCS5PADDING"),
                  List.of("nrUwN61laFc115jyyQHmng=="),
                  List.of("JCKtXkM8spJLyZdAqZKf/g==")),
              /* expectedResult= */ new BigQueryRemoteFnResponse(List.of("Anant", "Damle"), null),
              /* factories= */ List.of()
            })
        .add(
            new Object[] {
              /* testName= */ "AES128 (default: CBC) Deidentify",
              /* testRequestJson= */ testRequest(
                  Map.of("mode", "deidentify", "algo", "aes"), List.of("Anant"), List.of("Damle")),
              /* expectedResult= */ new BigQueryRemoteFnResponse(
                  List.of("VhxcfvLBLRy8ag4DVl+7yQ==", "vjVNUHd2cpR0S8XLqhR+VQ=="), null),
              /* factories= */ List.of()
            })
        .add(
            new Object[] {
              /* testName= */ "AES128 (default: CBC) pro ReIdentify",
              /* testRequestJson= */ testRequest(
                  Map.of("mode", "reidentify", "algo", "aes"),
                  List.of("VhxcfvLBLRy8ag4DVl+7yQ=="),
                  List.of("vjVNUHd2cpR0S8XLqhR+VQ==")),
              /* expectedResult= */ new BigQueryRemoteFnResponse(List.of("Anant", "Damle"), null),
              /* factories= */ List.of()
            })
        .add(
            new Object[] {
              /* testName= */ "AES128 CBC User provided ivParameter Deidentify",
              /* testRequestJson= */ testRequest(
                  Map.of(
                      "mode",
                      "deidentify",
                      "algo",
                      "aes",
                      "aes-iv-parameter-base64",
                      "VGhpc0lzVGVzdFZlY3Rvcg=="),
                  List.of("Anant"),
                  List.of("Damle")),
              /* expectedResult= */ new BigQueryRemoteFnResponse(
                  List.of("8MWXmtCTjwOlpBopOGQZfg==", "m3XXwCieBwdWi700D9yZdg=="), null),
              /* factories= */ List.of()
            })
        .add(
            new Object[] {
              /* testName= */ "AES128 CBC User provided ivParameter ReIdentify",
              /* testRequestJson= */ testRequest(
                  Map.of(
                      "mode",
                      "reidentify",
                      "algo",
                      "aes",
                      "aes-iv-parameter-base64",
                      "VGhpc0lzVGVzdFZlY3Rvcg=="),
                  List.of("8MWXmtCTjwOlpBopOGQZfg=="),
                  List.of("m3XXwCieBwdWi700D9yZdg==")),
              /* expectedResult= */ new BigQueryRemoteFnResponse(List.of("Anant", "Damle"), null),
              /* factories= */ List.of()
            })
        .add(
            new Object[] {
              /* testName= */ "DLP deidentify",
              /* testRequestJson= */ testRequest(
                  Map.of(
                      "mode",
                      "deidentify",
                      "algo",
                      "dlp",
                      "dlp-deid-template",
                      "projects/test-project-id/locations/test-region1/deidentifyTemplates/template1"),
                  List.of("Anant"),
                  List.of("Damle")),
              /* expectedResult= */ new BigQueryRemoteFnResponse(
                  List.of("QW5hbnQ=", "RGFtbGU="), null),
              /* factories= */ List.of(
                  base64Stub.deidentifyFactory(), base64Stub.reidentifyFactory())
            })
        .add(
            new Object[] {
              /* testName= */ "DLP deidentify with inspect-Template",
              /* testRequestJson= */ testRequest(
                  Map.of(
                      "mode",
                      "deidentify",
                      "algo",
                      "dlp",
                      "dlp-deid-template",
                      "projects/test-project-id/locations/test-region1/deidentifyTemplates/template1",
                      "dlp-inspect-template",
                      "testing-inspect-template-name"),
                  List.of("Anant"),
                  List.of("Damle")),
              /* expectedResult= */ new BigQueryRemoteFnResponse(
                  List.of("QW5hbnQ=", "RGFtbGU="), null),
              /* factories= */ List.of(
                  new VerifyingDeidentifyCallerFactory(
                      jsonToProto(
                          loadResourceAsString(
                              "deidentify_request_with_inspect_template_name.json"),
                          DeidentifyContentRequest.class),
                      base64Stub.deidentifyFactory()),
                  base64Stub.reidentifyFactory())
            })
        .add(
            new Object[] {
              /* testName= */ "DLP reidentify Single Surrogate",
              /* testRequestJson= */ testRequest(
                  Map.of(
                      "mode",
                      "reidentify",
                      "algo",
                      "dlp",
                      "dlp-deid-template",
                      "projects/test-project-id/locations/test-region1/deidentifyTemplates/template1"),
                  List.of("QW5hbnQ="),
                  List.of("RGFtbGU=")),
              /* expectedResult= */ new BigQueryRemoteFnResponse(List.of("Anant", "Damle"), null),
              /* factories= */ List.of(
                  base64Stub.deidentifyFactory(),
                  MappingDeidentifyTemplateCallerFactory.using(
                      Map.of(
                          "projects/test-project-id/locations/test-region1/deidentifyTemplates/template1",
                          jsonToProto(
                              loadResourceAsString(
                                  "single_surrogate_info_type_transform_deid_template.json"),
                              DeidentifyTemplate.class))),
                  VerifyingReidentifyCallerFactory.withExpectedRequest(
                          jsonToProto(
                              loadResourceAsString(
                                  "single_surrogate_info_type_transform_reid_request.json"),
                              ReidentifyContentRequest.class))
                      .withReidFactory(base64Stub.reidentifyFactory()))
            })
        .add(
            new Object[] {
              /* testName= */ "DLP reidentify Record Transform Two Surrogates",
              /* testRequestJson= */ testRequest(
                  Map.of(
                      "mode",
                      "reidentify",
                      "algo",
                      "dlp",
                      "dlp-deid-template",
                      "projects/test-project-id/locations/test-region1/deidentifyTemplates/template2"),
                  List.of("QW5hbnQ="),
                  List.of("RGFtbGU=")),
              /* expectedResult= */ new BigQueryRemoteFnResponse(List.of("Anant", "Damle"), null),
              /* factories= */ List.of(
                  base64Stub.deidentifyFactory(),
                  MappingDeidentifyTemplateCallerFactory.using(
                      Map.of(
                          "projects/test-project-id/locations/test-region1/deidentifyTemplates/template2",
                          jsonToProto(
                              loadResourceAsString(
                                  "multiple_surrogate_record_info_type_transforms_deid_config.json"),
                              DeidentifyTemplate.class))),
                  VerifyingReidentifyCallerFactory.withExpectedRequest(
                          jsonToProto(
                              loadResourceAsString(
                                  "multiple_surrogate_record_info_type_reid_request.json"),
                              ReidentifyContentRequest.class))
                      .withReidFactory(base64Stub.reidentifyFactory()))
            })
        .add(
            new Object[] {
              /* testName= */ "DLP reidentify Record Transform Primitive",
              /* testRequestJson= */ testRequest(
                  Map.of(
                      "mode",
                      "reidentify",
                      "algo",
                      "dlp",
                      "dlp-deid-template",
                      "projects/test-project-id/locations/test-region1/deidentifyTemplates/template2"),
                  List.of("QW5hbnQ="),
                  List.of("RGFtbGU=")),
              /* expectedResult= */ new BigQueryRemoteFnResponse(List.of("Anant", "Damle"), null),
              /* factories= */ List.of(
                  base64Stub.deidentifyFactory(),
                  MappingDeidentifyTemplateCallerFactory.using(
                      Map.of(
                          "projects/test-project-id/locations/test-region1/deidentifyTemplates/template2",
                          jsonToProto(
                              loadResourceAsString(
                                  "single_surrogate_record_primitive_type_transform_deid_template.json"),
                              DeidentifyTemplate.class))),
                  VerifyingReidentifyCallerFactory.withExpectedRequest(
                          jsonToProto(
                              loadResourceAsString(
                                  "single_surrogate_record_primitive_type_transform_reid_request.json"),
                              ReidentifyContentRequest.class))
                      .withReidFactory(base64Stub.reidentifyFactory()))
            })
        .add(
            new Object[] {
              /* testName= */ "DLP reidentify Record Transform Primitive",
              /* testRequestJson= */ testRequest(
                  Map.of(
                      "mode",
                      "reidentify",
                      "algo",
                      "dlp",
                      "dlp-deid-template",
                      "projects/test-project-id/locations/test-region1/deidentifyTemplates/template2",
                      "dlp-inspect-template",
                      "testing-inspect-template"),
                  List.of("QW5hbnQ="),
                  List.of("RGFtbGU=")),
              /* expectedResult= */ new BigQueryRemoteFnResponse(List.of("Anant", "Damle"), null),
              /* factories= */ List.of(
                  base64Stub.deidentifyFactory(),
                  MappingDeidentifyTemplateCallerFactory.using(
                      Map.of(
                          "projects/test-project-id/locations/test-region1/deidentifyTemplates/template2",
                          jsonToProto(
                              loadResourceAsString(
                                  "single_surrogate_record_primitive_type_transform_deid_template.json"),
                              DeidentifyTemplate.class))),
                  VerifyingReidentifyCallerFactory.withExpectedRequest(
                          jsonToProto(
                              loadResourceAsString(
                                  "single_surrogate_record_primitive_type_transform_reid_with_inspect_template_request.json"),
                              ReidentifyContentRequest.class))
                      .withReidFactory(base64Stub.reidentifyFactory()))
            })
        .add(
            new Object[] {
              /* testName= */ "DLP reidentify Unsupported Transformation",
              /* testRequestJson= */ testRequest(
                  Map.of(
                      "mode",
                      "reidentify",
                      "algo",
                      "dlp",
                      "dlp-deid-template",
                      "projects/test-project-id/locations/test-region1/deidentifyTemplates/template2"),
                  List.of("QW5hbnQ="),
                  List.of("RGFtbGU=")),
              /* expectedResult= */ new BigQueryRemoteFnResponse(
                  null, "Unsupported ReId Primitive Transform CRYPTO_HASH_CONFIG"),
              /* factories= */ List.of(
                  base64Stub.deidentifyFactory(),
                  MappingDeidentifyTemplateCallerFactory.using(
                      Map.of(
                          "projects/test-project-id/locations/test-region1/deidentifyTemplates/template2",
                          jsonToProto(
                              loadResourceAsString(
                                  "non_reversabe_transformation_deid_template.json"),
                              DeidentifyTemplate.class))))
            })
        .build();
  }

  @TestConfiguration
  @Profile("test")
  public static class TestDlpClientFactoryConfiguration {
    private List<ApiFutureFactory<?, ?>> factories;

    @Bean
    public DlpClientFactory testDlpClientFactory() {
      return () -> DlpServiceClient.create(PatchyDlpStub.using(factories));
    }
  }

  private static String loadResourceAsString(String sourcePath) throws IOException {
    try (var reader =
        new BufferedReader(
            new InputStreamReader(
                Resources.getResource(sourcePath).openStream(), StandardCharsets.UTF_8))) {
      return reader.lines().collect(Collectors.joining("\n"));
    }
  }
}
