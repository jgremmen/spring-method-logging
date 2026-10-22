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

import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;


/**
 * Adapts an AspectJ method execution join point to an AOP Alliance method invocation, so the method logging
 * interceptor can be used for both proxy and AspectJ based method logging.
 *
 * @author Jeroen Gremmen
 * @since 0.5.1
 */
final class JoinPointMethodInvocation implements MethodInvocation
{
  private final ProceedingJoinPoint joinPoint;
  private final Method method;


  JoinPointMethodInvocation(@NotNull ProceedingJoinPoint joinPoint)
  {
    this.joinPoint = joinPoint;
    this.method = ((MethodSignature)joinPoint.getSignature()).getMethod();
  }


  @Override
  public @NotNull Method getMethod() {
    return method;
  }


  @Override
  public @NotNull Object @NotNull [] getArguments() {
    return joinPoint.getArgs();
  }


  @Override
  public Object proceed() throws Throwable {
    return joinPoint.proceed();
  }


  @Override
  public Object getThis() {
    return joinPoint.getThis();
  }


  @Override
  public @NotNull AccessibleObject getStaticPart() {
    return method;
  }
}
