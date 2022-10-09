package de.sayayi.lib.methodlogging.internal;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import lombok.val;
import lombok.var;
import org.jetbrains.annotations.NotNull;
import org.springframework.asm.*;
import org.springframework.context.annotation.Role;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.MethodClassKey;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;
import static org.springframework.asm.ClassReader.SKIP_FRAMES;
import static org.springframework.asm.SpringAsmInfo.ASM_VERSION;
import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;
import static org.springframework.core.annotation.AnnotatedElementUtils.getMergedAnnotation;
import static org.springframework.util.StringUtils.hasLength;


@Component
@Role(ROLE_INFRASTRUCTURE)
public class AnnotationMethodLoggingSource
{
  private final Map<MethodClassKey,MethodLoggingDef> methodLoggingDefinitionCache;
  private final LocalVariableTableParameterNameDiscoverer nameDiscoverer;


  public AnnotationMethodLoggingSource()
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
      val methodLogging = requireNonNull(getMergedAnnotation(method, MethodLogging.class));
      val parameterNames = nameDiscoverer.getParameterNames(method);
      val parameterDefs = new ArrayList<ParameterDef>(8);

      if (parameterNames != null && methodLogging.withParameters())
      {
        val parameters = method.getParameters();

        for(int p = 0; p < parameterNames.length; p++)
        {
          val paramLogAnnotation = getMergedAnnotation(parameters[p], ParamLog.class);
          if (paramLogAnnotation == null || !paramLogAnnotation.hide())
          {
            val def = new ParameterDef();

            def.index = p;
            def.inMethod = paramLogAnnotation != null && paramLogAnnotation.inMethod();

            if (!hasLength(def.name = paramLogAnnotation != null ? paramLogAnnotation.name() : ""))
              def.name = parameterNames[p];
            if (!hasLength(def.format = paramLogAnnotation != null ? paramLogAnnotation.format() : ""))
             def.format = "%{value}";

            parameterDefs.add(def);
          }
        }

        parameterDefs.trimToSize();

        return new MethodLoggingDef(parameterDefs, methodLogging, method,
            findMethodLineNumber(method), findLoggerAccessor(method.getDeclaringClass(), methodLogging));
      }
    }

    return null;
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


  private Field findLoggerAccessor(Class<?> clazz, MethodLogging methodLogging)
  {
    val loggerFieldName = methodLogging.loggerFieldName();

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