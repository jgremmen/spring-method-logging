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
import de.sayayi.lib.message.util.SupplierDelegate;
import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.Visibility;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import de.sayayi.lib.methodlogging.logger.AutoDetectLoggerFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.asm.Type;
import org.springframework.core.MethodClassKey;
import org.springframework.core.annotation.AnnotationAttributes;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static de.sayayi.lib.methodlogging.Visibility.HIDE;
import static de.sayayi.lib.methodlogging.Visibility.SHOW;
import static de.sayayi.lib.methodlogging.annotation.MethodLogging.DEFAULT_VALUE;
import static java.util.Objects.requireNonNullElse;
import static org.springframework.aop.support.AopUtils.getMostSpecificMethod;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotationAttributes;
import static org.springframework.core.annotation.AnnotationUtils.synthesizeAnnotation;


/**
 * Analyzes methods annotated with {@link MethodLogging @MethodLogging} and provides their method logging definitions.
 * <p>
 * The definition of a method is resolved from the method annotation, the {@link MethodLoggingConfig} annotation of the
 * target class and the {@link MethodLoggingConfigurer}, in that order: an attribute left at its default
 * ({@code DEFAULT} or {@link MethodLogging#DEFAULT_VALUE}) on the method annotation is taken from the class
 * annotation, and a level, line number, exception message or logger field name left at its default on the class
 * annotation is taken from the configurer. If the target class has no {@code @MethodLoggingConfig} annotation, the
 * annotation defaults are used.
 * <p>
 * Definitions are cached per method and target class, including the information that a method has no method logging.
 * Invoked methods resolving to the same method implementation, like an interface method and the implementing method,
 * share the definition. The analysis runs without holding a lock, so concurrent analysis of the same method is
 * possible; the definition cached first is used. A failed analysis is cached as well, so it is not repeated on every
 * invocation.
 * <p>
 * Creating the configurer may require beans with method logging, and creating the configurer bean or invoking the
 * configurer methods may acquire the bean factory's singleton lock. No thread therefore waits for the configuration:
 * while it is being created, methods with method logging are not analyzed (and not logged) until the configuration is
 * available. This avoids deadlocks with threads holding the singleton lock while invoking a method with method logging.
 * <p>
 * This class also provides the message support used by the definitions and the interceptor: the one provided by
 * {@link MethodLoggingConfigurer#messageSupport()} or, if none is provided, the default message support created by
 * {@link DefaultMessageSupport}. The parameters are analyzed by {@link ParameterAnalyzer}, the source locations are
 * read by {@link ClassLocation}.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see MethodDef
 */
final class AnnotationMethodLoggingSource
{
  /** Method logging definitions by method and target class, empty if the method has no method logging. */
  final @NotNull Map<MethodClassKey,Optional<MethodDef>> methodLoggingDefinitionCache;

  /** Failed analyses by method and target class. */
  final @NotNull Map<MethodClassKey,AnalysisFailure> methodLoggingFailureCache;

  /** Source file and method line numbers by declaring class. */
  final @NotNull Map<Class<?>,ClassLocation> classLocationCache;

  /** Supplier of the method logging configurer, invoked when the configuration is created. */
  private final @NotNull Supplier<MethodLoggingConfigurer> methodLoggingConfigurer;

  /** Tells whether invoked methods are resolved to the most specific method of the target class. */
  private final boolean resolveSpecificMethod;

  /** Tells whether the configuration is being created. */
  private final @NotNull AtomicBoolean creatingConfiguration = new AtomicBoolean();

  /** Configuration or {@code null} if it has not been created yet. */
  private volatile Configuration configuration;

  /** Cached failure of the configurer bean or the configurer methods or {@code null} if there is none. */
  private volatile RuntimeException configurationFailure;

  /** Tells whether all singleton beans have been created. */
  private volatile boolean singletonsInstantiated;

  /** Analyzer of the method parameters. */
  private final @NotNull ParameterAnalyzer parameterAnalyzer;

  /** Supplier of the default message support, created on first use. */
  private final Supplier<MessageSupport> messageSupportSupplier;


