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

import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.asm.*;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.MethodClassKey;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility.SHOW;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.springframework.aop.support.AopUtils.getMostSpecificMethod;
import static org.springframework.asm.ClassReader.SKIP_FRAMES;
import static org.springframework.asm.SpringAsmInfo.ASM_VERSION;
import static org.springframework.core.ResolvableType.forMethodParameter;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotationAttributes;
import static org.springframework.core.annotation.AnnotatedElementUtils.getMergedAnnotation;
import static org.springframework.core.annotation.AnnotationUtils.synthesizeAnnotation;
import static org.springframework.util.StringUtils.hasLength;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
public final class AnnotationMethodLoggingSource
{
  private final @NotNull Map<MethodClassKey,MethodDef> methodLoggingDefinitionCache;
  private final @NotNull LocalVariableTableParameterNameDiscoverer nameDiscoverer;
  final @NotNull MethodLoggingConfigurer methodLoggingConfigurer;


  AnnotationMethodLoggingSource(@NotNull MethodLoggingConfigurer methodLoggingConfigurer)
  {
    this.methodLoggingConfigurer = methodLoggingConfigurer;

    methodLoggingDefinitionCache = new ConcurrentHashMap<>();
    nameDiscoverer = new LocalVariableTableParameterNameDiscoverer();
  }


  @Contract(pure = true)
  MethodDef getMethodDefinition(@NotNull Method method, Class<?> targetClass)
  {
    final MethodClassKey cacheKey = new MethodClassKey(method, targetClass);
    MethodDef methodLoggingDefinition = methodLoggingDefinitionCache.get(cacheKey);

    if (methodLoggingDefinition == null &&
        (methodLoggingDefinition = analyseMethodDefinition(method, targetClass)) != null)
      methodLoggingDefinitionCache.put(cacheKey, methodLoggingDefinition);

    return methodLoggingDefinition;
  }


  @Contract(pure = true)
  private MethodDef analyseMethodDefinition(@NotNull Method method, @NotNull Class<?> targetClass)
  {
    final AnnotationAttributes methodLoggingAttributes =
        findMergedAnnotationAttributes(getMostSpecificMethod(method, targetClass),
            MethodLogging.class, false, true);

    if (methodLoggingAttributes != null)
    {
      final AnnotationAttributes methodLoggingConfigAttributes = findMethodLoggingConfigAttributes(targetClass);
      final MethodLogging methodLogging =
          findMergedMethodLogging(methodLoggingConfigAttributes, methodLoggingAttributes);
      final String[] parameterNames = nameDiscoverer.getParameterNames(method);
      final List<ParameterDef> parameterDefs;

      if (parameterNames != null && parameterNames.length > 0 && methodLogging.parameters() == SHOW)
      {
        parameterDefs = new ArrayList<>(8);
        final Parameter[] parameters = method.getParameters();
        final List<String> excludeParameters = asList(methodLogging.exclude());

        for(int p = 0; p < parameterNames.length; p++)
        {
          final ParamLog paramLog = getMergedAnnotation(parameters[p], ParamLog.class);
          final ParameterDef def = new ParameterDef();

          if (!hasLength(def.name = paramLog != null ? paramLog.name() : ""))
            def.name = parameterNames[p];

          final ResolvableType methodParameterType = forMethodParameter(method, p);
          final boolean exclude = excludeParameters.contains(def.name) ||
              (paramLog == null &&
                  (!methodParameterType.toClass().isPrimitive() || methodParameterType.isArray()) &&
                  methodLoggingConfigurer.excludeMethodParameter(methodParameterType));

          if (!exclude)
          {
            def.index = p;
            def.inline = paramLog == null || paramLog.inline();

            if (!hasLength(def.format = paramLog != null ? paramLog.format() : ""))
              def.format = "%{value}";

            parameterDefs.add(def);
          }
        }

        ((ArrayList<?>)parameterDefs).trimToSize();
      }
      else
        parameterDefs = emptyList();

      return new MethodDef(
          synthesizeAnnotation(methodLoggingConfigAttributes, MethodLoggingConfig.class, targetClass),
          parameterDefs, methodLogging, method,
          methodLogging.lineNumber() == SHOW ? findMethodLineNumber(method) : -1,
          findLoggerField(method.getDeclaringClass(), methodLogging));
    }

    return null;
  }


