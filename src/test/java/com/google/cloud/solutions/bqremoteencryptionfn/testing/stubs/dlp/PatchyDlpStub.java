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

import com.google.api.core.ApiFuture;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.cloud.dlp.v2.stub.DlpServiceStub;
import com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.BaseUnaryApiFuture.ApiFutureFactory;
import com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.TestingBackgroundResource;
import com.google.common.collect.ImmutableList;
import com.google.privacy.dlp.v2.DeidentifyContentRequest;
import com.google.privacy.dlp.v2.DeidentifyContentResponse;
import com.google.privacy.dlp.v2.DeidentifyTemplate;
import com.google.privacy.dlp.v2.GetDeidentifyTemplateRequest;
import com.google.privacy.dlp.v2.ReidentifyContentRequest;
import com.google.privacy.dlp.v2.ReidentifyContentResponse;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class PatchyDlpStub extends DlpServiceStub implements Serializable {
  protected final TestingBackgroundResource testingBackgroundResource =
      new TestingBackgroundResource();

  protected final ImmutableList<ApiFutureFactory<?, ?>> callableFactories;

  public PatchyDlpStub(List<ApiFutureFactory<?, ?>> callableFactories) {
    this.callableFactories = ImmutableList.copyOf(callableFactories);
  }

  public static PatchyDlpStub using(List<ApiFutureFactory<?, ?>> callableFactories) {
    return new PatchyDlpStub(callableFactories);
  }

  private static final class PatchyCallable<X, Y> extends UnaryCallable<X, Y>
      implements Serializable {
    private final ApiFutureFactory<X, Y> factory;

    public PatchyCallable(ApiFutureFactory<X, Y> factory) {
      this.factory = factory;
    }

    @Override
    public ApiFuture<Y> futureCall(X request, ApiCallContext context) {
      return factory.create(request, context);
    }
  }

  @SuppressWarnings("unchecked") // Checks are done in PatchyUnaryCallable#matchIO
  protected <I, O> UnaryCallable<I, O> findCallable(
      Class<I> inputClass,
      Class<O> outputClass,
      Supplier<UnaryCallable<I, O>> defaultCallableGetter) {
    if (testingBackgroundResource.isShutdown() || testingBackgroundResource.isTerminated()) {
      throw new RuntimeException("Stub already shutdown or terminated");
    }

    return callableFactories.stream()
        .filter(factory -> factory.matchIO(inputClass, outputClass))
        .findFirst()
        .map(PatchyCallable::new)
        .map(p -> (UnaryCallable<I, O>) p)
        .orElseGet(defaultCallableGetter);
  }

  @Override
  public UnaryCallable<DeidentifyContentRequest, DeidentifyContentResponse>
      deidentifyContentCallable() {
    return findCallable(
        DeidentifyContentRequest.class,
        DeidentifyContentResponse.class,
        super::deidentifyContentCallable);
  }

  @Override
  public UnaryCallable<ReidentifyContentRequest, ReidentifyContentResponse>
      reidentifyContentCallable() {
    return findCallable(
        ReidentifyContentRequest.class,
        ReidentifyContentResponse.class,
        super::reidentifyContentCallable);
  }

  @Override
  public UnaryCallable<GetDeidentifyTemplateRequest, DeidentifyTemplate>
      getDeidentifyTemplateCallable() {
    return findCallable(
        GetDeidentifyTemplateRequest.class,
        DeidentifyTemplate.class,
        super::getDeidentifyTemplateCallable);
  }

  @Override
  public void shutdown() {
    testingBackgroundResource.shutdown();
  }

  @Override
  public boolean isShutdown() {
    return testingBackgroundResource.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return testingBackgroundResource.isTerminated();
  }

  @Override
  public void shutdownNow() {
    testingBackgroundResource.shutdownNow();
  }

  @Override
  public boolean awaitTermination(long l, TimeUnit timeUnit) {
    return testingBackgroundResource.awaitTermination(l, timeUnit);
  }

  @Override
  public void close() {
    testingBackgroundResource.close();
  }
}
