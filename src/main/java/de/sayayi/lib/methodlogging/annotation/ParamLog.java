package de.sayayi.lib.methodlogging.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ParamLog
{
  @AliasFor("format")
  String value() default "";

  @AliasFor("value")
  String format() default "";

  boolean hide() default false;

  boolean inMethod() default false;

  String name() default "";
}