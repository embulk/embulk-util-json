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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.filter.FilteringParserDelegate;
import com.fasterxml.jackson.core.filter.JsonPointerBasedFilter;
import com.fasterxml.jackson.core.filter.TokenFilter;
import com.fasterxml.jackson.core.json.PackageVersion;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.embulk.spi.json.JsonDouble;
import org.embulk.spi.json.JsonLong;
import org.embulk.spi.json.JsonValue;

/**
 * Parses a stringified JSON to Embulk's {@link org.embulk.spi.json.JsonValue}.
 */
public final class JsonValueParser implements Closeable {
    private JsonValueParser(
            final com.fasterxml.jackson.core.JsonParser jacksonParser,
            final int depthToFlattenJsonArrays,
            final boolean hasLiteralsWithNumbers,
            final boolean hasFallbacksForUnparsableNumbers,
            final double defaultDouble,
            final long defaultLong) {
        this.jacksonParser = Objects.requireNonNull(jacksonParser);
        this.valueReader = new InternalJsonValueReader(
                hasLiteralsWithNumbers, hasFallbacksForUnparsableNumbers, defaultDouble, defaultLong);
        this.depthToFlattenJsonArrays = depthToFlattenJsonArrays;
        this.hasLiteralsWithNumbers = hasLiteralsWithNumbers;
        this.hasFallbacksForUnparsableNumbers = hasFallbacksForUnparsableNumbers;
        this.defaultDouble = defaultDouble;
        this.defaultLong = defaultLong;
    }

    /**
     * Builds {@link JsonValueParser}.
     */
    public static final class Builder {
        Builder(final JsonFactory factory) {
            this.factory = Objects.requireNonNull(factory);
            this.root = null;
            this.depthToFlattenJsonArrays = 0;
            this.hasLiteralsWithNumbers = false;
            this.hasFallbacksForUnparsableNumbers = false;
            this.defaultDouble = 0.0;
            this.defaultLong = 0;
        }

        /**
         * Sets the JSON Pointer to the "root" element to parse.
         *
         * @param root  the JSON Pointer to the root element
         * @return this builder
         */
        public Builder root(final JsonPointer root) {
            this.root = root;
            return this;
        }

        /**
         * Sets the JSON Pointer to the "root" element to parse.
         *
         * @param root  the JSON Pointer to the root element
         * @return this builder
         *
         * @throws IllegalArgumentException  if the input does not present a valid JSON Pointer expression
         */
        public Builder root(final String root) {
            this.root = JsonPointer.compile(root);
            return this;
        }

        /**
         * Sets the depth to flatten JSON Arrays to parse.
         *
         * @param depthToFlattenJsonArrays  the depth to flatten JSON Arrays
         * @return this builder
         */
        public Builder setDepthToFlattenJsonArrays(final int depthToFlattenJsonArrays) {
            this.depthToFlattenJsonArrays = depthToFlattenJsonArrays;
            return this;
        }

        /**
         * Enables creating {@link JsonDouble} and {@link JsonLong} instances with supplemental literal strings.
         *
         * <p>{@link JsonDouble#withLiteral(double,String)} and {@link JsonLong#withLiteral(long,String)} are
         * used to create {@link JsonDouble} and {@link JsonLong} instances if enabled. The text representations
         * from {@link com.fasterxml.jackson.core.JsonParser} are passed as literals.
         *
         * <p>The supplemental literal strings would help with representing unparsable numbers, such as integers
         * larger than {@link java.lang.Long#MAX_VALUE}, but literals would consume larger object heap memory.
         *
         * @return this builder
         */
        public Builder enableSupplementalLiteralsWithNumbers() {
            this.hasLiteralsWithNumbers = true;
            return this;
        }

        /**
         * Enables falling back to default numbers for unparsable numbers.
         *
         * <p>The parser would throw an exception for unparsable numbers if falling back is not enabled.
         *
         * @param defaultDouble  the default for floating-point numbers
         * @param defaultLong  the default for integral numbers
         * @return this builder
         */
        public Builder fallbackForUnparsableNumbers(final double defaultDouble, final long defaultLong) {
            this.hasFallbacksForUnparsableNumbers = true;
            this.defaultDouble = defaultDouble;
            this.defaultLong = defaultLong;
            return this;
        }

        /**
         * Builds {@link JsonValueParser} for the stringified JSON.
         *
         * @param json  the stringified JSON
         * @return the {@link JsonValueParser} instance created
         */
        public JsonValueParser build(final String json) throws IOException {
            return new JsonValueParser(
                    buildJacksonParser(json),
                    this.depthToFlattenJsonArrays,
                    this.hasLiteralsWithNumbers,
                    this.hasFallbacksForUnparsableNumbers,
                    this.defaultDouble,
                    this.defaultLong);
        }

