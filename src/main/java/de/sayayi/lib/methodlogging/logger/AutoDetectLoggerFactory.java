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
 * class is used. The same applies to a logger of a supported logging framework which is not accessible to this library,
 * e.g. if it is loaded by a class loader not visible to this library. An unsupported or inaccessible logger is reported
 * once per logger field at WARN level. If the logger field contains {@code null}, the method is not logged.
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public class AutoDetectLoggerFactory extends AbstractMethodLoggerFactory
{
  /** Logger reporting logger fields with an unsupported or inaccessible logger. */
  private static final Log LOG = LogFactory.getLog(AutoDetectLoggerFactory.class);

  /** Ordinal of the supported logger kind, by logger instance class. */
  private static final ClassValue<Integer> LOGGER_KIND_ORDINAL = new ClassValue<>() {
    @Override
    protected @NotNull Integer computeValue(@NotNull Class<?> type) {
      return LoggerKind.of(type).ordinal();
    }
  };

  /** All logger kinds, indexed by ordinal. */
  private static final LoggerKind[] LOGGER_KINDS = LoggerKind.values();

  /** Logger fields with an unsupported or inaccessible logger which have been reported already. */
  private final Set<String> reportedLoggerFields = newKeySet();


  /** Creates a new auto-detecting method logger factory. */
  public AutoDetectLoggerFactory() {
    super(true);
  }


  /**
   * {@inheritDoc}
   * <p>
   * This factory creates a JCL based method logger as a fallback.
   */
  @Override
  protected @NotNull MethodLogger createMethodLogger(@NotNull Class<?> clazz) {
    return new JCLLogger(LogFactory.getLog(clazz));
  }


  /**
   * {@inheritDoc}
   * <p>
   * This factory creates a method logger matching the type of the logger instance held by the logger field. For a field
   * containing {@code null}, {@link MethodLogger#NO_OP} is returned. For an unsupported or inaccessible logger, the JCL
   * based method logger for the target class is used.
   */
  @Override
  protected @NotNull MethodLogger createMethodLogger(@NotNull Field loggerField, @NotNull Object obj)
  {
    final var logger = FrameworkMethodLoggerFactory.readLoggerField(loggerField, obj);
    if (logger == null)
      return NO_OP;

    MethodLogger methodLogger;
    LinkageError linkageError = null;

    try {
      methodLogger = switch(LOGGER_KINDS[LOGGER_KIND_ORDINAL.get(logger.getClass())]) {
        case LOGBACK -> LogbackLogger.of(logger);
        case SLF4J -> Slf4jLogger.of(logger);
        case LOG4J2 -> Log4j2Logger.of(logger);
        case JUL -> JULLogger.of(logger);
        case JCL -> JCLLogger.of(logger);
        case JBOSS -> JBossLogger.of(logger);
        case TINYLOG -> TinylogTaggedLogger.of(logger);
        case SYSTEM -> SystemLogger.of(logger);
        case UNSUPPORTED -> null;
      };
    } catch(LinkageError ex) {
      // logging framework not visible to this library
      methodLogger = NO_OP;
      linkageError = ex;
    }

    if (methodLogger == null)
      return createFallbackMethodLogger(loggerField, logger, "an unsupported", null);

    // the logger is not an instance of the logging framework type visible to this library
    return methodLogger == NO_OP
        ? createFallbackMethodLogger(loggerField, logger, "an inaccessible", linkageError)
        : methodLogger;
  }


  /**
   * Returns the fallback method logger for a logger field holding an unsupported or inaccessible logger. The logger is
   * reported once per logger field.
   *
   * @param loggerField  logger field, not {@code null}
   * @param logger       unsupported or inaccessible logger held by the logger field, not {@code null}
   * @param kind         kind of logger for the report ({@code "an unsupported"} or {@code "an inaccessible"}), not
   *                     {@code null}
   * @param cause        error raised while accessing the logger or {@code null}
   *
   * @return  {@link #CLASS_METHOD_LOGGER}, selecting the JCL method logger for the target class, never {@code null}
   *
   * @since 0.6.0
   */
  private @NotNull MethodLogger createFallbackMethodLogger(@NotNull Field loggerField, @NotNull Object logger,
                                                           @NotNull String kind, Throwable cause)
  {
    final var declaringClass = loggerField.getDeclaringClass();

    if (reportedLoggerFields.add(declaringClass.getName() + '#' + loggerField.getName()))
    {
      LOG.warn("Logger field '" + loggerField.getName() + "' of " + declaringClass + " contains " + kind +
          " logger of type " + logger.getClass().getName() + "; a JCL logger is used instead", cause);
    }

    return CLASS_METHOD_LOGGER;
  }




  /**
   * Supported logger kinds, identified by type name, as the logging frameworks are optional dependencies.
   *
   * @since 0.6.0
   */
  private enum LoggerKind
  {
    // Logback loggers are SLF4J loggers as well, so Logback must be checked first
    /** Logback logger. */
    LOGBACK("ch.qos.logback.classic.Logger"),

    /** SLF4J logger. */
    SLF4J("org.slf4j.Logger"),

    /** Log4j2 logger. */
    LOG4J2("org.apache.logging.log4j.Logger"),

    /** JUL logger. */
    JUL("java.util.logging.Logger"),

    /** Apache Commons Logging logger. */
    JCL("org.apache.commons.logging.Log"),

    /** JBoss logger. */
    JBOSS("org.jboss.logging.Logger"),

    /** Tinylog tagged logger. */
    TINYLOG("org.tinylog.TaggedLogger"),

    /** JDK platform logger. */
    SYSTEM("java.lang.System$Logger"),

    /** Any other logger. */
    UNSUPPORTED(null);


    /** Fully qualified name of the logger type or {@code null} for unsupported loggers. */
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
