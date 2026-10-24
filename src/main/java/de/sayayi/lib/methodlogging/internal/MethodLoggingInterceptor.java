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
import de.sayayi.lib.methodlogging.MethodLogger;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.StringJoiner;

import static java.lang.System.nanoTime;
import static java.util.Objects.requireNonNull;
import static org.springframework.aop.framework.AopProxyUtils.ultimateTargetClass;
import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;
import static org.springframework.util.StringUtils.hasLength;


/**
 * Method interceptor logging the entry, parameters, result and exit of methods with a method logging definition. It is
 * used for both proxy and AspectJ based method logging.
 * <p>
 * The result and exceptions of the invoked method are passed on unchanged. If a parameter value or the result cannot be
 * formatted, it is logged as {@code <format failed: exception>}. Other failures are reported at WARN level through the
 * JCL logger of this class: if the method cannot be analyzed (for AspectJ based method logging on first invocation;
 * reported once per method and target class, as the failed analysis is not repeated) or the method logger cannot be
 * obtained, the method is invoked without logging; if logging the entry, result or exit fails, the remaining messages
 * are still logged. This includes errors like a {@link StackOverflowError} raised by a cyclic {@code toString()}; only
 * virtual machine errors the application cannot recover from, like an {@link OutOfMemoryError}, are passed on.
 * Failures obtaining the method logger or logging the entry, result or exit are reported at WARN level for the first
 * failure of each phase per method definition; subsequent failures of that phase are reported at DEBUG level.
 * <p>
 * Formatting a parameter value or the result may execute code of the value and thereby affect the invoked method or its
 * caller: parameters are formatted before the method is invoked and the result before it is returned. The default
 * message support evaluates suppliers and iterates iterables completely, which may repeat side effects, consume
 * iterables which can be iterated only once or not return for infinite iterables. An {@link OutOfMemoryError} raised
 * while formatting a very large value prevents the invocation (parameters) or replaces the result (result). See
 * {@link de.sayayi.lib.methodlogging.annotation.MethodLogging MethodLogging}.
 * <p>
 * Nothing is logged for an invocation if the method logger does not enable the entry/exit level; parameters and result
 * are logged only together with the entry and exit messages.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
public final class MethodLoggingInterceptor implements MethodInterceptor
{
  private static final Log LOG = LogFactory.getLog(MethodLoggingInterceptor.class);

  private final AnnotationMethodLoggingSource annotationMethodLoggingSource;


  /**
   * Creates the method logging interceptor.
   *
   * @param annotationMethodLoggingSource  method logging source providing the method logging definitions and the
   *                                       method logger factory, not {@code null}
   */
  MethodLoggingInterceptor(@NotNull AnnotationMethodLoggingSource annotationMethodLoggingSource) {
    this.annotationMethodLoggingSource = annotationMethodLoggingSource;
  }


  /**
   * Invokes the method, logging its entry, parameters, result and exit if the method has a method logging definition
   * and the entry/exit level is enabled for the method logger.
   *
   * @param invocation  method invocation, not {@code null}
   *
   * @return  result of the method invocation, passed on unchanged
   *
   * @throws Throwable  if the method invocation throws an exception, passed on unchanged
   */
  @Override
  public Object invoke(@NotNull MethodInvocation invocation) throws Throwable
  {
    final var _this = requireNonNull(invocation.getThis());
    final var thisType = ultimateTargetClass(_this);
    final var invocationMethod = invocation.getMethod();
    final MethodDef methodDef;

    try {
      // usually analyzed on startup; for AspectJ based method logging on first invocation
      methodDef = annotationMethodLoggingSource.getMethodDefinition(invocationMethod, thisType);
    } catch(Throwable ex) {
      rethrowIfFatal(ex);

      // a failed analysis is cached, so it is reported once instead of on every invocation
      if (annotationMethodLoggingSource.isFirstFailureReport(invocationMethod, thisType))
        LOG.warn("Method logging analysis failed for method '" + invocationMethod.getName() + '\'', ex);

      return invocation.proceed();
    }

    // no definition if the target differs from the class matched by the pointcut (e.g. for a custom target source)
    // or if the method is invoked while the configurer is being created
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
   * @param methodDef  method logging definition, not {@code null}
   * @param obj        object the method is invoked on, not {@code null}
   * @param type       target type of {@code obj}, not {@code null}
   *
   * @return  method logger or {@code null} if entry/exit logging is disabled or the method logger could not be obtained
   */
  private MethodLogger getEnabledMethodLogger(@NotNull MethodDef methodDef, @NotNull Object obj,
                                              @NotNull Class<?> type)
  {
    try {
      final var methodLogger = annotationMethodLoggingSource
          .getMethodLoggerFactory()
          .from(methodDef.loggerField, obj, type);

      return methodLogger.isLogEnabled(methodDef.entryExitLevel) ? methodLogger : null;
    } catch(Throwable ex) {
      rethrowIfFatal(ex);
      logFailure(methodDef, "logger lookup", ex);
      return null;
    }
  }


  /**
   * Logs the method entry, including the inline parameters, followed by the parameters which are logged on separate
   * lines. Parameters are only logged if the parameter level is enabled.
   *
   * @param methodDef     method logging definition, not {@code null}
   * @param arguments     method arguments, not {@code null}
   * @param methodLogger  method logger, not {@code null}
   */
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
        {
          parameterList.add(
              formatParameter(methodDef.inlineParameterMessage.get(), parameterDef, arguments[parameterDef.index()]));
        }

        method.append(parameterList);
      }

      if (methodDef.line > 0)
        method.append(':').append(methodDef.line);

      methodLogger.log(methodDef.entryExitLevel, method.toString(), methodDef.location);

      if (printParameters && !methodDef.inMethodParameters.isEmpty())
        for(var parameterDef: methodDef.inMethodParameters)
        {
          methodLogger.log(
              methodDef.parameterLevel,
              formatParameter(methodDef.parameterMessage.get(), parameterDef, arguments[parameterDef.index()]),
              methodDef.location);
        }
    } catch(Throwable ex) {
      rethrowIfFatal(ex);
      logFailure(methodDef, "entry", ex);
    }
  }


  /**
   * Formats a parameter using the inline or in-method parameter message.
   *
   * @param parameterMessage  inline or in-method parameter message, not {@code null}
   * @param parameterDef      parameter definition, not {@code null}
   * @param value             parameter value
   *
   * @return  formatted parameter, never {@code null}
   */
  @Contract(pure = true)
  private @NotNull String formatParameter(@NotNull Message parameterMessage, @NotNull ParameterDef parameterDef,
                                          Object value)
  {
    return annotationMethodLoggingSource
        .getMessageSupport()
        .message(parameterMessage)
        .with("parameter", parameterDef.name())
        .with("value", formatParameterValue(parameterDef, value))
        .format();
  }


  /**
   * Formats a parameter value using the parameter format message of the parameter definition. If the value cannot be
   * formatted, a format failure text is returned instead.
   *
   * @param parameterDef  parameter definition, not {@code null}
   * @param value         parameter value
   *
   * @return  formatted parameter value, never {@code null}
   */
  private @NotNull String formatParameterValue(@NotNull ParameterDef parameterDef, Object value)
  {
    try {
      return annotationMethodLoggingSource
          .getMessageSupport()
          .message(parameterDef.formatMessage().get())
          .with("value", value)
          .format();
    } catch(Throwable ex) {
      rethrowIfFatal(ex);
      // e.g. a failing toString() on the value; keep the remaining log output
      return formatFailed(ex);
    }
  }


  /**
   * Logs the method exit, including the elapsed time if configured and the exception thrown by the method, if any.
   *
   * @param methodDef     method logging definition, not {@code null}
   * @param methodLogger  method logger, not {@code null}
   * @param startTime     start time of the method invocation in nanoseconds
   * @param throwable     exception thrown by the method or {@code null} if the method returned normally
   */
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
            .append(formatElapsedTime((nanoTime() - startTime) / 1000000L))
            .append(')');
      }

      if (throwable != null)
      {
        exit.append(" -> ").append(exceptionName(throwable.getClass()));

        final var msg = throwable.getLocalizedMessage();

        if (hasLength(msg))
          exit.append('(').append(msg).append(')');
      }

      methodLogger.log(methodDef.entryExitLevel, exit.toString(), methodDef.location);
    } catch(Throwable ex) {
      rethrowIfFatal(ex);
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
  static @NotNull String formatElapsedTime(long millis)
  {
    final var s = new StringBuilder();

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


  /**
   * Logs the method result if the result level is enabled. If the result cannot be formatted, a format failure text is
   * logged instead.
   *
   * @param methodDef     method logging definition, not {@code null}
   * @param methodLogger  method logger, not {@code null}
   * @param result        method result
   */
  private void logResult(@NotNull MethodDef methodDef, @NotNull MethodLogger methodLogger, Object result)
  {
    try {
      final var resultLevel = methodDef.resultLevel;

      if (methodLogger.isLogEnabled(resultLevel))
      {
        final var messageConfigurer = annotationMethodLoggingSource
            .getMessageSupport()
            .message(methodDef.resultMessage.get());

        String message;

        try {
          message = messageConfigurer.with("result", result).format();
        } catch(Throwable ex) {
          rethrowIfFatal(ex);
          // e.g. a failing toString() on the result
          message = messageConfigurer.with("result", formatFailed(ex)).format();
        }

        methodLogger.log(resultLevel, message, methodDef.location);
      }
    } catch(Throwable ex) {
      rethrowIfFatal(ex);
      logFailure(methodDef, "result", ex);
    }
  }


  /**
   * Returns the text logged in place of a value which cannot be formatted.
   *
   * @param ex  exception thrown while formatting, not {@code null}
   *
   * @return  format failure text naming the exception, never {@code null}
   */
  @Contract(pure = true)
  private static @NotNull String formatFailed(@NotNull Throwable ex) {
    return "<format failed: " + exceptionName(getMostSpecificCause(ex).getClass()) + '>';
  }


  /**
   * Returns the name of the exception class without package. Nested classes include the enclosing classes
   * ({@code Outer.Inner}); classes without canonical name, like anonymous classes, use the binary name
   * ({@code Outer$1}).
   *
   * @param exceptionClass  exception class, not {@code null}
   *
   * @return  exception class name without package, never {@code null}
   */
  @Contract(pure = true)
  static @NotNull String exceptionName(@NotNull Class<?> exceptionClass)
  {
    final var canonicalName = exceptionClass.getCanonicalName();
    final var name = canonicalName != null ? canonicalName : exceptionClass.getName();
    final var packageName = exceptionClass.getPackageName();

    return packageName.isEmpty() ? name : name.substring(packageName.length() + 1);
  }


  /**
   * Reports a method logging failure: the first failure of {@code phase} for the method definition at WARN level, all
   * subsequent failures of that phase at DEBUG level.
   *
   * @param methodDef  method logging definition, not {@code null}
   * @param phase      logging phase which failed, not {@code null}
   * @param ex         exception raised while logging, not {@code null}
   */
  private static void logFailure(@NotNull MethodDef methodDef, @NotNull String phase, @NotNull Throwable ex)
  {
    final var message = "Method logging (" + phase + ") failed for method '" + methodDef.methodName + '\'';

    if (methodDef.isFirstFailureReport(phase))
      LOG.warn(message + "; subsequent failures are reported at DEBUG level", ex);
    else if (LOG.isDebugEnabled())
      LOG.debug(message, ex);
  }


  /**
   * Rethrows {@code throwable} if it is a virtual machine error the application cannot recover from, like an
   * {@link OutOfMemoryError}. All other throwables are handled as method logging failures, including a
   * {@link StackOverflowError} (e.g. from a cyclic {@code toString()}) and linkage errors (e.g. from a logging
   * framework not visible to this library).
   *
   * @param throwable  throwable caught while logging, not {@code null}
   */
  private static void rethrowIfFatal(@NotNull Throwable throwable)
  {
    if (throwable instanceof VirtualMachineError error && !(throwable instanceof StackOverflowError))
      throw error;
  }
}
