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
import de.sayayi.lib.message.MessageSupport;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import org.intellij.lang.annotations.Language;
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
@SuppressWarnings("UnknownLanguage")
final class MethodDef implements Serializable
{
  final String methodEntryPrefix;
  final String methodExitPrefix;
  final List<ParameterDef> inlineParameters;
  final List<ParameterDef> inMethodParameters;
  @Language("MessageFormat") String inlineParameterFormat;
  @Language("MessageFormat") String parameterFormat;
  @Language("MessageFormat") String resultFormat;
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


  MethodDef(@NotNull MethodLoggingConfig methodLoggingConfig,
            @NotNull List<ParameterDef> parameters, @NotNull MethodLogging methodLogging,
            @NotNull Method method, int line, Field loggerField)
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
    inlineParameterFormat =
        notEmpty(methodLogging.inlineParameterFormat(), DEFAULT_INLINE_PARAMETER_FORMAT);
    parameterFormat = notEmpty(methodLogging.parameterFormat(), DEFAULT_PARAMETER_FORMAT);
    resultFormat = notEmpty(methodLogging.resultFormat(), DEFAULT_RESULT_FORMAT);
    showElapsedTime = methodLogging.elapsedTime() == SHOW;
    showResult = method.getReturnType() != void.class && methodLogging.result() == SHOW;
    entryExitLevel = methodLogging.entryExitLevel();
    parameterLevel = methodLogging.parameterLevel();
    resultLevel = methodLogging.resultLevel();
  }


  @NotNull Message getInlineParameterMessage(@NotNull MessageSupport messageSupport)
  {
    synchronized(this) {
      if (inlineParameterMessage == null)
      {
        inlineParameterMessage = messageSupport.message(inlineParameterFormat).getMessage();
        inlineParameterFormat = null;
      }

      return inlineParameterMessage;
    }
  }


  @NotNull Message getParameterMessage(@NotNull MessageSupport messageSupport)
  {
    synchronized(this) {
      if (parameterMessage == null)
      {
        parameterMessage = messageSupport.message(parameterFormat).getMessage();
        parameterFormat = null;
      }

      return parameterMessage;
    }
  }


  @NotNull Message getResultMessage(@NotNull MessageSupport messageSupport)
  {
    synchronized(this) {
      if (resultMessage == null)
      {
        resultMessage = messageSupport.message(resultFormat).getMessage();
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
