package com.jaycode.rongpet.upload;

import lombok.Data;

/**
 * 图片上传结果。key 为对象存储键（入库使用），url 为可直接访问的临时地址（私有桶为预签名 URL）。
 */
@Data
public class UploadVO {

    private String key;

    private String url;

    private String contentType;

    private long size;
}
