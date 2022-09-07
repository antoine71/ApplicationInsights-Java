// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.applicationinsights.agent.internal.sampling;

import com.azure.monitor.opentelemetry.exporter.implementation.AiSemanticAttributes;
import com.azure.monitor.opentelemetry.exporter.implementation.SamplingScoreGeneratorV2;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.List;
import javax.annotation.Nullable;

// this sampler does two things:
// * implements same trace id hashing algorithm so that traces are sampled the same across multiple
//   nodes when some of those nodes are being monitored by other Application Insights SDKs (and 2.x
//   Java SDK)
// * adds item count to span attribute if it is sampled
public class AiSampler implements Sampler {

  private final boolean localParentBased;
  private final SamplingPercentage samplingPercentage;
  private final Cache<Long, SamplingResult> recordAndSampleWithItemCountMap = Cache.bounded(100);

  public AiSampler(SamplingPercentage samplingPercentage) {
    this(samplingPercentage, true);
  }

  public AiSampler(SamplingPercentage samplingPercentage, boolean localParentBased) {
    this.samplingPercentage = samplingPercentage;
    this.localParentBased = localParentBased;
  }

  @Override
  public SamplingResult shouldSample(
      Context parentContext,
      String traceId,
      String name,
      SpanKind spanKind,
      Attributes attributes,
      List<LinkData> parentLinks) {

    if (localParentBased) {
      SamplingResult samplingResult = handleLocalParent(parentContext);
      if (samplingResult != null) {
        return samplingResult;
      }
    }

    double sp = samplingPercentage.get();

    if (sp == 0) {
      return SamplingResult.drop();
    }

    if (!shouldRecordAndSample(traceId, sp)) {
      return SamplingResult.drop();
    }

    // sp cannot be 0 here
    long itemCount = Math.round(100.0 / sp);
    SamplingResult samplingResult = recordAndSampleWithItemCountMap.get(itemCount);
    if (samplingResult == null) {
      samplingResult = new RecordAndSampleWithItemCount(itemCount);
      recordAndSampleWithItemCountMap.put(itemCount, samplingResult);
    }
    return samplingResult;
  }

  @Nullable
  private static SamplingResult handleLocalParent(Context parentContext) {
    // remote parent-based sampling messes up item counts since item count is not propagated in
    // tracestate (yet), but local parent-based sampling doesn't have this issue since we are
    // propagating item count locally
    Span parentSpan = Span.fromContext(parentContext);
    SpanContext parentSpanContext = parentSpan.getSpanContext();
    if (!parentSpanContext.isValid() || parentSpanContext.isRemote()) {
      return null;
    }
    if (!parentSpanContext.isSampled()) {
      return SamplingResult.drop();
    }
    if (parentSpan instanceof ReadableSpan) {
      Long itemCount = ((ReadableSpan) parentSpan).getAttribute(AiSemanticAttributes.ITEM_COUNT);
      if (itemCount != null) {
        return new RecordAndSampleWithItemCount(itemCount);
      }
    }
    return null;
  }

  public static boolean shouldRecordAndSample(String traceId, double percentage) {
    if (percentage == 100) {
      // optimization, no need to calculate score
      return true;
    }
    if (percentage == 0) {
      // optimization, no need to calculate score
      return false;
    }
    return SamplingScoreGeneratorV2.getSamplingScore(traceId) < percentage;
  }

  @Override
  public String getDescription() {
    return "AiSampler";
  }

  private static class RecordAndSampleWithItemCount implements SamplingResult {

    private final Attributes attributes;

    RecordAndSampleWithItemCount(long itemCount) {
      attributes = Attributes.builder().put(AiSemanticAttributes.ITEM_COUNT, itemCount).build();
    }

    @Override
    public SamplingDecision getDecision() {
      return SamplingDecision.RECORD_AND_SAMPLE;
    }

    @Override
    public Attributes getAttributes() {
      return attributes;
    }
  }
}