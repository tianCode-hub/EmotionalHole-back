package com.myk.emotionalHole.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("comment")
public class Comment implements Serializable {

    private Long id;
    @NotNull(message = "内容ID不能为空")
    private Long contentId;      // 关联内容ID
    private Long parentId;       // 父评论ID，NULL表示直接对树洞内容的评论
    @NotBlank(message = "评论内容不能为空")
    private String commentText;  // 评论内容，限制500字
    private String anonymousId;  // 匿名用户ID
    private String avatar;       // 用户头像URL
    private int likeCount;        // 评论点赞数
    private int commentStatus; // 评论状态：0-待审核，1-已发布，2-已下架
    private String createTime;   // 创建时间

}
