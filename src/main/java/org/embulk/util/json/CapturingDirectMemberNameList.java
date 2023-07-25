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
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.embulk.spi.json.JsonValue;

class CapturingDirectMemberNameList extends CapturingPointers {
    private CapturingDirectMemberNameList(final List<String> memberNames) {
        final HashMap<String, Integer> memberNamesMap = new HashMap<>();
        int i = 0;
        for (final String memberName : memberNames) {
            memberNamesMap.put(memberName, i);
            i++;
        }
        this.memberNames = Collections.unmodifiableMap(memberNamesMap);

        this.size = memberNames.size();
    }

    static CapturingDirectMemberNameList of(final List<String> memberNames) {
        return new CapturingDirectMemberNameList(Collections.unmodifiableList(new ArrayList<>(memberNames)));
    }

    @Override
    JsonValue[] captureFromParser(
            final JsonParser jacksonParser,
            final InternalJsonValueReader valueReader) throws IOException {
        final JsonValue[] values = new JsonValue[this.size];
        for (int i = 0; i < values.length; i++) {
            values[i] = null;
        }

        try {
            final JsonToken firstToken = jacksonParser.nextToken();
            if (firstToken == null) {
                throw new JsonParseException("Failed to parse JSON");
            }
            if (firstToken != JsonToken.START_OBJECT) {
                throw new JsonParseException("Failed to parse JSON: Expected JSON Object, but " + firstToken.toString());
            }
        } catch (final com.fasterxml.jackson.core.JsonParseException ex) {
            throw new JsonParseException("Failed to parse JSON", ex);
        } catch (final IOException ex) {
            throw ex;
        } catch (final JsonParseException ex) {
            throw ex;
        } catch (final RuntimeException ex) {
            throw new JsonParseException("Failed to parse JSON", ex);
        }

        while (true) {
            final JsonToken token = jacksonParser.nextToken();

            if (token == null) {
                throw new JsonParseException("Failed to parse JSON: Unexpected end");
            }
            if (token == JsonToken.END_OBJECT) {
                break;
            }
            if (token != JsonToken.FIELD_NAME) {
                throw new JsonParseException("Failed to parse JSON: Unexpected token: " + token.toString());
            }

            final String key = jacksonParser.getCurrentName();
            if (key == null) {
                throw new JsonParseException(
                    "Unexpected token "
                    + token
                    + " at "
                    + jacksonParser.getTokenLocation());
            }

            final Integer index = this.memberNames.get(key);
            if (index == null) {
                valueReader.skip(jacksonParser);
            } else {
                values[index] = valueReader.read(jacksonParser);
            }
        }

        return values;
    }

    private final Map<String, Integer> memberNames;

    private final int size;
}
