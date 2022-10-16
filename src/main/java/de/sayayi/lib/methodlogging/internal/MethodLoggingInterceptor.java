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

import de.sayayi.lib.message.MessageContext;
import de.sayayi.lib.message.MessageContext.ParameterBuilder;
import de.sayayi.lib.message.MessageFactory;
import de.sayayi.lib.message.formatter.DefaultFormatterService;
import de.sayayi.lib.message.parser.normalizer.LRUMessagePartNormalizer;
import de.sayayi.lib.methodlogging.MethodLogger;
import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import de.sayayi.lib.methodlogging.logger.AutoDetectLoggerFactory;
import lombok.val;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.ResourceLoader;

import java.util.StringJoiner;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static org.springframework.aop.framework.AopProxyUtils.ultimateTargetClass;
import static org.springframework.util.StringUtils.hasLength;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
public final class MethodLoggingInterceptor implements MethodInterceptor
{
  private final AnnotationMethodLoggingSource annotationMethodLoggingSource;

  private MessageContext messageContext;
  private MethodLoggerFactory methodLoggerFactory;


  MethodLoggingInterceptor(@NotNull AnnotationMethodLoggingSource annotationMethodLoggingSource,
                           @NotNull ResourceLoader resourceLoader)
  {
    this.annotationMethodLoggingSource = annotationMethodLoggingSource;

    val methodLoggingConfigurer = annotationMethodLoggingSource.methodLoggingConfigurer;
    val classLoader = resourceLoader.getClassLoader();

    if ((messageContext = methodLoggingConfigurer.messageContext()) == null)
    {
      messageContext = new MessageContext(new DefaultFormatterService(classLoader),
          new MessageFactory(new LRUMessagePartNormalizer(64)));
    }

    if ((methodLoggerFactory = methodLoggingConfigurer.methodLoggerFactory()) == null)
      methodLoggerFactory = AutoDetectLoggerFactory.INSTANCE;
  }


  @Override
  public Object invoke(@NotNull MethodInvocation invocation) throws Throwable
  {
    val _this = requireNonNull(invocation.getThis());
    val thisType = ultimateTargetClass(_this);
    val methodDef = annotationMethodLoggingSource.getMethodDefinition(invocation.getMethod(), thisType);

    val methodLogger = methodLoggerFactory.from(methodDef.loggerField, _this, thisType);
    if (!methodLogger.isLogEnabled(methodDef.entryExitLevel))
      return invocation.proceed();

    val startTime = currentTimeMillis();
    Throwable throwable = null;

    logMethodEntry(methodDef, invocation.getArguments(), methodLogger);
    try {
      return methodDef.showResult
          ? logResult(methodDef, methodLogger, invocation.proceed())
          : invocation.proceed();
    } catch(Throwable ex) {
      throw throwable = ex;
    } finally {
      logMethodExit(methodDef, methodLogger, startTime, throwable);
    }
  }


  private void logMethodEntry(@NotNull MethodDef methodDef, @NotNull Object[] arguments,
                              @NotNull MethodLogger methodLogger)
  {
    val parameters = messageContext.parameters();
    val printParameters = methodLogger.isLogEnabled(methodDef.parameterLevel);
    val method = new StringBuilder(methodDef.methodEntryPrefix).append(methodDef.methodName);

    if (printParameters && !methodDef.inlineParameters.isEmpty())
    {
      val parameterList = new StringJoiner(",", "(", ")");
      for(val parameterDef: methodDef.inlineParameters)
      {
        parameterList.add(
            logMethodEntry_inlineParameter(methodDef, parameterDef, parameters, arguments[parameterDef.index]));
      }

      method.append(parameterList);
    }

    if (methodDef.line > 0)
      method.append(':').append(methodDef.line);

    methodLogger.log(methodDef.entryExitLevel, method.toString());

    if (printParameters && !methodDef.inMethodParameters.isEmpty())
      for(val parameterDef: methodDef.inMethodParameters)
      {
        methodLogger.log(methodDef.parameterLevel,
            logMethodEntry_parameter(methodDef, parameterDef, parameters, arguments[parameterDef.index]));
      }
  }


  private @NotNull String logMethodEntry_inlineParameter(@NotNull MethodDef methodDef,
                                                         @NotNull ParameterDef parameterDef,
                                                         @NotNull ParameterBuilder parameters, Object value)
  {
    return methodDef.getInlineParameterMessage(messageContext).format(messageContext, parameters
        .with("parameter", parameterDef.name)
        .with("value", parameterDef.getFormatMessage(messageContext)
            .format(messageContext, singletonMap("value", value))));
  }


  private @NotNull String logMethodEntry_parameter(@NotNull MethodDef methodDef,
                                                   @NotNull ParameterDef parameterDef,
                                                   @NotNull ParameterBuilder parameters, Object value)
  {
    return methodDef.getParameterMessage(messageContext).format(messageContext, parameters
        .with("parameter", parameterDef.name)
        .with("value", parameterDef.getFormatMessage(messageContext)
            .format(messageContext, singletonMap("value", value))));
  }


  private void logMethodExit(@NotNull MethodDef methodDef, @NotNull MethodLogger methodLogger,
                             long startTime, Throwable throwable)
  {
    val exit = new StringBuilder(methodDef.methodExitPrefix)
        .append(methodDef.methodName);

    if (methodDef.line > 0)
      exit.append(':').append(methodDef.line);

    if (methodDef.showElapsedTime)
      exit.append(" (elapsed ").append(logMethodExit_elapsed(currentTimeMillis() - startTime)).append(')');

    if (throwable != null)
    {
      exit.append(" -> ").append(throwable.getClass().getSimpleName());

      val msg = throwable.getLocalizedMessage();

      if (hasLength(msg))
        exit.append('(').append(msg).append(')');
    }

    methodLogger.log(methodDef.entryExitLevel, exit.toString());
  }


  private @NotNull String logMethodExit_elapsed(long millis)
  {
/*
    h|m|s|ms           h|m|s|ms
    0|0|0|0 -> ms      1|0|0|0 -> h,m
    0|0|0|1 -> ms      1|0|0|1 -> h,m
    0|0|1|0 -> s       1|0|1|0 -> h,m
    0|0|1|1 -> s,ms    1|0|1|1 -> h,m
    0|1|0|0 -> m       1|1|0|0 -> h,m
    0|1|0|1 -> m,s     1|1|0|1 -> h,m
    0|1|1|0 -> m,s     1|1|1|0 -> h,m
    0|1|1|1 -> m,s     1|1|1|1 -> h,m
 */
    val s = new StringBuilder();
    val hour = (millis / 3600000L) % 60;
    val min = (millis / 60000L) % 60;

    if (hour > 0)
      s.append(hour).append('h').append(min).append('m');
    else
    {
      if (min > 0)
        s.append(min).append('m');

      val sec = (millis / 1000L) % 60;
      val msec = millis % 1000;

      if (sec > 0 || (min > 0 && msec > 0))
        s.append(sec).append('s');
      if (min == 0 && (sec == 0 || msec > 0))
        s.append(msec).append("ms");
    }

    return s.toString();
  }


  @Contract("_, _, _ -> param3")
  private Object logResult(@NotNull MethodDef methodDef, @NotNull MethodLogger methodLogger, Object result)
  {
    val resultLevel = methodDef.resultLevel;

    if (methodLogger.isLogEnabled(resultLevel))
    {
      methodLogger.log(resultLevel, methodDef
          .getResultMessage(messageContext)
          .format(messageContext, singletonMap("result", result)));
    }

    return result;
  }
}