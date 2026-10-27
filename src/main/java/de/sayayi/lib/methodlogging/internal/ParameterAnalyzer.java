/*
 * Copyright 2026 Jeroen Gremmen
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
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotationSelectors;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.RepeatableContainers;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

import static de.sayayi.lib.methodlogging.Visibility.SHOW;
import static java.lang.reflect.Modifier.*;
import static java.util.Arrays.asList;
import static java.util.List.copyOf;
import static org.springframework.core.ResolvableType.forMethodParameter;
import static org.springframework.core.annotation.AnnotatedElementUtils.getMergedAnnotation;
import static org.springframework.core.annotation.MergedAnnotations.SearchStrategy.TYPE_HIERARCHY;
import static org.springframework.util.ClassUtils.getAllInterfacesForClassAsSet;
import static org.springframework.util.StringUtils.hasLength;


/**
 * Analyzes the parameters of a method with method logging and creates the definitions of the parameters to be logged.
 * <p>
 * Instances are thread-safe, as methods may be analyzed concurrently.
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 *
 * @see ParameterDef
 */
final class ParameterAnalyzer
{
  /** Default format of a parameter value. */
  @Language("MessageFormat")
  private static final String DEFAULT_PARAMETER_VALUE_FORMAT = "%{value,null:'(null)'}";

  /** Discoverer of the method parameter names. */
  private final @NotNull ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();


  /**
   * Analyzes the method parameters. {@link ParamLog} annotations are taken from the most specific declaration of the
   * method annotating the parameter, including overridden interface and superclass methods; a parameter excluded by any
   * declaration stays excluded.
   * <p>
   * The logged parameter name is resolved from {@link ParamLog#name()}, the parameter name discoverer (from the most
   * specific declaration providing names) or, if no name can be discovered, the synthesized reflection name
   * ({@code arg0}, {@code arg1}, ...) in that order.
   * <p>
   * A name excluded by {@link MethodLogging#exclude()} matches a parameter if it equals the {@code @ParamLog} name or
   * the discovered name of any declaration. Each excluded name must match a parameter, unless parameter names are
   * available and the names are excluded by a composed annotation. Otherwise, a misspelled or outdated name would leave
   * the parameter meant to be excluded in the log output. Excluded names and {@code @ParamLog} formats are validated
   * even if the method has no parameters or parameter logging is disabled.
   * <p>
   * Parameter types passed to {@link MethodLoggingConfigurer#excludeMethodParameter(ResolvableType)} are resolved
   * against {@code targetClass}, so generic parameter types declared in a superclass are resolved as well.
   *
   * @param specificMethod  method implementation, not {@code null}
   * @param method          invoked method, possibly an interface method, not {@code null}
   * @param targetClass     class of the target instance, not {@code null}
   * @param methodLogging   merged method logging settings, not {@code null}
   * @param messageParser   message parser for the parameter formats, not {@code null}
   * @param configurer      method logging configurer deciding on the exclusion of parameters by type, not
   *                        {@code null}
   *
   * @return  definitions of the parameters to be logged, never {@code null}
   *
   * @throws IllegalStateException  if an excluded parameter name to be validated matches no parameter or a custom
   *                                parameter format is invalid
   *
   * @since 0.6.0
   */
  @NotNull @Unmodifiable List<ParameterDef> getParameterDefs(@NotNull Method specificMethod, @NotNull Method method,
                                                             @NotNull Class<?> targetClass,
                                                             @NotNull MethodLogging methodLogging,
                                                             @NotNull MessageParser messageParser,
                                                             @NotNull MethodLoggingConfigurer configurer)
  {
    final var parameterCount = specificMethod.getParameterCount();
    if (parameterCount == 0 && methodLogging.exclude().length == 0)
      return List.of();

    // excluded names and @ParamLog formats are validated even if parameters are not logged
    final var showParameters = methodLogging.parameters() == SHOW;
    final var parameterDefs = new ArrayList<ParameterDef>(8);
    final var specificParameters = specificMethod.getParameters();
    final var declarations = findMethodDeclarations(specificMethod, method, targetClass);
    final var declarationParameterNames = discoverParameterNames(declarations);
    final var excludeParameters = Set.copyOf(asList(methodLogging.exclude()));
    final var matchedExcludeParameters = new HashSet<String>();

    for(var p = 0; p < parameterCount; p++)
    {
      final var paramLog = findParamLog(declarations, p);
      final var names = getParameterNames(paramLog, declarationParameterNames, specificParameters[p], p);
      final var name = names.iterator().next();

      // all names match, so a parameter named differently in an interface and its implementation is excluded either way
      var excludedByName = false;
      for(var n: names)
        if (excludeParameters.contains(n))
        {
          matchedExcludeParameters.add(n);
          excludedByName = true;
        }

      if (paramLog != null && paramLog.exclude())
        continue;

      final var parameterMessage = messageParser.messageSupplier(
          paramLog != null ? paramLog.format() : "",
          DEFAULT_PARAMETER_VALUE_FORMAT,
          "@ParamLog format of parameter '" + name + '\'',
          specificMethod);

      if (showParameters && !excludedByName &&
          (paramLog != null || isParameterIncluded(configurer, forMethodParameter(specificMethod, p, targetClass))))
        parameterDefs.add(new ParameterDef(p, name, paramLog == null || paramLog.inline(), parameterMessage));
    }

    validateExcludedParameterNames(specificMethod, methodLogging.exclude(), matchedExcludeParameters,
        !declarationParameterNames.isEmpty());

    return copyOf(parameterDefs);
  }


