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
package de.sayayi.lib.methodlogging.logger;

import de.sayayi.lib.methodlogging.MethodLogger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;


/**
 * Base class for the method logger factories of a specific logging framework. The factory is defined by the logger
 * type of the framework, a function creating the method logger for a class without logger field and a function
 * creating the method logger for the logger instance held by a logger field.
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
abstract class FrameworkMethodLoggerFactory extends AbstractMethodLoggerFactory
{
  /** Logger type of the logging framework or {@code null} if the logger field type is not restricted. */
  private final Class<?> loggerType;
  /** Function creating the method logger for a class without logger field. */
  private final @NotNull Function<Class<?>,MethodLogger> classMethodLoggerFactory;

  /** Function creating the method logger for the logger instance held by a logger field. */
  private final @NotNull Function<Object,MethodLogger> fieldMethodLoggerFactory;


  /**
   * Creates a new method logger factory for a specific logging framework.
   *
   * @param createLoggerOnNoField     if {@code true}, a logger is created even when no logger field is found on the
   *                                  target class; if {@code false}, an {@link IllegalStateException} is thrown in that
   *                                  case
   * @param loggerType                logger type of the logging framework or {@code null} if the logger field type is
   *                                  not restricted
   * @param classMethodLoggerFactory  function creating the method logger for a class without logger field, not
   *                                  {@code null}
   * @param fieldMethodLoggerFactory  function creating the method logger for the logger instance held by a logger
   *                                  field (the instance may be {@code null}), not {@code null}
   */
  FrameworkMethodLoggerFactory(boolean createLoggerOnNoField, Class<?> loggerType,
                               @NotNull Function<Class<?>,MethodLogger> classMethodLoggerFactory,
                               @NotNull Function<Object,MethodLogger> fieldMethodLoggerFactory)
  {
    super(createLoggerOnNoField);

    this.loggerType = loggerType;
    this.classMethodLoggerFactory = requireNonNull(classMethodLoggerFactory);
    this.fieldMethodLoggerFactory = requireNonNull(fieldMethodLoggerFactory);
  }


  /**
   * {@inheritDoc}
   * <p>
   * This factory returns the logger type of the logging framework, so logger fields which can never hold a logger of
   * the logging framework are rejected.
   */
  @Override
  protected Class<?> getLoggerType() {
    return loggerType;
  }


  /**
   * {@inheritDoc}
   * <p>
   * This factory creates a method logger for a logger of the logging framework for {@code clazz}.
   */
  @Override
  protected @NotNull MethodLogger createMethodLogger(@NotNull Class<?> clazz) {
    return classMethodLoggerFactory.apply(clazz);
  }


  /**
   * {@inheritDoc}
   * <p>
   * This factory creates a method logger for the logger held by {@code loggerField}. If the field is not accessible,
   * the method logger is created for a {@code null} logger.
   */
  @Override
  protected @NotNull MethodLogger createMethodLogger(@NotNull Field loggerField, @NotNull Object obj) {
    return fieldMethodLoggerFactory.apply(readLoggerField(loggerField, obj));
  }


  /**
   * Reads the logger instance held by {@code loggerField} of {@code obj}.
   *
   * @param loggerField  the field containing the logger instance, not {@code null}
   * @param obj          the object owning the field, not {@code null}
   *
   * @return  logger instance or {@code null} if the field contains {@code null} or is not accessible
   */
  @Contract(pure = true)
  static Object readLoggerField(@NotNull Field loggerField, @NotNull Object obj)
  {
    try {
      return loggerField.get(obj);
    } catch(IllegalAccessException ex) {
      return null;
    }
  }
}
