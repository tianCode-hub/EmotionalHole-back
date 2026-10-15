package com.myk.emotionalHole.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("hug")
public class Hug implements Serializable {

    private Long id;
    @Min(value = 1, message = "抱抱目标类型必须为1(内容)或2(评论)")
    @Max(value = 2, message = "抱抱目标类型必须为1(内容)或2(评论)")
    private int targetType;         //抱抱目标类型：1-内容抱抱，2-评论抱抱
    @NotNull(message = "抱抱目标ID不能为空")
    private Long targetId;          //抱抱目标ID（content_id/comment_id）
    private String anonymousId;     //抱抱者匿名ID
    private String createTime;
}
