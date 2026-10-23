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
import java.util.function.Supplier;

import static de.sayayi.lib.methodlogging.Visibility.SHOW;
import static de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig.*;
import static java.util.stream.Collectors.partitioningBy;


/**
 * Method logging definition of a method, with all settings resolved (see {@link AnnotationMethodLoggingSource}). The
 * levels are never {@link Level#DEFAULT}.
 * <p>
 * Instances are immutable and shared by all threads invoking the method. The messages for parameters and the result
 * are created on first use, as many of them are never needed when the corresponding log level is disabled.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
final class MethodDef
{
  final String methodEntryPrefix;
  final String methodExitPrefix;
  final List<ParameterDef> inlineParameters;
  final List<ParameterDef> inMethodParameters;
  final Supplier<Message> inlineParameterMessage;
  final Supplier<Message> parameterMessage;
  final Supplier<Message> resultMessage;
  final String methodName;
  final int line;
  /** Source location of the method, passed to the method logger. */
  final @NotNull StackTraceElement location;
  final boolean showElapsedTime;
  final boolean showResult;
  final Field loggerField;
  final Level entryExitLevel;
  final Level parameterLevel;
  final Level resultLevel;


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
    inlineParameterMessage = SupplierDelegate.of(() -> messageFactory.parseMessage(
        notEmpty(methodLogging.inlineParameterFormat(), DEFAULT_INLINE_PARAMETER_FORMAT)));
    parameterMessage = SupplierDelegate.of(() -> messageFactory.parseMessage(
        notEmpty(methodLogging.parameterFormat(), DEFAULT_PARAMETER_FORMAT)));
    resultMessage = SupplierDelegate.of(() -> messageFactory.parseMessage(
        notEmpty(methodLogging.resultFormat(), DEFAULT_RESULT_FORMAT)));
    showElapsedTime = methodLogging.elapsedTime() == SHOW;
    showResult = method.getReturnType() != void.class && methodLogging.result() == SHOW;
    entryExitLevel = methodLogging.entryExitLevel();
    parameterLevel = methodLogging.parameterLevel();
    resultLevel = methodLogging.resultLevel();
  }


  @Contract(pure = true)
  private static @NotNull String notEmpty(@NotNull String s, @NotNull String defaultValue) {
    return s.isEmpty() ? defaultValue : s;
  }
}
