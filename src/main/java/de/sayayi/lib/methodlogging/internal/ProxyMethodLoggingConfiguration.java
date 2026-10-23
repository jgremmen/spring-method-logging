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


    MethodLoggingAdvisor(@NotNull MethodLoggingPointcut pointcut) {
      this.pointcut = pointcut;
    }


    @Override
    public @NotNull Pointcut getPointcut() {
      return pointcut;
    }


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


    MethodLoggingPointcut(@NotNull AnnotationMethodLoggingSource annotationMethodLoggingSource)
    {
      this.annotationMethodLoggingSource = annotationMethodLoggingSource;

      deferredMethods = newKeySet();
      deferAnalysis = true;
    }


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
     * failure is passed on, so the application context fails to start.
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


    @Override
    public boolean equals(Object o)
    {
      return this == o || o instanceof MethodLoggingPointcut that &&
          annotationMethodLoggingSource == that.annotationMethodLoggingSource;
    }


    @Override
    public int hashCode() {
      return annotationMethodLoggingSource.hashCode();
    }


    @Override
    public String toString() {
      return getClass().getName() + ": " + annotationMethodLoggingSource;
    }
  }
}
