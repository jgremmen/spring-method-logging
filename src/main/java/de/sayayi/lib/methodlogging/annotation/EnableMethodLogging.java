package de.sayayi.lib.methodlogging.annotation;

import de.sayayi.lib.methodlogging.internal.EnableMethodLoggingSelector;
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
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.springframework.context.annotation.AdviceMode.PROXY;
import static org.springframework.core.Ordered.LOWEST_PRECEDENCE;


/**
 * @author Jeroen Gremmen
 * @version 0.1.0
 */
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