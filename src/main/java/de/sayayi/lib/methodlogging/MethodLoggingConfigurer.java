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
package de.sayayi.lib.methodlogging;

import de.sayayi.lib.message.MessageContext;
import de.sayayi.lib.message.formatter.DefaultFormatterService;
import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.logger.GenericMethodLoggerFactory;
import org.jetbrains.annotations.Contract;
import org.springframework.context.annotation.Configuration;


/**
 * <p>
 *   Interface to be implemented by @{@link Configuration} classes annotated
 *   with @{@link MethodLogging} that wish or need to specify explicitly how messages are
 *   formatted and logged for annotation-driven method logging.
 * </p>
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see EnableMethodLogging
 */
public interface MethodLoggingConfigurer
{
  /**
   * <p>
   *   Construct the message context to be used with method/parameter/result logging.
   * </p>
   * <p>
   *   The default message context is constructed for the default locale and uses the shared
   *   message formatter service {@link DefaultFormatterService#getSharedInstance()} and a message factory
   *   with limited caching capabilities.
   * </p>
   *
   * @return  Message context or {@code null}
   */
  @Contract(pure = true)
  default MessageContext messageContext() {
    return null;
  }


  /**
   * <p>
   *   Construct the method logger factory to be used with method logging.
   * </p>
   * <p>
   *   The default is an instance of {@link GenericMethodLoggerFactory} which is capable of handling logger fields
   *   for the following logger frameworks:
   * </p>
   * <ul>
   *   <li>java util logging (part of jre since 1.4)</li>
   *   <li>log4j2</li>
   *   <li>slf4j</li>
   * </ul>
   *
   * @return  Method logger factory or {@code null}
   *
   * @see MethodLogger
   */
  @Contract(pure = true)
  default MethodLoggerFactory methodLoggerFactory() {
    return null;
  }
}