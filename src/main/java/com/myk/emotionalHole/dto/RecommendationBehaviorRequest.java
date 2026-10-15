package com.myk.emotionalHole.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 记录用户行为请求参数（单条）
 */
@Data
public class RecommendationBehaviorRequest {

    @NotNull(message = "内容ID不能为空")
    private Long contentId;

    @NotNull(message = "行为类型不能为空")
    private Integer behaviorType;
}
