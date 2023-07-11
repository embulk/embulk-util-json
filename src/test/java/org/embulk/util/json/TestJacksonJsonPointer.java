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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.json.PackageVersion;
import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TestJacksonJsonPointer {
    @Test
    public void testEmpty() throws IOException {
        final JsonPointer empty = JsonPointer.compile("");
        assertEquals("", empty.toString());
        if (isBefore2_14_0()) {
            assertEquals("", empty.getMatchingProperty());
        } else {
            assertEquals(null, empty.getMatchingProperty());
        }
        assertTrue(empty.getMatchingIndex() < 0);
        assertEquals(JsonPointer.compile(""), empty);
        assertNotEquals(JsonPointer.compile("/"), empty);
        assertNotEquals(JsonPointer.compile("/foo"), empty);

        assertNull(empty.tail());
    }

    @Test
    public void testRoot() throws IOException {
        final JsonPointer root = JsonPointer.compile("/");
        assertEquals("/", root.toString());
        assertEquals("", root.getMatchingProperty());
        assertTrue(root.getMatchingIndex() < 0);
        assertNotEquals(JsonPointer.compile(""), root);
        assertEquals(JsonPointer.compile("/"), root);
        assertNotEquals(JsonPointer.compile("/foo"), root);

        final JsonPointer tail = root.tail();
        assertEquals("", tail.toString());
        if (isBefore2_14_0()) {
            assertEquals("", tail.getMatchingProperty());
        } else {
            assertEquals(null, tail.getMatchingProperty());
        }
        assertTrue(tail.getMatchingIndex() < 0);
        assertEquals(JsonPointer.compile(""), tail);
        assertNotEquals(JsonPointer.compile("/"), tail);
        assertNotEquals(JsonPointer.compile("/foo"), tail);

        assertNull(tail.tail());
    }

    @Test
    public void testRootDuplicated() throws IOException {
        final JsonPointer root = JsonPointer.compile("//");
        assertEquals("//", root.toString());
        assertEquals("", root.getMatchingProperty());
        assertTrue(root.getMatchingIndex() < 0);
        assertNotEquals(JsonPointer.compile(""), root);
        assertNotEquals(JsonPointer.compile("/"), root);
        assertEquals(JsonPointer.compile("//"), root);
        assertNotEquals(JsonPointer.compile("/foo"), root);

        final JsonPointer tail1 = root.tail();
        assertEquals("/", tail1.toString());
        assertEquals("", tail1.getMatchingProperty());
        assertTrue(tail1.getMatchingIndex() < 0);
        assertNotEquals(JsonPointer.compile(""), tail1);
        assertEquals(JsonPointer.compile("/"), tail1);
        assertNotEquals(JsonPointer.compile("/foo"), tail1);

        final JsonPointer tail2 = tail1.tail();
        assertEquals("", tail2.toString());
        if (isBefore2_14_0()) {
            assertEquals("", tail2.getMatchingProperty());
        } else {
            assertEquals(null, tail2.getMatchingProperty());
        }
        assertTrue(tail2.getMatchingIndex() < 0);
        assertEquals(JsonPointer.compile(""), tail2);
        assertNotEquals(JsonPointer.compile("/"), tail2);
        assertNotEquals(JsonPointer.compile("/foo"), tail2);

        assertNull(tail2.tail());
    }

    @Test
    public void testSingleProperty() throws IOException {
        final JsonPointer root = JsonPointer.compile("/foo");
        assertEquals("/foo", root.toString());
        assertEquals("foo", root.getMatchingProperty());
        assertTrue(root.getMatchingIndex() < 0);
        assertNotEquals(JsonPointer.compile(""), root);
        assertNotEquals(JsonPointer.compile("/"), root);
        assertEquals(JsonPointer.compile("/foo"), root);

        final JsonPointer tail = root.tail();
        assertEquals("", tail.toString());
        if (isBefore2_14_0()) {
            assertEquals("", tail.getMatchingProperty());
        } else {
            assertEquals(null, tail.getMatchingProperty());
        }
        assertTrue(tail.getMatchingIndex() < 0);
        assertEquals(JsonPointer.compile(""), tail);
        assertNotEquals(JsonPointer.compile("/"), tail);
        assertNotEquals(JsonPointer.compile("/foo"), tail);

        assertNull(tail.tail());
    }

    @Test
    public void testSingleIndex() throws IOException {
        final JsonPointer root = JsonPointer.compile("/123");
        assertEquals("/123", root.toString());
        assertEquals("123", root.getMatchingProperty());
        assertEquals(123, root.getMatchingIndex());
        assertNotEquals(JsonPointer.compile(""), root);
        assertNotEquals(JsonPointer.compile("/"), root);
        assertEquals(JsonPointer.compile("/123"), root);

        final JsonPointer tail = root.tail();
        assertEquals("", tail.toString());
        if (isBefore2_14_0()) {
            assertEquals("", tail.getMatchingProperty());
        } else {
            assertEquals(null, tail.getMatchingProperty());
        }
        assertTrue(tail.getMatchingIndex() < 0);
        assertEquals(JsonPointer.compile(""), tail);
        assertNotEquals(JsonPointer.compile("/"), tail);
        assertNotEquals(JsonPointer.compile("/123"), tail);

        assertNull(tail.tail());
    }

    @Test
    public void testMultipleProperties() throws IOException {
        final JsonPointer root = JsonPointer.compile("/foo/bar/baz");
        assertEquals("/foo/bar/baz", root.toString());
        assertEquals("foo", root.getMatchingProperty());
        assertTrue(root.getMatchingIndex() < 0);
        assertNotEquals(JsonPointer.compile(""), root);
        assertNotEquals(JsonPointer.compile("/"), root);
        assertNotEquals(JsonPointer.compile("/foo"), root);
        assertEquals(JsonPointer.compile("/foo/bar/baz"), root);

        final JsonPointer tail1 = root.tail();
        assertEquals("/bar/baz", tail1.toString());
        assertEquals("bar", tail1.getMatchingProperty());
        assertTrue(tail1.getMatchingIndex() < 0);
        assertNotEquals(JsonPointer.compile(""), tail1);
        assertNotEquals(JsonPointer.compile("/"), tail1);
        assertNotEquals(JsonPointer.compile("/bar"), tail1);
        assertEquals(JsonPointer.compile("/bar/baz"), tail1);

        final JsonPointer tail2 = tail1.tail();
        assertEquals("/baz", tail2.toString());
        assertEquals("baz", tail2.getMatchingProperty());
        assertTrue(tail2.getMatchingIndex() < 0);
        assertNotEquals(JsonPointer.compile(""), tail2);
        assertNotEquals(JsonPointer.compile("/"), tail2);
        assertNotEquals(JsonPointer.compile("/bar"), tail2);
        assertEquals(JsonPointer.compile("/baz"), tail2);

        final JsonPointer tail3 = tail2.tail();
        assertEquals("", tail3.toString());
        if (isBefore2_14_0()) {
            assertEquals("", tail3.getMatchingProperty());
        } else {
            assertEquals(null, tail3.getMatchingProperty());
        }
        assertTrue(tail3.getMatchingIndex() < 0);
        assertEquals(JsonPointer.compile(""), tail3);
        assertNotEquals(JsonPointer.compile("/"), tail3);
        assertNotEquals(JsonPointer.compile("/bar"), tail3);
        assertNotEquals(JsonPointer.compile("/baz"), tail3);

        assertNull(tail3.tail());
    }

    @BeforeAll
    static void printJacksonVersion() {
        System.out.println("Tested with Jackson: " + PackageVersion.VERSION.toString());
    }

    // The behavior of JsonPointer.compile("").tail() is different from Jackson 2.14.0.
    //
    // https://github.com/FasterXML/jackson-core/issues/788
    // https://github.com/FasterXML/jackson-core/commit/b0f6eb9bb2d2d829efb19020e7df4d732066f8cd
    private static boolean isBefore2_14_0() {
        return PackageVersion.VERSION.getMajorVersion() <= 2 && PackageVersion.VERSION.getMinorVersion() <= 13;
    }
}
