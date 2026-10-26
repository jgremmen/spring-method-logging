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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractBeanFactoryPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.STATIC;
import static java.util.concurrent.ConcurrentHashMap.newKeySet;
import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;
import static org.springframework.util.ClassUtils.getAllInterfacesForClassAsSet;
import static org.springframework.util.ReflectionUtils.getAllDeclaredMethods;


/**
 * Configuration for proxy based method logging, registering an advisor applying the method logging interceptor to the
 * methods annotated with {@code @MethodLogging}.
 * <p>
 * The methods of classes matched while the singleton beans are created are analyzed after all singleton beans have been
 * created. So the configurer is not created during the post-processing of a bean and may depend on beans with method
 * logging.
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@Configuration(proxyBeanMethods = false)
@Role(ROLE_INFRASTRUCTURE)
@SuppressWarnings("SpringFacetCodeInspection")
public class ProxyMethodLoggingConfiguration extends MethodLoggingConfiguration
{
  /**
   * {@inheritDoc}
   * <p>
   * The invoked method is resolved: proxies pass the invoked method, which may be an interface or superclass method.
   *
   * @return  {@code true}
   */
  @Override
  boolean isInvokedMethodResolved() {
    return true;
  }


  /**
   * Creates the advisor applying the method logging interceptor to the methods with method logging, ordered as
   * configured by {@link de.sayayi.lib.methodlogging.annotation.EnableMethodLogging#order() @EnableMethodLogging}.
   *
   * @param annotationMethodLoggingSource  method logging source, not {@code null}
   * @param methodLoggingInterceptor       method logging interceptor, not {@code null}
   *
   * @return  method logging advisor, never {@code null}
   */
  @Bean @Role(ROLE_INFRASTRUCTURE)
  MethodLoggingAdvisor internalMethodLoggingAdvisor(
      AnnotationMethodLoggingSource annotationMethodLoggingSource,
      MethodLoggingInterceptor methodLoggingInterceptor)
  {
    final var advisor = new MethodLoggingAdvisor(new MethodLoggingPointcut(annotationMethodLoggingSource));

    advisor.setAdvice(methodLoggingInterceptor);
    advisor.setOrder(enableMethodLogging.<Integer>getNumber("order"));

    return advisor;
  }




  /**
   * Advisor analyzing the deferred methods of its pointcut after all singleton beans have been created.
   */
  static final class MethodLoggingAdvisor extends AbstractBeanFactoryPointcutAdvisor
      implements SmartInitializingSingleton
  {
    /** Pointcut matching the methods with method logging. */
    private final @NotNull MethodLoggingPointcut pointcut;


    /**
     * Creates the advisor for the given pointcut.
     *
     * @param pointcut  method logging pointcut, not {@code null}
     */
    MethodLoggingAdvisor(@NotNull MethodLoggingPointcut pointcut) {
      this.pointcut = pointcut;
    }


    /**
     * Returns the method logging pointcut.
     *
     * @return  method logging pointcut, never {@code null}
     */
    @Override
    public @NotNull Pointcut getPointcut() {
      return pointcut;
    }


    /**
     * Creates the configuration and analyzes the methods of the classes matched by the pointcut while the singleton
     * beans were created.
     *
     * @throws RuntimeException  if the configurer bean cannot be obtained or the method logging configuration of a
     *                           method is invalid
     */
    @Override
    public void afterSingletonsInstantiated() {
      pointcut.analyseDeferredMethods();
    }
  }




  /**
   * Pointcut matching the methods with a method logging definition.
   * <p>
   * Until {@link #analyseDeferredMethods()} is invoked, methods are matched by their {@code @MethodLogging} annotation
   * only and the methods of their target classes are analyzed later. Analyzing a method requires the configurer, which
   * must not be created while a bean is being post-processed: the configurer may depend on that bean.
   */
  static final class MethodLoggingPointcut extends StaticMethodMatcherPointcut
  {
    /** Method logging source analyzing the matched methods. */
    private final @NotNull AnnotationMethodLoggingSource annotationMethodLoggingSource;

    /** Target classes whose methods are to be analyzed by {@link #analyseDeferredMethods()}. */
    private final @NotNull Set<Class<?>> deferredClasses;

    /** Tells whether the analysis of matched methods is deferred until {@link #analyseDeferredMethods()}. */
    private volatile boolean deferAnalysis;


    /**
     * Creates the pointcut, deferring the analysis of the methods of matched classes.
     *
     * @param annotationMethodLoggingSource  method logging source, not {@code null}
     */
    MethodLoggingPointcut(@NotNull AnnotationMethodLoggingSource annotationMethodLoggingSource)
    {
      this.annotationMethodLoggingSource = annotationMethodLoggingSource;

      deferredClasses = newKeySet();
      deferAnalysis = true;
    }


    /**
     * Tells whether {@code method} invoked on an instance of {@code targetClass} has method logging. While the analysis
     * is deferred, a method annotated with {@code @MethodLogging} matches and all methods of {@code targetClass} are
     * analyzed later. Spring stops matching the methods of a class at the first matching method when deciding whether
     * to create a proxy, so the other methods are matched on their first invocation only (e.g. for JDK proxies).
     * <p>
     * Spring matches methods when creating a proxy and on the first invocation of each proxied method. A failed
     * analysis is therefore not passed on: the method matches if it is annotated with {@code @MethodLogging}, so the
     * interceptor reports the failure and invokes the method without logging.
     *
     * @param method       method, possibly an interface method, not {@code null}
     * @param targetClass  target class, not {@code null}
     *
     * @return  {@code true} if the method has method logging, {@code false} otherwise
     */
    @Override
    public boolean matches(@NotNull Method method, @NotNull Class<?> targetClass)
    {
      if (!deferAnalysis)
      {
        try {
          return annotationMethodLoggingSource.hasMethodLogging(method, targetClass);
        } catch(RuntimeException ex) {
          // matching is also part of the invocation: the interceptor reports the failure and invokes the method
          return annotationMethodLoggingSource.hasMethodLoggingAnnotation(method, targetClass);
        }
      }

      if (!annotationMethodLoggingSource.hasMethodLoggingAnnotation(method, targetClass))
        return false;

      deferredClasses.add(targetClass);
      return true;
    }


    /**
     * Creates the configuration, analyzes the methods of the classes matched so far and stops deferring the analysis
     * of methods matched from now on. The methods of classes matched while the configurer is created are analyzed as
     * well. A failure is passed on, so the application context fails to start. If another thread is creating the
     * configuration at that time, the remaining methods are analyzed on first invocation instead.
     *
     * @throws RuntimeException  if the configurer bean cannot be obtained or the method logging configuration of a
     *                           method is invalid
     */
    void analyseDeferredMethods()
    {
      // still deferring, so beans with method logging created for the configurer are analyzed below
      annotationMethodLoggingSource.singletonsInstantiated();
      deferAnalysis = false;

      for(var deferredClass: deferredClasses)
      {
        for(var method: getProxiedMethods(deferredClass))
          if (annotationMethodLoggingSource.hasMethodLoggingAnnotation(method, deferredClass))
            annotationMethodLoggingSource.getMethodDefinition(method, deferredClass);

        deferredClasses.remove(deferredClass);
      }
    }


    /**
     * Returns the methods which may be invoked through a proxy for {@code targetClass}: the non-private, non-static
     * methods of the class and its superclasses and the methods of its interfaces. These are the methods Spring matches
     * when deciding whether to create a proxy and when invoking a proxied method.
     *
     * @param targetClass  target class, not {@code null}
     *
     * @return  methods which may be proxied, never {@code null}
     */
    @Contract(pure = true)
    private static @NotNull List<Method> getProxiedMethods(@NotNull Class<?> targetClass)
    {
      final var methods = new ArrayList<Method>();

      for(var method: getAllDeclaredMethods(targetClass))
        if (isProxyable(method))
          methods.add(method);

      for(var interfaceType: getAllInterfacesForClassAsSet(targetClass))
        for(var method: interfaceType.getMethods())
          if (isProxyable(method))
            methods.add(method);

      return methods;
    }


    /**
     * Tells whether {@code method} may be invoked through a proxy.
     *
     * @param method  method, not {@code null}
     *
     * @return  {@code true} if the method is neither private, static, a bridge method nor declared by {@link Object}
     */
    @Contract(pure = true)
    private static boolean isProxyable(@NotNull Method method)
    {
      return (method.getModifiers() & (PRIVATE | STATIC)) == 0 && !method.isBridge() &&
          method.getDeclaringClass() != Object.class;
    }


    /**
     * Tells whether {@code o} is a method logging pointcut for the same method logging source.
     *
     * @param o  object to compare with
     *
     * @return  {@code true} if both pointcuts are equal, {@code false} otherwise
     */
    @Override
    public boolean equals(Object o)
    {
      return this == o || o instanceof MethodLoggingPointcut that &&
          annotationMethodLoggingSource == that.annotationMethodLoggingSource;
    }


    /**
     * Returns a hash code based on the method logging source.
     *
     * @return  hash code
     */
    @Override
    public int hashCode() {
      return annotationMethodLoggingSource.hashCode();
    }


    /**
     * Returns a string representation of this pointcut, naming the method logging source.
     *
     * @return  string representation, never {@code null}
     */
    @Override
    public String toString() {
      return getClass().getName() + ": " + annotationMethodLoggingSource;
    }
  }
}
