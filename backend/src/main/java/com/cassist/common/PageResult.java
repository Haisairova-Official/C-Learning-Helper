package com.cassist.common;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int pageSize, long total) {
    public PageResult {
        items = List.copyOf(items);
        if (page < 1 || pageSize < 1 || pageSize > 100 || total < 0) {
            throw new IllegalArgumentException("Invalid pagination");
        }
    }
}
