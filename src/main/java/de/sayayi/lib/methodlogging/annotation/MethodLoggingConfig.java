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

import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility;
import org.intellij.lang.annotations.Language;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Level.DEFAULT;
import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility.HIDE;
import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility.SHOW;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
@Target(TYPE)
@Retention(RUNTIME)
@SuppressWarnings({"UnknownLanguage", "RedundantSuppression"})
public @interface MethodLoggingConfig
{
  @Language("MessageFormat")
  String DEFAULT_INLINE_PARAMETER_FORMAT = "%{parameter}=%{value}";

  @Language("MessageFormat")
  String DEFAULT_PARAMETER_FORMAT = "parameter '%{parameter}' = %{value}";

  @Language("MessageFormat")
  String DEFAULT_RESULT_FORMAT = "result = %{result}";


  /**
   * <p>
   *   The field name in this class that contains a loggable instance. The default is {@code "log"}.
   * </p>
   * <p>
   *   Generally the loggable instance is a slf4j/jul/log4j logger but there are no restrictions to the kind
   *   of object held by the field.<br>
   *   The method logger factory provided by {@link MethodLoggingConfigurer#methodLoggerFactory()}
   *   must be able to handle the contents of the field.
   * </p>
   *
   * @return  logger field name
   *
   * @see MethodLoggingConfigurer#defaultLoggerFieldName()
   */
  String loggerFieldName() default "<DEFAULT>";


  String methodEntryPrefix() default "> ";


  String methodExitPrefix() default "< ";


  /**
   * <p>
   *   Indicate whether the method line number is logged or not.
   * </p>
   * <p>
   *   The line number is not necessarily the exact line of the method declaration. The reason is that
   *   a java class does not provide line information of methods but only of statements. The line number
   *   presented is usually the line of the first statement in the method body.
   * </p>
   * <p>
   *   If the class does not provide line number information, the visibility is reduced to
   *   {@link Visibility#HIDE}.
   * </p>
   *
   * @return  {@link Visibility#SHOW} (the default) shows the line number if available,
   *          {@link Visibility#HIDE} does not show the line number
   *
   * @see MethodLoggingConfigurer#defaultLineNumber()
   * @see MethodLogging#lineNumber()
   */
  Visibility lineNumber() default Visibility.DEFAULT;


  /**
   * <p>
   *   Indicate whether the elapsed time measured on a method is to be logged on method exit or not.
   * </p>
   *
   * @return  {@link Visibility#SHOW} shows the elapsed time on method exit, {@link Visibility#HIDE}
   *          (the default) does not show the elapsed time
   *
   * @see MethodLogging#elapsedTime()
   */
  Visibility elapsedTime() default HIDE;


  Visibility parameters() default SHOW;

  Visibility result() default SHOW;

  @Language("MessageFormat")
  String inlineParameterFormat() default DEFAULT_INLINE_PARAMETER_FORMAT;

  @Language("MessageFormat")
  String parameterFormat() default DEFAULT_PARAMETER_FORMAT;

  @Language("MessageFormat")
  String resultFormat() default DEFAULT_RESULT_FORMAT;

  Level entryExitLevel() default DEFAULT;

  Level parameterLevel() default DEFAULT;

  Level resultLevel() default DEFAULT;
}
