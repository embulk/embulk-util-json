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
import java.util.Arrays;
import org.embulk.spi.json.JsonArray;
import org.embulk.spi.json.JsonBoolean;
import org.embulk.spi.json.JsonNull;
import org.embulk.spi.json.JsonObject;
import org.embulk.spi.json.JsonString;
import org.embulk.spi.json.JsonValue;
import org.junit.jupiter.api.Test;

public class TestCapturingDirectMemberNameList {
    @Test
    public void testOrdinaryRead() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final JsonParser parser = factory.createParser(
                "{\"foo\":{\"ignored\":[1,2,{},\"skipped\"]},\"bar\":[true,false],\"baz\":null,\"qux\":{\"hoge\":\"fuga\"}}");
        final InternalJsonValueReader reader = new InternalJsonValueReader(false, false, false, 0.0, 0L);

        final CapturingDirectMemberNameList capturingMembers1 = capturingMembers(
                "bar",
                "baz",
                "dummy",
                "qux");

        final JsonValue[] actual1 = capturingMembers1.captureFromParser(parser, reader);

        assertEquals(4, actual1.length);
        assertEquals(JsonArray.of(JsonBoolean.TRUE, JsonBoolean.FALSE), actual1[0]);
        assertEquals(JsonNull.NULL, actual1[1]);
        assertNotNull(actual1[1]);
        assertNull(actual1[2]);
        assertEquals(JsonObject.of("hoge", JsonString.of("fuga")), actual1[3]);

        // Confirming that JsonParser reaches at the end as expected.

        assertNull(parser.nextToken());
    }

    private static CapturingDirectMemberNameList capturingMembers(final String... memberNames) {
        return CapturingDirectMemberNameList.of(Arrays.asList(memberNames));
    }
}
