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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.filter.FilteringParserDelegate;
import com.fasterxml.jackson.core.filter.JsonPointerBasedFilter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;

/**
 * Parses a stringified JSON to MessagePack {@link org.msgpack.value.Value}.
 */
public class JsonParser {
    /**
     * Creates a {@link JsonParser} instance.
     */
    public JsonParser() {
        this.factory = new JsonFactory();
        factory.enable(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS);
        factory.enable(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS);
    }

    /**
     * A parsed stream of MessagePack {@link org.msgpack.value.Value}s.
     */
    public interface Stream extends Closeable {
        /**
         * Gets the next MessagePack {@link org.msgpack.value.Value}.
         *
         * @return parsed {@link org.msgpack.value.Value}
         */
        Value next() throws IOException;

        /**
         * Closes the stream.
         */
        void close() throws IOException;
    }

    /**
     * Parses the stringified JSON {@link java.io.InputStream} to {@link Stream}.
     *
     * @param in  stringified JSON {@link java.io.InputStream} to parse
     * @return a stream of parsed {@link org.msgpack.value.Value}
     */
    public Stream open(final InputStream in) throws IOException {
        return openWithOffsetInJsonPointer(in, null);
    }

    /**
     * Parses the stringified JSON {@link java.io.InputStream} with the specified offset to {@link Stream}.
     *
     * @param in  stringified JSON {@link java.io.InputStream} to parse
     * @param offsetInJsonPointer  offset in JSON Pointer to parse
     * @return a stream of parsed {@link org.msgpack.value.Value}
     */
    public Stream openWithOffsetInJsonPointer(final InputStream in, final String offsetInJsonPointer) throws IOException {
        return new StreamParseContext(factory, in, offsetInJsonPointer);
    }

    /**
     * Parses the stringified JSON {@link java.lang.String} to {@link org.msgpack.value.Value}.
     *
     * @param json  stringified JSON to parse
     * @return parsed {@link org.msgpack.value.Value}
     */
    public Value parse(final String json) {
        return parseWithOffsetInJsonPointer(json, null);
    }

    /**
     * Parses the stringified JSON {@link java.lang.String} with the specified offset to {@link org.msgpack.value.Value}.
     *
     * @param json  stringified JSON to parse
     * @param offsetInJsonPointer  offset in JSON Pointer to parse
     * @return parsed {@link org.msgpack.value.Value}
     */
    public Value parseWithOffsetInJsonPointer(final String json, final String offsetInJsonPointer) {
        return new SingleParseContext(factory, json, offsetInJsonPointer).parse();
    }

    private static String sampleJsonString(String json) {
        if (json.length() < 100) {
            return json;
        } else {
            return json.substring(0, 97) + "...";
        }
    }

    private static com.fasterxml.jackson.core.JsonParser wrapWithPointerFilter(
            com.fasterxml.jackson.core.JsonParser baseParser, String offsetInJsonPointer) {
        return new FilteringParserDelegate(baseParser, new JsonPointerBasedFilter(offsetInJsonPointer), false, false);
    }

    private static class StreamParseContext extends AbstractParseContext implements Stream {
        public StreamParseContext(JsonFactory factory, InputStream in, String offsetInJsonPointer) throws IOException {
            super(createParser(factory, in, Optional.ofNullable(offsetInJsonPointer)));
        }

        private static com.fasterxml.jackson.core.JsonParser createParser(
                JsonFactory factory, InputStream in, Optional<String> offsetInJsonPointer) throws IOException {
            try {
                final com.fasterxml.jackson.core.JsonParser baseParser = factory.createParser(in);
                return offsetInJsonPointer.map(p -> wrapWithPointerFilter(baseParser, p)).orElse(baseParser);
            } catch (IOException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new JsonParseException("Failed to parse JSON", ex);
            }
        }

        @Override
        public void close() throws IOException {
            parser.close();
        }

        @Override
        protected String sampleJsonString() {
            return "in";
        }
    }

    private static class SingleParseContext extends AbstractParseContext {
        private final String json;

        public SingleParseContext(JsonFactory factory, String json, String offsetInJsonPointer) {
            super(createParser(factory, json, Optional.ofNullable(offsetInJsonPointer)));
            this.json = json;
        }

