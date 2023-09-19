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
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.embulk.spi.json.JsonArray;
import org.embulk.spi.json.JsonBoolean;
import org.embulk.spi.json.JsonDouble;
import org.embulk.spi.json.JsonLong;
import org.embulk.spi.json.JsonNull;
import org.embulk.spi.json.JsonObject;
import org.embulk.spi.json.JsonString;
import org.embulk.spi.json.JsonValue;

/**
 * A context-ful JSON value "capturer" based on {@link JsonPointerTree}.
 */
class TreeBasedCapturer {
    TreeBasedCapturer(
            final JsonParser parser,
            final JsonPointerTree tree,
            final int size,
            final InternalJsonValueReader valueReader) {
        this.parser = parser;
        this.tree = tree;
        this.hasRootToCapture = this.tree.captures().isEmpty();

        this.size = size;

        this.valueReader = valueReader;

        this.pointerStack = new ArrayDeque<>();
        this.pointerStack.push(this.tree);
        this.parsingStack = new ArrayDeque<>();
        this.builderStack = new ArrayDeque<>();

        this.hasFinished = false;
        this.firstToken = null;

        this.values = new JsonValue[size];
        for (int i = 0; i < this.values.length; i++) {
            this.values[i] = null;
        }
    }

    @SuppressWarnings("checkstyle:FallThrough")
    boolean next() throws IOException {
        if (this.hasFinished) {
            return false;
        }

        final JsonToken token;
        try {
            token = this.parser.nextToken();
        } catch (final com.fasterxml.jackson.core.JsonParseException ex) {
            throw new JsonParseException("Failed to parse JSON", ex);
        } catch (final IOException ex) {
            throw ex;
        } catch (final JsonParseException ex) {
            throw ex;
        } catch (final RuntimeException ex) {
            throw new JsonParseException("Failed to parse JSON", ex);
        }

        if (token == null) {
            return false;
        }

        final JsonPointerTree parentPointer = this.pointerStack.peekFirst();

        if (this.hasRootToCapture && this.parsingStack.isEmpty() && token.isStructStart()) {
            if (token == JsonToken.START_ARRAY) {
                this.builderStack.push(new ArrayBuilder());
            } else if (token == JsonToken.START_OBJECT) {
                this.builderStack.push(new ObjectBuilder());
            }
        }

        // Deepen the pointer stack when the token is a scalar value, START_ARRAY, or START_OBJECT.
        if (token.isScalarValue() || token.isStructStart()) {
            if (this.parsingStack.isEmpty()) {
                this.firstToken = token;
            } else {
                final ParsingContext context = this.parsingStack.getFirst();
                if (context.isObject()) {
                    final String propertyName = context.getPropertyName();
                    if (propertyName == null) {
                        throw new JsonParseException("Value in JSON Object before any field comes.");
                    }

                    final JsonPointerTree toBePointer = parentPointer.get(propertyName);
                    if (toBePointer != null) {
                        this.pointerStack.push(parentPointer.get(propertyName));
                    } else {  // This else case includes when parentPointer is INVALID.
                        this.pointerStack.push(JsonPointerTree.INVALID);
                    }
                } else {  // Array
                    context.incrementIndex();
                    final String indexInString = context.getIndexInString();

                    // An array index in JSON Pointer does not have ambiguity. An integer index can be
                    // reverse-resolved uniquely into a string representation.
                    //
                    // The ABNF syntax for array indices is:
                    //
                    // array-index = %x30 / ( %x31-39 *(%x30-39) )
                    //               ; "0", or digits without a leading "0"
                    //
                    // Implementations will evaluate each reference token against the
                    // document's contents and will raise an error condition if it fails to
                    // resolve a concrete value for any of the JSON pointer's reference
                    // tokens.  For example, if an array is referenced with a non-numeric
                    // token, an error condition will be raised.  See Section 7 for details.
                    //
                    // https://datatracker.ietf.org/doc/html/rfc6901

                    final JsonPointerTree toBePointer = parentPointer.get(indexInString);
                    if (toBePointer != null) {
                        this.pointerStack.push(parentPointer.get(indexInString));
                    } else {  // This else case includes when parentPointer is INVALID.
                        this.pointerStack.push(JsonPointerTree.INVALID);
                    }
                }
            }
        }

        if (token == JsonToken.START_ARRAY) {
            this.parsingStack.push(new ParsingContext(false));

            final List<Integer> captures = this.pointerStack.getFirst().captures();
            // When |captures| is not empty for the JSON array, the array must be built as a JsonArray instance eventually.
            //
            // Whenever |builderStack| is not empty, there must be something to build for the parent builder.
            if ((!captures.isEmpty()) || (!this.builderStack.isEmpty())) {
                this.builderStack.push(new ArrayBuilder());
            }
        } else if (token == JsonToken.END_ARRAY) {
            if (this.parsingStack.isEmpty() || this.parsingStack.pop().isObject()) {
                throw new JsonParseException("END_ARRAY does not match.");
            }
        } else if (token == JsonToken.START_OBJECT) {
            this.parsingStack.push(new ParsingContext(true));

            final List<Integer> captures = this.pointerStack.getFirst().captures();
            // When |captures| is not empty for the JSON object, the object must be built as a JsonObject instance eventually.
            //
            // Whenever |builderStack| is not empty, there must be something to build for the parent builder.
            if ((!captures.isEmpty()) || (!this.builderStack.isEmpty())) {
                this.builderStack.push(new ObjectBuilder());
            }
        } else if (token == JsonToken.END_OBJECT) {
            if (this.parsingStack.isEmpty() || !this.parsingStack.pop().isObject()) {
                throw new JsonParseException("END_OBJECT does not match.");
            }
        } else if (token == JsonToken.FIELD_NAME) {
            if (this.parsingStack.isEmpty()) {
                throw new JsonParseException("FIELD_NAME out of JSON Object.");
            } else {
                final ParsingContext context = this.parsingStack.getFirst();
                if (!context.isObject()) {
                    throw new JsonParseException("FIELD_NAME in JSON Array.");
                }
                context.setPropertyName(this.parser.getCurrentName());
            }
        } else if (!token.isScalarValue()) {
            throw new JsonParseException("Unexpected token in JSON: " + token.toString());
        }

        if (token.isScalarValue() || token.isStructEnd()) {
            final JsonValue value;
            if (token.isScalarValue()) {
                value = this.getScalarValue(token);
            } else {  // Structure end
                if (this.builderStack.isEmpty()) {
                    value = null;
                } else {
                    final StructureBuilder thisBuilder = this.builderStack.pop();
                    if (token == JsonToken.END_ARRAY && thisBuilder.isObject()) {
                        throw new JsonParseException("END_ARRAY does not match.");
                    }
                    if (token == JsonToken.END_OBJECT && thisBuilder.isArray()) {
                        throw new JsonParseException("END_OBJECT does not match.");
                    }
                    value = thisBuilder.build();
                }
            }

            if (value != null) {
                final JsonPointerTree thisPointer = this.pointerStack.peekFirst();
                assert thisPointer != null;  // this.pointerStack must not be empty here, but asserting.
                for (final int capture : thisPointer.captures()) {
                    this.values[capture] = value;
                }

                if (!this.builderStack.isEmpty()) {
                    final StructureBuilder parentBuilder = this.builderStack.getFirst();
                    if (parentBuilder.isArray()) {
                        parentBuilder.add(value);
                    } else {  // Object
                        final ParsingContext context = this.parsingStack.peekFirst();
                        // If context == null, it's the end of JSON to be parsed while a JSON Pointer "/" is specified.
                        if (context != null) {
                            if (!context.isObject()) {
                                throw new JsonParseException("END_OBJECT does not match.");
                            } else {
                                parentBuilder.put(context.getPropertyName(), value);
                            }
                        }
                    }
                }
            }
        }

        if (token.isScalarValue() || token.isStructEnd()) {  // A scalar value, END_ARRAY, or END_OBJECT
            if (this.pointerStack.isEmpty()) {
                throw new JsonParseException("Too many structure ends.");
            }

            if (this.parsingStack.isEmpty()) {  // When the parsing stack is empty, it should be on the top-level.
                assert this.pointerStack.size() == 1;
            } else {
                this.pointerStack.pop();
            }
        }

        if (this.parsingStack.isEmpty()) {
            this.hasFinished = true;
        }

        return true;
    }

