package com.debbly.server.stage.repository

import com.debbly.server.stage.model.LiveStageEntity
import org.springframework.data.repository.CrudRepository

interface LiveStageRedisRepository : CrudRepository<LiveStageEntity, String>