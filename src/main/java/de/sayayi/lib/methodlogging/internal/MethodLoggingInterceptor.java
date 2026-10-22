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

import de.sayayi.lib.message.MessageSupport;
import de.sayayi.lib.message.MessageSupportFactory;
import de.sayayi.lib.message.formatter.DefaultFormatterService;
import de.sayayi.lib.methodlogging.MethodLogger;
import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import de.sayayi.lib.methodlogging.logger.AutoDetectLoggerFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.ResourceLoader;

import java.util.StringJoiner;

import static java.lang.System.nanoTime;
import static java.util.Objects.requireNonNull;
import static org.springframework.aop.framework.AopProxyUtils.ultimateTargetClass;
import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;
import static org.springframework.util.StringUtils.hasLength;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
public final class MethodLoggingInterceptor implements MethodInterceptor
{
  private static final Log LOG = LogFactory.getLog(MethodLoggingInterceptor.class);

  private final AnnotationMethodLoggingSource annotationMethodLoggingSource;

  private MessageSupport messageSupport;
  private MethodLoggerFactory methodLoggerFactory;


  MethodLoggingInterceptor(@NotNull AnnotationMethodLoggingSource annotationMethodLoggingSource,
                           @NotNull ResourceLoader resourceLoader)
  {
    this.annotationMethodLoggingSource = annotationMethodLoggingSource;

    final var methodLoggingConfigurer = annotationMethodLoggingSource.methodLoggingConfigurer;

    if ((messageSupport = methodLoggingConfigurer.messageSupport()) == null)
    {
      messageSupport = MessageSupportFactory
          .create(new DefaultFormatterService(resourceLoader.getClassLoader(), 128));
    }

    if ((methodLoggerFactory = methodLoggingConfigurer.methodLoggerFactory()) == null)
      methodLoggerFactory = new AutoDetectLoggerFactory();
  }


  @Override
  public Object invoke(@NotNull MethodInvocation invocation) throws Throwable
  {
    final var _this = requireNonNull(invocation.getThis());
    final var thisType = ultimateTargetClass(_this);
    final var methodDef = annotationMethodLoggingSource.getMethodDefinition(invocation.getMethod(), thisType);

    // no definition if the target differs from the class matched by the pointcut (e.g. for a custom target source)
    if (methodDef == null)
      return invocation.proceed();

    final var methodLogger = getEnabledMethodLogger(methodDef, _this, thisType);
    if (methodLogger == null)
      return invocation.proceed();

    final var startTime = nanoTime();
    Throwable throwable = null;

    logMethodEntry(methodDef, invocation.getArguments(), methodLogger);
    try {
      final var result = invocation.proceed();

      if (methodDef.showResult)
        logResult(methodDef, methodLogger, result);

      return result;
    } catch(Throwable ex) {
      throw throwable = ex;
    } finally {
      logMethodExit(methodDef, methodLogger, startTime, throwable);
    }
  }


  /**
   * Returns the method logger for the given method definition, if logging for method entry/exit is enabled.
   *
   * @return  method logger or {@code null} if entry/exit logging is disabled or the method logger could not be
   *          obtained
   */
  private MethodLogger getEnabledMethodLogger(@NotNull MethodDef methodDef, @NotNull Object obj,
                                              @NotNull Class<?> type)
  {
    try {
      final var methodLogger = methodLoggerFactory.from(methodDef.loggerField, obj, type);

      return methodLogger.isLogEnabled(methodDef.entryExitLevel) ? methodLogger : null;
    } catch(Exception ex) {
      logFailure(methodDef, "logger lookup", ex);
      return null;
    }
  }


  private void logMethodEntry(@NotNull MethodDef methodDef, @NotNull Object[] arguments,
                              @NotNull MethodLogger methodLogger)
  {
    try {
      final var printParameters = methodLogger.isLogEnabled(methodDef.parameterLevel);
      final var method = new StringBuilder(methodDef.methodEntryPrefix).append(methodDef.methodName);

      if (printParameters && !methodDef.inlineParameters.isEmpty())
      {
        final var parameterList = new StringJoiner(",", "(", ")");

        for(var parameterDef: methodDef.inlineParameters)
          parameterList.add(logMethodEntry_inlineParameter(methodDef, parameterDef, arguments[parameterDef.index]));

        method.append(parameterList);
      }

      if (methodDef.line > 0)
        method.append(':').append(methodDef.line);

      methodLogger.log(methodDef.entryExitLevel, method.toString());

      if (printParameters && !methodDef.inMethodParameters.isEmpty())
        for(var parameterDef: methodDef.inMethodParameters)
        {
          methodLogger.log(methodDef.parameterLevel,
              logMethodEntry_parameter(methodDef, parameterDef, arguments[parameterDef.index]));
        }
    } catch(Exception ex) {
      logFailure(methodDef, "entry", ex);
    }
  }