  /**
   * Creates a method logging source with a lazily resolved configurer. The configurer is obtained when the first method
   * with method logging is analyzed or once all singleton beans have been created, whichever comes first, so the
   * configurer bean and its dependencies are not created during the creation of the method logging infrastructure.
   *
   * @param methodLoggingConfigurer  supplier of the method logging configurer, not {@code null}
   * @param resolveSpecificMethod    {@code true} if the invoked method is resolved to the most specific method of the
   *                                 target class (proxies), {@code false} if the invoked method is the executed
   *                                 method already (AspectJ)
   *
   * @since 0.6.0
   */
  AnnotationMethodLoggingSource(@NotNull Supplier<MethodLoggingConfigurer> methodLoggingConfigurer,
                                boolean resolveSpecificMethod)
  {
    this.methodLoggingConfigurer = methodLoggingConfigurer;
    this.resolveSpecificMethod = resolveSpecificMethod;

    methodLoggingDefinitionCache = new ConcurrentHashMap<>();
    methodLoggingFailureCache = new ConcurrentHashMap<>();
    classLocationCache = new ConcurrentHashMap<>();
    parameterAnalyzer = new ParameterAnalyzer();
    messageSupportSupplier = SupplierDelegate.of(DefaultMessageSupport::create);
  }


  /**
   * Returns the configuration, creating it on first invocation, unless it is being created.
   * <p>
   * Creating the configuration obtains the configurer bean and invokes the configurer methods, both of which may
   * acquire the bean factory's singleton lock. A thread waiting for the configuration could therefore deadlock with the
   * creating thread, if it holds the singleton lock itself (e.g. while creating a bean which invokes a method with
   * method logging). So no thread waits: while the configuration is being created by one thread, {@code null} is
   * returned to all threads, including the creating thread, which may require beans with method logging for creating
   * the configurer. The configurer methods are invoked by a single thread, once the configuration has been created.
   * <p>
   * If the configurer methods fail (e.g. by returning an invalid default), they would fail again; the failure is cached
   * and passed on by all subsequent invocations, without invoking the configurer methods again. An error raised by the
   * configurer is passed on as {@link IllegalStateException}, unless it is a virtual machine error the application
   * cannot recover from. A failure obtaining the configurer bean is not cached while the singleton beans are being
   * created, as the bean may be available later on (e.g. if it depends on a bean which is currently being created).
   * Once all singleton beans have been created (see {@link #singletonsInstantiated()}), the configurer bean would fail
   * again (e.g. multiple configurers or a failing configurer bean), so the failure is cached as well.
   *
   * @return  configuration or {@code null} if the configuration is being created
   *
   * @throws IllegalStateException  if the configurer returns an invalid default
   *
   * @since 0.6.0
   */
  private Configuration findConfiguration()
  {
    var config = configuration;
    if (config == null)
    {
      throwConfigurationFailure();

      if (creatingConfiguration.compareAndSet(false, true))
      {
        try {
          if ((config = configuration) == null)
          {
            throwConfigurationFailure();

            final MethodLoggingConfigurer configurer;

            try {
              configurer = methodLoggingConfigurer.get();
            } catch(Throwable ex) {
              final var failure = asRuntimeException(ex, "Method logging configurer is not available");

              // the configurer bean is not created again on each invocation once all singleton beans exist
              if (singletonsInstantiated)
                configurationFailure = failure;

              throw failure;
            }

            try {
              configuration = config = new Configuration(configurer, messageSupportSupplier);
            } catch(Throwable ex) {
              final var failure = asRuntimeException(ex, "Method logging configurer failed");

              configurationFailure = failure;
              throw failure;
            }
          }
        } finally {
          creatingConfiguration.set(false);
        }
      }
    }

    return config;
  }


  /**
   * Creates the configuration once all singleton beans have been created, so an unavailable configurer bean or an
   * invalid configurer default fails the start of the application context, even if no method with method logging has
   * been analyzed yet. From now on, a failure obtaining the configurer bean is cached (see
   * {@link #findConfiguration()}).
   * <p>
   * If another thread is creating the configuration, this method returns without waiting for it.
   *
   * @throws RuntimeException  if the configurer bean cannot be obtained or the configurer returns an invalid default
   *
   * @since 0.6.0
   */
  void singletonsInstantiated()
  {
    singletonsInstantiated = true;
    findConfiguration();
  }