  /**
   * Discovers the parameter names of each declaration providing them.
   *
   * @param declarations  method declarations, ordered from most to least specific, not {@code null}
   *
   * @return  parameter names of the declarations providing them, in declaration order, never {@code null}
   *
   * @since 0.6.0
   */
  @Contract(pure = true)
  private @NotNull List<String[]> discoverParameterNames(@NotNull List<Method> declarations)
  {
    final var declarationParameterNames = new ArrayList<String[]>();

    for(var declaration: declarations)
    {
      final var parameterNames = nameDiscoverer.getParameterNames(declaration);
      if (parameterNames != null)
        declarationParameterNames.add(parameterNames);
    }

    return declarationParameterNames;
  }


  /**
   * Returns all names of parameter {@code p}, starting with the name to log: the name given by {@link ParamLog#name()},
   * followed by the discovered names of all declarations. If no declaration provides parameter names, the synthesized
   * reflection name ({@code arg0}, {@code arg1}, ...) is used instead of the discovered names.
   *
   * @param paramLog                   {@code @ParamLog} annotation of the parameter or {@code null}
   * @param declarationParameterNames  discovered parameter names of the declarations, not {@code null}
   * @param specificParameter          parameter of the method implementation, not {@code null}
   * @param p                          parameter index
   *
   * @return  parameter names, never empty
   *
   * @since 0.6.0
   */
  @Contract(pure = true)
  private static @NotNull Set<String> getParameterNames(ParamLog paramLog,
                                                        @NotNull List<String[]> declarationParameterNames,
                                                        @NotNull Parameter specificParameter, int p)
  {
    final var names = new LinkedHashSet<String>();

    if (paramLog != null && hasLength(paramLog.name()))
      names.add(paramLog.name());

    for(var parameterNames: declarationParameterNames)
      names.add(parameterNames[p]);

    if (declarationParameterNames.isEmpty())
      names.add(specificParameter.getName());

    return names;
  }


