/*
 * Copyright 2026 Jeroen Gremmen
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

/**
 * Parameter formatters for message-format, used by the default message support of method logging.
 * <p>
 * {@link de.sayayi.lib.methodlogging.format.WrapIterableOrArrayFormatter WrapIterableOrArrayFormatter} encloses the
 * formatted elements of iterables and arrays in square brackets, {@link
 * de.sayayi.lib.methodlogging.format.WrapMapFormatter WrapMapFormatter} the formatted entries of maps in braces. Neither
 * formats the elements itself: both delegate to the next formatter of the formatter service, which must therefore
 * provide formatters for iterables, arrays and maps.
 * <p>
 * The formatters can be added to the formatter service of a custom message support, so it logs collections and maps
 * enclosed in brackets and braces like the default message support:
 *
 * <pre>
 *   &#064;Override
 *   public MessageSupport messageSupport()
 *   {
 *     final var formatterService = new DefaultFormatterService();
 *
 *     formatterService.addFormatter(new WrapIterableOrArrayFormatter());
 *     formatterService.addFormatter(new WrapMapFormatter());
 *
 *     return MessageSupportFactory.create(formatterService).setLocale(Locale.US);
 *   }
 * </pre>
 *
 * @see de.sayayi.lib.methodlogging.MethodLoggingConfigurer#messageSupport()
 *
 * @since 0.6.0
 */
package de.sayayi.lib.methodlogging.format;
