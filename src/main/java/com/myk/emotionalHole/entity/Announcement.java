
package com.myk.emotionalHole.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("announcement")
public class Announcement implements Serializable {

    private Long id;
    private Integer adminId;
    @NotBlank(message = "公告标题不能为空")
    private String title;
    @NotBlank(message = "公告内容不能为空")
    private String contentText;
    private Integer announcementType;
    private Integer isPinned;
    private Integer announcementStatus;
    private String createTime;
    private String updateTime;
}
