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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static de.sayayi.lib.methodlogging.MethodLogger.NO_OP;
import static java.util.concurrent.ConcurrentHashMap.newKeySet;


/**
 * Method logger factory implementation which dynamically selects the appropriate logger based on the logger instance
 * held by the logger field.
 * <p>
 * Currently it supports loggers of the following logging frameworks, including subtypes:
 * <ul>
 *   <li>Apache commons logging ({@code org.apache.commons.logging.Log})</li>
 *   <li>JBoss logging ({@code org.jboss.logging.Logger})</li>
 *   <li>JDK logging ({@code java.util.logging.Logger})</li>
 *   <li>JDK platform logging ({@code java.lang.System.Logger})</li>
 *   <li>Log4j2 ({@code org.apache.logging.log4j.Logger})</li>
 *   <li>Logback ({@code ch.qos.logback.classic.Logger})</li>
 *   <li>Slf4j ({@code org.slf4j.Logger})</li>
 *   <li>Tinylog ({@code org.tinylog.TaggedLogger})</li>
 * </ul>
 * If no logger field is available or the logger field contains a logger of another type, a JCL logger for the target
 * class is used; an unsupported logger type is reported once per logger field at WARN level. If the logger field
 * contains {@code null}, the method is not logged.
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public class AutoDetectLoggerFactory extends AbstractMethodLoggerFactory
{
  private static final Log LOG = LogFactory.getLog(AutoDetectLoggerFactory.class);

  /** Supported logger kind, by logger instance class. */
  private static final ClassValue<LoggerKind> LOGGER_KIND = new ClassValue<>() {
    @Override
    protected @NotNull LoggerKind computeValue(@NotNull Class<?> type) {
      return LoggerKind.of(type);
    }
  };

  /** Logger fields with an unsupported logger type which have been reported already. */
  private final Set<String> reportedLoggerFields = newKeySet();


  /** Creates a new auto-detecting method logger factory. */
  public AutoDetectLoggerFactory() {
    super(true);
  }


  /**
   * Creates a JCL-based method logger as a fallback when no logger field is available.
   * {@inheritDoc}
   */
  @Override
  protected @NotNull MethodLogger createMethodLogger(@NotNull Class<?> clazz) {
    return new JCLLogger(LogFactory.getLog(clazz));
  }


  /**
   * Creates a method logger matching the type of the logger instance held by the logger field.
   * {@inheritDoc}
   */
  @Override
  protected @NotNull MethodLogger createMethodLogger(@NotNull Field loggerField, @NotNull Object obj)
  {
    final Object logger;

    try {
      if ((logger = loggerField.get(obj)) == null)
        return NO_OP;
    } catch(IllegalAccessException ex) {
      return NO_OP;
    }

    return switch(LOGGER_KIND.get(logger.getClass())) {
      case LOGBACK -> LogbackLogger.of(logger);
      case SLF4J -> Slf4jLogger.of(logger);
      case LOG4J2 -> Log4j2Logger.of(logger);
      case JUL -> JULLogger.of(logger);
      case JCL -> JCLLogger.of(logger);
      case JBOSS -> JBossLogger.of(logger);
      case TINYLOG -> TinylogTaggedLogger.of(logger);
      case SYSTEM -> SystemLogger.of(logger);
      case UNSUPPORTED -> createUnsupportedMethodLogger(loggerField, logger, obj);
    };
  }


  /**
   * Returns the JCL fallback method logger for a logger field holding an unsupported logger. The unsupported logger
   * type is reported once per logger field.
   *
   * @param loggerField  logger field, not {@code null}
   * @param logger       unsupported logger held by the logger field, not {@code null}
   * @param obj          object owning the logger field, not {@code null}
   *
   * @return  JCL method logger for the class of {@code obj}, never {@code null}
   */
  private @NotNull MethodLogger createUnsupportedMethodLogger(@NotNull Field loggerField, @NotNull Object logger,
                                                              @NotNull Object obj)
  {
    final var declaringClass = loggerField.getDeclaringClass();

    if (reportedLoggerFields.add(declaringClass.getName() + '#' + loggerField.getName()))
    {
      LOG.warn("Logger field '" + loggerField.getName() + "' of " + declaringClass + " contains an unsupported " +
          "logger of type " + logger.getClass().getName() + "; a JCL logger is used instead");
    }

    return getClassMethodLogger(obj.getClass());
  }




  /**
   * Supported logger kinds, identified by type name, as the logging frameworks are optional dependencies.
   */
  private enum LoggerKind
  {
    // Logback loggers are SLF4J loggers as well, so Logback must be checked first
    LOGBACK("ch.qos.logback.classic.Logger"),
    SLF4J("org.slf4j.Logger"),
    LOG4J2("org.apache.logging.log4j.Logger"),
    JUL("java.util.logging.Logger"),
    JCL("org.apache.commons.logging.Log"),
    JBOSS("org.jboss.logging.Logger"),
    TINYLOG("org.tinylog.TaggedLogger"),
    SYSTEM("java.lang.System$Logger"),
    UNSUPPORTED(null);


    private final String typeName;


    /**
     * Creates a logger kind identified by the given type name.
     *
     * @param typeName  fully qualified name of the logger type or {@code null} for unsupported loggers
     */
    LoggerKind(String typeName) {
      this.typeName = typeName;
    }


    /**
     * Returns the logger kind for a logger instance class, checking all of its superclasses and interfaces.
     *
     * @param type  logger instance class, not {@code null}
     *
     * @return  logger kind or {@link #UNSUPPORTED} if the class is not a supported logger, never {@code null}
     */
    @Contract(pure = true)
    private static @NotNull LoggerKind of(@NotNull Class<?> type)
    {
      final var typeNames = new HashSet<String>();

      collectTypeNames(type, typeNames);

      for(var kind: values())
        if (kind.typeName == null || typeNames.contains(kind.typeName))
          return kind;

      return UNSUPPORTED;
    }


    /**
     * Collects the names of {@code type}, its superclasses and all implemented interfaces.
     *
     * @param type       type to collect the names for
     * @param typeNames  set receiving the type names, not {@code null}
     */
    private static void collectTypeNames(Class<?> type, @NotNull Set<String> typeNames)
    {
      for(; type != null; type = type.getSuperclass())
        if (typeNames.add(type.getName()))
          for(var interfaceType: type.getInterfaces())
            collectTypeNames(interfaceType, typeNames);
    }
  }
}