  /**
   * Passes on the cached failure of the configurer bean or the configurer methods, if any.
   *
   * @throws RuntimeException  if creating the configuration has failed
   *
   * @since 0.6.0
   */
  private void throwConfigurationFailure()
  {
    var failure = configurationFailure;
    if (failure != null)
      throw failure;
  }


  /**
   * Returns {@code throwable} as runtime exception. A runtime exception is returned unchanged; any other throwable is
   * wrapped in an {@link IllegalStateException}, except for virtual machine errors the application cannot recover from,
   * which are rethrown.
   *
   * @param throwable  throwable caught, not {@code null}
   * @param message    message of the wrapping exception, not {@code null}
   *
   * @return  runtime exception, never {@code null}
   *
   * @see MethodLoggingInterceptor#rethrowIfFatal(Throwable)
   *
   * @since 0.6.0
   */
  @Contract(pure = true)
  private static @NotNull RuntimeException asRuntimeException(@NotNull Throwable throwable, @NotNull String message)
  {
    MethodLoggingInterceptor.rethrowIfFatal(throwable);

    return throwable instanceof RuntimeException runtimeException
        ? runtimeException
        : new IllegalStateException(message, throwable);
  }


  /**
   * Returns the configuration, creating it on first invocation.
   *
   * @return  configuration, never {@code null}
   *
   * @throws IllegalStateException  if the configuration is being created or the configurer returns an invalid default
   *
   * @since 0.6.0
   */
  private @NotNull Configuration getConfiguration()
  {
    var config = findConfiguration();
    if (config == null)
      throw new IllegalStateException("Method logging configuration is being created");

    return config;
  }


  /**
   * Returns the message support provided by the configurer or the default message support. The configuration is
   * available once a method logging definition exists.
   *
   * @return  message support, never {@code null}
   *
   * @throws IllegalStateException  if the configuration is being created
   *
   * @since 0.6.0
   */
  @NotNull MessageSupport getMessageSupport() {
    return getConfiguration().messageSupport;
  }


  /**
   * Returns the method logger factory provided by the configurer or the default method logger factory. The
   * configuration is available once a method logging definition exists.
   *
   * @return  method logger factory, never {@code null}
   *
   * @throws IllegalStateException  if the configuration is being created
   *
   * @since 0.6.0
   */
  @NotNull MethodLoggerFactory getMethodLoggerFactory() {
    return getConfiguration().methodLoggerFactory;
  }


  /**
   * Validates a default value provided by the method logging configurer.
   *
   * @param value       default value returned by the configurer
   * @param methodName  name of the configurer method, not {@code null}
   * @param <T>         type of the default value
   *
   * @return  {@code value}, never {@code null}
   *
   * @throws IllegalStateException  if {@code value} is {@code null} or {@code DEFAULT}
   *
   * @since 0.6.0
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
   * Tells whether {@code method} invoked on an instance of {@code targetClass} has method logging.
   * <p>
   * The method is analyzed, unless the configuration is being created. In that case only the presence of the
   * {@code @MethodLogging} annotation is checked, as the analysis requires the configuration. The definition is
   * analyzed on first invocation instead.
   *
   * @param method       method, possibly an interface method, not {@code null}
   * @param targetClass  target class, not {@code null}
   *
   * @return  {@code true} if the method has method logging, {@code false} otherwise
   *
   * @since 0.6.0
   */
  boolean hasMethodLogging(@NotNull Method method, @NotNull Class<?> targetClass)
  {
    if (getMethodDefinition(method, targetClass) != null)
      return true;

    // no definition is cached for an annotated method while the configuration is being created
    final var methodDef = methodLoggingDefinitionCache.get(new MethodClassKey(method, targetClass));

    return methodDef != null ? methodDef.isPresent() : hasMethodLoggingAnnotation(method, targetClass);
  }


