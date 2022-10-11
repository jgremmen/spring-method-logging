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
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.logger.GenericMethodLoggerFactory;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.StringJoiner;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PROTECTED;
import static org.springframework.aop.framework.AopProxyUtils.ultimateTargetClass;
import static org.springframework.util.StringUtils.hasLength;


/**
 * @author Jeroen Gremmen
 * @version 0.1.0
 */
@RequiredArgsConstructor(access = PROTECTED)
class MethodLoggingInterceptor implements MethodInterceptor, InitializingBean
{
  private final AnnotationMethodLoggingSource annotationMethodLoggingSource;

  @Autowired(required = false)
  @SuppressWarnings("SpringJavaAutowiredMembersInspection")
  private MethodLoggingConfigurer methodLoggingConfigurer;

  private MessageContext messageContext;
  private MethodLoggerFactory methodLoggerFactory;


  @Override
  public void afterPropertiesSet()
  {
    if (methodLoggingConfigurer == null)
      methodLoggingConfigurer = new MethodLoggingConfigurer() {};

    if ((messageContext = methodLoggingConfigurer.messageContext()) == null)
    {
      messageContext = new MessageContext(DefaultFormatterService.getSharedInstance(),
          new MessageFactory(new LRUMessagePartNormalizer(64)));
    }

    if ((methodLoggerFactory = methodLoggingConfigurer.methodLoggerFactory()) == null)
      methodLoggerFactory = new GenericMethodLoggerFactory();
  }


  @Override
  public Object invoke(@NotNull MethodInvocation invocation) throws Throwable
  {
    val _this = requireNonNull(invocation.getThis());

    //noinspection OptionalGetWithoutIsPresent
    val methodLoggingDef = annotationMethodLoggingSource
        .getMethodLoggingDefinition(invocation.getMethod(), ultimateTargetClass(_this)).get();

    val methodLogger = methodLoggerFactory.from(methodLoggingDef.loggerField, _this);
    if (methodLogger.isLogEnabled(methodLoggingDef.entryExitLevel))
    {
      val startTime = currentTimeMillis();
      Throwable throwable = null;

      logMethodEntry(methodLoggingDef, invocation.getArguments(), methodLogger);
      try {
        return methodLoggingDef.showResult
            ? logResult(methodLoggingDef, methodLogger, invocation.proceed())
            : invocation.proceed();
      } catch(Throwable ex) {
        throw throwable = ex;
      } finally {
        logMethodExit(methodLoggingDef, methodLogger, startTime, throwable);
      }
    }
    else
      return invocation.proceed();
  }


  private void logMethodEntry(@NotNull MethodLoggingDef methodLoggingDef, @NotNull Object[] arguments,
                              @NotNull MethodLogger methodLogger)
  {
    val parameters = messageContext.parameters();
    val method = new StringBuilder(methodLoggingDef.methodEntryPrefix)
        .append(methodLoggingDef.methodName);
    val printParameters = methodLogger.isLogEnabled(methodLoggingDef.parameterLevel);

    if (printParameters && !methodLoggingDef.inlineParameters.isEmpty())
    {
      val parameterList = new StringJoiner(",", "(", ")");
      for(val pd: methodLoggingDef.inlineParameters)
        parameterList.add(logMethodEntry_parameter(methodLoggingDef, pd, parameters, arguments[pd.index]));

      method.append(parameterList);
    }

    if (methodLoggingDef.line > 0)
      method.append(':').append(methodLoggingDef.line);

    methodLogger.log(methodLoggingDef.entryExitLevel, method.toString());

    if (printParameters && !methodLoggingDef.inMethodParameters.isEmpty())
      for(val pd: methodLoggingDef.inMethodParameters)
      {
        methodLogger.log(methodLoggingDef.parameterLevel,
            logMethodEntry_parameter(methodLoggingDef, pd, parameters, arguments[pd.index]));
      }
  }


  private @NotNull String logMethodEntry_parameter(@NotNull MethodLoggingDef methodLoggingDef,
                                                   @NotNull ParameterDef parameterDef,
                                                   @NotNull ParameterBuilder parameters, Object value)
  {
    return methodLoggingDef.getParameterMessage(messageContext).format(messageContext, parameters
        .with("parameter", parameterDef.name)
        .with("value", parameterDef.getFormatMessage(messageContext)
            .format(messageContext, singletonMap("value", value))));
  }


  private void logMethodExit(@NotNull MethodLoggingDef methodLoggingDef, @NotNull MethodLogger methodLogger,
                             long startTime, Throwable throwable)
  {
    val exit = new StringBuilder(methodLoggingDef.methodExitPrefix)
        .append(methodLoggingDef.methodName);

    if (methodLoggingDef.line > 0)
      exit.append(':').append(methodLoggingDef.line);

    if (methodLoggingDef.showElapsedTime)
      exit.append(" (elapsed ").append(logMethodExit_elapsed(currentTimeMillis() - startTime)).append(')');

    if (throwable != null)
    {
      exit.append(" -> ").append(throwable.getClass().getSimpleName());

      val msg = throwable.getLocalizedMessage();

      if (hasLength(msg))
        exit.append('(').append(msg).append(')');
    }

    methodLogger.log(methodLoggingDef.entryExitLevel, exit.toString());
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
  private Object logResult(@NotNull MethodLoggingDef methodLoggingDef, @NotNull MethodLogger methodLogger,
                           Object result)
  {
    val resultLevel = methodLoggingDef.resultLevel;

    if (methodLogger.isLogEnabled(resultLevel))
    {
      methodLogger.log(resultLevel, methodLoggingDef
          .getResultMessage(messageContext)
          .format(messageContext, singletonMap("result", result)));
    }

    return result;
  }
}