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


import com.google.cloud.dlp.v2.DlpServiceClient;
import com.google.cloud.dlp.v2.DlpServiceSettings;
import com.google.cloud.solutions.bqremoteencryptionfn.fns.dlp.DlpFn.DlpClientFactory;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
@EnableConfigurationProperties
@Configuration
public class BqTransformFnApp {

  public static void main(String[] args) {
    SpringApplication.run(BqTransformFnApp.class, args);
  }

  @Bean
  @Profile("!test")
  public DlpClientFactory defaultDlpClientFactory(UserAgentHeaderProvider userAgentHeaderProvider) {
    return () ->
        DlpServiceClient.create(
            DlpServiceSettings.newBuilder().setHeaderProvider(userAgentHeaderProvider).build());
  }

  // Enable Keep-Alive HTTP Response header
  @Bean
  public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
    return (tomcat) ->
        tomcat.addConnectorCustomizers(
            (connector) -> {
              if (connector.getProtocolHandler()
                  instanceof AbstractHttp11Protocol<?> protocolHandler) {
                protocolHandler.setKeepAliveTimeout(300000);
                protocolHandler.setMaxKeepAliveRequests(100);
                protocolHandler.setUseKeepAliveResponseHeader(true);
              }
            });
  }
}