  private @NotNull String logMethodEntry_inlineParameter(@NotNull MethodDef methodDef,
                                                         @NotNull ParameterDef parameterDef, Object value)
  {
    return messageSupport
        .message(methodDef.getInlineParameterMessage(messageSupport))
        .with("parameter", parameterDef.name)
        .with("value", formatParameterValue(parameterDef, value))
        .format();
  }


  private @NotNull String logMethodEntry_parameter(@NotNull MethodDef methodDef,
                                                   @NotNull ParameterDef parameterDef,
                                                   Object value)
  {
    return messageSupport
        .message(methodDef.getParameterMessage(messageSupport))
        .with("parameter", parameterDef.name)
        .with("value", formatParameterValue(parameterDef, value))
        .format();
  }


  private @NotNull String formatParameterValue(@NotNull ParameterDef parameterDef, Object value)
  {
    try {
      return messageSupport
          .message(parameterDef.getFormatMessage(messageSupport))
          .with("value", value)
          .format();
    } catch(Exception ex) {
      // e.g. a failing toString() on the value; keep the remaining log output
      return formatFailed(ex);
    }
  }


  private void logMethodExit(@NotNull MethodDef methodDef, @NotNull MethodLogger methodLogger, long startTime,
                             Throwable throwable)
  {
    try {
      final var exit = new StringBuilder(methodDef.methodExitPrefix).append(methodDef.methodName);

      if (methodDef.line > 0)
        exit.append(':').append(methodDef.line);

      if (methodDef.showElapsedTime)
      {
        exit.append(" (elapsed ")
            .append(logMethodExit_elapsed((nanoTime() - startTime) / 1000000L))
            .append(')');
      }

      if (throwable != null)
      {
        exit.append(" -> ").append(throwable.getClass().getSimpleName());

        final var msg = throwable.getLocalizedMessage();

        if (hasLength(msg))
          exit.append('(').append(msg).append(')');
      }

      methodLogger.log(methodDef.entryExitLevel, exit.toString());
    } catch(Exception ex) {
      logFailure(methodDef, "exit", ex);
    }
  }


  /**
   * Formats an elapsed time. Durations below one hour are shown with minutes, seconds and milliseconds, omitting parts
   * which are not significant. Durations of one hour or more are shown in hours and minutes, rounded to the nearest
   * minute.
   *
   * @param millis  elapsed time in milliseconds, not negative
   *
   * @return  formatted elapsed time, never {@code null}
   */
  @Contract(pure = true)
  static @NotNull String logMethodExit_elapsed(long millis)
  {
/*
    h|m|s|ms           h|m|s|ms
    0|0|0|0 -> ms      1|0|0|0 -> h
    0|0|0|1 -> ms      1|0|0|1 -> h
    0|0|1|0 -> s       1|0|1|0 -> h,m if s >= 30 (rounded)
    0|0|1|1 -> s,ms    1|0|1|1 -> h,m if s >= 30 (rounded)
    0|1|0|0 -> m       1|1|0|0 -> h,m
    0|1|0|1 -> m       1|1|0|1 -> h,m
    0|1|1|0 -> m,s     1|1|1|0 -> h,m (rounded)
    0|1|1|1 -> m,s     1|1|1|1 -> h,m (rounded)
 */
    final var s = new StringBuilder();

    if (millis >= 3600000L)
    {
      final var minutes = (millis + 30000L) / 60000L;
      final var min = (int)(minutes % 60);

      s.append(minutes / 60).append('h');

      if (min > 0)
        s.append(min).append('m');
    }
    else
    {
      final var min = (int)(millis / 60000L);
      final var sec = (int)((millis / 1000L) % 60);
      final var msec = (int)(millis % 1000);

      if (min > 0)
        s.append(min).append('m');
      if (sec > 0)
        s.append(sec).append('s');
      if (min == 0 && (sec == 0 || msec > 0))
        s.append(msec).append("ms");
    }

    return s.toString();
  }


  private void logResult(@NotNull MethodDef methodDef, @NotNull MethodLogger methodLogger, Object result)
  {
    try {
      final var resultLevel = methodDef.resultLevel;

      if (methodLogger.isLogEnabled(resultLevel))
      {
        final var resultMessage = methodDef.getResultMessage(messageSupport);
        final var messageConfigurer = messageSupport.message(resultMessage);
        String message;

        try {
          message = messageConfigurer.with("result", result).format();
        } catch(Exception ex) {
          // e.g. a failing toString() on the result
          message = messageConfigurer.with("result", formatFailed(ex)).format();
        }

        methodLogger.log(resultLevel, message);
      }
    } catch(Exception ex) {
      logFailure(methodDef, "result", ex);
    }
  }


  @Contract(pure = true)
  private static @NotNull String formatFailed(@NotNull Exception ex) {
    return "<format failed: " + getMostSpecificCause(ex).getClass().getSimpleName() + '>';
  }


  private static void logFailure(@NotNull MethodDef methodDef, @NotNull String phase, @NotNull Exception ex) {
    LOG.warn("Method logging (" + phase + ") failed for method '" + methodDef.methodName + '\'', ex);
  }
}