    JsonValue[] peekValues() {
        return this.values;
    }

    JsonToken firstToken() {
        return this.firstToken;
    }

    private double getDoubleValue() throws IOException {
        try {
            return this.parser.getDoubleValue();
        } catch (final IOException ex) {
            if (this.valueReader.hasFallbacksForUnparsableNumbers()) {
                return this.valueReader.defaultDouble();
            }
            throw ex;
        }
    }

    private long getLongValue() throws IOException {
        try {
            return this.parser.getLongValue();
        } catch (final IOException ex) {
            if (this.valueReader.hasFallbacksForUnparsableNumbers()) {
                return this.valueReader.defaultLong();
            }
            throw ex;
        }
    }

    private JsonValue getScalarValue(final JsonToken token) throws IOException {
        switch (token) {
            case VALUE_NULL:
                return JsonNull.NULL;
            case VALUE_TRUE:
                return JsonBoolean.TRUE;
            case VALUE_FALSE:
                return JsonBoolean.FALSE;
            case VALUE_NUMBER_FLOAT:
                if (this.valueReader.hasLiteralsWithNumbers()) {
                    return JsonDouble.withLiteral(this.getDoubleValue(), this.parser.getValueAsString());
                } else {
                    return JsonDouble.of(this.getDoubleValue());  // throws JsonParseException
                }
            case VALUE_NUMBER_INT:
                if (this.valueReader.hasLiteralsWithNumbers()) {
                    return JsonLong.withLiteral(this.getLongValue(), this.parser.getValueAsString());
                } else {
                    return JsonLong.of(this.getLongValue());  // throws JsonParseException
                }
            case VALUE_STRING:
                return JsonString.of(this.parser.getText());
            default:
                throw new JsonParseException("Unexpected token in JSON: " + token.toString());
        }
    }

