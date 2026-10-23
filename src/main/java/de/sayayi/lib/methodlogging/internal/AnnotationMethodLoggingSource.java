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
import de.sayayi.lib.message.util.SupplierDelegate;
import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.Visibility;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import de.sayayi.lib.methodlogging.logger.AutoDetectLoggerFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.asm.*;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodClassKey;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.function.SingletonSupplier;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static de.sayayi.lib.methodlogging.Visibility.SHOW;
import static de.sayayi.lib.methodlogging.annotation.MethodLogging.DEFAULT_VALUE;
import static java.util.Arrays.asList;
import static java.util.List.copyOf;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;
import static org.springframework.aop.support.AopUtils.getMostSpecificMethod;
import static org.springframework.asm.ClassReader.SKIP_FRAMES;
import static org.springframework.asm.SpringAsmInfo.ASM_VERSION;
import static org.springframework.core.ResolvableType.forMethodParameter;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotationAttributes;
import static org.springframework.core.annotation.AnnotatedElementUtils.getMergedAnnotation;
import static org.springframework.core.annotation.AnnotationUtils.synthesizeAnnotation;
import static org.springframework.util.StringUtils.hasLength;


/**
 * Analyses methods annotated with {@link MethodLogging @MethodLogging} and provides their method logging definitions.
 * <p>
 * The definition of a method is resolved from the method annotation, the {@link MethodLoggingConfig} annotation of the
 * target class and the {@link MethodLoggingConfigurer}, in that order: an attribute left at its default
 * ({@code DEFAULT} or {@link MethodLogging#DEFAULT_VALUE}) on the method annotation is taken from the class
 * annotation, and a level, line number or logger field name left at its default on the class annotation is taken from
 * the configurer. If the target class has no {@code @MethodLoggingConfig} annotation, the annotation defaults are
 * used.
 * <p>
 * Definitions are cached per method and target class, including the information that a method has no method logging.
 * The analysis runs without holding a lock, so concurrent analysis of the same method is possible; the definition
 * cached first is used.
 * <p>
 * This class also creates the message support used by the definitions and the interceptor: the one provided by
 * {@link MethodLoggingConfigurer#messageSupport()} or, if none is provided, a default message support.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see MethodDef
 */
public final class AnnotationMethodLoggingSource
{
  final @NotNull Map<MethodClassKey,Optional<MethodDef>> methodLoggingDefinitionCache;
  private final @NotNull SingletonSupplier<Configuration> configuration;
  private final @NotNull ParameterNameDiscoverer nameDiscoverer;


  /**
   * Creates a method logging source for the given configurer.
   *
   * @param methodLoggingConfigurer  method logging configurer, not {@code null}
   * @param resourceLoader           resource loader providing the class loader for the default message support,
   *                                 not {@code null}
   */
  AnnotationMethodLoggingSource(@NotNull MethodLoggingConfigurer methodLoggingConfigurer,
                                @NotNull ResourceLoader resourceLoader) {
    this(() -> methodLoggingConfigurer, resourceLoader);
  }


  /**
   * Creates a method logging source with a lazily resolved configurer. The configurer is obtained when the first
   * method with method logging is analyzed, so the configurer bean and its dependencies are not created during the
   * creation of the method logging infrastructure.
   *
   * @param methodLoggingConfigurer  supplier of the method logging configurer, not {@code null}
   * @param resourceLoader           resource loader providing the class loader for the default message support,
   *                                 not {@code null}
   */
  AnnotationMethodLoggingSource(@NotNull Supplier<MethodLoggingConfigurer> methodLoggingConfigurer,
                                @NotNull ResourceLoader resourceLoader)
  {
    configuration = SingletonSupplier.of(() -> new Configuration(methodLoggingConfigurer.get(), resourceLoader));

    methodLoggingDefinitionCache = new ConcurrentHashMap<>();
    nameDiscoverer = new DefaultParameterNameDiscoverer();
  }


  /**
   * Returns the message support provided by the configurer or the default message support.
   *
   * @return  message support, never {@code null}
   */
  @NotNull MessageSupport getMessageSupport() {
    return configuration.obtain().messageSupport;
  }


  /**
   * Returns the method logger factory provided by the configurer or the default method logger factory.
   *
   * @return  method logger factory, never {@code null}
   */
  @NotNull MethodLoggerFactory getMethodLoggerFactory() {
    return configuration.obtain().methodLoggerFactory;
  }


