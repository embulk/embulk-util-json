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

import com.fasterxml.jackson.core.JsonPointer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A matching tree node of dissolved JSON Pointers.
 *
 * <p>For example, consider a {@link JsonPointerTree} instance that contains the following five JSON Pointers.
 *
 * <ul>
 * <li>0: {@code "/foo/bar"}
 * <li>1: {@code "/foo/baz"}
 * <li>2: {@code "/foo/baz/qux"}
 * <li>3: {@code "/quux/0/fred"}
 * <li>4: {@code "/quux/1/thud"}
 * </ul>
 *
 * <p>The index numbers 0, 1, ..., and 4 there in the example are called "captures".
 * A JSON value at the corresponding JSON Pointer is captured to the capture number.
 *
 * <p>A {@link JsonPointerTree} instance for the example above would consist of a tree like the following.
 *
 * <ul>
 * <li>{@code "foo"}
 *   <ul>
 *   <li>{@code "bar"} ... a JSON value at {@code "/foo/bar"} is captured to 0
 *   <li>{@code "baz"} ... a JSON value at {@code "/foo/baz"} is captured to 1
 *     <ul>
 *     <li>{@code "qux"} ... a JSON value at {@code "/foo/baz/qux"} is captured to 2
 *     </ul>
 *   </ul>
 * <li>{@code "quux"}
 *   <ul>
 *   <li>{@code "0"}
 *     <ul>
 *       <li>{@code "fred"} ... a JSON value at {@code "/quux/0/fred"} is captured to 3
 *     </ul>
 *   <li>{@code "1"}
 *     <ul>
 *       <li>{@code "thud"} ... a JSON value at {@code "/quux/1/thud"} is captured to 4
 *     </ul>
 *   </ul>
 * </ul>
 */
class JsonPointerTree extends AbstractMap<String, JsonPointerTree> {
    private JsonPointerTree(final HashMap<String, JsonPointerTree> nextSegments, final ArrayList<Integer> captures) {
        this.nextSegments = Collections.unmodifiableMap(nextSegments);
        if (captures.isEmpty()) {
            this.captures = Collections.emptyList();
        } else {
            this.captures = Collections.unmodifiableList(captures);
        }
    }

    private JsonPointerTree() {  // Only for INVALID.
        this.nextSegments = null;
        this.captures = Collections.emptyList();
    }

    /**
     * A builder of {@link JsonPointerTree}.
     */
    static class Builder {
        private Builder() {
            this.nextSegments = new HashMap<>();
            this.captures = new ArrayList<>();
        }

        /**
         * Builds a {@link JsonPointerTree} instance from the builder.
         *
         * @return the {@code JsonPointerTree} instance built
         */
        JsonPointerTree build() {
            final HashMap<String, JsonPointerTree> fixedTokens = new HashMap<>();
            for (final Map.Entry<String, Builder> entry : this.nextSegments.entrySet()) {
                // TODO: Remove recursions to avoid call stack overflow.
                fixedTokens.put(entry.getKey(), entry.getValue().build());
            }
            return new JsonPointerTree(fixedTokens, this.captures);
        }

        /**
         * Adds a JSON Pointer to the matcher tree.
         *
         * <p>Note that adding a JSON Pointer and constructing a matching tree is not a very lightweight operation.
         * Recommended to re-use the same matching tree instance for the same matching.
         *
         * @param pointer  the JSON Pointer to add
         * @param capture  the capture for the JSON Pointer
         * @return this builder
         */
        Builder add(final JsonPointer pointer, final int capture) {
            if (isJsonPointerEmpty(pointer)) {
                throw new IllegalArgumentException("Empty JSON Pointer \"\" is not permitted.");
            }

            if (isJsonPointerRoot(pointer)) {
                this.captures.add(capture);
                return this;
            }

            final List<String> splitPointers = split(pointer);

            Builder node = this;
            for (final String pointerElement : splitPointers) {
                node = node.addOnThis(pointerElement);
            }
            node.captures.add(capture);
            return this;
        }

        private Builder addOnThis(final String element) {
            final Builder node = this.nextSegments.get(element);
            if (node == null) {
                final Builder newNode = new Builder();
                this.nextSegments.put(element, newNode);
                return newNode;
            }
            return node;
        }

