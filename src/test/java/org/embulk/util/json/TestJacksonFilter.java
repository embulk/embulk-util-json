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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.filter.FilteringParserDelegate;
import com.fasterxml.jackson.core.filter.JsonPointerBasedFilter;
import com.fasterxml.jackson.core.filter.TokenFilter;
import com.fasterxml.jackson.core.json.PackageVersion;
import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TestJacksonFilter {
    @Test
    public void testFilterSequenceSkippingNonMatch() throws IOException {
        // This |parser| returns only {"bar":"baz"} and {"bar":"qux"}, with just skipping {"xxx":{"yyy":"zzz"}}.
        final com.fasterxml.jackson.core.JsonParser parser = createFilteredParser(
                "{\"foo\":{\"bar\":\"baz\"}}{\"xxx\":{\"yyy\":\"zzz\"}}{\"foo\":{\"bar\":\"quux\"}}",
                JsonPointer.compile("/foo"));
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("bar", parser.getValueAsString());
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("baz", parser.getValueAsString());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("bar", parser.getValueAsString());
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("quux", parser.getValueAsString());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        assertNull(parser.nextToken());
    }

    @BeforeAll
    static void printJacksonVersion() {
        System.out.println("Tested with Jackson: " + PackageVersion.VERSION.toString());
    }

    private static com.fasterxml.jackson.core.JsonParser createFilteredParser(
            final String json, final JsonPointer jsonPointer) throws IOException {
        final JsonFactory factory = new JsonFactory();
        factory.enable(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS);
        factory.enable(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS);

        return new FilteringParserDelegate(
                factory.createParser(json),
                new JsonPointerBasedFilter(jsonPointer),
                TokenFilter.Inclusion.ONLY_INCLUDE_ALL,
                true  // Allow multiple matches
                );
    }
}