  /**
   * Tells whether {@code method} invoked on an instance of {@code targetClass} is annotated with
   * {@code @MethodLogging}. The method is not analyzed, so the configurer is not required.
   *
   * @param method       method, possibly an interface method, not {@code null}
   * @param targetClass  target class, not {@code null}
   *
   * @return  {@code true} if the method is annotated with {@code @MethodLogging}, {@code false} otherwise
   *
   * @since 0.6.0
   */
  @Contract(pure = true)
  boolean hasMethodLoggingAnnotation(@NotNull Method method, @NotNull Class<?> targetClass) {
    return findMethodLoggingAttributes(getSpecificMethod(method, targetClass)) != null;
  }


  /**
   * Returns the method implementation executed for {@code method} invoked on an instance of {@code targetClass}.
   * <p>
   * For proxies, the invoked method may be an interface or superclass method (e.g. for JDK proxies), which is resolved
   * to the most specific method of {@code targetClass}. For AspectJ, the invoked method is the executed method already.
   * It must not be resolved, as it may be a superclass method invoked by an overriding method of {@code targetClass}
   * (e.g. {@code super.method()}).
   *
   * @param method       invoked method, not {@code null}
   * @param targetClass  class of the target instance, not {@code null}
   *
   * @return  executed method implementation, never {@code null}
   *
   * @since 0.6.0
   */
  @Contract(pure = true)
  private @NotNull Method getSpecificMethod(@NotNull Method method, @NotNull Class<?> targetClass) {
    return resolveSpecificMethod ? getMostSpecificMethod(method, targetClass) : method;
  }


  /**
   * Returns the method logging definition for {@code method} invoked on an instance of {@code targetClass}.
   * <p>
   * A failed analysis is cached: it is not repeated, but its failure is passed on by all subsequent invocations for the
   * same method and target class (see {@link #isFirstFailureReport(Method, Class)}). An error raised by the analysis,
   * like a {@link NoClassDefFoundError}, is passed on as {@link IllegalStateException}, unless it is a virtual machine
   * error the application cannot recover from.
   *
   * @param method       invoked method, possibly an interface method, not {@code null}
   * @param targetClass  class of the target instance, not {@code null}
   *
   * @return  method logging definition or {@code null} if the method has no {@code @MethodLogging} annotation or the
   *          configuration is being created
   *
   * @throws RuntimeException  if the analysis of the method has failed
   *
   * @since 0.3.0
   */
  MethodDef getMethodDefinition(@NotNull Method method, @NotNull Class<?> targetClass)
  {
    final var methodClassKey = new MethodClassKey(method, targetClass);

    var methodDef = methodLoggingDefinitionCache.get(methodClassKey);
    if (methodDef == null)
    {
      var failure = methodLoggingFailureCache.get(methodClassKey);
      if (failure != null)
        throw failure.exception;

      // the specific method is the implementation providing parameter names/annotations and line numbers
      final var specificMethod = getSpecificMethod(method, targetClass);
      final var specificMethodClassKey = new MethodClassKey(specificMethod, targetClass);

      // invoked methods resolving to the same specific method (e.g. an interface method and the implementation) share
      // the definition or the failure of the analysis
      if (!specificMethodClassKey.equals(methodClassKey) &&
          (failure = methodLoggingFailureCache.get(specificMethodClassKey)) != null)
      {
        methodLoggingFailureCache.putIfAbsent(methodClassKey, failure);
        throw failure.exception;
      }

      var specificMethodDef = methodLoggingDefinitionCache.get(specificMethodClassKey);
      if (specificMethodDef == null)
      {
        final var methodLoggingAttributes = findMethodLoggingAttributes(specificMethod);

        if (methodLoggingAttributes == null)
          specificMethodDef = Optional.empty();
        else
        {
          final Configuration config;

          // the configuration is required for methods with method logging only
          try {
            config = findConfiguration();
          } catch(RuntimeException ex) {
            // the configurer bean may be available later on, unless the failure is cached
            if (ex == configurationFailure)
              cacheFailure(methodClassKey, specificMethodClassKey, ex);

            throw ex;
          }

          if (config == null)
            return null;

          try {
            specificMethodDef = Optional.of(
                analyseMethodDefinition(method, specificMethod, methodLoggingAttributes, targetClass, config));
          } catch(Throwable ex) {
            final var analysisFailure =
                asRuntimeException(ex, "Method logging analysis failed for " + specificMethod);

            cacheFailure(methodClassKey, specificMethodClassKey, analysisFailure);
            throw analysisFailure;
          }
        }

        specificMethodDef = requireNonNullElse(
            methodLoggingDefinitionCache.putIfAbsent(specificMethodClassKey, specificMethodDef), specificMethodDef);
      }

      methodDef = requireNonNullElse(
          methodLoggingDefinitionCache.putIfAbsent(methodClassKey, specificMethodDef), specificMethodDef);
    }

    return methodDef.orElse(null);
  }


