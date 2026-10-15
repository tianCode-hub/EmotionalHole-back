package com.myk.emotionalHole.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("emotion_keyword")
public class EmotionKeyword implements Serializable {

    private Long id;
    @NotBlank(message = "关键词不能为空")
    private String keyword;
    private Integer emotionType;
    @DecimalMin(value = "0.01", message = "权重值必须在 0.01 到 9.99 之间")
    @DecimalMax(value = "9.99", message = "权重值必须在 0.01 到 9.99 之间")
    private Double weight;
    private Integer status;
    private String createTime;
    private String updateTime;

}