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
import org.embulk.spi.json.JsonBoolean;
import org.embulk.spi.json.JsonDouble;
import org.embulk.spi.json.JsonLong;
import org.embulk.spi.json.JsonNull;
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

    @Test
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

    @Test
    public void testFlattenJsonArray() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder()
                .setDepthToFlattenJsonArrays(1)
                .build("[{\"a\": {\"b\": 1}},{\"a\": {\"b\": 2}}]");
        assertEquals(JsonObject.of("a", JsonObject.of("b", JsonLong.of(1))), parser.readJsonValue());
        assertEquals(JsonObject.of("a", JsonObject.of("b", JsonLong.of(2))), parser.readJsonValue());
        assertNull(parser.readJsonValue());
    }

    @Test
    public void testRootWithFlattenJsonArray() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder()
                .root("/f")
                .setDepthToFlattenJsonArrays(1)
                .build("{\"f\":[{\"a\": {\"b\": 1}},{\"a\": {\"b\": 2}}]}");
        assertEquals(JsonObject.of("a", JsonObject.of("b", JsonLong.of(1))), parser.readJsonValue());
        assertEquals(JsonObject.of("a", JsonObject.of("b", JsonLong.of(2))), parser.readJsonValue());
        assertNull(parser.readJsonValue());
    }

    @Test
    public void testCaptureJsonPointers() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder().build(
                "{\"foo\":12,\"bar\":[true,false],\"baz\":null,\"qux\":{\"hoge\":\"fuga\"}}");
        final CapturingPointers pointers = CapturingPointers.builder()
                .addJsonPointer("/foo")
                .addJsonPointer("/")
                .addJsonPointer("/qux").build();
        final JsonValue[] values = parser.captureJsonValues(pointers);
        assertEquals(3, values.length);
        assertEquals(JsonLong.of(12L), values[0]);
        assertEquals(
                JsonObject.of(
                        "foo", JsonLong.of(12L),
                        "bar", JsonArray.of(JsonBoolean.TRUE, JsonBoolean.FALSE),
                        "baz", JsonNull.NULL,
                        "qux", JsonObject.of("hoge", JsonString.of("fuga"))),
                values[1]);
        assertEquals(JsonObject.of("hoge", JsonString.of("fuga")), values[2]);

        // Confirming that JsonValueParser reaches at the end as expected.

        assertNull(parser.captureJsonValues(pointers));
    }

    @Test
    public void testCaptureDirectMemberNames() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder().build(
                "{\"foo\":12,\"bar\":[true,false],\"baz\":null,\"qux\":{\"hoge\":\"fuga\"}}");
        final CapturingPointers pointers = CapturingPointers.builder()
                .addDirectMemberName("foo")
                .addDirectMemberName("qux").build();
        final JsonValue[] values = parser.captureJsonValues(pointers);
        assertEquals(2, values.length);
        assertEquals(JsonLong.of(12L), values[0]);
        assertEquals(JsonObject.of("hoge", JsonString.of("fuga")), values[1]);

        // Confirming that JsonValueParser reaches at the end as expected.

        assertNull(parser.captureJsonValues(pointers));
    }

    @Test
    public void testCaptureMixed() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder().build(
                "{\"foo\":12,\"bar\":[true,false],\"baz\":null,\"qux\":{\"hoge\":\"fuga\"}}");
        final CapturingPointers pointers = CapturingPointers.builder()
                .addDirectMemberName("foo")
                .addJsonPointer("/")
                .addJsonPointer("/qux").build();
        final JsonValue[] values = parser.captureJsonValues(pointers);
        assertEquals(3, values.length);
        assertEquals(JsonLong.of(12L), values[0]);
        assertEquals(
                JsonObject.of(
                        "foo", JsonLong.of(12L),
                        "bar", JsonArray.of(JsonBoolean.TRUE, JsonBoolean.FALSE),
                        "baz", JsonNull.NULL,
                        "qux", JsonObject.of("hoge", JsonString.of("fuga"))),
                values[1]);
        assertEquals(JsonObject.of("hoge", JsonString.of("fuga")), values[2]);

        // Confirming that JsonValueParser reaches at the end as expected.

        assertNull(parser.captureJsonValues(pointers));
    }

    @Test
    public void testCaptureRootPointer() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder().build(
                "{\"foo\":12,\"bar\":[true,false],\"baz\":null,\"qux\":{\"hoge\":\"fuga\"}}");
        final CapturingPointers pointers = CapturingPointers.builder().build();  // No pointers -- root.
        final JsonValue[] values = parser.captureJsonValues(pointers);
        assertEquals(1, values.length);
        assertEquals(
                JsonObject.of(
                        "foo", JsonLong.of(12L),
                        "bar", JsonArray.of(JsonBoolean.TRUE, JsonBoolean.FALSE),
                        "baz", JsonNull.NULL,
                        "qux", JsonObject.of("hoge", JsonString.of("fuga"))),
                values[0]);

        // Confirming that JsonValueParser reaches at the end as expected.

        assertNull(parser.captureJsonValues(pointers));
    }

    @Test
    public void testCaptureWithRoot() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder().root("/ex").build(
                "{\"ex\":{\"foo\":12,\"bar\":[true,false],\"baz\":null,\"qux\":{\"hoge\":\"fuga\"}}}");
        final CapturingPointers pointers = CapturingPointers.builder().build();
        final JsonValue[] values = parser.captureJsonValues(pointers);
        assertEquals(1, values.length);
        assertEquals(
                JsonObject.of(
                        "foo", JsonLong.of(12L),
                        "bar", JsonArray.of(JsonBoolean.TRUE, JsonBoolean.FALSE),
                        "baz", JsonNull.NULL,
                        "qux", JsonObject.of("hoge", JsonString.of("fuga"))),
                values[0]);

        // Confirming that JsonValueParser reaches at the end as expected.

        assertNull(parser.captureJsonValues(pointers));
    }

    @Test
    public void testCaptureWithFlattenJsonArray() throws Exception {
        final JsonValueParser parser = JsonValueParser.builder().setDepthToFlattenJsonArrays(1).build(
                "[{\"foo\":12,\"bar\":[true,false],\"baz\":null,\"qux\":{\"hoge\":\"fuga\"}},{\"foo\":14,\"bar\":[false],\"baz\":null,\"qux\":{}}]");
        final CapturingPointers pointers = CapturingPointers.builder().build();

        final JsonValue[] values1 = parser.captureJsonValues(pointers);
        assertEquals(1, values1.length);
        assertEquals(
                JsonObject.of(
                        "foo", JsonLong.of(12L),
                        "bar", JsonArray.of(JsonBoolean.TRUE, JsonBoolean.FALSE),
                        "baz", JsonNull.NULL,
                        "qux", JsonObject.of("hoge", JsonString.of("fuga"))),
                values1[0]);

        final JsonValue[] values2 = parser.captureJsonValues(pointers);
        assertEquals(1, values2.length);
        assertEquals(
                JsonObject.of(
                        "foo", JsonLong.of(14L),
                        "bar", JsonArray.of(JsonBoolean.FALSE),
                        "baz", JsonNull.NULL,
                        "qux", JsonObject.of()),
                values2[0]);

        // Confirming that JsonValueParser reaches at the end as expected.

        assertNull(parser.captureJsonValues(pointers));
    }
}
