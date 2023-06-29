/*
 * Copyright 2017 The Embulk project
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;

public class TestJsonParser {
    @Test
    public void testString() throws Exception {
        final JsonParser parser = new JsonParser();
        final Value msgpackValue = parser.parse("\"foobar\"");
        assertFalse(msgpackValue.getValueType().isNumberType());
        assertTrue(msgpackValue.getValueType().isStringType());
        assertEquals("foobar", msgpackValue.asStringValue().toString());
    }

    public void testStringUnquoted() throws Exception {
        final JsonParser parser = new JsonParser();
        assertThrows(JsonParseException.class, () -> {
            parser.parse("foobar");
        });
    }

    @Test
    public void testOrdinaryInteger() throws Exception {
        final JsonParser parser = new JsonParser();
        final Value msgpackValue = parser.parse("12345");
        assertTrue(msgpackValue.getValueType().isNumberType());
        assertTrue(msgpackValue.getValueType().isIntegerType());
        assertFalse(msgpackValue.getValueType().isFloatType());
        assertFalse(msgpackValue.getValueType().isStringType());
        assertEquals(12345, msgpackValue.asIntegerValue().asInt());
    }

    @Test
    public void testExponentialInteger1() throws Exception {
        final JsonParser parser = new JsonParser();
        final Value msgpackValue = parser.parse("12345e3");
        assertTrue(msgpackValue.getValueType().isNumberType());
        // TODO: Consider this needs to be an integer?
        // See: https://github.com/embulk/embulk/issues/775
        assertTrue(msgpackValue.getValueType().isFloatType());
        assertFalse(msgpackValue.getValueType().isIntegerType());
        assertFalse(msgpackValue.getValueType().isStringType());
        assertEquals(12345000.0, msgpackValue.asFloatValue().toDouble(), 0.000000001);
        // Not sure this |toString| is to be tested...
        assertEquals("1.2345E7", msgpackValue.asFloatValue().toString());
    }

    @Test
    public void testExponentialInteger2() throws Exception {
        final JsonParser parser = new JsonParser();
        final Value msgpackValue = parser.parse("123e2");
        assertTrue(msgpackValue.getValueType().isNumberType());
        // TODO: Consider this needs to be an integer?
        // See: https://github.com/embulk/embulk/issues/775
        assertTrue(msgpackValue.getValueType().isFloatType());
        assertFalse(msgpackValue.getValueType().isIntegerType());
        assertFalse(msgpackValue.getValueType().isStringType());
        assertEquals(12300.0, msgpackValue.asFloatValue().toDouble(), 0.000000001);
        // Not sure this |toString| is to be tested...
        assertEquals("12300.0", msgpackValue.asFloatValue().toString());
    }

    @Test
    public void testOrdinaryFloat() throws Exception {
        final JsonParser parser = new JsonParser();
        final Value msgpackValue = parser.parse("12345.12");
        assertTrue(msgpackValue.getValueType().isNumberType());
        assertTrue(msgpackValue.getValueType().isFloatType());
        assertFalse(msgpackValue.getValueType().isIntegerType());
        assertFalse(msgpackValue.getValueType().isStringType());
        assertEquals(12345.12, msgpackValue.asFloatValue().toDouble(), 0.000000001);
        // Not sure this |toString| is to be tested...
        assertEquals("12345.12", msgpackValue.asFloatValue().toString());
    }

    @Test
    public void testExponentialFloat() throws Exception {
        final JsonParser parser = new JsonParser();
        final Value msgpackValue = parser.parse("1.234512E4");
        assertTrue(msgpackValue.getValueType().isNumberType());
        assertTrue(msgpackValue.getValueType().isFloatType());
        assertFalse(msgpackValue.getValueType().isIntegerType());
        assertFalse(msgpackValue.getValueType().isStringType());
        assertEquals(12345.12, msgpackValue.asFloatValue().toDouble(), 0.000000001);
        // Not sure this |toString| is to be tested...
        assertEquals("12345.12", msgpackValue.asFloatValue().toString());
    }

    @Test
    public void testParseJson() throws Exception {
        final JsonParser parser = new JsonParser();
        final Value msgpackValue = parser.parse("{\"col1\": 1, \"col2\": \"foo\", \"col3\": [1,2,3], \"col4\": {\"a\": 1}}");
        assertTrue(msgpackValue.isMapValue());
        final Map<Value, Value> map = msgpackValue.asMapValue().map();
        assertEquals(1, map.get(key("col1")).asIntegerValue().asInt());
        assertEquals("foo", map.get(key("col2")).asStringValue().toString());
        // Check array value
        final Value col3Value = map.get(key("col3"));
        assertTrue(col3Value.isArrayValue());
        assertEquals(
                Arrays.asList(1,2,3),
                col3Value.asArrayValue().list().stream()
                        .map(v -> v.asIntegerValue().asInt())
                        .collect(Collectors.toList())
        );
        // Check map value
        final Value col4Value = map.get(key("col4"));
        assertTrue(col4Value.isMapValue());
        final Value aOfCol4 = col4Value.asMapValue().map().get(key("a"));
        assertEquals(1, aOfCol4.asIntegerValue().asInt());
    }

    @Test
    public void testParseMultipleJsons() throws Exception {
        final JsonParser parser = new JsonParser();
        final String multipleJsons = "{\"col1\": 1}{\"col1\": 2}";
        try (JsonParser.Stream stream = parser.open(toInputStream(multipleJsons))) {
            assertEquals("{\"col1\":1}", stream.next().toJson());
            assertEquals("{\"col1\":2}", stream.next().toJson());
            assertNull(stream.next());
        }
    }

    @Test
    public void testParseWithPointer1() throws Exception {
        final JsonParser parser = new JsonParser();
        final Value msgpackValue = parser.parseWithOffsetInJsonPointer("{\"a\": {\"b\": 1}}", "/a/b");
        assertEquals(1, msgpackValue.asIntegerValue().asInt());
    }

    @Test
    public void testParseWithPointer2() throws Exception {
        final JsonParser parser = new JsonParser();
        final Value msgpackValue = parser.parseWithOffsetInJsonPointer("{\"a\": [{\"b\": 1}, {\"b\": 2}]}", "/a/1/b");
        assertEquals(2, msgpackValue.asIntegerValue().asInt());
    }

    @Test
    public void testParseMultipleJsonsWithPointer() throws Exception {
        final JsonParser parser = new JsonParser();
        final String multipleJsons = "{\"a\": {\"b\": 1}}{\"a\": {\"b\": 2}}";
        try (JsonParser.Stream stream = parser.openWithOffsetInJsonPointer(toInputStream(multipleJsons), "/a/b")) {
            assertEquals(1, stream.next().asIntegerValue().asInt());
            assertEquals(2, stream.next().asIntegerValue().asInt());
            assertNull(stream.next());
        }
    }

    private static Value key(String keyString) {
        return ValueFactory.newString(keyString);
    }

    private static InputStream toInputStream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
