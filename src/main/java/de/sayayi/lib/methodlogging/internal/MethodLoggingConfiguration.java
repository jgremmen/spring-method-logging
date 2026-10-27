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

import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;


/**
 * Base configuration providing the beans shared by the proxy and AspectJ based method logging: the
 * {@link AnnotationMethodLoggingSource} and the {@link MethodLoggingInterceptor}. A {@link MethodLoggingConfigurer}
 * bean is optional; without it, the configurer defaults are used. The configurer bean is resolved lazily.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see ProxyMethodLoggingConfiguration
 * @see AspectJMethodLoggingConfiguration
 */
@Configuration(proxyBeanMethods = false)
@Role(ROLE_INFRASTRUCTURE)
@SuppressWarnings("SpringFacetCodeInspection")
abstract class MethodLoggingConfiguration implements ImportAware
{
  /** Attributes of the {@code @EnableMethodLogging} annotation of the importing configuration class. */
  AnnotationAttributes enableMethodLogging;


  /**
   * Reads the {@code @EnableMethodLogging} attributes of the importing configuration class.
   *
   * @param importMetadata  annotation metadata of the importing class
   *
   * @throws IllegalArgumentException  if the importing class is not annotated with {@code @EnableMethodLogging}
   */
  @Override
  public void setImportMetadata(AnnotationMetadata importMetadata)
  {
    enableMethodLogging = AnnotationAttributes.fromMap(
        importMetadata.getAnnotationAttributes(EnableMethodLogging.class.getName()));

    if (enableMethodLogging == null)
    {
      throw new IllegalArgumentException("@EnableMethodLogging is not present on importing class " +
          importMetadata.getClassName());
    }
  }


  /**
   * Creates the source analyzing the methods with method logging. The configurer bean is resolved when the first method
   * with method logging is analyzed or once all singleton beans have been created, whichever comes first; if there is
   * none, the configurer defaults are used.
   *
   * @param methodLoggingConfigurer  provider of the optional configurer bean
   *
   * @return  method logging source, never {@code null}
   *
   * @since 0.6.0
   */
  @Bean @Role(ROLE_INFRASTRUCTURE)
  AnnotationMethodLoggingSource internalAnnotationMethodLoggingSource(
      ObjectProvider<MethodLoggingConfigurer> methodLoggingConfigurer)
  {
    // resolve the configurer lazily, so it is not created together with the method logging infrastructure
    return new AnnotationMethodLoggingSource(
        () -> methodLoggingConfigurer.getIfAvailable(() -> new MethodLoggingConfigurer() {}),
        isInvokedMethodResolved());
  }


  /**
   * Tells whether the method passed to the method logging interceptor is resolved to the most specific method of the
   * target class. This is required for proxies, which pass the invoked (e.g. interface) method, but not for AspectJ,
   * which passes the executed method.
   *
   * @return  {@code true} if the invoked method is resolved to the most specific method, {@code false} otherwise
   *
   * @since 0.6.0
   */
  abstract boolean isInvokedMethodResolved();


  /**
   * Creates the method interceptor performing the method logging.
   *
   * @param annotationMethodLoggingSource  method logging source providing the method logging definitions
   *
   * @return  method logging interceptor, never {@code null}
   *
   * @since 0.6.0
   */
  @Bean @Role(ROLE_INFRASTRUCTURE)
  MethodLoggingInterceptor internalMethodLoggingInterceptor(
      AnnotationMethodLoggingSource annotationMethodLoggingSource) {
    return new MethodLoggingInterceptor(annotationMethodLoggingSource);
  }
}
