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

import java.util.concurrent.atomic.AtomicReference;


/**
 * Holds the method interceptor used by the {@link MethodLoggingAspect} for AspectJ based method logging. The
 * interceptor is set and removed by the {@link AspectJMethodLoggingConfiguration} of the application context which
 * configures the aspect; the context created last configures the aspect.
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
public final class AspectJMethodInterceptor
{
  private static final AtomicReference<MethodInterceptor> METHOD_INTERCEPTOR = new AtomicReference<>();


  private AspectJMethodInterceptor() {
  }


  /**
   * Returns the method interceptor performing the method logging.
   *
   * @return  method logging interceptor or {@code null} if no application context has configured the aspect
   */
  @Contract(pure = true)
  public static MethodInterceptor get() {
    return METHOD_INTERCEPTOR.get();
  }


  /**
   * Sets the method interceptor performing the method logging, replacing any interceptor set before.
   *
   * @param methodInterceptor  method logging interceptor, not {@code null}
   */
  static void set(@NotNull MethodInterceptor methodInterceptor) {
    METHOD_INTERCEPTOR.set(methodInterceptor);
  }


  /**
   * Removes the method interceptor performing the method logging, if it is {@code methodInterceptor}; if another method
   * interceptor is set, it is kept.
   *
   * @param methodInterceptor  method logging interceptor to remove, not {@code null}
   */
  static void remove(@NotNull MethodInterceptor methodInterceptor) {
    METHOD_INTERCEPTOR.compareAndSet(methodInterceptor, null);
  }
}
