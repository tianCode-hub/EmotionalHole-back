package com.myk.emotionalHole.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

/**
 * 批量查询抱抱状态请求DTO
 */
@Data
public class BatchHugStatusRequest {
    
    @Min(value = 1, message = "目标类型必须为1(内容)或2(评论)")
    @Max(value = 2, message = "目标类型必须为1(内容)或2(评论)")
    private int targetType;

    @NotEmpty(message = "目标ID列表不能为空")
    @Size(max = 100, message = "单次查询的目标ID数量不能超过100")
    private List<Long> targetIds;

    private String anonymousId;
    
}
