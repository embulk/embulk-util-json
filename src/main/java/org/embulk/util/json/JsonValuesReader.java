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
import com.fasterxml.jackson.core.JsonPointer;
import java.io.IOException;
import org.embulk.spi.json.JsonValue;

/**
 * A reader to read a set of values captured by {@link JsonPointerTree} from {@link com.fasterxml.jackson.core.JsonParser}.
 */
public class JsonValuesReader {
    private JsonValuesReader(final JsonPointerTree tree, final int size) {
        this.tree = tree;
        this.size = size;
    }

    /**
     * Creates a {@link JsonValuesReader} instance with an array of {@link JsonPointer}s as captures.
     *
     * @param pointers  {@link JsonPointer}s as captures
     * @return the new {@link JsonValuesReader} created
     */
    public static JsonValuesReader of(final JsonPointer... pointers) {
        return new JsonValuesReader(JsonPointerTree.of(pointers), pointers.length);
    }

    /**
     * Reads a set of values captured by {@link JsonPointer}s from {@link com.fasterxml.jackson.core.JsonParser}.
     *
     * <p>The read stops at the end of same level with the starting. For example, consider a case starting
     * to read from the second object beginning (left curly brace) before {@code "bar"} in an example JSON
     * {@code {"foo":{"bar":12,"baz":98},"qux":0}}. In this case, the read stops at the first object end
     * (right curly brace) after {@code 98}. The {@code JsonParser} can continue parsing from {@code "qux"}.
     *
     * <p>The returned array of JSON values has the same length with the number of JSON Pointers given to
     * the reader. The indices in the returned array correspond to the indices of {@link JsonPointer}s
     * given in creating the {@link JsonValuesReader} instace by {@link #of(JsonPointer...)}.
     *
     * <p>For example, consider the given {@link JsonValuesReader} created like the following.</p>
     *
     * <pre>{@code JsonValuesReader.of(
     *      JsonPointer.of("/foo"),
     *      JsonPointer.of("/bar"),
     *      JsonPointer.of("/baz"))}</pre>
     *
     * <p>The returned array of JSON values consists of three elements. The first element corresponds to
     * {@code "/foo"}, the second to {@code "/bar"}, and the third to {@code "/baz"}.
     *
     * @param parser  {@link com.fasterxml.jackson.core.JsonParser} to read from
     * @return an array of captured JSON values
     * @throws IOException  when failing to read
     */
    public JsonValue[] readValuesCaptured(final JsonParser parser) throws IOException {
        return this.readValuesCaptured(parser, false, 0.0, 0L);
    }

    /**
     * Reads a set of values captured by {@link JsonPointer}s from {@link com.fasterxml.jackson.core.JsonParser}.
     *
     * <p>It is mostly the same with {@link #readValuesCaptured(JsonParser)}, but with some configuration on
     * parsing numbers in JSON.
     *
     * @param parser  {@link com.fasterxml.jackson.core.JsonParser} to read from
     * @param withNumbersFallbackWithLiterals  {@code true} to set a literal in {@link JsonDouble} and {@link JsonLong}
     * @param defaultDouble  the default {@code double} value when the parser cannot parse a floating-point number
     * @param defaultLong  the default {@code long} value when the parser cannot parse an integral number
     * @return an array of captured JSON values
     * @throws IOException  when failing to read
     */
    public JsonValue[] readValuesCaptured(
            final JsonParser parser,
            final boolean withNumbersFallbackWithLiterals,
            final double defaultDouble,
            final long defaultLong) throws IOException {
        final InternalJsonValuesReader innerReader = new InternalJsonValuesReader(
                parser,
                this.tree,
                this.size,
                withNumbersFallbackWithLiterals,
                defaultDouble,
                defaultLong);

        while (innerReader.next()) {
            ;
        }

        return innerReader.peekValues();
    }

    /**
     * Returns the number of {@link JsonPointer}s given when creating the reader.
     */
    public int size() {
        return this.size;
    }

    private final JsonPointerTree tree;

    private final int size;
}
