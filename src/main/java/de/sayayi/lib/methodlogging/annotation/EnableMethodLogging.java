package de.sayayi.lib.methodlogging.annotation;

import de.sayayi.lib.methodlogging.internal.EnableMethodLoggingSelector;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.springframework.context.annotation.AdviceMode.PROXY;
import static org.springframework.core.Ordered.LOWEST_PRECEDENCE;


@Target(TYPE)
@Retention(RUNTIME)
@Documented
@Import(EnableMethodLoggingSelector.class)
public @interface EnableMethodLogging
{
  boolean proxyTargetClass() default false;

  AdviceMode mode() default PROXY;

  int order() default LOWEST_PRECEDENCE;
}