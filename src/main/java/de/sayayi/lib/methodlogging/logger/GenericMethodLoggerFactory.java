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
package de.sayayi.lib.methodlogging.logger;

import de.sayayi.lib.methodlogging.MethodLogger;
import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
public final class GenericMethodLoggerFactory implements MethodLoggerFactory
{
  private final Map<Field,LoggerType> fieldLoggerTypeMap = new ConcurrentHashMap<>();


  @Override
  public @NotNull MethodLogger from(Field loggerField, @NotNull Object obj)
  {
    switch(fieldLoggerTypeMap.computeIfAbsent(
        requireNonNull(loggerField, () ->
            "Class " + obj.getClass() + " or one of its superclasses must provide a logger field"),
        this::from_type))
    {
      case JUL:
        return JULMethodLogger.from(loggerField, obj);

      case LOG4J2:
        return Log4J2MethodLogger.from(loggerField, obj);

      case SLF4J:
        return Slf4JMethodLogger.from(loggerField, obj);

      default:
        throw new IllegalStateException("unknown logger type for type " + loggerField.getType());
    }
  }


  @Contract(pure = true)
  private @NotNull LoggerType from_type(@NotNull Field loggerField)
  {
    switch(loggerField.getType().getName())
    {
      case "java.util.logging.Logger":
        return LoggerType.JUL;

      case "org.apache.logging.log4j.Logger":
        return LoggerType.LOG4J2;

      case "org.slf4j.Logger":
        return LoggerType.SLF4J;
    }

    throw new IllegalStateException("unknown logger class: " + loggerField.getType());
  }




  private enum LoggerType {
    JUL, LOG4J2, SLF4J
  }
}