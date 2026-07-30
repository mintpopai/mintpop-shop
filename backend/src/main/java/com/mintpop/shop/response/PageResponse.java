package com.mintpop.shop.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 统一分页响应体（管理端列表接口共用）。
 */
@Data
@AllArgsConstructor
public class PageResponse<T> {

    /** 当前页记录 */
    private List<T> records;
    /** 总记录数 */
    private Long total;
    /** 页码（从 1 起） */
    private Long page;
    /** 每页条数 */
    private Long size;
}
