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

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import lombok.val;
import lombok.var;
import org.jetbrains.annotations.NotNull;
import org.springframework.asm.*;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.MethodClassKey;
import org.springframework.core.annotation.AnnotationAttributes;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility.SHOW;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.springframework.asm.ClassReader.SKIP_FRAMES;
import static org.springframework.asm.SpringAsmInfo.ASM_VERSION;
import static org.springframework.core.annotation.AnnotatedElementUtils.getMergedAnnotation;
import static org.springframework.core.annotation.AnnotatedElementUtils.getMergedAnnotationAttributes;
import static org.springframework.core.annotation.AnnotationUtils.synthesizeAnnotation;
import static org.springframework.util.StringUtils.hasLength;


/**
 * @author Jeroen Gremmen
 * @version 0.1.0
 */
final class AnnotationMethodLoggingSource
{
  private final Map<MethodClassKey,MethodLoggingDef> methodLoggingDefinitionCache;
  private final LocalVariableTableParameterNameDiscoverer nameDiscoverer;


  AnnotationMethodLoggingSource()
  {
    methodLoggingDefinitionCache = new ConcurrentHashMap<>();
    nameDiscoverer = new LocalVariableTableParameterNameDiscoverer();
  }


  public @NotNull Optional<MethodLoggingDef> getMethodLoggingDefinition(@NotNull Method method, Class<?> targetClass)
  {
    val cacheKey = new MethodClassKey(method, targetClass);
    var methodLoggingDefinition = methodLoggingDefinitionCache.get(cacheKey);

    if (methodLoggingDefinition == null &&
        (methodLoggingDefinition = findMethodLoggingDefinition(method)) != null)
      methodLoggingDefinitionCache.put(cacheKey, methodLoggingDefinition);

    return Optional.ofNullable(methodLoggingDefinition);
  }


  private MethodLoggingDef findMethodLoggingDefinition(@NotNull Method method)
  {
    if (method.isAnnotationPresent(MethodLogging.class))
    {
      val classType = method.getDeclaringClass();
      val methodLoggingConfigAttributes = findMethodLoggingConfigAttributes(classType);
      val methodLogging = findMethodLogging(methodLoggingConfigAttributes, method);
      val parameterNames = nameDiscoverer.getParameterNames(method);
      val parameterDefs = new ArrayList<ParameterDef>(8);

      if (parameterNames != null && methodLogging.parameters() == SHOW)
      {
        val parameters = method.getParameters();
        val excludeParameters = asList(methodLogging.exclude());

        for(int p = 0; p < parameterNames.length; p++)
        {
          val paramLog = getMergedAnnotation(parameters[p], ParamLog.class);
          if (paramLog == null || !paramLog.hide())
          {
            val def = new ParameterDef();

            if (!hasLength(def.name = paramLog != null ? paramLog.name() : ""))
              def.name = parameterNames[p];
            if (!excludeParameters.contains(def.name))
            {
              def.index = p;
              def.inMethod = paramLog != null && paramLog.inMethod();

              if (!hasLength(def.format = paramLog != null ? paramLog.format() : ""))
                def.format = "%{value}";

              parameterDefs.add(def);
            }
          }
        }

        parameterDefs.trimToSize();

        return new MethodLoggingDef(
            synthesizeAnnotation(methodLoggingConfigAttributes, MethodLoggingConfig.class, classType),
            parameterDefs, methodLogging, method,
            methodLogging.lineNumber() == SHOW ? findMethodLineNumber(method) : -1,
            findLoggerField(method.getDeclaringClass(), methodLogging));
      }
    }

    return null;
  }


  private @NotNull AnnotationAttributes findMethodLoggingConfigAttributes(@NotNull Class<?> classType)
  {
    var attributes = getMergedAnnotationAttributes(classType, MethodLoggingConfig.class);
    if (attributes == null)
      attributes = new AnnotationAttributes(MethodLoggingConfig.class);

    for(val m: MethodLoggingConfig.class.getDeclaredMethods())
    {
      val attributeType = m.getReturnType();

      if (attributeType != void.class && m.getParameterCount() == 0)
      {
        val name = m.getName();

        if (!attributes.containsKey(name) ||
            (attributeType == Visibility.class && attributes.getEnum(name) == Visibility.DEFAULT) ||
            (attributeType == Level.class && attributes.getEnum(name) == Level.DEFAULT) ||
            (attributeType == String.class && "<DEFAULT>".equals(attributes.getString(name))))
          attributes.put(name, m.getDefaultValue());
      }
    }

    return attributes;
  }


  private @NotNull MethodLogging findMethodLogging(@NotNull AnnotationAttributes methodLoggingConfigAttributes,
                                                   @NotNull Method method)
  {
    val methodAttributes = requireNonNull(getMergedAnnotationAttributes(method, MethodLogging.class));

    for(val methodAttribute: methodAttributes.entrySet())
    {
      val name = methodAttribute.getKey();
      val value = methodAttribute.getValue();

      if (value == Visibility.DEFAULT || value == Level.DEFAULT)
        methodAttributes.put(name, methodLoggingConfigAttributes.getEnum(name));
      else if ("<DEFAULT>".equals(value))
        methodAttributes.put(name, methodLoggingConfigAttributes.getString(name));
    }

    return synthesizeAnnotation(methodAttributes, MethodLogging.class, method);
  }


  private int findMethodLineNumber(Method method)
  {
    val declaringClass = method.getDeclaringClass();
    val classResourceName = declaringClass.getName().replace('.', '/') + ".class";
    val methodDescriptor = method.getName().concat(Type.getMethodDescriptor(method));

    val lineNumber = new AtomicInteger(-1);
    val methodVisitor = new MethodVisitor(ASM_VERSION) {
      @Override
      public void visitLineNumber(int line, Label start) {
        lineNumber.compareAndSet(-1, line);
      }
    };

    try(val classInputStream = requireNonNull(
        declaringClass.getClassLoader().getResourceAsStream(classResourceName))) {
      new ClassReader(classInputStream).accept(new ClassVisitor(ASM_VERSION) {
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] ex) {
          return methodDescriptor.equals(name.concat(descriptor)) ? methodVisitor : null;
        }
      }, SKIP_FRAMES);
    } catch(IOException ignored) {
    }

    return lineNumber.get();
  }


  private Field findLoggerField(Class<?> clazz, @NotNull MethodLogging methodLogging)
  {
    val loggerFieldName = methodLogging.loggerFieldName();

    if (!loggerFieldName.isEmpty())
      for(; clazz != Object.class && clazz != null; clazz = clazz.getSuperclass())
      {
        try {
          val field = clazz.getDeclaredField(loggerFieldName);
          field.setAccessible(true);

          return field;
        } catch(Exception ignored) {
        }
      }

    return null;
  }
}