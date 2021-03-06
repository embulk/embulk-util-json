/*
 * Copyright 2015 The Embulk project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.embulk.util.json;

import org.embulk.spi.DataException;

/**
 * Represents an Exception in parsing a stringified JSON.
 */
public class JsonParseException extends DataException {
    /**
     * Constructs a new {@link JsonParseException} with the specified detail message.
     *
     * @param message  the detail message
     */
    public JsonParseException(final String message) {
        super(message);
    }

    /**
     * Constructs a new {@link JsonParseException} with the specified detail message and cause.
     *
     * @param message  the detail message
     * @param cause  the cause
     */
    public JsonParseException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
