package com.debbly.server.stage.model

import org.springframework.data.repository.CrudRepository

interface LiveStageRedisRepository : CrudRepository<LiveStageEntity, String>