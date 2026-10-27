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
 * @since 0.6.0
 */
final class JoinPointMethodInvocation implements MethodInvocation
{
  /** Method execution join point, proceeding with the method execution. */
  private final ProceedingJoinPoint joinPoint;

  /** Executed method, as declared by the class of the join point. */
  private final Method method;


  /**
   * Creates a method invocation for the given method execution join point.
   *
   * @param joinPoint  method execution join point, not {@code null}
   */
  JoinPointMethodInvocation(@NotNull ProceedingJoinPoint joinPoint)
  {
    this.joinPoint = joinPoint;
    this.method = ((MethodSignature)joinPoint.getSignature()).getMethod();
  }


  /**
   * Returns the executed method, as declared by the class of the join point.
   *
   * @return  executed method, never {@code null}
   */
  @Override
  public @NotNull Method getMethod() {
    return method;
  }


  /**
   * Returns the arguments passed to the executed method.
   *
   * @return  method arguments, never {@code null}. Elements are {@code null} for {@code null} arguments
   */
  @Override
  public Object @NotNull [] getArguments() {
    return joinPoint.getArgs();
  }


  /**
   * Proceeds with the execution of the method.
   *
   * @return  result of the method execution
   *
   * @throws Throwable  if the method execution throws an exception
   */
  @Override
  public Object proceed() throws Throwable {
    return joinPoint.proceed();
  }


  /**
   * Returns the object the method is executed on.
   *
   * @return  object executing the method
   */
  @Override
  public Object getThis() {
    return joinPoint.getThis();
  }


  /**
   * Returns the static part of this invocation, which is the executed method.
   *
   * @return  executed method, never {@code null}
   */
  @Override
  public @NotNull AccessibleObject getStaticPart() {
    return method;
  }
}
