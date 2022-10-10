package de.sayayi.lib.methodlogging.annotation;

import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Level.DEBUG;
import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Level.INFO;
import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility.HIDE;
import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility.SHOW;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


@Target({TYPE})
@Retention(RUNTIME)
public @interface MethodLoggingConfig
{
  String loggerFieldName() default "logger";

  String methodEntryPrefix() default "> ";

  String methodExitPrefix() default "< ";

  Visibility lineNumber() default SHOW;

  Visibility elapsedTime() default HIDE;

  Visibility parameters() default SHOW;

  Visibility result() default SHOW;

  String parameterFormat() default "%{parameter}=%{value}";

  String resultFormat() default "result = %{result}";

  Level entryExitLevel() default INFO;

  Level parameterLevel() default DEBUG;

  Level resultLevel() default DEBUG;
}