        /**
         * Builds {@link JsonValueParser} for the stringified JSON.
         *
         * @param jsonStream  {@link java.io.InputStream} of the stringified JSON
         * @return the {@link JsonValueParser} instance created
         */
        public JsonValueParser build(final InputStream jsonStream) throws IOException {
            return new JsonValueParser(
                    buildJacksonParser(jsonStream),
                    this.depthToFlattenJsonArrays,
                    this.hasLiteralsWithNumbers,
                    this.hasFallbacksForUnparsableNumbers,
                    this.defaultDouble,
                    this.defaultLong);
        }

        private com.fasterxml.jackson.core.JsonParser buildJacksonParser(final String json) throws IOException {
            return this.extendJacksonParser(this.factory.createParser(Objects.requireNonNull(json)));
        }

        private com.fasterxml.jackson.core.JsonParser buildJacksonParser(final InputStream jsonStream) throws IOException {
            return this.extendJacksonParser(this.factory.createParser(Objects.requireNonNull(jsonStream)));
        }

        private com.fasterxml.jackson.core.JsonParser extendJacksonParser(final com.fasterxml.jackson.core.JsonParser baseParser) {
            com.fasterxml.jackson.core.JsonParser parser = baseParser;
            if (this.root != null) {
                parser = new FilteringParserDelegate(
                        parser,
                        new JsonPointerBasedFilter(this.root),
                        TokenFilter.Inclusion.ONLY_INCLUDE_ALL,
                        true  // Allow multiple matches
                        );
            }
            if (this.depthToFlattenJsonArrays > 0) {
                parser = new FilteringParserDelegate(
                        parser,
                        new FlattenJsonArrayFilter(this.depthToFlattenJsonArrays),
                        TokenFilter.Inclusion.ONLY_INCLUDE_ALL,
                        true  // Allow multiple matches
                        );
            }
            return parser;
        }

        private final JsonFactory factory;

        private JsonPointer root;
        private int depthToFlattenJsonArrays;
        private boolean hasLiteralsWithNumbers;
        private boolean hasFallbacksForUnparsableNumbers;
        private double defaultDouble;
        private long defaultLong;
    }

    /**
     * Returns a new builder.
     *
     * <p>It applies some default configurations for its internal {@link JsonFactory}. The defaults include:
     *
     * <ul>
     * <li>Allowing JSON Strings to contain unquoted control characters
     * <li>Allowing to recognize set of "Not-a-Number" (NaN) tokens as legal floating number values
     * </ul>
     *
     * <p>Note that the defaults may change in future versions.
     *
     * @return the new builder
     */
    public static Builder builder() {
        final JsonFactory factory = new JsonFactory();
        factory.enable(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS);
        factory.enable(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS);
        return builder(factory);
    }

    /**
     * Returns a new builder with the specified {@link JsonFactory}.
     *
     * <p>It applies no additional configuration for the specified {@link JsonFactory}.
     *
     * @return the new builder
     */
    public static Builder builder(final JsonFactory jsonFactory) {
        assertJacksonVersion();
        return new Builder(jsonFactory);
    }

    /**
     * Reads a {@link org.embulk.spi.json.JsonValue} from the parser.
     *
     * @return the JSON value, or {@code null} if the parser reaches at the end of input in the beginning
     * @throws IOException  if failing to read JSON
     * @throws JsonParseException  if failing to parse JSON
     */
    public JsonValue readJsonValue() throws IOException {
        return this.valueReader.read(this.jacksonParser);
    }

    /**
     * Captures {@link org.embulk.spi.json.JsonValue}s from the parser with the specified capturing pointers.
     *
     * @return an array of the captured JSON values, or {@code null} if the parser reaches at the end of input in the beginning
     * @throws IOException  if failing to read JSON
     * @throws JsonParseException  if failing to parse JSON
     */
    public JsonValue[] captureJsonValues(final CapturingPointers capturingPointers) throws IOException {
        return capturingPointers.captureFromParser(this.jacksonParser, this.valueReader);
    }

    /**
     * Closes the parser.
     *
     * @throws IOException  if failing to close
     */
    @Override
    public final void close() throws IOException {
        this.jacksonParser.close();
    }

    private static void assertJacksonVersion() {
        if (PackageVersion.VERSION.getMajorVersion() != 2) {
            throw new UnsupportedOperationException("embulk-util-json is not used with Jackson 2.");
        }

        final int minor = PackageVersion.VERSION.getMinorVersion();
        if (minor < 14 || (minor == 15 && PackageVersion.VERSION.getPatchLevel() <= 2)) {
            throw new UnsupportedOperationException("embulk-util-json is not used with Jackson 2.15.3 or later.");
        }
    }

    private final com.fasterxml.jackson.core.JsonParser jacksonParser;
    private final InternalJsonValueReader valueReader;

    private final int depthToFlattenJsonArrays;
    private final boolean hasLiteralsWithNumbers;
    private final boolean hasFallbacksForUnparsableNumbers;
    private final double defaultDouble;
    private final long defaultLong;
}
