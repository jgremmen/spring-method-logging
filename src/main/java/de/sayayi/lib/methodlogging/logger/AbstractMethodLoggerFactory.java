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

import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.STATIC;


/**
 * Base class for {@link MethodLoggerFactory} implementations that provides caching for loggers obtained from
 * static final fields and handles the case where no logger field is available on the target class. Loggers created
 * for a class without logger field are cached per class.
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
  private final ClassValue<Map<String,MethodLogger>> fieldLoggerCache = new ClassValue<>() {
    @Override
    protected @NotNull Map<String,MethodLogger> computeValue(@NotNull Class<?> type) {
      return new ConcurrentHashMap<>();
    }
  };

  private final ClassValue<MethodLogger> classLoggerCache = new ClassValue<>() {
    @Override
    protected @NotNull MethodLogger computeValue(@NotNull Class<?> type) {
      return createMethodLogger(type);
    }
  };

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


  /**
   * {@inheritDoc}
   * <p>
   * Method loggers for static final logger fields are cached per field and method loggers for classes without logger
   * field are cached per class. For all other logger fields, a method logger is created on each invocation, as the
   * field content may change.
   *
   * @throws IllegalStateException  if no logger field is available and this factory requires one
   */
  @Override
  public @NotNull MethodLogger from(Field loggerField, @NotNull Object obj, @NotNull Class<?> type)
  {
    if (loggerField == null)
    {
      if (!createLoggerOnNoField)
        throw new IllegalStateException("Class " + type + " or one of its superclasses must provide a logger field");

      return getClassMethodLogger(type);
    }
    else
    {
      if ((loggerField.getModifiers() & (STATIC | FINAL)) == (STATIC | FINAL))
      {
        final var fieldLoggers = fieldLoggerCache.get(loggerField.getDeclaringClass());
        final var fieldName = loggerField.getName();

        var methodLogger = fieldLoggers.get(fieldName);
        if (methodLogger == null)
        {
          // create the logger without holding a lock; concurrently created loggers are equivalent
          methodLogger = createMethodLogger(loggerField, obj);

          var cachedMethodLogger = fieldLoggers.putIfAbsent(fieldName, methodLogger);
          if (cachedMethodLogger != null)
            methodLogger = cachedMethodLogger;
        }

        return methodLogger;
      }

      return createMethodLogger(loggerField, obj);
    }
  }


  /**
   * Validates the logger field: fails if no logger field is available and this factory requires one, or if the declared
   * type of the logger field can never hold a logger of the type returned by {@link #getLoggerType()}.
   *
   * @param loggerField  logger field or {@code null} if no logger field was found
   * @param type         target type, not {@code null}
   *
   * @throws IllegalStateException  if no method logger can be provided for the logger field
   *
   * @since 0.6.0
   */
  @Override
  public void validate(Field loggerField, @NotNull Class<?> type)
  {
    if (loggerField == null)
    {
      if (!createLoggerOnNoField)
        throw new IllegalStateException("Class " + type + " or one of its superclasses must provide a logger field");
    }
    else
    {
      final var loggerType = getLoggerType();
      final var fieldType = loggerField.getType();

      if (loggerType != null && !loggerType.isAssignableFrom(fieldType) && !fieldType.isAssignableFrom(loggerType))
      {
        throw new IllegalStateException("Logger field '" + loggerField.getName() + "' of " +
            loggerField.getDeclaringClass() + " must be of type " + loggerType.getName());
      }
    }
  }


  /**
   * Returns the type of logger instances supported by this factory, used for validating logger fields.
   *
   * @return  supported logger type or {@code null} (the default) if the logger field type is not restricted
   *
   * @since 0.6.0
   */
  @Contract(pure = true)
  protected Class<?> getLoggerType() {
    return null;
  }


  /**
   * Returns the cached method logger for {@code type}, as created by {@link #createMethodLogger(Class)}.
   *
   * @param type  the target class, not {@code null}
   *
   * @return  a method logger instance, never {@code null}
   *
   * @since 0.6.0
   */
  protected final @NotNull MethodLogger getClassMethodLogger(@NotNull Class<?> type) {
    return classLoggerCache.get(type);
  }


  /**
   * Creates a method logger for the given class when no logger field is available. The method logger is cached, so this
   * method is invoked once per class.
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
