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
import de.sayayi.lib.message.MessageContext;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import lombok.Synchronized;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility.SHOW;
import static de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig.*;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
final class MethodLoggingDef implements Serializable
{
  final String methodEntryPrefix;
  final String methodExitPrefix;
  final List<ParameterDef> inlineParameters;
  final List<ParameterDef> inMethodParameters;
  String inlineParameterFormat;
  String parameterFormat;
  String resultFormat;
  final String methodName;
  final int line;
  final boolean showElapsedTime;
  final boolean showResult;
  final Field loggerField;
  final Level entryExitLevel;
  final Level parameterLevel;
  final Level resultLevel;

  Message inlineParameterMessage;
  Message parameterMessage;
  Message resultMessage;


  MethodLoggingDef(@NotNull MethodLoggingConfig methodLoggingConfig, @NotNull List<ParameterDef> parameters,
                   @NotNull MethodLogging methodLogging, @NotNull Method method, int line, Field loggerField)
  {
    methodEntryPrefix = methodLoggingConfig.methodEntryPrefix();
    methodExitPrefix = methodLoggingConfig.methodExitPrefix();

    inlineParameters = new ArrayList<>();
    inMethodParameters = new ArrayList<>();

    parameters.forEach(parameterDef ->
        (parameterDef.inline ? inlineParameters : inMethodParameters).add(parameterDef));

    ((ArrayList<ParameterDef>)inlineParameters).trimToSize();
    ((ArrayList<ParameterDef>)inMethodParameters).trimToSize();

    this.loggerField = loggerField;
    this.line = line;

    methodName = method.getName();
    inlineParameterFormat = notEmpty(methodLogging.inlineParameterFormat(), DEFAULT_INLINE_PARAMETER_FORMAT);
    parameterFormat = notEmpty(methodLogging.parameterFormat(), DEFAULT_PARAMETER_FORMAT);
    resultFormat = notEmpty(methodLogging.resultFormat(), DEFAULT_RESULT_FORMAT);
    showElapsedTime = methodLogging.elapsedTime() == SHOW;
    showResult = method.getReturnType() != void.class && methodLogging.result() == SHOW;
    entryExitLevel = methodLogging.entryExitLevel();
    parameterLevel = methodLogging.parameterLevel();
    resultLevel = methodLogging.resultLevel();
  }


  @NotNull Message getInlineParameterMessage(@NotNull MessageContext messageContext)
  {
    synchronized(this) {
      if (inlineParameterMessage == null)
      {
        inlineParameterMessage = messageContext.getMessageFactory().parse(inlineParameterFormat).trim();
        inlineParameterFormat = null;
      }

      return inlineParameterMessage;
    }
  }


  @Synchronized
  @NotNull Message getParameterMessage(@NotNull MessageContext messageContext)
  {
    synchronized(this) {
      if (parameterMessage == null)
      {
        parameterMessage = messageContext.getMessageFactory().parse(parameterFormat).trim();
        parameterFormat = null;
      }

      return parameterMessage;
    }
  }


  @Synchronized
  @NotNull Message getResultMessage(@NotNull MessageContext messageContext)
  {
    synchronized(this) {
      if (resultMessage == null)
      {
        resultMessage = messageContext.getMessageFactory().parse(resultFormat).trim();
        resultFormat = null;
      }

      return resultMessage;
    }
  }


  @Contract(pure = true)
  private @NotNull String notEmpty(@NotNull String s, @NotNull String defaultValue) {
    return s.isEmpty() ? defaultValue : s;
  }
}