package com.myk.emotionalHole.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员更新内容状态请求参数
 */
@Data
public class UpdateContentStatusRequest {

    @NotNull(message = "内容ID不能为空")
    private Long contentId;

    @NotNull(message = "内容状态不能为空")
    private Integer status;
}