  /**
   * Returns all declarations of {@code specificMethod}: the method itself, the invoked method and the methods it
   * overrides in its superclasses and the interfaces of {@code targetClass}, ordered from most to least specific.
   * Subclasses of the declaring class are not searched, as their methods override {@code specificMethod} (e.g. if a
   * superclass method is executed by a {@code super} call with AspectJ).
   * <p>
   * Parameter annotations are not inherited. Without collecting the declarations, a {@code @ParamLog} annotation on an
   * interface or superclass method would only be visible if the invoked method is that very method (e.g. for JDK
   * proxies), but not for CGLIB proxies or AspectJ.
   *
   * @param specificMethod  method implementation, not {@code null}
   * @param method          invoked method, possibly an interface method, not {@code null}
   * @param targetClass     class of the target instance, not {@code null}
   *
   * @return  method declarations, starting with {@code specificMethod}, never {@code null}
   *
   * @since 0.6.0
   */
  @Contract(pure = true)
  private static @NotNull @Unmodifiable List<Method> findMethodDeclarations(@NotNull Method specificMethod,
                                                                            @NotNull Method method,
                                                                            @NotNull Class<?> targetClass)
  {
    final var types = new ArrayList<Class<?>>();
    final var declaringClass = specificMethod.getDeclaringClass();

    for(var type = declaringClass; type != null && type != Object.class; type = type.getSuperclass())
      types.add(type);

    types.addAll(getAllInterfacesForClassAsSet(targetClass));

    final var declarations = new LinkedHashSet<Method>();

    declarations.add(specificMethod);
    declarations.add(method);

    for(var type: types)
      for(var candidate: type.getDeclaredMethods())
        if (isOverriddenBy(candidate, specificMethod, targetClass))
          declarations.add(candidate);

    return copyOf(declarations);
  }


  /**
   * Tells whether {@code candidate} is overridden by {@code specificMethod}. Generic parameter types of both methods
   * are resolved against {@code targetClass}. A package-private {@code candidate} is only overridden
   * by a method declared in the same runtime package, i.e. the same package of the same class loader.
   *
   * @param candidate       method declared by a superclass or interface of {@code targetClass}, not {@code null}
   * @param specificMethod  method implementation, not {@code null}
   * @param targetClass     class of the target instance, not {@code null}
   *
   * @return  {@code true} if {@code specificMethod} overrides {@code candidate}, {@code false} otherwise
   *
   * @since 0.6.0
   */
  @Contract(pure = true)
  private static boolean isOverriddenBy(@NotNull Method candidate, @NotNull Method specificMethod,
                                        @NotNull Class<?> targetClass)
  {
    final var modifiers = candidate.getModifiers();
    if ((modifiers & (PRIVATE | STATIC)) != 0 || candidate.isBridge() || candidate.isSynthetic())
      return false;

    if (!candidate.getName().equals(specificMethod.getName()))
      return false;

    if ((modifiers & (PUBLIC | PROTECTED)) == 0 &&
        !isSameRuntimePackage(candidate.getDeclaringClass(), specificMethod.getDeclaringClass()))
      return false;

    final var parameterCount = specificMethod.getParameterCount();
    if (candidate.getParameterCount() != parameterCount)
      return false;

    for(var p = 0; p < parameterCount; p++)
      if (forMethodParameter(candidate, p, targetClass).toClass() !=
          forMethodParameter(specificMethod, p, targetClass).toClass())
        return false;

    return true;
  }


  /**
   * Tells whether both classes belong to the same runtime package: the same package defined by the same class loader.
   *
   * @param type1  first class, not {@code null}
   * @param type2  second class, not {@code null}
   *
   * @return  {@code true} if both classes belong to the same runtime package, {@code false} otherwise
   *
   * @since 0.6.0
   */
  @Contract(pure = true)
  private static boolean isSameRuntimePackage(@NotNull Class<?> type1, @NotNull Class<?> type2) {
    return type1.getClassLoader() == type2.getClassLoader() && type1.getPackageName().equals(type2.getPackageName());
  }