  /**
   * Validates a default value provided by the method logging configurer.
   *
   * @param value       default value returned by the configurer
   * @param methodName  name of the configurer method, not {@code null}
   *
   * @return  {@code value}, never {@code null}
   *
   * @throws IllegalStateException  if {@code value} is {@code null} or {@code DEFAULT}
   */
  @Contract(value = "null, _ -> fail", pure = true)
  private static <T> @NotNull T requireDefault(T value, @NotNull String methodName)
  {
    if (value == null || value == Level.DEFAULT || value == Visibility.DEFAULT)
    {
      throw new IllegalStateException("MethodLoggingConfigurer#" + methodName + "() must not return " +
          (value == null ? "null" : "DEFAULT"));
    }

    return value;
  }


  /**
   * Returns the method logging definition for {@code method} invoked on an instance of {@code targetClass}.
   *
   * @param method       invoked method, possibly an interface method, not {@code null}
   * @param targetClass  class of the target instance, not {@code null}
   *
   * @return  method logging definition or {@code null} if the method has no {@code @MethodLogging} annotation
   */
  @Contract(pure = true)
  MethodDef getMethodDefinition(@NotNull Method method, Class<?> targetClass)
  {
    final var methodClassKey = new MethodClassKey(method, targetClass);

    var methodDef = methodLoggingDefinitionCache.get(methodClassKey);
    if (methodDef == null)
    {
      methodDef = ofNullable(analyseMethodDefinition(method, targetClass));
      methodDef = requireNonNullElse(methodLoggingDefinitionCache.putIfAbsent(methodClassKey, methodDef), methodDef);
    }

    return methodDef.orElse(null);
  }


  @Contract(pure = true)
  private MethodDef analyseMethodDefinition(@NotNull Method method, @NotNull Class<?> targetClass)
  {
    // method may be an interface method (e.g. for JDK proxies); the specific method is the implementation
    // providing parameter names/annotations and line numbers
    final var specificMethod = getMostSpecificMethod(method, targetClass);
    final var methodLoggingAttributes =
        findMergedAnnotationAttributes(specificMethod, MethodLogging.class, false, true);

    if (methodLoggingAttributes == null)
      return null;

    // the configurer is required for methods with method logging only
    final var config = configuration.obtain();
    final var methodLoggingConfigAttributes = findMethodLoggingConfigAttributes(config, targetClass);
    final var methodLogging =
        findMergedMethodLogging(methodLoggingConfigAttributes, methodLoggingAttributes);
    final var location = findMethodLocation(specificMethod);
    final var loggerField = findLoggerField(targetClass, methodLogging);

    config.methodLoggerFactory.validate(loggerField, targetClass);

    return new MethodDef(config.messageSupport.getMessageAccessor().getMessageFactory(),
        synthesizeAnnotation(methodLoggingConfigAttributes, MethodLoggingConfig.class, targetClass),
        getParameterDefs(specificMethod, method, targetClass, methodLogging), methodLogging, method, location,
        methodLogging.lineNumber() == SHOW ? location.getLineNumber() : -1, loggerField);
  }


