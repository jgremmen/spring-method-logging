/*
 * Copyright 2022 Jeroen Gremmen
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
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Method;

import static java.lang.reflect.Modifier.ABSTRACT;
import static java.lang.reflect.Modifier.STATIC;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
@SuppressWarnings("SpringJavaAutowiredMembersInspection")
public final class BeanFactoryMethodLoggingAdvisor extends AbstractBeanFactoryPointcutAdvisor
{
  private final @NotNull AnnotationMethodLoggingSource annotationMethodLoggingSource;


  BeanFactoryMethodLoggingAdvisor(@NotNull AnnotationMethodLoggingSource annotationMethodLoggingSource) {
    this.annotationMethodLoggingSource = annotationMethodLoggingSource;
  }


  @Autowired
  public void setAdvice(MethodLoggingInterceptor methodLoggingInterceptor) {
    super.setAdvice(methodLoggingInterceptor);
  }


  @Override
  public @NotNull Pointcut getPointcut()
  {
    return new StaticMethodMatcherPointcut() {
      @Override
      public boolean matches(@NotNull Method method, @NotNull Class<?> targetClass)
      {
        return method.getDeclaringClass() != Object.class &&
            (method.getModifiers() & (STATIC | ABSTRACT)) == 0 &&
            annotationMethodLoggingSource.getMethodDefinition(method, targetClass) != null;
      }
    };
  }
}