package de.sayayi.lib.methodlogging;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;


public interface MethodLoggerFactory
{
  @Contract(pure = true)
  @NotNull MethodLogger from(Field loggerField, Object obj);
}