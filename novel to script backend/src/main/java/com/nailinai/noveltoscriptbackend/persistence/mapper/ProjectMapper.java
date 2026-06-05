package com.nailinai.noveltoscriptbackend.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nailinai.noveltoscriptbackend.domain.entity.ProjectEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapper<ProjectEntity> {
}
