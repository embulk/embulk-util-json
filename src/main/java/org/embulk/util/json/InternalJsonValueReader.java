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
import java.util.List;
import org.embulk.spi.json.JsonArray;
import org.embulk.spi.json.JsonBoolean;
import org.embulk.spi.json.JsonDouble;
import org.embulk.spi.json.JsonLong;
import org.embulk.spi.json.JsonNull;
import org.embulk.spi.json.JsonObject;
import org.embulk.spi.json.JsonString;
import org.embulk.spi.json.JsonValue;

final class InternalJsonValueReader {
    InternalJsonValueReader(
            final boolean hasLiteralsWithNumbers,
            final boolean hasFallbacksForUnparsableNumbers,
            final double defaultDouble,
            final long defaultLong) {
        this.hasLiteralsWithNumbers = hasLiteralsWithNumbers;
        this.hasFallbacksForUnparsableNumbers = hasFallbacksForUnparsableNumbers;
        this.defaultDouble = defaultDouble;
        this.defaultLong = defaultLong;
    }

    JsonValue read(final JsonParser jacksonParser) throws IOException {
        try {
            final JsonToken token = jacksonParser.nextToken();
            if (token == null) {
                return null;
            }
            return readJsonValue(jacksonParser, token);
        } catch (final com.fasterxml.jackson.core.JsonParseException ex) {
            throw new JsonParseException("Failed to parse JSON", ex);
        } catch (final IOException ex) {
            throw ex;
        } catch (final JsonParseException ex) {
            throw ex;
        } catch (final RuntimeException ex) {
            throw new JsonParseException("Failed to parse JSON", ex);
        }
    }

    void skip(final JsonParser jacksonParser) throws IOException {
        try {
            final JsonToken token = jacksonParser.nextToken();
            if (token == null) {
                throw new JsonParseException("Failed to parse JSON");
            }
            this.skipJsonValue(jacksonParser, token);
        } catch (final com.fasterxml.jackson.core.JsonParseException ex) {
            throw new JsonParseException("Failed to parse JSON", ex);
        } catch (final IOException ex) {
            throw ex;
        } catch (final JsonParseException ex) {
            throw ex;
        } catch (final RuntimeException ex) {
            throw new JsonParseException("Failed to parse JSON", ex);
        }
    }

    private JsonValue readJsonValue(final JsonParser jacksonParser, final JsonToken token) throws IOException {
        switch (token) {
            case VALUE_NULL:
                return JsonNull.NULL;

            case VALUE_TRUE:
                return JsonBoolean.TRUE;

            case VALUE_FALSE:
                return JsonBoolean.FALSE;

            case VALUE_NUMBER_FLOAT:
                if (this.hasLiteralsWithNumbers) {
                    return JsonDouble.withLiteral(this.getDoubleValue(jacksonParser), jacksonParser.getValueAsString());
                } else {
                    return JsonDouble.of(this.getDoubleValue(jacksonParser));  // throws JsonParseException
                }

            case VALUE_NUMBER_INT:
                if (this.hasLiteralsWithNumbers) {
                    return JsonLong.withLiteral(this.getLongValue(jacksonParser), jacksonParser.getValueAsString());
                } else {
                    return JsonLong.of(this.getLongValue(jacksonParser));  // throws JsonParseException
                }

            case VALUE_STRING:
                return JsonString.of(jacksonParser.getText());

            case START_ARRAY:
            {
                final List<JsonValue> list = new ArrayList<>();
                while (true) {
                    final JsonToken nextToken = jacksonParser.nextToken();
                    if (nextToken == null) {
                        throw new JsonParseException(
                                "Unexpected end of JSON at "
                                        + jacksonParser.getTokenLocation()
                                        + " while expecting an element of an array");
                    }
                    if (nextToken == JsonToken.END_ARRAY) {
                        return JsonArray.ofList(list);
                    }
                    list.add(this.readJsonValue(jacksonParser, nextToken));
                }
            }
            // Never fall through from the previous branch of START_ARRAY.

            case START_OBJECT:
            {
                final List<String> keys = new ArrayList<>();
                final List<JsonValue> values = new ArrayList<>();

                while (true) {
                    final JsonToken nextToken = jacksonParser.nextToken();

                    if (nextToken == null) {
                        throw new JsonParseException(
                                "Unexpected end of JSON at "
                                        + jacksonParser.getTokenLocation()
                                        + " while expecting a key of an object");
                    }
                    if (nextToken == JsonToken.END_OBJECT) {
                        return JsonObject.ofUnsafe(
                                keys.toArray(new String[keys.size()]),
                                values.toArray(new JsonValue[values.size()]));
                    }

                    final String key = jacksonParser.getCurrentName();
                    if (key == null) {
                        throw new JsonParseException(
                                "Unexpected token "
                                        + nextToken
                                        + " at "
                                        + jacksonParser.getTokenLocation());
                    }

                    final JsonToken nextNextToken = jacksonParser.nextToken();
                    if (nextNextToken == null) {
                        throw new JsonParseException(
                                "Unexpected end of JSON at "
                                        + jacksonParser.getTokenLocation()
                                        + " while expecting a value of an object");
                    }
                    final JsonValue value = this.readJsonValue(jacksonParser, nextNextToken);
                    keys.add(key);
                    values.add(value);
                }
            }
            // Never fall through from the previous branch of START_OBJECT.

            case VALUE_EMBEDDED_OBJECT:
            case FIELD_NAME:
            case END_ARRAY:
            case END_OBJECT:
            case NOT_AVAILABLE:
            default:
                throw new JsonParseException(
                        "Unexpected token " + token + " at " + jacksonParser.getTokenLocation());
        }
    }

