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
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

import static java.util.logging.LogManager.getLogManager;
import static org.springframework.util.ClassUtils.isPresent;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
public class GenericMethodLoggerFactory extends AbstractMethodLoggerFactory
{
  private final @NotNull LoggerType loggerFactoryType;


  public GenericMethodLoggerFactory(boolean createLoggerOnNoField) {
    this(null, createLoggerOnNoField);
  }


  public GenericMethodLoggerFactory(ClassLoader classLoader, boolean createLoggerOnNoField)
  {
    super(createLoggerOnNoField);

    if (isPresent("org.slf4j.LoggerFactory", classLoader))
      loggerFactoryType = LoggerType.SLF4J;
    else if (isPresent("org.apache.logging.log4j.LogManager", classLoader))
      loggerFactoryType = LoggerType.LOG4J2;
    else if (isPresent("java.util.logging.LogManager", classLoader))
      loggerFactoryType = LoggerType.JUL;
    else
      throw new IllegalStateException("unable to detect logger factory");
  }


  @Override
  protected @NotNull MethodLogger createMethodLogger(@NotNull Class<?> clazz)
  {
    switch(loggerFactoryType)
    {
      case SLF4J:
        return new Slf4jMethodLogger(LoggerFactory.getLogger(clazz));

      case LOG4J2:
        return new Log4j2MethodLogger(LogManager.getLogger(clazz));

      case JUL:
        return new JULMethodLogger(getLogManager().getLogger(clazz.getName()));

      default:  // never reached
        throw new IllegalStateException();
    }
  }


  @Override
  protected @NotNull MethodLogger createMethodLogger(@NotNull Field loggerField, @NotNull Object obj)
  {
    switch(loggerField.getType().getName())
    {
      case "org.slf4j.Logger":
        return Slf4jMethodLogger.from(loggerField, obj);

      case "org.apache.logging.log4j.Logger":
        return Log4j2MethodLogger.from(loggerField, obj);

      case "java.util.logging.Logger":
        return JULMethodLogger.from(loggerField, obj);
    }

    return createMethodLogger(obj.getClass());
  }




  private enum LoggerType {
    JUL, LOG4J2, SLF4J
  }
}