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
import org.springframework.aop.support.AbstractBeanFactoryPointcutAdvisor;
import org.springframework.aop.support.DefaultBeanFactoryPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

import java.lang.reflect.Method;

import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;


/**
 * Configuration for proxy based method logging, registering an advisor applying the method logging interceptor to the
 * methods annotated with {@code @MethodLogging}.
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
  AbstractBeanFactoryPointcutAdvisor internalMethodLoggingAdvisor(
      AnnotationMethodLoggingSource annotationMethodLoggingSource,
      MethodLoggingInterceptor methodLoggingInterceptor)
  {
    final var advisor = new DefaultBeanFactoryPointcutAdvisor();

    advisor.setPointcut(new MethodLoggingPointcut(annotationMethodLoggingSource));
    advisor.setAdvice(methodLoggingInterceptor);
    advisor.setOrder(enableMethodLogging.<Integer>getNumber("order"));

    return advisor;
  }




  /**
   * Pointcut matching the methods with a method logging definition.
   */
  static final class MethodLoggingPointcut extends StaticMethodMatcherPointcut
  {
    private final @NotNull AnnotationMethodLoggingSource annotationMethodLoggingSource;


    MethodLoggingPointcut(@NotNull AnnotationMethodLoggingSource annotationMethodLoggingSource) {
      this.annotationMethodLoggingSource = annotationMethodLoggingSource;
    }


    @Override
    public boolean matches(@NotNull Method method, @NotNull Class<?> targetClass) {
      return annotationMethodLoggingSource.getMethodDefinition(method, targetClass) != null;
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
