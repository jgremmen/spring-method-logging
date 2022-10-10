package de.sayayi.lib.methodlogging.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


@Target(METHOD)
@Retention(RUNTIME)
public @interface MethodLogging
{
  String loggerFieldName() default "<DEFAULT>";

  Visibility lineNumber() default Visibility.DEFAULT;

  Visibility elapsedTime() default Visibility.DEFAULT;

  Visibility parameters() default Visibility.DEFAULT;

  Visibility result() default Visibility.DEFAULT;

  String parameterFormat() default "<DEFAULT>";

  String resultFormat() default "<DEFAULT>";

  Level entryExitLevel() default Level.DEFAULT;

  Level parameterLevel() default Level.DEFAULT;

  Level resultLevel() default Level.DEFAULT;




  enum Visibility {
    DEFAULT, SHOW, HIDE;
  }




  enum Level {
    DEFAULT, TRACE, DEBUG, INFO
  }
}