    private static class ParsingContext {
        ParsingContext(final boolean isObject) {
            this.isObject = isObject;
            this.propertyName = null;
            this.index = -1;
        }

        boolean isArray() {
            return !this.isObject;
        }

        boolean isObject() {
            return this.isObject;
        }

        void incrementIndex() {
            this.index++;
        }

        String getIndexInString() {
            return Integer.toString(this.index);
        }

        void setPropertyName(final String propertyName) {
            this.propertyName = propertyName;
        }

        String getPropertyName() {
            return this.propertyName;
        }

        @Override
        public String toString() {
            if (this.isObject) {
                if (this.propertyName == null) {
                    return "null";
                } else {
                    return "\"" + this.propertyName + "\"";
                }
            } else {
                return Integer.toString(this.index);
            }
        }

        private final boolean isObject;
        private String propertyName;
        private int index;
    }

    private abstract static class StructureBuilder {
        boolean isArray() {
            return false;
        }

        boolean isObject() {
            return false;
        }

        abstract StructureBuilder add(JsonValue value);

        abstract StructureBuilder put(String key, JsonValue value);

        abstract JsonValue build();
    }

    private static class ArrayBuilder extends StructureBuilder {
        ArrayBuilder() {
            this.array = new ArrayList<>();
        }

        @Override
        boolean isArray() {
            return true;
        }

        @Override
        ArrayBuilder add(final JsonValue value) {
            this.array.add(value);
            return this;
        }

        @Override
        ArrayBuilder put(final String key, final JsonValue value) {
            throw new UnsupportedOperationException();
        }

        @Override
        JsonArray build() {
            return JsonArray.ofList(this.array);
        }

        @Override
        public String toString() {
            return this.array.toString();
        }

        private final ArrayList<JsonValue> array;
    }

    private static class ObjectBuilder extends StructureBuilder {
        ObjectBuilder() {
            this.entries = new ArrayList<>();
        }

        @Override
        boolean isObject() {
            return true;
        }

        @Override
        ArrayBuilder add(final JsonValue value) {
            throw new UnsupportedOperationException();
        }

        @Override
        ObjectBuilder put(final String key, final JsonValue value) {
            this.entries.add(new AbstractMap.SimpleEntry<>(key, value));
            return this;
        }

        @Override
        JsonObject build() {
            return JsonObject.ofEntries(toArray(this.entries));
        }

        @Override
        public String toString() {
            return this.entries.toString();
        }

        @SuppressWarnings("unchecked")
        private static Map.Entry<String, JsonValue>[] toArray(final ArrayList<Map.Entry<String, JsonValue>> entries) {
            return entries.toArray(new Map.Entry[entries.size()]);
        }

        private final ArrayList<Map.Entry<String, JsonValue>> entries;
    }

    private final JsonParser parser;
    private final JsonPointerTree tree;
    private final boolean hasRootToCapture;
    private final int size;
    private final InternalJsonValueReader valueReader;

    private final ArrayDeque<JsonPointerTree> pointerStack;
    private final ArrayDeque<ParsingContext> parsingStack;
    private final ArrayDeque<StructureBuilder> builderStack;

    private final JsonValue[] values;

    private boolean hasFinished;
    private JsonToken firstToken;
}