        private static com.fasterxml.jackson.core.JsonParser createParser(
                JsonFactory factory, String json, Optional<String> offsetInJsonPointer) {
            try {
                final com.fasterxml.jackson.core.JsonParser baseParser = factory.createParser(json);
                return offsetInJsonPointer.map(p -> wrapWithPointerFilter(baseParser, p)).orElse(baseParser);
            } catch (Exception ex) {
                throw new JsonParseException("Failed to parse JSON: " + JsonParser.sampleJsonString(json), ex);
            }
        }

        public Value parse() {
            try {
                Value v = next();
                if (v == null) {
                    throw new JsonParseException("Unable to parse empty string");
                }
                return v;
            } catch (IOException ex) {
                throw new JsonParseException("Failed to parse JSON: " + sampleJsonString(), ex);
            }
        }

        @Override
        protected String sampleJsonString() {
            return JsonParser.sampleJsonString(json);
        }
    }

    private abstract static class AbstractParseContext {
        protected final com.fasterxml.jackson.core.JsonParser parser;

        public AbstractParseContext(com.fasterxml.jackson.core.JsonParser parser) {
            this.parser = parser;
        }

        protected abstract String sampleJsonString();

        public Value next() throws IOException {
            try {
                JsonToken token = parser.nextToken();
                if (token == null) {
                    return null;
                }
                return jsonTokenToValue(token);
            } catch (com.fasterxml.jackson.core.JsonParseException ex) {
                throw new JsonParseException("Failed to parse JSON: " + sampleJsonString(), ex);
            } catch (IOException ex) {
                throw ex;
            } catch (JsonParseException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new JsonParseException("Failed to parse JSON: " + sampleJsonString(), ex);
            }
        }

        @SuppressWarnings("checkstyle:FallThrough")
        private Value jsonTokenToValue(JsonToken token) throws IOException {
            switch (token) {
                case VALUE_NULL:
                    return ValueFactory.newNil();
                case VALUE_TRUE:
                    return ValueFactory.newBoolean(true);
                case VALUE_FALSE:
                    return ValueFactory.newBoolean(false);
                case VALUE_NUMBER_FLOAT:
                    return ValueFactory.newFloat(parser.getDoubleValue());
                case VALUE_NUMBER_INT:
                    try {
                        return ValueFactory.newInteger(parser.getLongValue());
                    } catch (com.fasterxml.jackson.core.JsonParseException ex) {
                        return ValueFactory.newInteger(parser.getBigIntegerValue());
                    }
                case VALUE_STRING:
                    return ValueFactory.newString(parser.getText());
                case START_ARRAY: {
                    List<Value> list = new ArrayList<>();
                    while (true) {
                        token = parser.nextToken();
                        if (token == JsonToken.END_ARRAY) {
                            return ValueFactory.newArray(list);
                        } else if (token == null) {
                            throw new JsonParseException(
                                    "Unexpected end of JSON at "
                                            + parser.getTokenLocation()
                                            + " while expecting an element of an array: "
                                            + sampleJsonString());
                        }
                        list.add(jsonTokenToValue(token));
                    }
                }
                // Never fall through from the previous branch of START_ARRAY.
                case START_OBJECT:
                    Map<Value, Value> map = new HashMap<>();
                    while (true) {
                        token = parser.nextToken();
                        if (token == JsonToken.END_OBJECT) {
                            return ValueFactory.newMap(map);
                        } else if (token == null) {
                            throw new JsonParseException(
                                    "Unexpected end of JSON at "
                                            + parser.getTokenLocation()
                                            + " while expecting a key of object: "
                                            + sampleJsonString());
                        }
                        String key = parser.getCurrentName();
                        if (key == null) {
                            throw new JsonParseException(
                                    "Unexpected token "
                                            + token
                                            + " at "
                                            + parser.getTokenLocation()
                                            + ": "
                                            + sampleJsonString());
                        }
                        token = parser.nextToken();
                        if (token == null) {
                            throw new JsonParseException(
                                    "Unexpected end of JSON at "
                                            + parser.getTokenLocation()
                                            + " while expecting a value of object: "
                                            + sampleJsonString());
                        }
                        Value value = jsonTokenToValue(token);
                        map.put(ValueFactory.newString(key), value);
                    }
                // Never fall through from the previous branch of START_OBJECT.
                case VALUE_EMBEDDED_OBJECT:
                case FIELD_NAME:
                case END_ARRAY:
                case END_OBJECT:
                case NOT_AVAILABLE:
                default:
                    throw new JsonParseException("Unexpected token " + token + " at " + parser.getTokenLocation() + ": " + sampleJsonString());
            }
        }
    }

    private final JsonFactory factory;
}
