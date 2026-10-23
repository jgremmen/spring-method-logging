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
package de.sayayi.lib.methodlogging.aspectj;

import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.aopalliance.intercept.MethodInterceptor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.AdviceMode;


/**
 * AspectJ aspect applying method logging to the execution of methods annotated with
 * {@link MethodLogging @MethodLogging}. It is used if method logging is enabled with
 * {@link EnableMethodLogging#mode() @EnableMethodLogging(mode = AdviceMode.ASPECTJ)}.
 * <p>
 * The aspect is applied either by load-time weaving (this library provides a {@code META-INF/aop.xml} declaring the
 * aspect) or by compile-time weaving, with this library on the aspect path. Contrary to proxy based method logging,
 * calls within the same class are logged as well.
 * <p>
 * Only non-static methods with a {@code @MethodLogging} annotation on the method itself are logged; annotations on
 * interface or superclass methods are not considered, as AspectJ does not inherit method annotations. Composed
 * annotations, which are meta-annotated with {@code @MethodLogging}, are not supported either. Until the aspect has
 * been configured by the Spring application context, annotated methods are invoked without logging.
 * <p>
 * The aspect is a singleton. If multiple application contexts enable method logging in AspectJ mode, the context
 * created last configures the aspect.
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 *
 * @see AdviceMode#ASPECTJ
 */
@Aspect
public class MethodLoggingAspect
{
  private volatile MethodInterceptor methodInterceptor;


  /**
   * Sets the method interceptor performing the method logging. This method is invoked by the method logging
   * configuration and is not intended to be used by applications.
   *
   * @param methodInterceptor  method logging interceptor or {@code null} to disable method logging
   */
  public void setMethodInterceptor(MethodInterceptor methodInterceptor) {
    this.methodInterceptor = methodInterceptor;
  }


  /**
   * Around advice for the execution of methods annotated with {@code @MethodLogging}.
   *
   * @param joinPoint  method execution join point, not {@code null}
   *
   * @return  result of the method execution
   *
   * @throws Throwable  if the method execution throws an exception
   */
  @Around("execution(!static * *(..)) && @annotation(de.sayayi.lib.methodlogging.annotation.MethodLogging)")
  public Object logMethod(@NotNull ProceedingJoinPoint joinPoint) throws Throwable
  {
    final var interceptor = methodInterceptor;

    return interceptor == null
        ? joinPoint.proceed()
        : interceptor.invoke(new JoinPointMethodInvocation(joinPoint));
  }
}
