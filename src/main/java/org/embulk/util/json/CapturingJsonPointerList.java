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
 * A list of {@link JsonPointer}-based capturing pointers to capture JSON values.
 *
 * <p>The term "capturing pointer" is inspired from "capturing group" of regular expressions.
 *
 * @see <a href="https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html#cg">Groups and capturing</a>
 */
public class CapturingJsonPointerList {
    private CapturingJsonPointerList(final JsonPointerTree tree, final int size) {
        this.tree = tree;
        this.size = size;
    }

    /**
     * Creates a {@link CapturingJsonPointerList} instance with capturing pointers by {@link JsonPointer}s.
     *
     * @param pointers  capturing pointers by {@link JsonPointer}s
     * @return the new {@link CapturingJsonPointerList} created
     */
    public static CapturingJsonPointerList of(final JsonPointer... pointers) {
        return new CapturingJsonPointerList(JsonPointerTree.of(pointers), pointers.length);
    }

    /**
     * Captures JSON values by this list of capturing pointers, reading from {@link com.fasterxml.jackson.core.JsonParser}.
     *
     * <p>The read stops at the end of the same level with the starting. For example, consider capturing
     * values from the following JSON: {@code {"foo":{"bar":12,"baz":98},"qux":0}}, starting to read from
     * the second object beginning (left curly brace) before {@code "bar"}. In this case, the read stops
     * at the first object end (right curly brace) after {@code 98}. The {@code JsonParser} can continue
     * parsing from {@code "qux"}.
     *
     * <p>The returned array of JSON values has the same length with the number of JSON Pointers given to
     * {@link CapturingJsonPointerList}. The indices in the returned array correspond to the indices of
     * {@link JsonPointer}s given to {@link CapturingJsonPointerList} by {@link #of(JsonPointer...)}.
     *
     * <p>For example, consider {@link CapturingJsonPointerList} created like the following.</p>
     *
     * <pre>{@code CapturingJsonPointerList.of(
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
    JsonValue[] captureFromParser(final JsonParser parser) throws IOException {
        return this.captureFromParser(parser, false, 0.0, 0L);
    }

    /**
     * Captures JSON values by this list of capturing pointers, reading from {@link com.fasterxml.jackson.core.JsonParser}.
     *
     * <p>It is mostly the same with {@link #captureFromParser(JsonParser)}, but with some configuration
     * on parsing numbers in JSON.
     *
     * @param parser  {@link com.fasterxml.jackson.core.JsonParser} to read from
     * @param withNumbersFallbackWithLiterals  {@code true} to set a literal in {@link JsonDouble} and {@link JsonLong}
     * @param defaultDouble  the default {@code double} value when the parser cannot parse a floating-point number
     * @param defaultLong  the default {@code long} value when the parser cannot parse an integral number
     * @return an array of captured JSON values
     * @throws IOException  when failing to read
     */
    JsonValue[] captureFromParser(
            final JsonParser parser,
            final boolean withNumbersFallbackWithLiterals,
            final double defaultDouble,
            final long defaultLong) throws IOException {
        final TreeBasedCapturer capturer = new TreeBasedCapturer(
                parser,
                this.tree,
                this.size,
                withNumbersFallbackWithLiterals,
                defaultDouble,
                defaultLong);

        while (capturer.next()) {
            ;
        }

        return capturer.peekValues();
    }

    /**
     * Returns the number of capturing pointers specified.
     */
    int size() {
        return this.size;
    }

    private final JsonPointerTree tree;

    private final int size;
}
