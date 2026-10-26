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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.DefaultResourceLoader;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static de.sayayi.lib.methodlogging.Visibility.HIDE;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@DisplayName("Exclude method parameter")
class ExcludeMethodParameterTest
{
  @Test
  @DisplayName("Parameters are excluded by name")
  void excludeByName() throws Exception
  {
    final var methodDef = new AnnotationMethodLoggingSource(
        new MethodLoggingConfigurer() {},
        new DefaultResourceLoader())
        .getMethodDefinition(
            ExcludeParams.class.getMethod("excludeParams", int.class, int.class, String.class, Locale.class),
            ExcludeParams.class);

    // "id" is the @ParamLog name, "p1" the parameter name
    assertEquals(
        List.of("name", "locale"),
        requireNonNull(methodDef)
            .inlineParameters
            .stream()
            .map(ParameterDef::name)
            .toList());
  }


  @Test
  @DisplayName("Parameters are excluded by @ParamLog(exclude = true)")
  void excludeByParamLog() throws Exception
  {
    final var methodDef = new AnnotationMethodLoggingSource(
        new MethodLoggingConfigurer() {},
        new DefaultResourceLoader())
        .getMethodDefinition(
            ExcludeParams.class.getMethod("excludeAnnotated", String.class, String.class, String.class, String.class),
            ExcludeParams.class);

    assertEquals(List.of("user"), requireNonNull(methodDef).inlineParameters.stream().map(ParameterDef::name).toList());
    assertEquals(List.of(), methodDef.inMethodParameters);
  }


  @Test
  @DisplayName("@ParamLog on interface method is used for implementation method (CGLIB proxy, AspectJ)")
  void paramLogFromInterface() throws Exception
  {
    assertEquals(List.of("user"), inlineParameterNames(
        LoginServiceImpl.class.getMethod("login", String.class, String.class), LoginServiceImpl.class));
  }


  @Test
  @DisplayName("@ParamLog on abstract superclass method is used for overriding method")
  void paramLogFromSuperclass() throws Exception
  {
    assertEquals(List.of("user"), inlineParameterNames(
        ConcreteLogin.class.getMethod("login", String.class, String.class), ConcreteLogin.class));
  }


  @Test
  @DisplayName("Exclusion on interface method is not overridden by @ParamLog on implementation method")
  void exclusionNotOverridden() throws Exception
  {
    assertEquals(List.of("account"), inlineParameterNames(
        RenamingLoginService.class.getMethod("login", String.class, String.class), RenamingLoginService.class));
  }


  @Test
  @DisplayName("@ParamLog on generic interface method is used for implementation method")
  void paramLogFromGenericInterface() throws Exception
  {
    assertEquals(List.of("id"), inlineParameterNames(
        StringSecretHandler.class.getMethod("handle", String.class, String.class), StringSecretHandler.class));
  }


  @Test
  @DisplayName("@ParamLog on generic interface method is used for generic superclass implementation (CGLIB proxy)")
  void paramLogFromGenericInterfaceForGenericImplementation() throws Exception
  {
    assertEquals(List.of("value"), inlineParameterNames(
        AbstractGenericStore.class.getMethod("store", Object.class, String.class), StringStore.class));
  }


  @Test
  @DisplayName("@ParamLog on generic interface method is used for generic superclass implementation (JDK proxy)")
  void paramLogFromGenericInterfaceForGenericImplementationViaInterface() throws Exception
  {
    final var source = new AnnotationMethodLoggingSource(new MethodLoggingConfigurer() {}, new DefaultResourceLoader());

    // the pointcut matches the class methods first, so the definition of the specific method is analyzed before the
    // interface method is invoked
    source.getMethodDefinition(
        AbstractGenericStore.class.getMethod("store", Object.class, String.class), StringStore.class);

    final var methodDef = source.getMethodDefinition(
        GenericStore.class.getMethod("store", Object.class, String.class), StringStore.class);

    assertEquals(List.of("value"), requireNonNull(methodDef).inlineParameters.stream().map(ParameterDef::name).toList());
  }


  @Test
  @DisplayName("@ParamLog on generic interface method is not used for overload of generic superclass")
  void paramLogFromGenericInterfaceNotUsedForOverload() throws Exception
  {
    assertEquals(List.of("value", "secret"), inlineParameterNames(
        AbstractGenericStore.class.getMethod("store", Integer.class, String.class), StringStore.class));
  }


  @Test
  @DisplayName("@ParamLog on generic superclass method is used for generic subclass implementation")
  void paramLogFromGenericSuperclassForGenericImplementation() throws Exception
  {
    assertEquals(List.of("value"), inlineParameterNames(
        GenericStoreImpl.class.getMethod("store", Object.class, String.class), LongStore.class));
  }


  @Test
  @DisplayName("Excluded names are validated for methods without parameters or with hidden parameters")
  void excludeUnmatchedNameNotLogged() throws Exception
  {
    final var source = new AnnotationMethodLoggingSource(new MethodLoggingConfigurer() {}, new DefaultResourceLoader());

    final var ex1 = assertThrowsExactly(IllegalStateException.class, () -> source.getMethodDefinition(
        ExcludeParams.class.getMethod("excludeWithoutParameters"), ExcludeParams.class));
    assertTrue(ex1.getMessage().startsWith("Excluded parameter 'pasword' matches no parameter of "), ex1.getMessage());

    final var ex2 = assertThrowsExactly(IllegalStateException.class, () -> source.getMethodDefinition(
        ExcludeParams.class.getMethod("excludeHidden", String.class), ExcludeParams.class));
    assertTrue(ex2.getMessage().startsWith("Excluded parameter 'pasword' matches no parameter of "), ex2.getMessage());
  }


