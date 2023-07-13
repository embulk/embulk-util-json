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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonPointer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class TestJsonPointerTree {
    @Test
    public void testAdd() throws Exception {
        final JsonPointerTree.Builder builder = JsonPointerTree.builder();
        builder.add(JsonPointer.compile("/foo/bar/qux"), 0);
        builder.add(JsonPointer.compile("/foo/baz/qux"), 1);
        final JsonPointerTree root = builder.build();

        assertEquals(1, root.size());
        final JsonPointerTree foo = root.get("foo");
        assertNotNull(foo);
        assertTrue(root.captures().isEmpty());

        assertEquals(2, foo.size());
        final JsonPointerTree bar = foo.get("bar");
        final JsonPointerTree baz = foo.get("baz");
        assertNotNull(bar);
        assertTrue(bar.captures().isEmpty());
        assertNotNull(baz);
        assertTrue(baz.captures().isEmpty());

        assertEquals(1, bar.size());
        final JsonPointerTree quxInBar = bar.get("qux");
        assertNotNull(quxInBar);
        assertEquals(Arrays.asList(0), quxInBar.captures());

        assertEquals(1, baz.size());
        final JsonPointerTree quxInBaz = baz.get("qux");
        assertNotNull(quxInBaz);
        assertEquals(Arrays.asList(1), quxInBaz.captures());
    }

    @Test
    public void testSplit() throws Exception {
        assertTrue(JsonPointerTree.Builder.split(JsonPointer.compile("")).isEmpty());
        assertEquals(
                strings(""),
                JsonPointerTree.Builder.split(JsonPointer.compile("/")));
        assertEquals(
                strings("foo"),
                JsonPointerTree.Builder.split(JsonPointer.compile("/foo")));
        assertEquals(
                strings("123"),
                JsonPointerTree.Builder.split(JsonPointer.compile("/123")));
        assertEquals(
                strings("foo", "bar", "baz", "qux"),
                JsonPointerTree.Builder.split(JsonPointer.compile("/foo/bar/baz/qux")));
        assertEquals(
                strings("1", "2", "3", "4"),
                JsonPointerTree.Builder.split(JsonPointer.compile("/1/2/3/4")));
        assertEquals(
                strings("foo", "45", "baz", "67"),
                JsonPointerTree.Builder.split(JsonPointer.compile("/foo/45/baz/67")));
        assertEquals(
                strings("a~b", "~1", "/"),
                JsonPointerTree.Builder.split(JsonPointer.compile("/a~0b/~01/~1")));
    }

    @ParameterizedTest
    @CsvSource({
            ",true",
            "/,false",
            "/foo,false",
            "/0,false",
            "//,false",
    })
    public void testIsJsonPointerEmpty(final String pointerCsv, final String toBeEmptyInString) throws Exception {
        final String pointer = (pointerCsv == null ? "" : pointerCsv);
        final boolean toBeEmpty = Boolean.valueOf(toBeEmptyInString);
        System.out.printf("\"%s\" is considered to be%s empty\n", pointer, (toBeEmpty ? "" : " not"));
        assertEquals(toBeEmpty, JsonPointerTree.isJsonPointerEmpty(JsonPointer.compile(pointer)));
    }

    @ParameterizedTest
    @CsvSource({
            ",false",
            "/,true",
            "/foo,false",
            "/0,false",
            "//,false",
    })
    public void testIsJsonPointerRoot(final String pointerCsv, final String toBeRootInString) throws Exception {
        final String pointer = pointerCsv == null ? "" : pointerCsv;
        final boolean toBeRoot = Boolean.valueOf(toBeRootInString);
        System.out.printf("\"%s\" is considered to be%s root\n", pointer, (toBeRoot ? "" : " not"));
        assertEquals(Boolean.valueOf(toBeRootInString), JsonPointerTree.isJsonPointerRoot(JsonPointer.compile(pointer)));
    }

    private static List<String> strings(final String... strings) {
        final ArrayList<String> list = new ArrayList<>();
        for (final String string : strings) {
            list.add(string);
        }
        return Collections.unmodifiableList(list);
    }
}
