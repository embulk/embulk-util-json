/*
 * Copyright 2023 The Embulk project
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

import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import org.embulk.spi.json.JsonValue;

class CapturingPointerToRoot extends CapturingPointers {
    private CapturingPointerToRoot() {
    }

    @Override
    JsonValue[] captureFromParser(
            final JsonParser jacksonParser,
            final InternalJsonValueReader valueReader) throws IOException {
        final JsonValue value = valueReader.read(jacksonParser);
        if (value == null) {
            return null;
        }

        final JsonValue[] values = new JsonValue[1];
        values[0] = value;
        return values;
    }

    static final CapturingPointerToRoot INSTANCE = new CapturingPointerToRoot();
}