  /**
   * Analyzes the method parameters. Parameter names and {@link ParamLog} annotations are taken from
   * {@code specificMethod}, falling back to {@code method} if {@code specificMethod} does not provide them.
   * <p>
   * The parameter name is resolved from {@link ParamLog#name()}, the parameter name discoverer or, if no name can be
   * discovered, the synthesized reflection name ({@code arg0}, {@code arg1}, ...) in that order.
   * <p>
   * Parameter types passed to {@link MethodLoggingConfigurer#excludeMethodParameter(ResolvableType)} are resolved
   * against {@code targetClass}, so generic parameter types declared in a superclass are resolved as well.
   */
  @Contract(pure = true)
  private @NotNull List<ParameterDef> getParameterDefs(@NotNull Method specificMethod, @NotNull Method method,
                                                       @NotNull Class<?> targetClass,
                                                       @NotNull MethodLogging methodLogging)
  {
    final var parameterCount = specificMethod.getParameterCount();
    if (parameterCount == 0 || methodLogging.parameters() != SHOW)
      return List.of();

    var parameterNames = nameDiscoverer.getParameterNames(specificMethod);
    if (parameterNames == null && specificMethod != method)
      parameterNames = nameDiscoverer.getParameterNames(method);

    final var parameterDefs = new ArrayList<ParameterDef>(8);
    final var specificParameters = specificMethod.getParameters();
    final var parameters = method.getParameters();
    final var excludeParameters = asList(methodLogging.exclude());
    final var messageFactory = getMessageSupport().getMessageAccessor().getMessageFactory();

    for(int p = 0; p < parameterCount; p++)
    {
      var paramLog = getMergedAnnotation(specificParameters[p], ParamLog.class);
      if (paramLog == null && specificMethod != method)
        paramLog = getMergedAnnotation(parameters[p], ParamLog.class);

      final var parameterDef = new ParameterDef();

      if (!hasLength(parameterDef.name = paramLog != null ? paramLog.name() : ""))
        parameterDef.name = parameterNames != null ? parameterNames[p] : specificParameters[p].getName();

      if (!excludeParameters.contains(parameterDef.name) &&
          (paramLog != null || isParameterIncluded(forMethodParameter(specificMethod, p, targetClass))))
      {
        final var format = paramLog != null && hasLength(paramLog.format()) ? paramLog.format() : "%{value}";

        parameterDef.index = p;
        parameterDef.inline = paramLog == null || paramLog.inline();
        parameterDef.formatMessage = SupplierDelegate.of(() -> messageFactory.parseMessage(format));

        parameterDefs.add(parameterDef);
      }
    }

    return copyOf(parameterDefs);
  }


  private boolean isParameterIncluded(@NotNull ResolvableType methodParameterType)
  {
    return
        (methodParameterType.toClass().isPrimitive() && !methodParameterType.isArray()) ||
        !configuration.obtain().methodLoggingConfigurer.excludeMethodParameter(methodParameterType);
  }


  @Contract(pure = true)
  private @NotNull AnnotationAttributes findMethodLoggingConfigAttributes(@NotNull Configuration config,
                                                                         @NotNull Class<?> type)
  {
    var attributes =
        findMergedAnnotationAttributes(type, MethodLoggingConfig.class, false, true);

    if (attributes == null)
      attributes = createDefaultMethodLoggingConfigAttributes();

    if (DEFAULT_VALUE.equals(attributes.getString("loggerFieldName")))
      attributes.put("loggerFieldName", config.defaultLoggerFieldName);

    if (attributes.getEnum("entryExitLevel") == Level.DEFAULT)
      attributes.put("entryExitLevel", config.defaultEntryExitLevel);

    if (attributes.getEnum("parameterLevel") == Level.DEFAULT)
      attributes.put("parameterLevel", config.defaultParameterLevel);

    if (attributes.getEnum("resultLevel") == Level.DEFAULT)
      attributes.put("resultLevel", config.defaultResultLevel);

    if (attributes.getEnum("lineNumber") == Visibility.DEFAULT)
      attributes.put("lineNumber", config.defaultLineNumber);

    return attributes;
  }


  @Contract(pure = true)
  private @NotNull AnnotationAttributes createDefaultMethodLoggingConfigAttributes()
  {
    final var attributes = new AnnotationAttributes(MethodLoggingConfig.class);

    for(var annotationMethod: MethodLoggingConfig.class.getDeclaredMethods())
      if (annotationMethod.getReturnType() != void.class && annotationMethod.getParameterCount() == 0)
        attributes.put(annotationMethod.getName(), annotationMethod.getDefaultValue());

    return attributes;
  }


  @Contract(pure = true)
  private @NotNull MethodLogging findMergedMethodLogging(@NotNull AnnotationAttributes methodLoggingConfigAttributes,
                                                         @NotNull AnnotationAttributes methodLoggingAttributes)
  {
    for(var methodAttribute: methodLoggingAttributes.entrySet())
    {
      final var value = methodAttribute.getValue();

      if (value == Visibility.DEFAULT || value == Level.DEFAULT)
        methodAttribute.setValue(methodLoggingConfigAttributes.getEnum(methodAttribute.getKey()));
      else if (DEFAULT_VALUE.equals(value))
        methodAttribute.setValue(methodLoggingConfigAttributes.getString(methodAttribute.getKey()));
    }

    return synthesizeAnnotation(methodLoggingAttributes, MethodLogging.class, null);
  }


