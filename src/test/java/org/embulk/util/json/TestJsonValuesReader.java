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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonToken;
import org.embulk.spi.json.JsonArray;
import org.embulk.spi.json.JsonBoolean;
import org.embulk.spi.json.JsonLong;
import org.embulk.spi.json.JsonNull;
import org.embulk.spi.json.JsonObject;
import org.embulk.spi.json.JsonString;
import org.embulk.spi.json.JsonValue;
import org.junit.jupiter.api.Test;

public class TestJsonValuesReader {
    @Test
    public void testRead1() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final JsonParser parser = factory.createParser(
                "{\"foo\":12,\"bar\":[true,false],\"baz\":null,\"qux\":{\"hoge\":\"fuga\"}}");

        final JsonValuesReader reader1 = JsonValuesReader.of(
                JsonPointer.compile("/"),
                JsonPointer.compile("/baz"),
                JsonPointer.compile("/bar"),
                JsonPointer.compile("/qux/hoge"));

        final JsonValue[] actual1 = reader1.readValuesCaptured(parser);

        assertEquals(4, actual1.length);
        assertEquals(
                JsonObject.of(
                        "foo", JsonLong.of(12L),
                        "bar", JsonArray.of(JsonBoolean.TRUE, JsonBoolean.FALSE),
                        "baz", JsonNull.NULL,
                        "qux", JsonObject.of("hoge", JsonString.of("fuga"))),
                actual1[0]);
        assertEquals(JsonNull.NULL, actual1[1]);
        assertNotNull(actual1[1]);
        assertEquals(JsonArray.of(JsonBoolean.TRUE, JsonBoolean.FALSE), actual1[2]);
        assertEquals(JsonString.of("fuga"), actual1[3]);

        // Confirming that JsonParser reaches at the end as expected.

