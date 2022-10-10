package de.sayayi.lib.methodlogging.internal;

import de.sayayi.lib.message.Message;
import de.sayayi.lib.message.MessageContext;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import lombok.Synchronized;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;


final class MethodLoggingDef implements Serializable
{
  final List<ParameterDef> inlineParameters;
  final List<ParameterDef> inMethodParameters;
  final String parameterFormat;
  final String resultFormat;
  final String methodName;
  final int line;
  final boolean showElapsedTime;
  final Field loggerField;
  final Level entryExitLevel;
  final Level parameterLevel;
  final Level resultLevel;

  Message parameterMessage;
  Message resultMessage;


  MethodLoggingDef(@NotNull List<ParameterDef> parameters, @NotNull MethodLogging methodLogging,
                   @NotNull Method method, int line, Field loggerField)
  {
    inlineParameters = new ArrayList<>();
    inMethodParameters = new ArrayList<>();

    parameters.forEach(pd -> {
      if (pd.inMethod)
        inMethodParameters.add(pd);
      else
        inlineParameters.add(pd);
    });

    ((ArrayList<ParameterDef>)inlineParameters).trimToSize();
    ((ArrayList<ParameterDef>)inMethodParameters).trimToSize();

    this.loggerField = loggerField;
    this.line = line;

    methodName = method.getName();
    parameterFormat = notEmpty(methodLogging.parameterFormat(), "%{parameter}=%{value}");
    resultFormat = notEmpty(methodLogging.resultFormat(), "result = %{result}");
    showElapsedTime = methodLogging.showElapsedTime();
    entryExitLevel = methodLogging.entryExitLevel();
    parameterLevel = methodLogging.parameterLevel();
    resultLevel = methodLogging.resultLevel();
  }


  @Synchronized
  @NotNull Message getParameterMessage(@NotNull MessageContext messageContext)
  {
    if (parameterMessage == null)
      parameterMessage = messageContext.getMessageFactory().parse(parameterFormat).trim();

    return parameterMessage;
  }


  @Synchronized
  @NotNull Message getResultMessage(@NotNull MessageContext messageContext)
  {
    if (resultMessage == null)
      resultMessage = messageContext.getMessageFactory().parse(resultFormat).trim();

    return resultMessage;
  }


  @Contract(pure = true)
  private @NotNull String notEmpty(@NotNull String s, @NotNull String defaultValue) {
    return s.isEmpty() ? defaultValue : s;
  }
}