  @Contract(pure = true)
  private @NotNull AnnotationAttributes findMethodLoggingConfigAttributes(@NotNull Class<?> type)
  {
    AnnotationAttributes attributes =
        findMergedAnnotationAttributes(type, MethodLoggingConfig.class, false, true);

    if (attributes == null)
      attributes = createDefaultMethodLoggingConfigAttributes();

    if ("<DEFAULT>".equals(attributes.getString("loggerFieldName")))
      attributes.put("loggerFieldName", methodLoggingConfigurer.defaultLoggerFieldName());

    if (attributes.getEnum("entryExitLevel") == Level.DEFAULT)
      attributes.put("entryExitLevel", methodLoggingConfigurer.defaultEntryExitLevel());

    if (attributes.getEnum("parameterLevel") == Level.DEFAULT)
      attributes.put("parameterLevel", methodLoggingConfigurer.defaultParameterLevel());

    if (attributes.getEnum("resultLevel") == Level.DEFAULT)
      attributes.put("resultLevel", methodLoggingConfigurer.defaultResultLevel());

    if (attributes.getEnum("lineNumber") == Visibility.DEFAULT)
      attributes.put("lineNumber", methodLoggingConfigurer.defaultLineNumber());

    return attributes;
  }


  @Contract(pure = true)
  private @NotNull AnnotationAttributes createDefaultMethodLoggingConfigAttributes()
  {
    final AnnotationAttributes attributes = new AnnotationAttributes(MethodLoggingConfig.class);

    for(final Method m: MethodLoggingConfig.class.getDeclaredMethods())
      if (m.getReturnType() != void.class && m.getParameterCount() == 0)
        attributes.put(m.getName(), m.getDefaultValue());

    return attributes;
  }


  private @NotNull MethodLogging findMergedMethodLogging(
      @NotNull AnnotationAttributes methodLoggingConfigAttributes,
      @NotNull AnnotationAttributes methodLoggingAttributes)
  {
    for(final Entry<String,Object> methodAttribute: methodLoggingAttributes.entrySet())
    {
      final String name = methodAttribute.getKey();
      final Object value = methodAttribute.getValue();

      if (value == Visibility.DEFAULT || value == Level.DEFAULT)
        methodLoggingAttributes.put(name, methodLoggingConfigAttributes.getEnum(name));
      else if ("<DEFAULT>".equals(value))
        methodLoggingAttributes.put(name, methodLoggingConfigAttributes.getString(name));
    }

    return synthesizeAnnotation(methodLoggingAttributes, MethodLogging.class, null);
  }


  @Contract(pure = true)
  private int findMethodLineNumber(@NotNull Method method)
  {
    final Class<?> declaringClass = method.getDeclaringClass();
    final String classResourceName =
        declaringClass.getName().replace('.', '/') + ".class";
    final String methodDescriptor = method.getName().concat(Type.getMethodDescriptor(method));

    final AtomicInteger lineNumber = new AtomicInteger(-1);
    final MethodVisitor methodVisitor = new MethodVisitor(ASM_VERSION) {
      @Override
      public void visitLineNumber(int line, Label start) {
        lineNumber.compareAndSet(-1, line);
      }
    };

    try(final InputStream classInputStream = requireNonNull(
        declaringClass.getClassLoader().getResourceAsStream(classResourceName))) {
      new ClassReader(classInputStream).accept(new ClassVisitor(ASM_VERSION) {
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] ex) {
          return methodDescriptor.equals(name.concat(descriptor)) ? methodVisitor : null;
        }
      }, SKIP_FRAMES);
    } catch(NullPointerException | IOException ignored) {
    }

    return lineNumber.get();
  }


  private Field findLoggerField(Class<?> clazz, @NotNull MethodLogging methodLogging)
  {
    final String loggerFieldName = methodLogging.loggerFieldName();

    if (!loggerFieldName.isEmpty())
      for(; clazz != Object.class && clazz != null; clazz = clazz.getSuperclass())
      {
        try {
          final Field field = clazz.getDeclaredField(loggerFieldName);
          field.setAccessible(true);

          return field;
        } catch(Exception ignored) {
        }
      }

    return null;
  }
}
