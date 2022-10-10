package de.sayayi.lib.methodlogging.internal;

import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import lombok.val;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;


@Configuration
@Role(ROLE_INFRASTRUCTURE)
@SuppressWarnings("SpringFacetCodeInspection")
public class ProxyMethodLoggingConfiguration implements ImportAware
{
  private AnnotationAttributes enableMethodLogging;


  @Bean @Role(ROLE_INFRASTRUCTURE)
  public BeanFactoryMethodLoggingAdvisor internalMethodLoggingAdvisor()
  {
    val advisor = new BeanFactoryMethodLoggingAdvisor();

    advisor.setOrder(enableMethodLogging.<Integer>getNumber("order"));

    return advisor;
  }


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
}