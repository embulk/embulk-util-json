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

import com.fasterxml.jackson.core.JsonPointer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class TestCapturingPointers {
    @ParameterizedTest(name = "\"{1}\" => \"{0}\"")
    @CsvSource({
            "/foo,foo",
            "/,''",
            "/~0,~",
            "/~1,/",
            "/~0~1,~/",
            "/~1~0,/~",
            "/~00~00,~0~0",
            "/~01~01,~1~1",
    })
    public void testCompileMemberNameToJsonPointer(final String pointer, final String memberName) {
        assertEquals(JsonPointer.compile(pointer), CapturingPointers.compileMemberNameToJsonPointer(memberName));
    }
}