  /**
   * Determines the source location of {@code method}: its declaring class, name, source file and the line of the
   * first statement. Source file and line are taken from the class file and are {@code null} and {@code -1}
   * respectively, if not available.
   */
  @Contract(pure = true)
  private @NotNull StackTraceElement findMethodLocation(@NotNull Method method)
  {
    final var declaringClass = method.getDeclaringClass();
    final var classResourceName = declaringClass.getName().replace('.', '/') + ".class";
    final var methodDescriptor = method.getName().concat(Type.getMethodDescriptor(method));

    final var sourceFile = new AtomicReference<String>();
    final var lineNumber = new AtomicInteger(-1);
    final var methodVisitor = new MethodVisitor(ASM_VERSION) {
      @Override
      public void visitLineNumber(int line, Label start) {
        lineNumber.compareAndSet(-1, line);
      }
    };

    try(var classInputStream = requireNonNull(
        declaringClass.getClassLoader().getResourceAsStream(classResourceName))) {
      new ClassReader(classInputStream).accept(
          new ClassVisitor(ASM_VERSION) {
            @Override
            public void visitSource(String source, String debug) {
              sourceFile.set(source);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] ex) {
              return methodDescriptor.equals(name.concat(descriptor)) ? methodVisitor : null;
            }
          },
          SKIP_FRAMES);
    } catch(IOException | RuntimeException ignored) {
      // class file not available or not readable by ASM (e.g. unsupported class file version)
    }

    return new StackTraceElement(declaringClass.getName(), method.getName(), sourceFile.get(), lineNumber.get());
  }


  @Contract(pure = true)
  private Field findLoggerField(Class<?> clazz, @NotNull MethodLogging methodLogging)
  {
    final var loggerFieldName = methodLogging.loggerFieldName();

    if (!loggerFieldName.isEmpty())
      for(; clazz != Object.class && clazz != null; clazz = clazz.getSuperclass())
      {
        final Field field;

        try {
          field = clazz.getDeclaredField(loggerFieldName);
        } catch(NoSuchFieldException ignored) {
          continue;
        }

        try {
          field.setAccessible(true);
        } catch(InaccessibleObjectException ex) {
          throw new IllegalStateException("Logger field '" + loggerFieldName + "' of " + clazz +
              " is not accessible; package " + clazz.getPackageName() + " must be opened to " +
              AnnotationMethodLoggingSource.class.getModule(), ex);
        }

        return field;
      }

    return null;
  }




  /**
   * Configuration provided by the method logging configurer, with validated defaults.
   */
  private static final class Configuration
  {
    private final @NotNull MethodLoggingConfigurer methodLoggingConfigurer;
    private final @NotNull MessageSupport messageSupport;
    private final @NotNull MethodLoggerFactory methodLoggerFactory;
    private final @NotNull String defaultLoggerFieldName;
    private final @NotNull Level defaultEntryExitLevel;
    private final @NotNull Level defaultParameterLevel;
    private final @NotNull Level defaultResultLevel;
    private final @NotNull Visibility defaultLineNumber;


    private Configuration(@NotNull MethodLoggingConfigurer methodLoggingConfigurer,
                          @NotNull ResourceLoader resourceLoader)
    {
      this.methodLoggingConfigurer = methodLoggingConfigurer;

      defaultLoggerFieldName =
          requireDefault(methodLoggingConfigurer.defaultLoggerFieldName(), "defaultLoggerFieldName");
      defaultEntryExitLevel = requireDefault(methodLoggingConfigurer.defaultEntryExitLevel(), "defaultEntryExitLevel");
      defaultParameterLevel = requireDefault(methodLoggingConfigurer.defaultParameterLevel(), "defaultParameterLevel");
      defaultResultLevel = requireDefault(methodLoggingConfigurer.defaultResultLevel(), "defaultResultLevel");
      defaultLineNumber = requireDefault(methodLoggingConfigurer.defaultLineNumber(), "defaultLineNumber");

      final var configuredMessageSupport = methodLoggingConfigurer.messageSupport();
      messageSupport = configuredMessageSupport != null
          ? configuredMessageSupport
          : MessageSupportFactory.create(new DefaultFormatterService(resourceLoader.getClassLoader(), 128));

      final var configuredMethodLoggerFactory = methodLoggingConfigurer.methodLoggerFactory();
      methodLoggerFactory = configuredMethodLoggerFactory != null
          ? configuredMethodLoggerFactory
          : new AutoDetectLoggerFactory();
    }
  }
}
