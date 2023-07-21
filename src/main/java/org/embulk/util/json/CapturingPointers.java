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
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.embulk.spi.json.JsonValue;

/**
 * Represents "capturing pointers" to capture JSON values from {@link JsonParser}.
 *
 * <p>It consists of a list of pointers, such as JSON Pointers, that represent positions in a JSON
 * structure value, an array or an object.
 *
 * <p>Its {@link #captureFromParser(JsonParser)} captures JSON values by the "capturing pointers",
 * reading from {@link com.fasterxml.jackson.core.JsonParser}. The captured JSON values are returned
 * as an array of {@link JsonValue}s.
 *
 * <p>The returned array of JSON values has the same length with the number of pointers represented
 * by this {@link CapturingPointers}. The indices in the returned array correspond to the indices of
 * the pointers represented by this {@link CapturingPointers}.
 *
 * <p>For example, consider {@link CapturingPointers} created like the following.</p>
 *
 * <pre>{@code  final CapturingPointers pointers = jsonValueParser.capturingPointerBuilder()
 *       .addJsonPointer("/foo")
 *       .addJsonPointer("/bar")
 *       .addJsonPointer("/baz").build();}</pre>
 *
 * <p>The returned array of JSON values would consist of three elements. The first element would
 * correspond {@code "/foo"}, the second to {@code "/bar"}, and the third to {@code "/baz"}.
 *
 * <p>The term "capturing pointer" is inspired from "capturing group" of regular expressions.
 *
 * @see <a href="https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html#cg">Groups and capturing</a>
 */
public abstract class CapturingPointers {
    CapturingPointers() {
    }

    /**
     * Builds {@link CapturingPointers}.
     *
     * <p>Use {@link JsonValueParser#capturingPointersBuilder} to create a builder instance.
     */
    public static class Builder {
        Builder(final boolean hasLiteralsWithNumbers,
                final boolean hasFallbacksForUnparsableNumbers,
                final double defaultDouble,
                final long defaultLong) {
            this.hasLiteralsWithNumbers = hasLiteralsWithNumbers;
            this.hasFallbacksForUnparsableNumbers = hasFallbacksForUnparsableNumbers;
            this.defaultDouble = defaultDouble;
            this.defaultLong = defaultLong;

            this.directMemberNames = new ArrayList<>();
            this.jsonPointers = new ArrayList<>();
            this.jsonPointerExceptions = new ArrayList<>();

            this.hasAtLeastOneJsonPointer = false;
        }

        /**
         * Adds a direct member name as a pointer to capture.
         *
         * <p>It works only for a JSON object. It captures a JSON value that is directly under the root JSON object
         * with the member name.
         *
         * <p>For example for the following JSON object {@code {"foo": 12, "bar": 34, "baz": 56}}, {@code "foo"} as
         * a direct member name would capture {@code 12}, and {@code "bar"} would capture {@code 34}.
         *
         * @param directMemberName  the direct member name to capture
         * @return this builder
         */
        public Builder addDirectMemberName(final String directMemberName) {
            this.directMemberNames.add(directMemberName);

            final JsonPointer compiledPointer;
            try {
                compiledPointer = compileMemberNameToJsonPointer(directMemberName);
            } catch (final RuntimeException ex) {
                this.jsonPointerExceptions.add(ex);
                return this;
            }
            this.jsonPointers.add(compiledPointer);

            return this;
        }

        /**
         * Adds a JSON Pointer to capture.
         *
         * <p>It captures a JSON value that is pointed by the JSON Pointer.
         *
         * <p>For example for the following JSON object {@code {"foo": {"bar": 12}, "baz": 56}}, {@code "/foo/bar"}
         * as a JSON Pointer would capture {@code 12}, and {@code "/foo"} would capture {@code {"bar": 12}}.
         *
         * @param jsonPointer  the JSON Pointer to capture
         * @return this builder
         * @throws IllegalArgumentException  if the input does not present a valid JSON Pointer expression
         */
        public Builder addJsonPointer(final String jsonPointer) {
            this.hasAtLeastOneJsonPointer = true;

            this.directMemberNames.add(null);

            this.jsonPointers.add(JsonPointer.compile(jsonPointer));

            return this;
        }

        /**
         * Adds a JSON Pointer to capture.
         *
         * <p>It captures a JSON value that is pointed by the JSON Pointer.
         *
         * <p>For example for the following JSON object {@code {"foo": {"bar": 12}, "baz": 56}}, {@code "/foo/bar"}
         * as a JSON Pointer would capture {@code 12}, and {@code "/foo"} would capture {@code {"bar": 12}}.
         *
         * @param jsonPointer  the JSON Pointer to capture
         * @return this builder
         */
        public Builder addJsonPointer(final JsonPointer jsonPointer) {
            this.hasAtLeastOneJsonPointer = true;

            this.directMemberNames.add(null);

            this.jsonPointers.add(jsonPointer);

            return this;
        }

        /**
         * Builds "capturing pointers".
         *
         * @return the new capturing pointers
         */
        public CapturingPointers build() {
            assert this.directMemberNames.size() == this.jsonPointers.size();
            if (this.directMemberNames.isEmpty()) {
                return new CapturingPointerToRoot(
                        this.hasLiteralsWithNumbers,
                        this.hasFallbacksForUnparsableNumbers,
                        this.defaultDouble,
                        this.defaultLong);
            }

            if (this.hasAtLeastOneJsonPointer) {
                if (!this.jsonPointerExceptions.isEmpty()) {
                    final IllegalArgumentException ex =
                            new IllegalArgumentException("Invalid JSON Pointer(s) specified.", this.jsonPointerExceptions.get(0));
                    for (final RuntimeException suppressed : this.jsonPointerExceptions) {
                        ex.addSuppressed(suppressed);
                    }
                    throw ex;
                }

                return CapturingJsonPointerList.of(
                        this.jsonPointers,
                        this.hasLiteralsWithNumbers,
                        this.hasFallbacksForUnparsableNumbers,
                        this.defaultDouble,
                        this.defaultLong);
            } else {
                return CapturingDirectMemberNameList.of(
                        this.directMemberNames,
                        this.hasLiteralsWithNumbers,
                        this.hasFallbacksForUnparsableNumbers,
                        this.defaultDouble,
                        this.defaultLong);
            }
        }

        private final boolean hasLiteralsWithNumbers;
        private final boolean hasFallbacksForUnparsableNumbers;
        private final double defaultDouble;
        private final long defaultLong;

        private final ArrayList<String> directMemberNames;
        private final ArrayList<JsonPointer> jsonPointers;

        private final ArrayList<RuntimeException> jsonPointerExceptions;

        private boolean hasAtLeastOneJsonPointer;
    }

    /**
     * Captures JSON values with the capturing pointers from the parser.
     *
     * @param parser  the parser to capture values from
     * @return the array of captured JSON values
     */
    public abstract JsonValue[] captureFromParser(final JsonParser parser) throws IOException;

    static JsonPointer compileMemberNameToJsonPointer(final String memberName) {
        if ((!memberName.contains("~")) && (!memberName.contains("/"))) {
            return JsonPointer.compile("/" + memberName);
        }

        final String untilde = TILDE.matcher(memberName).replaceAll("~0");
        return JsonPointer.compile("/" + SLASH.matcher(untilde).replaceAll("~1"));
    }

    private static Pattern TILDE = Pattern.compile("~");

    private static Pattern SLASH = Pattern.compile("/");
}
