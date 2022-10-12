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
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
@Configuration
@Role(ROLE_INFRASTRUCTURE)
@SuppressWarnings("SpringFacetCodeInspection")
public class MethodLoggingConfiguration implements ImportAware
{
  private AnnotationAttributes enableMethodLogging;


  @Override
  public void setImportMetadata(AnnotationMetadata importMetadata)
  {
    enableMethodLogging = AnnotationAttributes.fromMap(
        importMetadata.getAnnotationAttributes(EnableMethodLogging.class.getName(), false));

    if (enableMethodLogging == null)
    {
      throw new IllegalArgumentException("@EnableMethodLogging is not present on importing class " +
          importMetadata.getClassName());
    }
  }


  @Bean @Role(ROLE_INFRASTRUCTURE)
  public AnnotationMethodLoggingSource internalAnnotationMethodLoggingSource(
      @Autowired(required = false) MethodLoggingConfigurer methodLoggingConfigurer)
  {
    return new AnnotationMethodLoggingSource(
        methodLoggingConfigurer == null ? new MethodLoggingConfigurer() {} : methodLoggingConfigurer);
  }


  @Bean @Role(ROLE_INFRASTRUCTURE)
  public BeanFactoryMethodLoggingAdvisor internalMethodLoggingAdvisor(
      AnnotationMethodLoggingSource annotationMethodLoggingSource)
  {
    val advisor = new BeanFactoryMethodLoggingAdvisor(annotationMethodLoggingSource);

    advisor.setOrder(enableMethodLogging.<Integer>getNumber("order"));

    return advisor;
  }


  @Bean @Role(ROLE_INFRASTRUCTURE)
  public MethodLoggingInterceptor internalMethodLoggingInterceptor(
      AnnotationMethodLoggingSource annotationMethodLoggingSource) {
    return new MethodLoggingInterceptor(annotationMethodLoggingSource);
  }
}