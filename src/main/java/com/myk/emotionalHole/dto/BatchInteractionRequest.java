package com.myk.emotionalHole.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量获取内容互动数据请求参数
 */
@Data
public class BatchInteractionRequest {

    @NotEmpty(message = "内容ID列表不能为空")
    @Size(max = 100, message = "单次查询的内容ID数量不能超过100")
    private List<Long> contentIds;

    private String anonymousId;
}