  /**
   * Returns the {@code @ParamLog} annotation of parameter {@code p} from the most specific declaration providing one.
   * An exclusion is never overridden: if any declaration excludes the parameter, its annotation is returned instead.
   *
   * @param declarations  method declarations, ordered from most to least specific, not {@code null}
   * @param p             parameter index
   *
   * @return  {@code @ParamLog} annotation or {@code null} if no declaration annotates the parameter
   *
   * @since 0.6.0
   */
  @Contract(pure = true)
  private static ParamLog findParamLog(@NotNull List<Method> declarations, int p)
  {
    ParamLog paramLog = null;

    for(var declaration: declarations)
    {
      final var declarationParamLog = getMergedAnnotation(declaration.getParameters()[p], ParamLog.class);

      if (declarationParamLog != null)
      {
        if (declarationParamLog.exclude())
          return declarationParamLog;

        if (paramLog == null)
          paramLog = declarationParamLog;
      }
    }

    return paramLog;
  }


  /**
   * Validates that each excluded parameter name matches a parameter. Names excluded by a composed annotation are not
   * validated if parameter names are available, as a composed annotation may be used on methods with different
   * parameters.
   *
   * @param method                    method implementation, not {@code null}
   * @param excludeParameters         excluded parameter names, not {@code null}
   * @param matchedExcludeParameters  excluded parameter names matching a parameter, not {@code null}
   * @param parameterNamesAvailable   {@code true} if parameter names were discovered for at least one declaration
   *
   * @throws IllegalStateException  if an excluded parameter name matches no parameter name
   *
   * @since 0.6.0
   */
  private static void validateExcludedParameterNames(@NotNull Method method, @NotNull String[] excludeParameters,
                                                     @NotNull Set<String> matchedExcludeParameters,
                                                     boolean parameterNamesAvailable)
  {
    // names excluded by a composed annotation may be meant for other methods than this one
    if (parameterNamesAvailable && !isMethodLoggingDirectlyDeclared(method))
      return;

    for(var excludeParameter: excludeParameters)
      if (!matchedExcludeParameters.contains(excludeParameter))
      {
        throw new IllegalStateException("Excluded parameter '" + excludeParameter + "' matches no parameter of " +
            method + (parameterNamesAvailable
                ? ""
                : ", as parameter names are not available; compile with -parameters or exclude the parameter " +
                  "with @ParamLog(exclude = true)"));
      }
  }


  /**
   * Tells whether the {@code @MethodLogging} annotation providing the method logging attributes of
   * {@code specificMethod} is declared directly, as opposed to being a meta-annotation of a composed annotation.
   * The annotation is selected as when {@link AnnotationMethodLoggingSource} finds the method logging attributes.
   *
   * @param specificMethod  method implementation, not {@code null}
   *
   * @return  {@code true} if {@code @MethodLogging} is declared directly on the method or an overridden method,
   *          {@code false} otherwise
   *
   * @since 0.6.0
   */
  @Contract(pure = true)
  private static boolean isMethodLoggingDirectlyDeclared(@NotNull Method specificMethod)
  {
    return MergedAnnotations
        .from(specificMethod, TYPE_HIERARCHY, RepeatableContainers.none())
        .get(MethodLogging.class, null, MergedAnnotationSelectors.firstDirectlyDeclared())
        .getDistance() == 0;
  }


  /**
   * Tells whether a parameter without {@code @ParamLog} annotation is to be logged. Primitive parameters are always
   * logged; for all other parameters, the configurer decides.
   *
   * @param configurer           method logging configurer, not {@code null}
   * @param methodParameterType  method parameter type, not {@code null}
   *
   * @return  {@code true} if the parameter is to be logged, {@code false} otherwise
   *
   * @see MethodLoggingConfigurer#excludeMethodParameter(ResolvableType)
   *
   * @since 0.6.0
   */
  private static boolean isParameterIncluded(@NotNull MethodLoggingConfigurer configurer,
                                             @NotNull ResolvableType methodParameterType)
  {
    return
        methodParameterType.toClass().isPrimitive() ||
        !configurer.excludeMethodParameter(methodParameterType);
  }
}
