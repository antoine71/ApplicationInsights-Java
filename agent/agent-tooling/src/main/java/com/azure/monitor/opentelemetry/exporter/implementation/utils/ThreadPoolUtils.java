/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.azure.monitor.opentelemetry.exporter.implementation.utils;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class ThreadPoolUtils {

  /**
   * {@code poolName} will be appended with a hyphen and the unique name.
   *
   * @param clazz The class holding the thread pool
   * @param uniqueId The identifier of the instance of {@code clazz}
   */
  public static ThreadFactory createDaemonThreadFactory(Class<?> clazz, String uniqueId) {
    return createNamedDaemonThreadFactory(String.format("%s_%s", clazz.getSimpleName(), uniqueId));
  }

  public static ThreadFactory createDaemonThreadFactory(Class<?> clazz) {
    return createNamedDaemonThreadFactory(clazz.getSimpleName());
  }

  public static ThreadFactory createNamedDaemonThreadFactory(String poolName) {
    return new ThreadFactory() {
      private final AtomicInteger threadId = new AtomicInteger();

      @Override
      public Thread newThread(Runnable r) {
        Thread thread = new Thread(r);
        thread.setName(String.format("%s-%d", poolName, threadId.getAndIncrement()));
        thread.setDaemon(true);
        return thread;
      }
    };
  }

  private ThreadPoolUtils() {}
}