/*
 * Copyright 2026 Jeroen Gremmen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.sayayi.lib.methodlogging.internal;

import de.sayayi.lib.methodlogging.aspectj.MethodLoggingAspect;
import org.aopalliance.intercept.MethodInterceptor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;


/**
 * Holds the method interceptor used by the {@link MethodLoggingAspect} for AspectJ based method logging. The
 * interceptor is set and removed by the {@link AspectJMethodLoggingConfiguration} of each application context which
 * configures the aspect. The interceptor of the most recently created context which is still open is used; when that
 * context is closed, the interceptor of the context created before it is used again.
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
public final class AspectJMethodInterceptor
{
  /** Method interceptors set by the open application contexts, the interceptor in use first. */
  private static final Deque<MethodInterceptor> METHOD_INTERCEPTORS = new ConcurrentLinkedDeque<>();


  /** Prevents instantiation, as this class provides static methods only. */
  private AspectJMethodInterceptor() {
  }


  /**
   * Returns the method interceptor performing the method logging.
   *
   * @return  method logging interceptor or {@code null} if no application context has configured the aspect
   */
  @Contract(pure = true)
  public static MethodInterceptor get() {
    return METHOD_INTERCEPTORS.peekFirst();
  }


  /**
   * Sets the method interceptor performing the method logging. The interceptor set before is kept and used again once
   * {@code methodInterceptor} is removed.
   *
   * @param methodInterceptor  method logging interceptor, not {@code null}
   */
  static void set(@NotNull MethodInterceptor methodInterceptor) {
    METHOD_INTERCEPTORS.addFirst(methodInterceptor);
  }


  /**
   * Removes {@code methodInterceptor}. If it is the method interceptor performing the method logging, the interceptor
   * set before it is used again; otherwise the method interceptor performing the method logging is kept.
   *
   * @param methodInterceptor  method logging interceptor to remove, not {@code null}
   */
  static void remove(@NotNull MethodInterceptor methodInterceptor) {
    METHOD_INTERCEPTORS.removeFirstOccurrence(methodInterceptor);
  }
}