        /**
         * Split a JSON Pointer with unescaping {@code "~0"} to {@code "~"} and {@code "~1"} to {@code "/"}.
         *
         * <ul>
         * <li>{@code "/foo/bar/baz/qux"} : {@code ["foo", "bar", "baz", "qux"]}
         * <li>{@code "/1/2/3/4"} : {@code ["1", "2", "3", "4"]}
         * <li>{@code "/f~0o"} : {@code ["f~o"]}
         * <li>{@code "/"} : {@code [""]}
         * <li>{@code ""} : {@code []}
         * </ul>
         *
         * @see <a href="https://datatracker.ietf.org/doc/html/rfc6901">RFC 6901: JavaScript Object Notation (JSON) Pointer</a>
         */
        static List<String> split(final JsonPointer pointer) {
            JsonPointer tail = Objects.requireNonNull(pointer);

            final ArrayList<String> list = new ArrayList<>();
            while (tail != null) {
                if (isJsonPointerEmpty(tail)) {
                    break;
                }
                list.add(tail.getMatchingProperty());
                tail = tail.tail();
            }

            return Collections.unmodifiableList(list);
        }

        private final HashMap<String, Builder> nextSegments;

        private final ArrayList<Integer> captures;
    }

    /**
     * Creates a new builder instance for {@link JsonPointerTree}.
     *
     * @return the new builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builds a {@link JsonPointerTree} from an array of {@link JsonPointer}s.
     *
     * <p>It assigns the indices of the array as captures of the {@code JsonPointerTree}.
     *
     * @param pointers  an array of {@code JsonPointer}s
     * @return the new {@code JsonPointerTree} instance
     */
    static JsonPointerTree of(final JsonPointer... pointers) {
        final Builder builder = builder();
        for (int i = 0; i < pointers.length; i++) {
            builder.add(pointers[i], i);
        }
        return builder.build();
    }

    /**
     * Returns a {@link java.util.Set} view of this {@link JsonPointerTree} matching tree node.
     */
    @Override
    public Set<Map.Entry<String, JsonPointerTree>> entrySet() {
        if (this.nextSegments == null) {
            return Collections.emptySet();
        }
        return this.nextSegments.entrySet();
    }

    /**
     * Returns {@code true} if this {@link JsonPointerTree} is invalid.
     */
    boolean isInvalid() {
        return this.nextSegments == null;
    }

    /**
     * Returns a list of captures for this {@link JsonPointerTree} node.
     */
    List<Integer> captures() {
        return this.captures;
    }

    /**
     * Returns the hash code value for this matching tree.
     */
    @Override
    public int hashCode() {
        return Objects.hash(this.nextSegments, this.captures);
    }

    /**
     * Compares the specified object with this matching tree for equality.
     */
    @Override
    public boolean equals(final Object otherObject) {
        if (otherObject == null) {
            return false;
        }
        if (otherObject == this) {
            return true;
        }
        if (!(otherObject instanceof JsonPointerTree)) {
            return false;
        }
        final JsonPointerTree other = (JsonPointerTree) otherObject;
        return Objects.equals(this.nextSegments, other.nextSegments)
                && Objects.equals(this.captures, other.captures);
    }

    /**
     * Returns a string representation of this matching tree.
     */
    @Override
    public String toString() {
        if (this.nextSegments == null) {
            return "(invalid)";
        }

        if (this.captures.isEmpty()) {
            return this.nextSegments.toString();
        }
        return this.captures.toString() + ":" + this.nextSegments.toString();
    }

    static final JsonPointerTree INVALID = new JsonPointerTree();

    // Returns true if |pointer| is an "empty" JSON Pointer "".
    static boolean isJsonPointerEmpty(final JsonPointer pointer) {
        return pointer.tail() == null;
    }

    // Returns true if |pointer| is a "root" JSON Pointer "/".
    //
    // It can be replaced with |pointer.tail().getMatchingProperty() == null| in Jackson 2.14+,
    // but it does not work with Jackson earlier than 2.14.
    //
    // See also:
    // https://github.com/FasterXML/jackson-core/issues/788
    // https://github.com/FasterXML/jackson-core/commit/b0f6eb9bb2d2d829efb19020e7df4d732066f8cd
    static boolean isJsonPointerRoot(final JsonPointer pointer) {
        return "/".equals(pointer.toString());
    }

    private final Map<String, JsonPointerTree> nextSegments;

    private final List<Integer> captures;
}