  @Test
  @DisplayName("Hidden parameters are not passed to the configurer")
  void hiddenParametersNotQueried() throws Exception
  {
    final var parameterTypes = new ArrayList<Class<?>>();
    final var methodDef = new AnnotationMethodLoggingSource(
        new MethodLoggingConfigurer() {
          @Override
          public boolean excludeMethodParameter(@NotNull ResolvableType methodParameterType)
          {
            parameterTypes.add(methodParameterType.resolve());
            return false;
          }
        },
        new DefaultResourceLoader())
        .getMethodDefinition(ExcludeParams.class.getMethod("hidden", String.class, String.class), ExcludeParams.class);

    assertTrue(requireNonNull(methodDef).inlineParameters.isEmpty());
    assertTrue(parameterTypes.isEmpty(), parameterTypes::toString);
  }


  private static List<String> inlineParameterNames(Method method, Class<?> targetClass)
  {
    final var methodDef = new AnnotationMethodLoggingSource(
        new MethodLoggingConfigurer() {},
        new DefaultResourceLoader())
        .getMethodDefinition(method, targetClass);

    return requireNonNull(methodDef).inlineParameters.stream().map(ParameterDef::name).toList();
  }


  @Test
  @DisplayName("Generic parameter type is resolved against the target class")
  void genericParameterType() throws Exception
  {
    final var parameterTypes = new ArrayList<Class<?>>();
    final var source = new AnnotationMethodLoggingSource(
        new MethodLoggingConfigurer() {
          @Override
          public boolean excludeMethodParameter(@NotNull ResolvableType methodParameterType)
          {
            parameterTypes.add(methodParameterType.resolve());
            return methodParameterType.toClass() == String.class;
          }
        },
        new DefaultResourceLoader());

    final var methodDef = source.getMethodDefinition(
        AbstractRepository.class.getMethod("save", Object.class, Long.class),
        StringRepository.class);

    assertEquals(List.of(String.class, Long.class), parameterTypes);
    assertEquals(
        List.of("id"),
        requireNonNull(methodDef)
            .inlineParameters
            .stream()
            .map(ParameterDef::name)
            .toList());
  }




  public interface LoginService
  {
    @MethodLogging
    @SuppressWarnings("unused")
    void login(@ParamLog(name = "user") String user, @ParamLog(exclude = true) String password);
  }




  public static class LoginServiceImpl implements LoginService
  {
    @Override
    public void login(String user, String password) {
    }
  }




  public static class RenamingLoginService implements LoginService
  {
    @Override
    public void login(@ParamLog(name = "account") String user, @ParamLog(name = "pwd") String password) {
    }
  }




  public abstract static class AbstractLogin
  {
    @MethodLogging
    @SuppressWarnings("unused")
    public abstract void login(@ParamLog(name = "user") String user, @Sensitive String password);
  }




  public static class ConcreteLogin extends AbstractLogin
  {
    @Override
    public void login(String user, String password) {
    }
  }




  public interface SecretHandler<T>
  {
    @MethodLogging
    @SuppressWarnings("unused")
    void handle(@ParamLog(exclude = true) T secret, @ParamLog(name = "id") String id);
  }




  public static class StringSecretHandler implements SecretHandler<String>
  {
    @Override
    public void handle(String secret, String id) {
    }
  }




  public interface GenericStore<T>
  {
    @MethodLogging
    @SuppressWarnings("unused")
    void store(T value, @ParamLog(exclude = true) String secret);
  }




  public abstract static class AbstractGenericStore<T> implements GenericStore<T>
  {
    @Override
    public void store(T value, String secret) {
    }


    @MethodLogging
    @SuppressWarnings("unused")
    public void store(Integer value, String secret) {
    }
  }




  public static class StringStore extends AbstractGenericStore<String> {
  }




  public abstract static class AbstractStore<T>
  {
    @MethodLogging
    @SuppressWarnings("unused")
    public abstract void store(T value, @ParamLog(exclude = true) String secret);
  }




  public static class GenericStoreImpl<T> extends AbstractStore<T>
  {
    @Override
    public void store(T value, String secret) {
    }
  }




  public static class LongStore extends GenericStoreImpl<Long> {
  }




  public abstract static class AbstractRepository<T>
  {
    @MethodLogging
    @SuppressWarnings("unused")
    public void save(T entity, Long id) {
    }
  }




  public static class StringRepository extends AbstractRepository<String> {
  }




  @Target(PARAMETER)
  @Retention(RUNTIME)
  @ParamLog(exclude = true)
  @interface Sensitive {
  }




  public static class ExcludeParams
  {
    @MethodLogging(exclude = { "id", "p1" })
    @SuppressWarnings("unused")
    public void excludeParams(@ParamLog(name = "id") int p0, int p1, String name, Locale locale) {
    }


    @MethodLogging
    @SuppressWarnings("unused")
    public void excludeAnnotated(String user, @ParamLog(exclude = true) String password,
                                 @ParamLog(name = "secret", inline = false, exclude = true) String token,
                                 @Sensitive String apiKey) {
    }


    @MethodLogging(exclude = "pasword")
    public void excludeWithoutParameters() {
    }


    @MethodLogging(parameters = HIDE, exclude = "pasword")
    @SuppressWarnings("unused")
    public void excludeHidden(String password) {
    }


    @MethodLogging(parameters = HIDE, exclude = "password")
    @SuppressWarnings("unused")
    public void hidden(String user, String password) {
    }
  }
}
