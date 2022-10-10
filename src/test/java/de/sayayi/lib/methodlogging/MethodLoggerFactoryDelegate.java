package de.sayayi.lib.methodlogging;

import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;


final class MethodLoggerFactoryDelegate implements MethodLoggerFactory
{
  @Setter
  private MethodLoggerFactory factory;


  @Override
  public @NotNull MethodLogger from(Field loggerField, Object obj) {
    return factory.from(loggerField, obj);
  }
}