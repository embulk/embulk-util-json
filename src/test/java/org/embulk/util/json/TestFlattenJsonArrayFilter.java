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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.filter.FilteringParserDelegate;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class TestFlattenJsonArrayFilter {
    @Test
    public void testSimple() throws IOException {
        final com.fasterxml.jackson.core.JsonParser parser = createFilteredParser("[{\"foo\":\"bar\"}]", 1);
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("foo", parser.getValueAsString());
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("bar", parser.getValueAsString());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        assertNull(parser.nextToken());
    }

    @Test
    public void testMultiple() throws IOException {
        final com.fasterxml.jackson.core.JsonParser parser = createFilteredParser("[{\"foo\":\"bar\"},{\"foo\":\"baz\"}]", 1);
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("foo", parser.getValueAsString());
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("bar", parser.getValueAsString());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("foo", parser.getValueAsString());
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("baz", parser.getValueAsString());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        assertNull(parser.nextToken());
    }

    @Test
    public void testNested() throws IOException {
        final com.fasterxml.jackson.core.JsonParser parser = createFilteredParser("[[{\"foo\":\"bar\"}]]", 1);
        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("foo", parser.getValueAsString());
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("bar", parser.getValueAsString());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        assertEquals(JsonToken.END_ARRAY, parser.nextToken());
        assertNull(parser.nextToken());
    }

    @Test
    public void testNested2() throws IOException {
        final com.fasterxml.jackson.core.JsonParser parser = createFilteredParser("[[{\"foo\":\"bar\"}]]", 2);
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("foo", parser.getValueAsString());
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("bar", parser.getValueAsString());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        assertNull(parser.nextToken());
    }

    @Test
    public void testNoArray() throws IOException {
        final com.fasterxml.jackson.core.JsonParser parser = createFilteredParser("{\"foo\":\"bar\"}", 1);
        assertNull(parser.nextToken());
    }

    @Test
    public void test0() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> {
            new FlattenJsonArrayFilter(0);
        });
    }

    private static com.fasterxml.jackson.core.JsonParser createFilteredParser(
            final String json,
            final int depth) throws IOException {
        final JsonFactory factory = new JsonFactory();
        factory.enable(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS);
        factory.enable(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS);
        return new FilteringParserDelegate(
                factory.createParser(json),
                new FlattenJsonArrayFilter(depth),
                false,  // TODO: Use com.fasterxml.jackson.core.filter.TokenFilter.Inclusion since Jackson 2.12.
                true  // Allow multiple matches
                );
    }
}
