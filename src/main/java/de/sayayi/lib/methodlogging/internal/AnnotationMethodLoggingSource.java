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
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodClassKey;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationAttributes;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
  private final @NotNull ParameterNameDiscoverer nameDiscoverer;
  final @NotNull MethodLoggingConfigurer methodLoggingConfigurer;


  AnnotationMethodLoggingSource(@NotNull MethodLoggingConfigurer methodLoggingConfigurer)
  {
    this.methodLoggingConfigurer = methodLoggingConfigurer;

    methodLoggingDefinitionCache = new ConcurrentHashMap<>();
    nameDiscoverer = new DefaultParameterNameDiscoverer();
  }


  @Contract(pure = true)
  MethodDef getMethodDefinition(@NotNull Method method, Class<?> targetClass)
  {
    return methodLoggingDefinitionCache.computeIfAbsent(
        new MethodClassKey(method, targetClass),
        methodClassKey -> analyseMethodDefinition(method, targetClass));
  }


  @Contract(pure = true)
  private MethodDef analyseMethodDefinition(@NotNull Method method, @NotNull Class<?> targetClass)
  {
    var methodLoggingAttributes =
        findMergedAnnotationAttributes(getMostSpecificMethod(method, targetClass),
            MethodLogging.class, false, true);

    if (methodLoggingAttributes == null)
      return null;

    var methodLoggingConfigAttributes = findMethodLoggingConfigAttributes(targetClass);
    var methodLogging = findMergedMethodLogging(methodLoggingConfigAttributes, methodLoggingAttributes);

    return new MethodDef(
        synthesizeAnnotation(methodLoggingConfigAttributes, MethodLoggingConfig.class, targetClass),
        getParameterDefs(method, methodLogging), methodLogging, method,
        methodLogging.lineNumber() == SHOW ? findMethodLineNumber(method) : -1,
        findLoggerField(method.getDeclaringClass(), methodLogging));
  }


  @Contract(pure = true)
  private @NotNull List<ParameterDef> getParameterDefs(@NotNull Method method,
                                                       @NotNull MethodLogging methodLogging)
  {
    var parameterNames = nameDiscoverer.getParameterNames(method);
    if (parameterNames == null || parameterNames.length == 0 || methodLogging.parameters() != SHOW)
      return emptyList();

    var parameterDefs = new ArrayList<ParameterDef>(8);
    var parameters = method.getParameters();
    var excludeParameters = asList(methodLogging.exclude());

    for(int p = 0; p < parameterNames.length; p++)
    {
      var paramLog = getMergedAnnotation(parameters[p], ParamLog.class);
      var parameterDef = new ParameterDef();

      if (!hasLength(parameterDef.name = paramLog != null ? paramLog.name() : ""))
        parameterDef.name = parameterNames[p];

      if (!excludeParameters.contains(parameterDef.name) &&
          (paramLog != null || isParameterIncluded(forMethodParameter(method, p))))
      {
        parameterDef.index = p;
        parameterDef.inline = paramLog == null || paramLog.inline();

        if (!hasLength(parameterDef.format = paramLog != null ? paramLog.format() : ""))
          parameterDef.format = "%{value}";

        parameterDefs.add(parameterDef);
      }
    }

    parameterDefs.trimToSize();

    return parameterDefs;
  }


  private boolean isParameterIncluded(@NotNull ResolvableType methodParameterType)
  {
    return
        (methodParameterType.toClass().isPrimitive() && !methodParameterType.isArray()) ||
        !methodLoggingConfigurer.excludeMethodParameter(methodParameterType);
  }


  @Contract(pure = true)
  private @NotNull AnnotationAttributes findMethodLoggingConfigAttributes(@NotNull Class<?> type)
  {
    var attributes =
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
    var attributes = new AnnotationAttributes(MethodLoggingConfig.class);

    for(var annotationMethod: MethodLoggingConfig.class.getDeclaredMethods())
      if (annotationMethod.getReturnType() != void.class && annotationMethod.getParameterCount() == 0)
        attributes.put(annotationMethod.getName(), annotationMethod.getDefaultValue());

    return attributes;
  }


  private @NotNull MethodLogging findMergedMethodLogging(
      @NotNull AnnotationAttributes methodLoggingConfigAttributes,
      @NotNull AnnotationAttributes methodLoggingAttributes)
  {
    for(var methodAttribute: methodLoggingAttributes.entrySet())
    {
      var value = methodAttribute.getValue();

      if (value == Visibility.DEFAULT || value == Level.DEFAULT)
        methodAttribute.setValue(methodLoggingConfigAttributes.getEnum(methodAttribute.getKey()));
      else if ("<DEFAULT>".equals(value))
        methodAttribute.setValue(methodLoggingConfigAttributes.getString(methodAttribute.getKey()));
    }

    return synthesizeAnnotation(methodLoggingAttributes, MethodLogging.class, null);
  }


  @Contract(pure = true)
  private int findMethodLineNumber(@NotNull Method method)
  {
    var declaringClass = method.getDeclaringClass();
    var classResourceName = declaringClass.getName().replace('.', '/') + ".class";
    var methodDescriptor = method.getName().concat(Type.getMethodDescriptor(method));

    var lineNumber = new AtomicInteger(-1);
    var methodVisitor = new MethodVisitor(ASM_VERSION) {
      @Override
      public void visitLineNumber(int line, Label start) {
        lineNumber.compareAndSet(-1, line);
      }
    };

    try(var classInputStream = requireNonNull(
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
    var loggerFieldName = methodLogging.loggerFieldName();

    if (!loggerFieldName.isEmpty())
      for(; clazz != Object.class && clazz != null; clazz = clazz.getSuperclass())
      {
        try {
          var field = clazz.getDeclaredField(loggerFieldName);
          field.setAccessible(true);

          return field;
        } catch(Exception ignored) {
        }
      }

    return null;
  }
}
