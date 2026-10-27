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
package de.sayayi.lib.methodlogging.internal;

import de.sayayi.lib.message.Message;
import de.sayayi.lib.message.MessageFactory;
import de.sayayi.lib.message.util.SupplierDelegate;
import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static de.sayayi.lib.methodlogging.Visibility.SHOW;
import static de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig.*;
import static java.util.concurrent.ConcurrentHashMap.newKeySet;
import static java.util.stream.Collectors.partitioningBy;


/**
 * Method logging definition of a method, with all settings resolved (see {@link AnnotationMethodLoggingSource}). The
 * levels are never {@link Level#DEFAULT}.
 * <p>
 * Instances are shared by all threads invoking the method. Apart from the failures reported (see
 * {@link #isFirstFailureReport(String)}), they are immutable.
 * <p>
 * Messages with a built-in default format are created on first use, as many of them are never needed when the
 * corresponding log level is disabled. Messages with a custom format are created with the definition, so an invalid
 * format fails the method analysis.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
final class MethodDef
{
  /** Prefix of the method entry message, not {@code null}. */
  final String methodEntryPrefix;

  /** Prefix of the method exit message, not {@code null}. */
  final String methodExitPrefix;

  /** Definitions of the parameters logged inline with the method entry, immutable and not {@code null}. */
  final List<ParameterDef> inlineParameters;

  /** Definitions of the parameters logged separately after the method entry, immutable and not {@code null}. */
  final List<ParameterDef> inMethodParameters;

  /** Supplier of the message for inline parameters, not {@code null}. */
  final Supplier<Message> inlineParameterMessage;

  /** Supplier of the message for separately logged parameters, not {@code null}. */
  final Supplier<Message> parameterMessage;

  /** Supplier of the message for the method result, not {@code null}. */
  final Supplier<Message> resultMessage;

  /** Name of the logged method, not {@code null}. */
  final String methodName;

  /** Line number to log or a value {@code <= 0} if no line number is logged. */
  final int line;

  /** Source location of the method, passed to the method logger. */
  final @NotNull StackTraceElement location;

  /** Tells whether the elapsed time is logged with the method exit. */
  final boolean showElapsedTime;

  /** Tells whether the message of an exception thrown by the method is logged with the method exit. */
  final boolean showExceptionMessage;

  /** Tells whether the method result is logged. Always {@code false} for {@code void} methods. */
  final boolean showResult;

  /** Logger field or {@code null} if no logger field was found. */
  final Field loggerField;

  /** Log level of the method entry and exit messages. */
  final Level entryExitLevel;

  /** Log level of the parameter messages, also deciding whether inline parameters are logged. */
  final Level parameterLevel;

  /** Log level of the result message. */
  final Level resultLevel;

  /** Logging phases whose failure has been reported. */
  private final Set<String> reportedFailurePhases = newKeySet();


  /**
   * Creates the method logging definition from the resolved settings.
   *
   * @param messageFactory       message factory for parsing the message formats, not {@code null}
   * @param methodLoggingConfig  class level settings with the configurer defaults applied, not {@code null}
   * @param parameters           definitions of the parameters to be logged, not {@code null}
   * @param methodLogging        merged method level settings, not {@code null}
   * @param method               logged method, not {@code null}
   * @param location             source location of the method, not {@code null}
   * @param line                 line number to log or a value {@code <= 0} if no line number is logged
   * @param loggerField          logger field or {@code null} if no logger field was found
   *
   * @throws IllegalStateException  if a custom message format is invalid
   *
   * @since 0.6.0
   */
  MethodDef(final @NotNull MessageFactory messageFactory, @NotNull MethodLoggingConfig methodLoggingConfig,
            @NotNull List<ParameterDef> parameters, @NotNull MethodLogging methodLogging, @NotNull Method method,
            @NotNull StackTraceElement location, int line, Field loggerField)
  {
    methodEntryPrefix = methodLoggingConfig.methodEntryPrefix();
    methodExitPrefix = methodLoggingConfig.methodExitPrefix();

    final var partitionedParameters = parameters
        .stream()
        .collect(partitioningBy(ParameterDef::inline));

    inlineParameters = List.copyOf(partitionedParameters.get(true));
    inMethodParameters = List.copyOf(partitionedParameters.get(false));

    this.loggerField = loggerField;
    this.location = location;
    this.line = line;

    methodName = method.getName();
    inlineParameterMessage = messageSupplier(messageFactory, methodLogging.inlineParameterFormat(),
        DEFAULT_INLINE_PARAMETER_FORMAT, "inlineParameterFormat", method);
    parameterMessage = messageSupplier(messageFactory, methodLogging.parameterFormat(), DEFAULT_PARAMETER_FORMAT,
        "parameterFormat", method);
    resultMessage = messageSupplier(messageFactory, methodLogging.resultFormat(), DEFAULT_RESULT_FORMAT,
        "resultFormat", method);
    showElapsedTime = methodLogging.elapsedTime() == SHOW;
    showExceptionMessage = methodLogging.exceptionMessage() == SHOW;
    showResult = method.getReturnType() != void.class && methodLogging.result() == SHOW;
    entryExitLevel = methodLogging.entryExitLevel();
    parameterLevel = methodLogging.parameterLevel();
    resultLevel = methodLogging.resultLevel();
  }


  /**
   * Tells whether a failure in logging phase {@code phase} is to be reported as the first failure of that phase for
   * this method.
   *
   * @param phase  logging phase which failed, not {@code null}
   *
   * @return  {@code true} for the first failure of {@code phase}, {@code false} for all subsequent failures
   *
   * @since 0.6.0
   */
  boolean isFirstFailureReport(@NotNull String phase) {
    return reportedFailurePhases.add(phase);
  }


  /**
   * Returns a supplier of the message for {@code format}. A message for the built-in default format, also used if
   * {@code format} is empty, is created on first use. A message for a custom format is created immediately.
   *
   * @param messageFactory  message factory for parsing the message format, not {@code null}
   * @param format          message format or empty for the default format, not {@code null}
   * @param defaultFormat   built-in default format, not {@code null}
   * @param attributeName   name of the annotation attribute providing {@code format}, not {@code null}
   * @param method          method providing {@code format}, not {@code null}
   *
   * @return  message supplier, never {@code null}
   *
   * @throws IllegalStateException  if {@code format} is a custom format which cannot be parsed
   *
   * @since 0.6.0
   */
  @Contract(value = "_, _, _, _, _ -> new", pure = true)
  static @NotNull Supplier<Message> messageSupplier(@NotNull MessageFactory messageFactory, @NotNull String format,
                                                    @NotNull String defaultFormat, @NotNull String attributeName,
                                                    @NotNull Method method)
  {
    if (format.isEmpty() || format.equals(defaultFormat))
      return SupplierDelegate.of(() -> messageFactory.parseMessage(defaultFormat));

    final Message message;

    try {
      message = messageFactory.parseMessage(format);
    } catch(RuntimeException ex) {
      throw new IllegalStateException("Invalid " + attributeName + " '" + format + "' for " + method, ex);
    }

    return () -> message;
  }
}
