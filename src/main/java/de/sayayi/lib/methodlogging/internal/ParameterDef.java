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
package de.sayayi.lib.methodlogging.internal;

import de.sayayi.lib.message.Message;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;


/**
 * Logging definition of a method parameter to be logged. Parameters which are not logged have no definition.
 * <p>
 * Instances are immutable and shared by all threads invoking the method. The parameter format message is created on
 * first use.
 *
 * @param index          index of the parameter
 * @param name           name of the parameter, not {@code null}
 * @param inline         {@code true} if the parameter is logged inline with the method entry
 * @param formatMessage  lazily created parameter format message, not {@code null}
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
record ParameterDef(int index, @NotNull String name, boolean inline, @NotNull Supplier<Message> formatMessage) {
}
