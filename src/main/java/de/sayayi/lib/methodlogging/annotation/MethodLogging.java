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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * @author Jeroen Gremmen
 * @version 0.1.0
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface MethodLogging
{
  /**
   * <p>
   *   The field name in this class that contains a loggable instance. The default is the value provided by
   *   {@link MethodLoggingConfig#loggerFieldName()}.
   * </p>
   * <p>
   *   Generally the loggable instance is a slf4j/jul/log4j logger but there are no restrictions to the kind
   *   of object held by the field.<br>
   *   The method logger factory provided by {@link MethodLoggingConfigurer#methodLoggerFactory()}
   *   must be able to handle the contents of the field.
   * </p>
   *
   * @return  logger field name
   */
  String loggerFieldName() default "<DEFAULT>";

  Visibility lineNumber() default Visibility.DEFAULT;

  Visibility elapsedTime() default Visibility.DEFAULT;

  Visibility parameters() default Visibility.DEFAULT;

  Visibility result() default Visibility.DEFAULT;

  String inlineParameterFormat() default "<DEFAULT>";

  String parameterFormat() default "<DEFAULT>";

  String resultFormat() default "<DEFAULT>";

  Level entryExitLevel() default Level.DEFAULT;

  Level parameterLevel() default Level.DEFAULT;

  Level resultLevel() default Level.DEFAULT;

  String[] exclude() default {};



  enum Visibility {
    DEFAULT, SHOW, HIDE;
  }




  enum Level {
    DEFAULT, TRACE, DEBUG, INFO
  }
}