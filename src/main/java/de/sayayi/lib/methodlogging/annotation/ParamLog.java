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

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see MethodLoggingConfigurer#excludeMethodParameter(ResolvableType)
 */
@Target(PARAMETER)
@Retention(RUNTIME)
@SuppressWarnings("UnknownLanguage")
public @interface ParamLog
{
  @AliasFor("format")
  String value() default "";


  /**
   * Returns the format for the parameter value. The message context will provide a variable named
   * {@code value} containing the object passed to the method.
   * <p>
   * The default format is {@code %{value}}, which will format the parameter value using the default
   * formatter for this type from the message context.
   *
   * @see MethodLoggingConfigurer#messageSupport()
   * @see MessageFactory#parseMessage(String)
   */
  @Language("MessageFormat")
  String format() default "";


  /**
   * Tells if the parameter is to be logged inline (= {@code true}) as part of the parameter list.
   * If this annotation attribute equals {@code false} the parameter will be logged separately after
   * the method entry has been logged. Eg.:
   *
   * <pre>
   *   &gt; method(inlineParam=...)
   *   parameter 'notInlineParam' = ...
   *   &lt; method
   * </pre>
   *
   * By default parameters are logged inline.
   */
  boolean inline() default true;


  /**
   * Tells the name of the parameter. This attribute provides a way to name the parameter in cases
   * where the class bytecode does not provide compiled-in parameter name information.
   */
  String name() default "";
}
