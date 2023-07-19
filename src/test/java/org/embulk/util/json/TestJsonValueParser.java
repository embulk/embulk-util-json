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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.embulk.spi.json.JsonArray;
import org.embulk.spi.json.JsonDouble;
import org.embulk.spi.json.JsonLong;
import org.embulk.spi.json.JsonObject;
import org.embulk.spi.json.JsonString;
import org.embulk.spi.json.JsonValue;
import org.junit.jupiter.api.Test;

public class TestJsonValueParser {
    @Test
    public void testString() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder().build("\"foobar\"");
        final JsonValue value = parser.readJsonValue();
        assertEquals(JsonString.of("foobar"), value);
        assertNull(parser.readJsonValue());
    }

    public void testStringUnquoted() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder().build("foobar");
        assertThrows(JsonParseException.class, () -> {
            parser.readJsonValue();
        });
    }

    @Test
    public void testOrdinaryInteger() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder().build("12345");
        final JsonValue value = parser.readJsonValue();
        assertEquals(JsonLong.of(12345), value);
        assertNull(parser.readJsonValue());
    }

    @Test
    public void testExponentialInteger1() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder().build("12345e3");
        final JsonValue value = parser.readJsonValue();
        assertEquals(JsonDouble.of(12345000.0), value);
        assertNull(parser.readJsonValue());
    }

    @Test
    public void testExponentialInteger2() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder().build("123e2");
        final JsonValue value = parser.readJsonValue();
        assertEquals(JsonDouble.of(12300.0), value);
        assertNull(parser.readJsonValue());
    }

    @Test
    public void testOrdinaryFloat() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder().build("12345.12");
        final JsonValue value = parser.readJsonValue();
        assertEquals(JsonDouble.of(12345.12), value);
        assertNull(parser.readJsonValue());
    }

    @Test
    public void testExponentialFloat() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder().build("1.234512E4");
        final JsonValue value = parser.readJsonValue();
        assertEquals(JsonDouble.of(12345.12), value);
        assertNull(parser.readJsonValue());
    }

    @Test
    public void testParseJson() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder().build(
                "{\"col1\": 1, \"col2\": \"foo\", \"col3\": [1,2,3], \"col4\": {\"a\": 1}}");
        final JsonValue value = parser.readJsonValue();

        assertEquals(
                JsonObject.of(
                        "col1", JsonLong.of(1),
                        "col2", JsonString.of("foo"),
                        "col3", JsonArray.of(JsonLong.of(1), JsonLong.of(2), JsonLong.of(3)),
                        "col4", JsonObject.of("a", JsonLong.of(1))
                ),
                value);
        assertNull(parser.readJsonValue());
    }

    @Test
    public void testParseMultipleJsons() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder().build("{\"col1\": 1}{\"col1\": 2}");
        assertEquals(JsonObject.of("col1", JsonLong.of(1)), parser.readJsonValue());
        assertEquals(JsonObject.of("col1", JsonLong.of(2)), parser.readJsonValue());
        assertNull(parser.readJsonValue());
    }

    @Test
    public void testParseWithPointer1() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder().root("/a/b").build("{\"a\": {\"b\": 1}}");
        assertEquals(JsonLong.of(1), parser.readJsonValue());
        assertNull(parser.readJsonValue());
    }

    @Test
    public void testParseWithPointer2() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder().root("/a/1/b").build("{\"a\": [{\"b\": 1}, {\"b\": 2}]}");
        assertEquals(JsonLong.of(2), parser.readJsonValue());
        assertNull(parser.readJsonValue());
    }

    @Test
    public void testParseMultipleJsonsWithPointer() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder().root("/a/b").build("{\"a\": {\"b\": 1}}{\"a\": {\"b\": 2}}");
        assertEquals(JsonLong.of(1), parser.readJsonValue());
        assertEquals(JsonLong.of(2), parser.readJsonValue());
        assertNull(parser.readJsonValue());
    }
}