  /**
   * Caches the failure of the analysis for the invoked method and for the specific method it resolves to, so all
   * invoked methods resolving to the same specific method share the failure and its report.
   *
   * @param methodClassKey          key of the invoked method and target class, not {@code null}
   * @param specificMethodClassKey  key of the specific method and target class, not {@code null}
   * @param exception               exception raised by the analysis, not {@code null}
   *
   * @since 0.6.0
   */
  private void cacheFailure(@NotNull MethodClassKey methodClassKey, @NotNull MethodClassKey specificMethodClassKey,
                            @NotNull RuntimeException exception)
  {
    var failure = new AnalysisFailure(exception);

    failure = requireNonNullElse(methodLoggingFailureCache.putIfAbsent(specificMethodClassKey, failure), failure);
    methodLoggingFailureCache.putIfAbsent(methodClassKey, failure);
  }


  /**
   * Tells whether a failure of {@link #getMethodDefinition(Method, Class)} for {@code method} invoked on an instance of
   * {@code targetClass} is to be reported. A cached analysis failure is reported once; a failure which is not cached
   * (e.g. the configurer bean is not available yet) is reported every time.
   *
   * @param method       invoked method, possibly an interface method, not {@code null}
   * @param targetClass  class of the target instance, not {@code null}
   *
   * @return  {@code true} if the failure is to be reported, {@code false} if it has been reported already
   *
   * @since 0.6.0
   */
  boolean isFirstFailureReport(@NotNull Method method, @NotNull Class<?> targetClass)
  {
    final var failure = methodLoggingFailureCache.get(new MethodClassKey(method, targetClass));
    return failure == null || failure.reported.compareAndSet(false, true);
  }


  /**
   * Analyzes {@code method} invoked on an instance of {@code targetClass} and creates its method logging definition.
   * The settings of the method annotation are merged with the class annotation and the configurer defaults, the
   * parameters are analyzed, and the source location and logger field are determined.
   *
   * @param method                   invoked method, possibly an interface method, not {@code null}
   * @param specificMethod           method implementation, not {@code null}
   * @param methodLoggingAttributes  merged {@code @MethodLogging} attributes of {@code specificMethod}, not
   *                                 {@code null}
   * @param targetClass              class of the target instance, not {@code null}
   * @param config                   configuration, not {@code null}
   *
   * @return  method logging definition, never {@code null}
   *
   * @throws IllegalStateException  if the method logging configuration of the method is invalid
   *
   * @since 0.6.0
   */
  private @NotNull MethodDef analyseMethodDefinition(@NotNull Method method, @NotNull Method specificMethod,
                                                     @NotNull AnnotationAttributes methodLoggingAttributes,
                                                     @NotNull Class<?> targetClass, @NotNull Configuration config)
  {
    final var methodLoggingConfigAttributes = findMethodLoggingConfigAttributes(config, targetClass);
    final var methodLogging =
        findMergedMethodLogging(methodLoggingConfigAttributes, methodLoggingAttributes);
    final var location = findMethodLocation(specificMethod);
    final var loggerField = findLoggerField(targetClass, methodLogging);

    config.methodLoggerFactory.validate(loggerField, targetClass);

    final var parameterDefs = parameterAnalyzer.getParameterDefs(specificMethod, method,
        targetClass, methodLogging, config.messageParser, config.methodLoggingConfigurer);

    return new MethodDef(config.messageParser,
        synthesizeAnnotation(methodLoggingConfigAttributes, MethodLoggingConfig.class, targetClass), parameterDefs,
        methodLogging, method, location, methodLogging.lineNumber() == SHOW ? location.getLineNumber() : -1,
        loggerField);
  }


