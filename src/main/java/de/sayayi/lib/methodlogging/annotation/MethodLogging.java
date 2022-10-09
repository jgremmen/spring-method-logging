package de.sayayi.lib.methodlogging.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


@Target({METHOD, TYPE})
@Retention(RUNTIME)
public @interface MethodLogging
{
  String loggerFieldName() default "";  // logger

  boolean withParameters() default true;

  String parameterFormat() default "";  // %{parameter}=%{value}

  String resultFormat() default "";  // result = %{result}

  boolean showElapsedTime() default false;

  Level entryExitLevel() default Level.INFO;

  Level parameterLevel() default Level.DEBUG;

  Level resultLevel() default Level.DEBUG;




  enum Level
  {
    TRACE,
    DEBUG,
    INFO
  }
}