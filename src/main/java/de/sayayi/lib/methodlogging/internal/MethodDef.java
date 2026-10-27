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
import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
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
 * The messages are provided by the {@link MessageParser} of the configuration: messages with a built-in default
 * format are created on first use and shared by all definitions, messages with a custom format are created with the
 * definition, so an invalid format fails the method analysis.
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
   * @param messageParser        message parser for the message formats, not {@code null}
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
  MethodDef(@NotNull MessageParser messageParser, @NotNull MethodLoggingConfig methodLoggingConfig,
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
    inlineParameterMessage = messageParser.messageSupplier(methodLogging.inlineParameterFormat(),
        DEFAULT_INLINE_PARAMETER_FORMAT, "inlineParameterFormat", method);
    parameterMessage = messageParser.messageSupplier(methodLogging.parameterFormat(), DEFAULT_PARAMETER_FORMAT,
        "parameterFormat", method);
    resultMessage = messageParser.messageSupplier(methodLogging.resultFormat(), DEFAULT_RESULT_FORMAT,
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
}