  /**
   * Returns the merged {@code @MethodLogging} attributes of {@code specificMethod}, including composed annotations and
   * annotations inherited from overridden methods.
   *
   * @param specificMethod  method implementation, not {@code null}
   *
   * @return  method logging attributes or {@code null} if the method is not annotated with {@code @MethodLogging}
   *
   * @since 0.6.0
   */
  @Contract(pure = true)
  private static AnnotationAttributes findMethodLoggingAttributes(@NotNull Method specificMethod) {
    return findMergedAnnotationAttributes(specificMethod, MethodLogging.class, false, true);
  }


  /**
   * Returns the {@code @MethodLoggingConfig} attributes of {@code type}, or the annotation defaults if the type is not
   * annotated. Logger field name, levels, line number and exception message left at their default are replaced by the
   * configurer defaults. Elapsed time, parameters and result set to {@link Visibility#DEFAULT} are replaced by their
   * annotation defaults.
   *
   * @param config  configuration with the configurer defaults, not {@code null}
   * @param type    target class, not {@code null}
   *
   * @return  class level method logging attributes, never {@code null}
   *
   * @since 0.6.0
   */
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

    if (attributes.getEnum("exceptionMessage") == Visibility.DEFAULT)
      attributes.put("exceptionMessage", config.defaultExceptionMessage);

    if (attributes.getEnum("elapsedTime") == Visibility.DEFAULT)
      attributes.put("elapsedTime", HIDE);

    if (attributes.getEnum("parameters") == Visibility.DEFAULT)
      attributes.put("parameters", SHOW);

    if (attributes.getEnum("result") == Visibility.DEFAULT)
      attributes.put("result", SHOW);

