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
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.STATIC;


/**
 * Base class for {@link MethodLoggerFactory} implementations that provides caching for loggers obtained from
 * static final fields and handles the case where no logger field is available on the target class.
 * <p>
 * Subclasses only need to implement the two {@code createMethodLogger} methods to bridge to a specific logging
 * framework.
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 *
 * @see AutoDetectLoggerFactory
 */
public abstract class AbstractMethodLoggerFactory implements MethodLoggerFactory
{
  private final WeakHashMap<Class<?>,MethodLogger> loggerCache = new WeakHashMap<>();
  private final boolean createLoggerOnNoField;


  /**
   * Creates a new abstract method logger factory.
   *
   * @param createLoggerOnNoField  if {@code true}, a logger is created even when no logger field is found on the
   *                               target class; if {@code false}, an {@link IllegalStateException} is thrown in that
   *                               case
   */
  protected AbstractMethodLoggerFactory(boolean createLoggerOnNoField) {
    this.createLoggerOnNoField = createLoggerOnNoField;
  }


  @Override
  public @NotNull MethodLogger from(Field loggerField, @NotNull Object obj, @NotNull Class<?> type)
  {
    if (loggerField == null)
    {
      if (!createLoggerOnNoField)
        throw new IllegalStateException("Class " + type + " or one of its superclasses must provide a logger field");

      return createMethodLogger(type);
    }
    else
    {
      if ((loggerField.getModifiers() & (STATIC | FINAL)) == (STATIC | FINAL))
      {
        synchronized(loggerCache) {
          return loggerCache.computeIfAbsent(type, cl -> createMethodLogger(loggerField, obj));
        }
      }

      return createMethodLogger(loggerField, obj);
    }
  }


  /**
   * Creates a method logger for the given class when no logger field is available.
   *
   * @param clazz  the target class, not {@code null}
   *
   * @return  a method logger instance, never {@code null}
   */
  protected abstract @NotNull MethodLogger createMethodLogger(@NotNull Class<?> clazz);


  /**
   * Creates a method logger by reading the logger instance from the given field on the target object.
   *
   * @param loggerField  the field containing the logger instance, not {@code null}
   * @param obj          the target object owning the field, not {@code null}
   *
   * @return  a method logger instance, never {@code null}
   */
  protected abstract @NotNull MethodLogger createMethodLogger(@NotNull Field loggerField, @NotNull Object obj);
}
