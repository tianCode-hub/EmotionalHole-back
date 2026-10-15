package com.myk.emotionalHole.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("user_profile")
public class UserProfile implements Serializable {

    private Long id;
    private String anonymousId;
    private String interestTags;
    private String createTime;
    private String updateTime;

}