    return attributes;
  }


  /**
   * Creates the attributes of a {@code @MethodLoggingConfig} annotation with all attributes at their default value.
   *
   * @return  default method logging config attributes, never {@code null}
   */
  @Contract(pure = true)
  private @NotNull AnnotationAttributes createDefaultMethodLoggingConfigAttributes()
  {
    final var attributes = new AnnotationAttributes(MethodLoggingConfig.class);

    for(var annotationMethod: MethodLoggingConfig.class.getDeclaredMethods())
      if (annotationMethod.getReturnType() != void.class && annotationMethod.getParameterCount() == 0)
        attributes.put(annotationMethod.getName(), annotationMethod.getDefaultValue());

    return attributes;
  }


  /**
   * Merges the method annotation attributes with the class level attributes: each method attribute left at its default
   * is replaced by the corresponding class level attribute.
   *
   * @param methodLoggingConfigAttributes  class level attributes, not {@code null}
   * @param methodLoggingAttributes        method annotation attributes, not {@code null}
   *
   * @return  merged method logging annotation, never {@code null}
   */
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
   * Determines the source location of {@code method}: its declaring class, name, source file and the line of the first
   * statement. Source file and line are taken from the class file and are {@code null} and {@code -1} respectively, if
   * not available. The class file is read once per declaring class.
   *
   * @param method  method implementation, not {@code null}
   *
   * @return  source location of the method, never {@code null}
   *
   * @since 0.6.0
   */
  private @NotNull StackTraceElement findMethodLocation(@NotNull Method method)
  {
    final var declaringClass = method.getDeclaringClass();

    var classLocation = classLocationCache.get(declaringClass);
    if (classLocation == null)
    {
      // read the class file without holding a lock; concurrently read locations are equal
      classLocation = ClassLocation.read(declaringClass);
      classLocation = requireNonNullElse(classLocationCache.putIfAbsent(declaringClass, classLocation), classLocation);
    }

    final var name = method.getName();

    return new StackTraceElement(declaringClass.getName(), name, classLocation.sourceFile(),
        classLocation.lineNumbers().getOrDefault(name.concat(Type.getMethodDescriptor(method)), -1));
  }


  /**
   * Finds the logger field in {@code clazz} or one of its superclasses and makes it accessible.
   *
   * @param clazz          target class
   * @param methodLogging  merged method logging settings providing the logger field name, not {@code null}
   *
   * @return  logger field or {@code null} if no logger field was found or the logger field name is empty
   *
   * @throws IllegalStateException  if the logger field is not accessible, e.g. as its package is not opened to this
   *                                module
   */
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
   * Cached failure of a method analysis.
   *
   * @since 0.6.0
   */
  static final class AnalysisFailure
  {
    /** Exception raised by the analysis, passed on whenever the method definition is requested. */
    final @NotNull RuntimeException exception;

    /** Tells whether the failure has been reported. */
    final @NotNull AtomicBoolean reported = new AtomicBoolean();


    /**
     * Creates a cached analysis failure.
     *
     * @param exception  exception raised by the analysis, not {@code null}
     */
    AnalysisFailure(@NotNull RuntimeException exception) {
      this.exception = exception;
    }
  }




  /**
   * Configuration provided by the method logging configurer, with validated defaults.
   *
   * @since 0.6.0
   */
  private static final class Configuration
  {
    /** Method logging configurer deciding on the exclusion of parameters by type. */
    private final @NotNull MethodLoggingConfigurer methodLoggingConfigurer;

    /** Message support provided by the configurer or the default message support. */
    private final @NotNull MessageSupport messageSupport;

    /** Message parser for the message formats, using the message factory of the message support. */
    private final @NotNull MessageParser messageParser;

    /** Method logger factory provided by the configurer or the default method logger factory. */
    private final @NotNull MethodLoggerFactory methodLoggerFactory;

    /** Default logger field name provided by the configurer. */
    private final @NotNull String defaultLoggerFieldName;

    /** Default entry/exit log level provided by the configurer, never {@link Level#DEFAULT}. */
    private final @NotNull Level defaultEntryExitLevel;

    /** Default parameter log level provided by the configurer, never {@link Level#DEFAULT}. */
    private final @NotNull Level defaultParameterLevel;

    /** Default result log level provided by the configurer, never {@link Level#DEFAULT}. */
    private final @NotNull Level defaultResultLevel;

    /** Default line number visibility provided by the configurer, never {@link Visibility#DEFAULT}. */
    private final @NotNull Visibility defaultLineNumber;

    /** Default exception message visibility provided by the configurer, never {@link Visibility#DEFAULT}. */
    private final @NotNull Visibility defaultExceptionMessage;


    /**
     * Creates the configuration from the configurer, validating its defaults and falling back to the default message
     * support and method logger factory if the configurer provides none.
     *
     * @param methodLoggingConfigurer  method logging configurer, not {@code null}
     * @param messageSupportSupplier   supplier of the default message support, not {@code null}
     *
     * @throws IllegalStateException  if the configurer returns an invalid default
     */
    private Configuration(@NotNull MethodLoggingConfigurer methodLoggingConfigurer,
                          @NotNull Supplier<MessageSupport> messageSupportSupplier)
    {
      this.methodLoggingConfigurer = methodLoggingConfigurer;

      defaultLoggerFieldName =
          requireDefault(methodLoggingConfigurer.defaultLoggerFieldName(), "defaultLoggerFieldName");
      defaultEntryExitLevel = requireDefault(methodLoggingConfigurer.defaultEntryExitLevel(), "defaultEntryExitLevel");
      defaultParameterLevel = requireDefault(methodLoggingConfigurer.defaultParameterLevel(), "defaultParameterLevel");
      defaultResultLevel = requireDefault(methodLoggingConfigurer.defaultResultLevel(), "defaultResultLevel");
      defaultLineNumber = requireDefault(methodLoggingConfigurer.defaultLineNumber(), "defaultLineNumber");
      defaultExceptionMessage =
          requireDefault(methodLoggingConfigurer.defaultExceptionMessage(), "defaultExceptionMessage");

      final var configuredMessageSupport = methodLoggingConfigurer.messageSupport();
      messageSupport = configuredMessageSupport != null ? configuredMessageSupport : messageSupportSupplier.get();
      messageParser = new MessageParser(messageSupport.getMessageAccessor().getMessageFactory());

      final var configuredMethodLoggerFactory = methodLoggingConfigurer.methodLoggerFactory();
      methodLoggerFactory = configuredMethodLoggerFactory != null
          ? configuredMethodLoggerFactory
          : new AutoDetectLoggerFactory();
    }
  }
}
