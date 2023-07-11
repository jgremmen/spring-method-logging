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
import org.intellij.lang.annotations.Language;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
@Target(METHOD)
@Retention(RUNTIME)
@SuppressWarnings("UnknownLanguage")
public @interface MethodLogging
{
  /**
   * <p>
   *   The field name in this class that contains a loggable instance. The default is the value provided by
   *   {@link MethodLoggingConfig#loggerFieldName()}.
   * </p>
   * <p>
   *   Generally the loggable instance is a slf4j/jul/log4j logger or a logger from another logging framework
   *   but essentially there are no restrictions to the kind of object held by the field. The only constraint
   *   is that the method logger factory provided by {@link MethodLoggingConfigurer#methodLoggerFactory()}
   *   must be able to handle the contents of the field.
   * </p>
   *
   * @return  logger field name
   */
  String loggerFieldName() default "<DEFAULT>";


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
   *   If the class does not provide line number information, the visibility is reduced to {@link Visibility#HIDE}.
   * </p>
   *
   * @return  {@link Visibility#SHOW} shows the line number if available, {@link Visibility#HIDE} does not show
   *          the line number, {@link Visibility#DEFAULT} uses the default setting from
   *          {@link MethodLoggingConfig#lineNumber()}
   */
  Visibility lineNumber() default Visibility.DEFAULT;


  /**
   * <p>
   *   Indicate whether the elapsed time measured on a method is to be logged on method exit or not.
   * </p>
   *
   * @return  {@link Visibility#SHOW} shows the elapsed time on method exit, {@link Visibility#HIDE} does not show
   *          the elapsed time, {@link Visibility#DEFAULT} uses the default setting from
   *          {@link MethodLoggingConfig#elapsedTime()}
   */
  Visibility elapsedTime() default Visibility.DEFAULT;

  Visibility parameters() default Visibility.DEFAULT;

  Visibility result() default Visibility.DEFAULT;

  @Language("MessageFormat")
  String inlineParameterFormat() default "<DEFAULT>";

  @Language("MessageFormat")
  String parameterFormat() default "<DEFAULT>";

  @Language("MessageFormat")
  String resultFormat() default "<DEFAULT>";

  Level entryExitLevel() default Level.DEFAULT;

  Level parameterLevel() default Level.DEFAULT;

  Level resultLevel() default Level.DEFAULT;


  /**
   * Provides an array of parameter names that are to be excluded from method logging.
   */
  String[] exclude() default {};




  /** Feature visibility */
  enum Visibility
  {
    /** for internal use only */
    DEFAULT,

    SHOW,
    HIDE;
  }




  /** Logging level */
  enum Level
  {
    /** for internal use only */
    DEFAULT,

    /** Trace log level */
    TRACE,

    /** Debug log level */
    DEBUG,

    /** Info log level */
    INFO
  }
}
