package com.myk.emotionalHole.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("topic")
public class Topic implements Serializable {

    private Long id;
    private String topicName;
    private Integer isActive;
    private String createTime;

}