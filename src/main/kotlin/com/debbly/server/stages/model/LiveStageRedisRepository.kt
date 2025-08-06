package com.debbly.server.stages.model

import org.springframework.data.repository.CrudRepository

interface LiveStageRedisRepository : CrudRepository<LiveStageRedisDto, String>