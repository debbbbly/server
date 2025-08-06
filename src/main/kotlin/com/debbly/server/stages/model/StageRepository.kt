package com.debbly.server.stages.model

import org.springframework.data.repository.CrudRepository
import java.time.Instant

interface StageRepository : CrudRepository<StageEntity, String>
