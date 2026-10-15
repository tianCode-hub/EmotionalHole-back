package com.myk.emotionalHole.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量记录用户行为请求参数
 */
@Data
public class RecommendationBehaviorBatchRequest {

    @NotEmpty(message = "行为列表不能为空")
    @Size(max = 100, message = "单次上报的行为数量不能超过100")
    @Valid
    private List<RecommendationBehaviorRequest> behaviors;
}
