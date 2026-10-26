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
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;


/**
 * Configuration for AspectJ based method logging, configuring the {@link MethodLoggingAspect} singleton with the method
 * logging interceptor. The interceptor is removed from the aspect when the application context is closed, unless
 * another context has configured the aspect in the meantime.
 * <p>
 * Methods are analyzed on first invocation. The configuration is created once all singleton beans have been created, so
 * an unavailable configurer bean or an invalid configurer default fails the start of the application context.
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@Configuration(proxyBeanMethods = false)
@Role(ROLE_INFRASTRUCTURE)
@SuppressWarnings("SpringFacetCodeInspection")
public class AspectJMethodLoggingConfiguration extends MethodLoggingConfiguration
{
  /**
   * {@inheritDoc}
   * <p>
   * The invoked method is not resolved: AspectJ passes the executed method, which may be a superclass method invoked by
   * an overriding method ({@code super.method()}).
   *
   * @return  {@code false}
   */
  @Override
  boolean isInvokedMethodResolved() {
    return false;
  }


  /**
   * Configures the {@link MethodLoggingAspect} singleton with the method logging interceptor.
   *
   * @param annotationMethodLoggingSource  method logging source, not {@code null}
   * @param methodLoggingInterceptor       method logging interceptor, not {@code null}
   *
   * @return  aspect registration creating the configuration after all singleton beans have been created and removing
   *          the interceptor from the aspect when the application context is closed
   */
  @Bean @Role(ROLE_INFRASTRUCTURE)
  MethodLoggingAspectRegistration internalMethodLoggingAspectRegistration(
      AnnotationMethodLoggingSource annotationMethodLoggingSource,
      MethodLoggingInterceptor methodLoggingInterceptor) {
    return new MethodLoggingAspectRegistration(annotationMethodLoggingSource, methodLoggingInterceptor);
  }




  /**
   * Sets the method interceptor used by the aspect and removes it when the bean is destroyed. Once all singleton beans
   * have been created, the configuration is created.
   */
  static final class MethodLoggingAspectRegistration implements SmartInitializingSingleton, DisposableBean
  {
    /** Method logging source creating the configuration. */
    private final @NotNull AnnotationMethodLoggingSource annotationMethodLoggingSource;

    /** Method logging interceptor set as the method interceptor used by the aspect. */
    private final @NotNull MethodInterceptor methodInterceptor;


    /**
     * Creates the registration and sets {@code methodInterceptor} as the method interceptor used by the aspect.
     *
     * @param annotationMethodLoggingSource  method logging source, not {@code null}
     * @param methodInterceptor              method logging interceptor, not {@code null}
     *
     * @throws org.aspectj.lang.NoAspectBoundException  if the aspect is not available
     */
    MethodLoggingAspectRegistration(@NotNull AnnotationMethodLoggingSource annotationMethodLoggingSource,
                                    @NotNull MethodInterceptor methodInterceptor)
    {
      this.annotationMethodLoggingSource = annotationMethodLoggingSource;
      this.methodInterceptor = methodInterceptor;

      // fails if the AspectJ runtime is missing or the aspect has not been completed by the AspectJ compiler or weaver
      Aspects.aspectOf(MethodLoggingAspect.class);
      AspectJMethodInterceptor.set(methodInterceptor);
    }


    /**
     * Creates the configuration after all singleton beans have been created.
     *
     * @throws RuntimeException  if the configurer bean cannot be obtained or the configurer returns an invalid default
     */
    @Override
    public void afterSingletonsInstantiated() {
      annotationMethodLoggingSource.singletonsInstantiated();
    }


    /**
     * Removes the method interceptor from the aspect. If it is used by the aspect, the method interceptor of the
     * application context created before is used again.
     */
    @Override
    public void destroy() {
      AspectJMethodInterceptor.remove(methodInterceptor);
    }
  }
}
