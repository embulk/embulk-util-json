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
    CapturingPointerToRoot(
            final boolean hasLiteralsWithNumbers,
            final boolean hasFallbacksForUnparsableNumbers,
            final double defaultDouble,
            final long defaultLong) {
        this.valueReader = new InternalJsonValueReader(
                hasLiteralsWithNumbers, hasFallbacksForUnparsableNumbers, defaultDouble, defaultLong);
    }

    @Override
    public JsonValue[] captureFromParser(final JsonParser jacksonParser) throws IOException {
        final JsonValue[] values = new JsonValue[1];
        values[0] = this.valueReader.read(jacksonParser);
        return values;
    }

    private final InternalJsonValueReader valueReader;
}
