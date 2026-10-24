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
package de.sayayi.lib.methodlogging.annotation;

import de.sayayi.lib.message.MessageFactory;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import org.intellij.lang.annotations.Language;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * Annotation that marks a method parameter for logging when the enclosing method is annotated with
 * {@link MethodLogging @MethodLogging}. It allows customizing how the parameter value is formatted, whether it appears
 * inline or on a separate line, and what name is used in the log output.
 * <p>
 * Parameters without this annotation may still be logged depending on their type and the
 * {@link MethodLoggingConfigurer#excludeMethodParameter(ResolvableType)} configuration. Annotating a parameter with
 * {@code @ParamLog} ensures it is included regardless of that configuration and allows fine-grained control over its
 * log representation. A parameter annotated with {@code @ParamLog} is not logged however, if it is excluded
 * ({@link #exclude()} or by name with {@link MethodLogging#exclude()}) or if parameter logging is disabled
 * ({@link MethodLogging#parameters()}).
 * <p>
 * This annotation may be used as a meta-annotation to compose a custom parameter annotation with preset attributes.
 * Attributes of the composed annotation can override {@code @ParamLog} attributes using {@link AliasFor @AliasFor}:
 *
 * <pre>
 *   &#064;Target(PARAMETER)
 *   &#064;Retention(RUNTIME)
 *   &#064;ParamLog(format = "[%{value}]", inline = false)
 *   public &#064;interface Bracketed {
 *     &#064;AliasFor(annotation = ParamLog.class, attribute = "name")
 *     String name() default "";
 *   }
 * </pre>
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see MethodLogging
 * @see MethodLoggingConfigurer#excludeMethodParameter(ResolvableType)
 */
@Target({ PARAMETER, ANNOTATION_TYPE })
@Retention(RUNTIME)
public @interface ParamLog
{
  /**
   * Alias for {@link #format()}.
   *
   * @see #format()
   */
  @AliasFor("format")
  @Language("MessageFormat")
  String value() default "";


  /**
   * Returns the format for the parameter value. The message context will provide a variable named {@code value}
   * containing the object passed to the method.
   * <p>
   * The default format is {@code %{value}}, which will format the parameter value using the default formatter for this
   * type from the message context.
   *
   * @return  a message format string, or empty to use the default format
   *
   * @see MethodLoggingConfigurer#messageSupport()
   * @see MessageFactory#parseMessage(String)
   */
  @AliasFor("value")
  @Language("MessageFormat")
  String format() default "";


  /**
   * Tells if the parameter is to be logged inline (= {@code true}) as part of the parameter list. If this annotation
   * attribute equals {@code false} the parameter will be logged separately after the method entry has been logged.
   * E.g.:
   *
   * <pre>
   *   &gt; method(inlineParam=...)
   *   parameter 'notInlineParam' = ...
   *   &lt; method
   * </pre>
   *
   * By default, parameters are logged inline.
   *
   * @return  {@code true} to log the parameter inline, {@code false} to log it separately
   */
  boolean inline() default true;


  /**
   * Tells the name of the parameter. This attribute provides a way to name the parameter in cases where the class
   * bytecode does not provide compiled-in parameter name information.
   * <p>
   * If this attribute is empty and the parameter name cannot be discovered, the synthesized name provided by reflection
   * ({@code arg0}, {@code arg1}, ...) is used. Parameter names are discovered from classes compiled with
   * {@code -parameters}; Spring Framework versions before 6.1 also discover them from the debug information.
   *
   * @return  the parameter name to use in log output, or empty to use the discovered name
   */
  String name() default "";


  /**
   * Tells if the parameter is to be excluded from method logging. This is useful for parameters whose value has no
   * meaningful string representation (e.g. the default {@code toString()} of an object), produces far too much output
   * (e.g. large collections, documents or binary data) or contains sensitive information such as passwords or tokens.
   * Contrary to excluding the parameter by name with {@link MethodLogging#exclude()}, the exclusion does not depend on
   * the parameter name being available.
   * <p>
   * If this attribute is {@code true}, all other attributes are ignored.
   *
   * @return  {@code true} to exclude the parameter, {@code false} to log it
   *
   * @since 0.6.0
   */
  boolean exclude() default false;
}
