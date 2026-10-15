package com.myk.emotionalHole.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 处理举报请求参数
 */
@Data
public class HandleReportRequest {

    @NotNull(message = "举报ID不能为空")
    private Long reportId;

    @NotBlank(message = "处理操作不能为空")
    @Pattern(regexp = "delete|handle|ignore", message = "处理操作必须为delete、handle或ignore")
    private String handleAction;

    private String handleResult;
}