    private void skipJsonValue(final JsonParser jacksonParser, final JsonToken token) throws IOException {
        switch (token) {
            case VALUE_NULL:
            case VALUE_TRUE:
            case VALUE_FALSE:
            case VALUE_NUMBER_FLOAT:
            case VALUE_NUMBER_INT:
            case VALUE_STRING:
                return;

            case START_ARRAY:
                while (true) {
                    final JsonToken nextToken = jacksonParser.nextToken();
                    if (nextToken == null) {
                        throw new JsonParseException(
                                "Unexpected end of JSON at "
                                        + jacksonParser.getTokenLocation()
                                        + " while expecting an element of an array");
                    }
                    if (nextToken == JsonToken.END_ARRAY) {
                        return;
                    }
                    this.skipJsonValue(jacksonParser, nextToken);
                }
                // Never fall through from the previous branch of START_ARRAY.

            case START_OBJECT:
                while (true) {
                    final JsonToken nextToken = jacksonParser.nextToken();

                    if (nextToken == null) {
                        throw new JsonParseException(
                                "Unexpected end of JSON at "
                                        + jacksonParser.getTokenLocation()
                                        + " while expecting a key of an object");
                    }
                    if (nextToken == JsonToken.END_OBJECT) {
                        return;
                    }

                    if (nextToken != JsonToken.FIELD_NAME) {
                        throw new JsonParseException(
                                "Unexpected token "
                                        + nextToken
                                        + " at "
                                        + jacksonParser.getTokenLocation());
                    }

                    final JsonToken nextNextToken = jacksonParser.nextToken();
                    if (nextNextToken == null) {
                        throw new JsonParseException(
                                "Unexpected end of JSON at "
                                        + jacksonParser.getTokenLocation()
                                        + " while expecting a value of an object");
                    }
                    this.skipJsonValue(jacksonParser, nextNextToken);
                }
                // Never fall through from the previous branch of START_OBJECT.

            case VALUE_EMBEDDED_OBJECT:
            case FIELD_NAME:
            case END_ARRAY:
            case END_OBJECT:
            case NOT_AVAILABLE:
            default:
                throw new JsonParseException(
                        "Unexpected token " + token + " at " + jacksonParser.getTokenLocation());
        }
    }

    private double getDoubleValue(final JsonParser jacksonParser) throws IOException {
        try {
            return jacksonParser.getDoubleValue();
        } catch (final IOException ex) {
            if (this.hasFallbacksForUnparsableNumbers) {
                return this.defaultDouble;
            }
            throw ex;
        }
    }

    private long getLongValue(final JsonParser jacksonParser) throws IOException {
        try {
            return jacksonParser.getLongValue();
        } catch (final IOException ex) {
            if (this.hasFallbacksForUnparsableNumbers) {
                return this.defaultLong;
            }
            throw ex;
        }
    }

    private final boolean hasLiteralsWithNumbers;
    private final boolean hasFallbacksForUnparsableNumbers;
    private final double defaultDouble;
    private final long defaultLong;
}