        assertNull(parser.nextToken());
    }

    @Test
    public void testRead2() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final JsonParser parser = factory.createParser(
                "{\"foo\":12,\"bar\":[true,false],\"baz\":null,\"qux\":{\"hoge\":\"fuga\"}}");

        final JsonValuesReader reader1 = JsonValuesReader.of(
                JsonPointer.compile("/"),
                JsonPointer.compile("/baz"),
                JsonPointer.compile("/qux/hoge"));

        final JsonValue[] actual1 = reader1.readValuesCaptured(parser);

        assertEquals(3, actual1.length);
        assertEquals(
                JsonObject.of(
                        "foo", JsonLong.of(12L),
                        "bar", JsonArray.of(JsonBoolean.TRUE, JsonBoolean.FALSE),
                        "baz", JsonNull.NULL,
                        "qux", JsonObject.of("hoge", JsonString.of("fuga"))),
                actual1[0]);
        assertEquals(JsonNull.NULL, actual1[1]);
        assertNotNull(actual1[1]);
        assertEquals(JsonString.of("fuga"), actual1[2]);

        // Confirming that JsonParser reaches at the end as expected.

        assertNull(parser.nextToken());
    }

    @Test
    public void testRead3() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final JsonParser parser = factory.createParser(
                "{\"foo\":12,\"bar\":123,\"baz\":null,\"qux\":{\"hoge\":\"fuga\"}}");

        final JsonValuesReader reader1 = JsonValuesReader.of(
                JsonPointer.compile("/"),
                JsonPointer.compile("/baz"),
                JsonPointer.compile("/bar"),
                JsonPointer.compile("/qux/hoge"));

        final JsonValue[] actual1 = reader1.readValuesCaptured(parser);

        assertEquals(4, actual1.length);
        assertEquals(
                JsonObject.of(
                        "foo", JsonLong.of(12L),
                        "bar", JsonLong.of(123L),
                        "baz", JsonNull.NULL,
                        "qux", JsonObject.of("hoge", JsonString.of("fuga"))),
                actual1[0]);
        assertEquals(JsonNull.NULL, actual1[1]);
        assertNotNull(actual1[1]);
        assertEquals(JsonLong.of(123L), actual1[2]);
        assertEquals(JsonString.of("fuga"), actual1[3]);

        // Confirming that JsonParser reaches at the end as expected.

        assertNull(parser.nextToken());
    }

    @Test
    public void testRead4() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final JsonParser parser = factory.createParser(
                "{\"foo\":12,\"bar\":123,\"baz\":null,\"qux\":{\"hoge\":\"fuga\"}}");

        final JsonValuesReader reader1 = JsonValuesReader.of(
                JsonPointer.compile("/"),
                JsonPointer.compile("/baz"),
                JsonPointer.compile("/qux/hoge"));

        final JsonValue[] actual1 = reader1.readValuesCaptured(parser);

        assertEquals(3, actual1.length);
        assertEquals(
                JsonObject.of(
                        "foo", JsonLong.of(12L),
                        "bar", JsonLong.of(123L),
                        "baz", JsonNull.NULL,
                        "qux", JsonObject.of("hoge", JsonString.of("fuga"))),
                actual1[0]);
        assertEquals(JsonNull.NULL, actual1[1]);
        assertNotNull(actual1[1]);
        assertEquals(JsonString.of("fuga"), actual1[2]);

        // Confirming that JsonParser reaches at the end as expected.

        assertNull(parser.nextToken());
    }

    @Test
    public void testRead5() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final JsonParser parser = factory.createParser(
                "{\"foo\":12,\"bar\":[true,false],\"baz\":null,\"qux\":{\"hoge\":\"fuga\"}}");

        final JsonValuesReader reader1 = JsonValuesReader.of(
                JsonPointer.compile("/baz"),
                JsonPointer.compile("/bar"),
                JsonPointer.compile("/qux/hoge"));

        final JsonValue[] actual1 = reader1.readValuesCaptured(parser);

        assertEquals(3, actual1.length);
        assertEquals(JsonNull.NULL, actual1[0]);
        assertNotNull(actual1[0]);
        assertEquals(JsonArray.of(JsonBoolean.TRUE, JsonBoolean.FALSE), actual1[1]);
        assertEquals(JsonString.of("fuga"), actual1[2]);

        // Confirming that JsonParser reaches at the end as expected.

        assertNull(parser.nextToken());
    }

    @Test
    public void testRead6() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final JsonParser parser = factory.createParser(
                "{\"foo\":12,\"bar\":[true,false],\"baz\":null,\"qux\":{\"hoge\":\"fuga\"}}");

        final JsonValuesReader reader1 = JsonValuesReader.of(
                JsonPointer.compile("/baz"),
                JsonPointer.compile("/qux/hoge"));

        final JsonValue[] actual1 = reader1.readValuesCaptured(parser);

        assertEquals(2, actual1.length);
        assertEquals(JsonNull.NULL, actual1[0]);
        assertNotNull(actual1[0]);
        assertEquals(JsonString.of("fuga"), actual1[1]);

        // Confirming that JsonParser reaches at the end as expected.

        assertNull(parser.nextToken());
    }

    @Test
    public void testRead7() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final JsonParser parser = factory.createParser(
                "{\"foo\":12,\"bar\":123,\"baz\":null,\"qux\":{\"hoge\":\"fuga\"}}");

        final JsonValuesReader reader1 = JsonValuesReader.of(
                JsonPointer.compile("/baz"),
                JsonPointer.compile("/bar"),
                JsonPointer.compile("/qux/hoge"));

        final JsonValue[] actual1 = reader1.readValuesCaptured(parser);

        assertEquals(3, actual1.length);
        assertEquals(JsonNull.NULL, actual1[0]);
        assertNotNull(actual1[0]);
        assertEquals(JsonLong.of(123L), actual1[1]);
        assertEquals(JsonString.of("fuga"), actual1[2]);

        // Confirming that JsonParser reaches at the end as expected.

        assertNull(parser.nextToken());
    }

    @Test
    public void testRead8() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final JsonParser parser = factory.createParser(
                "{\"foo\":12,\"bar\":123,\"baz\":null,\"qux\":{\"hoge\":\"fuga\"}}");

        final JsonValuesReader reader1 = JsonValuesReader.of(
                JsonPointer.compile("/baz"),
                JsonPointer.compile("/qux/hoge"));

        final JsonValue[] actual1 = reader1.readValuesCaptured(parser);

        assertEquals(2, actual1.length);
        assertEquals(JsonNull.NULL, actual1[0]);
        assertNotNull(actual1[0]);
        assertEquals(JsonString.of("fuga"), actual1[1]);

        // Confirming that JsonParser reaches at the end as expected.

        assertNull(parser.nextToken());
    }

    @Test
    public void testReadArray() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final JsonParser parser = factory.createParser(
                "[{\"foo\":12,\"bar\":true},{\"bar\":false,\"foo\":84},{\"foo\":123,\"bar\":false}]");

        assertEquals(JsonToken.START_ARRAY, parser.nextToken());

        final JsonValuesReader reader = JsonValuesReader.of(
                JsonPointer.compile("/foo"),
                JsonPointer.compile("/"),
                JsonPointer.compile("/bar"),
                JsonPointer.compile("/none"));

        final JsonValue[] actual1 = reader.readValuesCaptured(parser);
        final JsonValue[] actual2 = reader.readValuesCaptured(parser);
        final JsonValue[] actual3 = reader.readValuesCaptured(parser);

        assertEquals(4, actual1.length);
        assertEquals(JsonLong.of(12), actual1[0]);
        assertEquals(JsonObject.of("foo", JsonLong.of(12), "bar", JsonBoolean.TRUE), actual1[1]);
        assertEquals(JsonBoolean.TRUE, actual1[2]);
        assertNull(actual1[3]);

        assertEquals(4, actual2.length);
        assertEquals(JsonLong.of(84), actual2[0]);
        assertEquals(JsonObject.of("foo", JsonLong.of(84), "bar", JsonBoolean.FALSE), actual2[1]);
        assertEquals(JsonBoolean.FALSE, actual2[2]);
        assertNull(actual2[3]);

        assertEquals(4, actual3.length);
        assertEquals(JsonLong.of(123), actual3[0]);
        assertEquals(JsonObject.of("foo", JsonLong.of(123), "bar", JsonBoolean.FALSE), actual3[1]);
        assertEquals(JsonBoolean.FALSE, actual3[2]);
        assertNull(actual3[3]);

        assertEquals(JsonToken.END_ARRAY, parser.nextToken());

        assertNull(parser.nextToken());
    }

    @Test
    public void testReadSequence() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final JsonParser parser = factory.createParser(
                "{\"bar\":true,\"foo\":12}{\"foo\":84,\"bar\":false}{\"foo\":123,\"bar\":false}");

        final JsonValuesReader reader = JsonValuesReader.of(
                JsonPointer.compile("/foo"),
                JsonPointer.compile("/"),
                JsonPointer.compile("/bar"),
                JsonPointer.compile("/none"));

        final JsonValue[] actual1 = reader.readValuesCaptured(parser);
        final JsonValue[] actual2 = reader.readValuesCaptured(parser);
        final JsonValue[] actual3 = reader.readValuesCaptured(parser);

        assertEquals(4, actual1.length);
        assertEquals(JsonLong.of(12), actual1[0]);
        assertEquals(JsonObject.of("foo", JsonLong.of(12), "bar", JsonBoolean.TRUE), actual1[1]);
        assertEquals(JsonBoolean.TRUE, actual1[2]);
        assertNull(actual1[3]);

        assertEquals(4, actual2.length);
        assertEquals(JsonLong.of(84), actual2[0]);
        assertEquals(JsonObject.of("foo", JsonLong.of(84), "bar", JsonBoolean.FALSE), actual2[1]);
        assertEquals(JsonBoolean.FALSE, actual2[2]);
        assertNull(actual2[3]);

        assertEquals(4, actual3.length);
        assertEquals(JsonLong.of(123), actual3[0]);
        assertEquals(JsonObject.of("foo", JsonLong.of(123), "bar", JsonBoolean.FALSE), actual3[1]);
        assertEquals(JsonBoolean.FALSE, actual3[2]);
        assertNull(actual3[3]);

        assertNull(parser.nextToken());
    }

    @Test
    public void testReadScalars() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final JsonParser parser = factory.createParser(
                "[12,\"foo\",null,true]");

        assertEquals(JsonToken.START_ARRAY, parser.nextToken());

        final JsonValuesReader reader = JsonValuesReader.of(
                JsonPointer.compile("/foo"),
                JsonPointer.compile("/"));

        final JsonValue[] actual1 = reader.readValuesCaptured(parser);
        final JsonValue[] actual2 = reader.readValuesCaptured(parser);
        final JsonValue[] actual3 = reader.readValuesCaptured(parser);
        final JsonValue[] actual4 = reader.readValuesCaptured(parser);

        assertEquals(2, actual1.length);
        assertNull(actual1[0]);
        assertEquals(JsonLong.of(12L), actual1[1]);

        assertEquals(2, actual2.length);
        assertNull(actual2[0]);
        assertEquals(JsonString.of("foo"), actual2[1]);

        assertEquals(2, actual3.length);
        assertNull(actual3[0]);
        assertEquals(JsonNull.NULL, actual3[1]);

        assertEquals(2, actual4.length);
        assertNull(actual4[0]);
        assertEquals(JsonBoolean.TRUE, actual4[1]);

        assertEquals(JsonToken.END_ARRAY, parser.nextToken());

        assertNull(parser.nextToken());
    }

    @Test
    public void testReadMultiParsers() throws Exception {
        final JsonValuesReader reader = JsonValuesReader.of(
                JsonPointer.compile("/foo"),
                JsonPointer.compile("/"),
                JsonPointer.compile("/bar"),
                JsonPointer.compile("/none"));

        final JsonFactory factory = new JsonFactory();
        final JsonParser parser1 = factory.createParser("{\"foo\":12,\"bar\":true}");
        final JsonParser parser2 = factory.createParser("{\"bar\":false,\"foo\":84}");
        final JsonParser parser3 = factory.createParser("{\"foo\":123,\"bar\":false}");

        final JsonValue[] actual1 = reader.readValuesCaptured(parser1);
        final JsonValue[] actual2 = reader.readValuesCaptured(parser2);
        final JsonValue[] actual3 = reader.readValuesCaptured(parser3);

        assertEquals(4, actual1.length);
        assertEquals(JsonLong.of(12), actual1[0]);
        assertEquals(JsonObject.of("foo", JsonLong.of(12), "bar", JsonBoolean.TRUE), actual1[1]);
        assertEquals(JsonBoolean.TRUE, actual1[2]);
        assertNull(actual1[3]);
        assertNull(parser1.nextToken());

        assertEquals(4, actual2.length);
        assertEquals(JsonLong.of(84), actual2[0]);
        assertEquals(JsonObject.of("foo", JsonLong.of(84), "bar", JsonBoolean.FALSE), actual2[1]);
        assertEquals(JsonBoolean.FALSE, actual2[2]);
        assertNull(actual2[3]);
        assertNull(parser2.nextToken());

        assertEquals(4, actual3.length);
        assertEquals(JsonLong.of(123), actual3[0]);
        assertEquals(JsonObject.of("foo", JsonLong.of(123), "bar", JsonBoolean.FALSE), actual3[1]);
        assertEquals(JsonBoolean.FALSE, actual3[2]);
        assertNull(actual3[3]);
        assertNull(parser3.nextToken());
    }

    @Test
    public void testReadContinued() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final JsonParser parser = factory.createParser(
                "{\"foo\":12,\"bar\":[true,false],\"baz\":null,\"qux\":{\"hoge\":\"fuga\"}}{\"dummy\":{\"in\":98}}{\"unreach\":7}");

        final JsonValuesReader reader1 = JsonValuesReader.of(
                JsonPointer.compile("/qux"),
                JsonPointer.compile("/"),
                JsonPointer.compile("/baz"),
                JsonPointer.compile("/bar"),
                JsonPointer.compile("/qux/hoge"));

        final JsonValue[] actual1 = reader1.readValuesCaptured(parser);

        assertEquals(5, actual1.length);
        assertEquals(JsonObject.of("hoge", JsonString.of("fuga")), actual1[0]);
        assertEquals(
                JsonObject.of(
                        "foo", JsonLong.of(12L),
                        "bar", JsonArray.of(JsonBoolean.TRUE, JsonBoolean.FALSE),
                        "baz", JsonNull.NULL,
                        "qux", JsonObject.of("hoge", JsonString.of("fuga"))),
                actual1[1]);
        assertEquals(JsonNull.NULL, actual1[2]);
        assertNotNull(actual1[2]);
        assertEquals(JsonArray.of(JsonBoolean.TRUE, JsonBoolean.FALSE), actual1[3]);
        assertEquals(JsonString.of("fuga"), actual1[4]);

        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("dummy", parser.getCurrentName());

        // Confirming that reading can continue on the same JsonParser by another JsonValuesReader.

        final JsonValuesReader reader2 = JsonValuesReader.of(
                JsonPointer.compile("/"),
                JsonPointer.compile("/in"));

        final JsonValue[] actual2 = reader2.readValuesCaptured(parser);

        assertEquals(2, actual2.length);
        assertEquals(JsonObject.of("in", JsonLong.of(98L)), actual2[0]);
        assertEquals(JsonLong.of(98L), actual2[1]);

        assertEquals(JsonToken.END_OBJECT, parser.nextToken());

        // Confirming that reading can continue on the same JsonParser.

        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("unreach", parser.getCurrentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(7L, parser.getLongValue());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());

        // Confirming that JsonParser reaches at the end as expected.

        assertNull(parser.nextToken());
    }
}
