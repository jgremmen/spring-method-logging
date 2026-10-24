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

import org.jetbrains.annotations.NotNull;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractBeanFactoryPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

import java.lang.reflect.Method;
import java.util.Map.Entry;
import java.util.Set;

import static java.util.Map.entry;
import static java.util.concurrent.ConcurrentHashMap.newKeySet;
import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;


/**
 * Configuration for proxy based method logging, registering an advisor applying the method logging interceptor to the
 * methods annotated with {@code @MethodLogging}.
 * <p>
 * Methods matched while the singleton beans are created are analyzed after all singleton beans have been created. So
 * the configurer is not created during the post-processing of a bean and may depend on beans with method logging.
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@Configuration(proxyBeanMethods = false)
@Role(ROLE_INFRASTRUCTURE)
@SuppressWarnings("SpringFacetCodeInspection")
public class ProxyMethodLoggingConfiguration extends MethodLoggingConfiguration
{
  /**
   * {@inheritDoc}
   * <p>
   * The invoked method is resolved: proxies pass the invoked method, which may be an interface or superclass method.
   *
   * @return  {@code true}
   */
  @Override
  boolean isInvokedMethodResolved() {
    return true;
  }


  /**
   * Creates the advisor applying the method logging interceptor to the methods with method logging, ordered as
   * configured by {@link de.sayayi.lib.methodlogging.annotation.EnableMethodLogging#order() @EnableMethodLogging}.
   *
   * @param annotationMethodLoggingSource  method logging source, not {@code null}
   * @param methodLoggingInterceptor       method logging interceptor, not {@code null}
   *
   * @return  method logging advisor, never {@code null}
   */
  @Bean @Role(ROLE_INFRASTRUCTURE)
  MethodLoggingAdvisor internalMethodLoggingAdvisor(
      AnnotationMethodLoggingSource annotationMethodLoggingSource,
      MethodLoggingInterceptor methodLoggingInterceptor)
  {
    final var advisor = new MethodLoggingAdvisor(new MethodLoggingPointcut(annotationMethodLoggingSource));

    advisor.setAdvice(methodLoggingInterceptor);
    advisor.setOrder(enableMethodLogging.<Integer>getNumber("order"));

    return advisor;
  }




  /**
   * Advisor analyzing the deferred methods of its pointcut after all singleton beans have been created.
   */
  static final class MethodLoggingAdvisor extends AbstractBeanFactoryPointcutAdvisor
      implements SmartInitializingSingleton
  {
    private final @NotNull MethodLoggingPointcut pointcut;


    /**
     * Creates the advisor for the given pointcut.
     *
     * @param pointcut  method logging pointcut, not {@code null}
     */
    MethodLoggingAdvisor(@NotNull MethodLoggingPointcut pointcut) {
      this.pointcut = pointcut;
    }


    /**
     * Returns the method logging pointcut.
     *
     * @return  method logging pointcut, never {@code null}
     */
    @Override
    public @NotNull Pointcut getPointcut() {
      return pointcut;
    }


    /**
     * Analyzes the methods matched by the pointcut while the singleton beans were created.
     *
     * @throws IllegalStateException  if the method logging configuration of a method is invalid
     */
    @Override
    public void afterSingletonsInstantiated() {
      pointcut.analyseDeferredMethods();
    }
  }




  /**
   * Pointcut matching the methods with a method logging definition.
   * <p>
   * Until {@link #analyseDeferredMethods()} is invoked, methods are matched by their {@code @MethodLogging} annotation
   * only and analyzed later. Analyzing a method requires the configurer, which must not be created while a bean is
   * being post-processed: the configurer may depend on that bean.
   */
  static final class MethodLoggingPointcut extends StaticMethodMatcherPointcut
  {
    private final @NotNull AnnotationMethodLoggingSource annotationMethodLoggingSource;
    private final @NotNull Set<Entry<Method,Class<?>>> deferredMethods;
    private volatile boolean deferAnalysis;


    /**
     * Creates the pointcut, deferring the analysis of matched methods.
     *
     * @param annotationMethodLoggingSource  method logging source, not {@code null}
     */
    MethodLoggingPointcut(@NotNull AnnotationMethodLoggingSource annotationMethodLoggingSource)
    {
      this.annotationMethodLoggingSource = annotationMethodLoggingSource;

      deferredMethods = newKeySet();
      deferAnalysis = true;
    }


    /**
     * Tells whether {@code method} invoked on an instance of {@code targetClass} has method logging. While the analysis
     * is deferred, a method annotated with {@code @MethodLogging} matches and is analyzed later.
     *
     * @param method       method, possibly an interface method, not {@code null}
     * @param targetClass  target class, not {@code null}
     *
     * @return  {@code true} if the method has method logging, {@code false} otherwise
     */
    @Override
    public boolean matches(@NotNull Method method, @NotNull Class<?> targetClass)
    {
      if (!deferAnalysis)
        return annotationMethodLoggingSource.hasMethodLogging(method, targetClass);

      if (!annotationMethodLoggingSource.hasMethodLoggingAnnotation(method, targetClass))
        return false;

      deferredMethods.add(entry(method, targetClass));
      return true;
    }


    /**
     * Analyzes the methods matched so far and stops deferring the analysis of methods matched from now on. An analysis
     * failure is passed on, so the application context fails to start. If another thread is creating the configuration
     * at that time, the remaining methods are analyzed on first invocation instead.
     *
     * @throws IllegalStateException  if the method logging configuration of a method is invalid
     */
    void analyseDeferredMethods()
    {
      deferAnalysis = false;

      for(var deferredMethod: deferredMethods)
      {
        annotationMethodLoggingSource.getMethodDefinition(deferredMethod.getKey(), deferredMethod.getValue());
        deferredMethods.remove(deferredMethod);
      }
    }


    /**
     * Tells whether {@code o} is a method logging pointcut for the same method logging source.
     *
     * @param o  object to compare with
     *
     * @return  {@code true} if both pointcuts are equal, {@code false} otherwise
     */
    @Override
    public boolean equals(Object o)
    {
      return this == o || o instanceof MethodLoggingPointcut that &&
          annotationMethodLoggingSource == that.annotationMethodLoggingSource;
    }


    /**
     * Returns a hash code based on the method logging source.
     *
     * @return  hash code
     */
    @Override
    public int hashCode() {
      return annotationMethodLoggingSource.hashCode();
    }


    /**
     * Returns a string representation of this pointcut, naming the method logging source.
     *
     * @return  string representation, never {@code null}
     */
    @Override
    public String toString() {
      return getClass().getName() + ": " + annotationMethodLoggingSource;
    }
  }
}
