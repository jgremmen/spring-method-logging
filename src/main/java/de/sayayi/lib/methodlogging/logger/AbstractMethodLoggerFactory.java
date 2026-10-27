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

import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.MethodLogger;
import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.STATIC;


/**
 * Base class for {@link MethodLoggerFactory} implementations that provides caching for loggers obtained from logger
 * fields and handles the case where no logger field is available on the target class. Loggers created for a class
 * without logger field are cached per class. A subclass returns {@link #CLASS_METHOD_LOGGER} for a logger field to use
 * the method logger for the target class instead.
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
  /**
   * Method logger returned by {@link #createMethodLogger(Field, Object)} to use the method logger for the target class,
   * as created by {@link #createMethodLogger(Class)}, instead. This method logger is never returned by
   * {@link #from(Field, Object, Class)}.
   *
   * @since 0.6.0
   */
  protected static final @NotNull MethodLogger CLASS_METHOD_LOGGER = new MethodLogger() {
    @Override public void log(@NotNull Level level, @NotNull String message) {}
    @Override public boolean isLogEnabled(@NotNull Level level) { return false; }
  };

  /** Method loggers for static final logger fields, by logger field. */
  private final Map<Field,MethodLogger> fieldLoggerCache = new ConcurrentHashMap<>();

  /** Last method logger with the logger instance it was created for, by other logger field and target class. */
  private final Map<Field,Map<Class<?>,FieldMethodLogger>> instanceFieldLoggerCache = new ConcurrentHashMap<>();

  /** Method logger with the logger instance it was created for, by other logger field and target object. */
  private final Map<TargetKey,FieldMethodLogger> targetFieldLoggerCache = new ConcurrentHashMap<>();

  /** Keys of {@link #targetFieldLoggerCache} whose target object has been garbage collected. */
  private final ReferenceQueue<Object> collectedTargets = new ReferenceQueue<>();

  /** Method loggers for classes without logger field, by class. */
  private final Map<Class<?>,MethodLogger> classLoggerCache = new ConcurrentHashMap<>();

  /** Tells whether a method logger for the target class is created if no logger field is available. */
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
   * field are cached per class. {@link MethodLogger#NO_OP} is not cached for a static final logger field, as the field
   * is {@code null} until its class has been initialized. For all other logger fields, the field is read on each
   * invocation, as its content may change. The method logger created for the field and {@code obj}, or else the method
   * logger last created for the field and {@code type}, is reused as long as the field holds the same logger instance.
   * Target objects are referenced weakly. If {@link #createMethodLogger(Field, Object)} returns
   * {@link #CLASS_METHOD_LOGGER}, the cached method logger for {@code type} is returned.
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
        var methodLogger = fieldLoggerCache.get(loggerField);
        if (methodLogger == null)
        {
          // create the logger without holding a lock; concurrently created loggers are equivalent
          methodLogger = createMethodLogger(loggerField, obj);

          // a method invoked during class initialization finds the field still null
          if (methodLogger != MethodLogger.NO_OP)
          {
            var cachedMethodLogger = fieldLoggerCache.putIfAbsent(loggerField, methodLogger);
            if (cachedMethodLogger != null)
              methodLogger = cachedMethodLogger;
          }
        }

        return methodLogger == CLASS_METHOD_LOGGER ? getClassMethodLogger(type) : methodLogger;
      }

      final var methodLogger = getInstanceFieldMethodLogger(loggerField, obj, type);

      return methodLogger == CLASS_METHOD_LOGGER ? getClassMethodLogger(type) : methodLogger;
    }
  }


  /**
   * Returns the method logger for a logger field which is not static final. The method logger created for
   * {@code loggerField} and {@code obj}, or else the method logger last created for {@code loggerField} and
   * {@code type}, is returned if the field still holds the logger instance it was created for. Otherwise, a new method
   * logger is created and cached for both.
   *
   * @param loggerField  logger field which is not static final, not {@code null}
   * @param obj          the target object owning the field, not {@code null}
   * @param type         target type, not {@code null}
   *
   * @return  a method logger instance or {@link #CLASS_METHOD_LOGGER}, never {@code null}
   *
   * @since 0.6.0
   */
  private @NotNull MethodLogger getInstanceFieldMethodLogger(@NotNull Field loggerField, @NotNull Object obj,
                                                             @NotNull Class<?> type)
  {
    final Object logger;

    try {
      logger = loggerField.get(obj);
    } catch(IllegalAccessException ex) {
      return createMethodLogger(loggerField, obj);
    }

    var cachedFieldMethodLogger = targetFieldLoggerCache.get(new TargetKey(loggerField, obj, null));
    if (cachedFieldMethodLogger != null && cachedFieldMethodLogger.logger == logger)
      return cachedFieldMethodLogger.methodLogger;

    final var typeLoggerCache = instanceFieldLoggerCache
        .computeIfAbsent(loggerField, f -> new ConcurrentHashMap<>());

    cachedFieldMethodLogger = typeLoggerCache.get(type);
    if (cachedFieldMethodLogger != null && cachedFieldMethodLogger.logger == logger)
      return cachedFieldMethodLogger.methodLogger;

    final var methodLogger = createMethodLogger(loggerField, obj);

    // the field may have been reassigned while the method logger was created
    try {
      if (loggerField.get(obj) == logger)
      {
        final var fieldMethodLogger = new FieldMethodLogger(logger, methodLogger);

        typeLoggerCache.put(type, fieldMethodLogger);

        removeCollectedTargets();
        targetFieldLoggerCache.put(new TargetKey(loggerField, obj, collectedTargets), fieldMethodLogger);
      }
    } catch(IllegalAccessException ignored) {
    }

    return methodLogger;
  }


  /**
   * Removes the method loggers of garbage collected target objects from the cache.
   *
   * @since 0.6.0
   */
  private void removeCollectedTargets()
  {
    for(Reference<?> collectedTarget; (collectedTarget = collectedTargets.poll()) != null;)
      targetFieldLoggerCache.remove((TargetKey)collectedTarget);
  }


  /**
   * {@inheritDoc}
   * <p>
   * The validation fails if no logger field is available and this factory requires one, or if the declared type of the
   * logger field can never hold a logger of the type returned by {@link #getLoggerType()}.
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
  protected final @NotNull MethodLogger getClassMethodLogger(@NotNull Class<?> type)
  {
    var methodLogger = classLoggerCache.get(type);
    if (methodLogger == null)
    {
      methodLogger = createMethodLogger(type);

      var cachedMethodLogger = classLoggerCache.putIfAbsent(type, methodLogger);
      if (cachedMethodLogger != null)
        methodLogger = cachedMethodLogger;
    }

    return methodLogger;
  }


  /**
   * Creates a method logger for the given class when no logger field is available. The method logger is cached, so this
   * method is invoked once per class, unless the method logger for a class is requested concurrently.
   *
   * @param clazz  the target class, not {@code null}
   *
   * @return  a method logger instance, never {@code null}
   */
  protected abstract @NotNull MethodLogger createMethodLogger(@NotNull Class<?> clazz);


  /**
   * Creates a method logger by reading the logger instance from the given field on the target object. The method
   * logger only depends on the logger instance read from the field, as it is reused for other target objects whose
   * field holds the same logger instance.
   *
   * @param loggerField  the field containing the logger instance, not {@code null}
   * @param obj          the target object owning the field, not {@code null}
   *
   * @return  a method logger instance or {@link #CLASS_METHOD_LOGGER} to use the method logger for the target class,
   *          never {@code null}
   */
  protected abstract @NotNull MethodLogger createMethodLogger(@NotNull Field loggerField, @NotNull Object obj);




  /**
   * Method logger created for a logger instance read from a logger field.
   *
   * @param logger        logger instance read from the logger field, may be {@code null}
   * @param methodLogger  method logger created for {@code logger}, not {@code null}
   *
   * @since 0.6.0
   */
  private record FieldMethodLogger(Object logger, @NotNull MethodLogger methodLogger) {
  }




  /**
   * Cache key of a logger field and a weakly referenced target object, compared by identity.
   *
   * @since 0.6.0
   */
  private static final class TargetKey extends WeakReference<Object>
  {
    /** Logger field read from the target object. */
    private final @NotNull Field loggerField;

    /** Hash code of the logger field and the identity of the target object. */
    private final int hashCode;


    /**
     * Creates a key for {@code loggerField} and {@code obj}.
     *
     * @param loggerField  logger field, not {@code null}
     * @param obj          target object, not {@code null}
     * @param queue        queue the key is enqueued in once {@code obj} has been garbage collected, or {@code null}
     *                     for a key used for lookups only
     */
    private TargetKey(@NotNull Field loggerField, @NotNull Object obj, ReferenceQueue<Object> queue)
    {
      super(obj, queue);

      this.loggerField = loggerField;
      hashCode = 31 * loggerField.hashCode() + System.identityHashCode(obj);
    }


    /**
     * Tells whether {@code o} is a key for the same logger field and target object. A key whose target object has been
     * garbage collected is equal to itself only.
     *
     * @param o  object to compare
     *
     * @return  {@code true} if {@code o} is equal to this key, {@code false} otherwise
     */
    @Override
    public boolean equals(Object o)
    {
      if (o == this)
        return true;

      final Object obj;

      return o instanceof TargetKey that && (obj = get()) != null && obj == that.get() &&
             loggerField.equals(that.loggerField);
    }


    @Override
    public int hashCode() {
      return hashCode;
    }
  }
}
