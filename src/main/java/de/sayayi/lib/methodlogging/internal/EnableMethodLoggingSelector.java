package de.sayayi.lib.methodlogging.internal;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.AutoProxyRegistrar;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;


public final class EnableMethodLoggingSelector implements ImportSelector
{
  @Override
  public String @NotNull [] selectImports(@NotNull AnnotationMetadata importingClassMetadata)
  {
    return new String[] {
      AutoProxyRegistrar.class.getName(),
      ProxyMethodLoggingConfiguration.class.getName()
    };
  }
}