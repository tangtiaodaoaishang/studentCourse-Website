package com.xuecheng.content.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @program: xuecheng-plus-project
 * @description: 用于课程预览的模型类
 * @author: ldjc
 * @create: 2024-01-16 19:55
 **/
@AllArgsConstructor
@Data
@NoArgsConstructor
public class CoursePreviewDto {
    //课程基本信息,课程营销信息
    CourseBaseInfoDto courseBase;
    //课程计划信息
    List<TeachplanDto> teachplans;
}
