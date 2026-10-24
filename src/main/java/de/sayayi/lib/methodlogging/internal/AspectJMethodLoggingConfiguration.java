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
import org.aspectj.lang.Aspects;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;


/**
 * Configuration for AspectJ based method logging, configuring the {@link MethodLoggingAspect} singleton with the method
 * logging interceptor. The interceptor is removed from the aspect when the application context is closed, unless
 * another context has configured the aspect in the meantime.
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@Configuration(proxyBeanMethods = false)
@Role(ROLE_INFRASTRUCTURE)
@SuppressWarnings("SpringFacetCodeInspection")
public class AspectJMethodLoggingConfiguration extends MethodLoggingConfiguration
{
  @Bean @Role(ROLE_INFRASTRUCTURE)
  MethodLoggingAspectRegistration internalMethodLoggingAspectRegistration(
      MethodLoggingInterceptor methodLoggingInterceptor) {
    return new MethodLoggingAspectRegistration(methodLoggingInterceptor);
  }




  /**
   * Sets the method interceptor on the aspect singleton and removes it when the bean is destroyed.
   */
  static final class MethodLoggingAspectRegistration implements DisposableBean
  {
    private final @NotNull MethodLoggingAspect aspect;
    private final @NotNull MethodInterceptor methodInterceptor;


    MethodLoggingAspectRegistration(@NotNull MethodInterceptor methodInterceptor)
    {
      this.methodInterceptor = methodInterceptor;

      aspect = Aspects.aspectOf(MethodLoggingAspect.class);
      aspect.setMethodInterceptor(methodInterceptor);
    }


    @Override
    public void destroy() {
      aspect.removeMethodInterceptor(methodInterceptor);
    }
  }
}
