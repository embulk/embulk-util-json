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

import com.fasterxml.jackson.core.filter.TokenFilter;

/**
 * Simple {@link TokenFilter} implementation to flatten top-level JSON Array(s).
 */
class FlattenJsonArrayFilter extends TokenFilter {
    FlattenJsonArrayFilter(final int depth) {
        if (depth <= 0) {
            throw new IllegalArgumentException("FlattenJsonArrayFilter must receive at least 1 as depth.");
        }
        this.depth = depth;
    }

    @Override
    public TokenFilter includeElement(final int index) {
        if (this.depth <= 1) {
            return TokenFilter.INCLUDE_ALL;
        }
        return new FlattenJsonArrayFilter(this.depth - 1);
    }

    @Override
    public TokenFilter includeProperty(final String name) {
        return null;
    }

    @Override
    public String toString() {
        return "[FlattenJsonArrayFilter depth: " + this.depth + "]";
    }

    private final int depth;
}
