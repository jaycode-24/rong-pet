package com.jaycode.rongpet.common.api;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果。小程序列表默认每页 20 条，后台管理列表使用传统页码分页。
 */
@Data
public class PageResult<T> implements Serializable {

    private List<T> records;
    private long total;
    private long pageNum;
    private long pageSize;

    public static <T> PageResult<T> of(List<T> records, long total, long pageNum, long pageSize) {
        PageResult<T> result = new PageResult<>();
        result.records = records;
        result.total = total;
        result.pageNum = pageNum;
        result.pageSize = pageSize;
        return result;
    }

    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return of(Collections.emptyList(), 0, pageNum, pageSize);
    }
}
