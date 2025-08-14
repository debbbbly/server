package com.debbly.server.stage.repository

import com.debbly.server.claim.CategoryEntity
import com.debbly.server.claim.TagEntity
import com.debbly.server.stage.model.StageType
import com.debbly.server.stage.repository.entities.StageEntity
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StageCachedRepositoryTest {

    @Autowired
    private lateinit var stageCachedRepository: StageCachedRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `getById should fetch from database and then from cache`() {
        // given
        val category = CategoryEntity("cat1", "Category 1", "url", true)
        val tag = TagEntity("tag1", "Tag 1")
        val claimId = "claim1"
        val stage = StageEntity(
            stageId = "stage1",
            type = StageType.SOLO,
            title = "Test Topic",
            hosts = emptySet(),
            claimId = claimId,
            createdAt = Instant.now()
        )

        entityManager.persist(category)
        entityManager.persist(tag)
        entityManager.persist(stage)
        entityManager.flush()
        entityManager.clear()

        // when
        val result1 = stageCachedRepository.getById("stage1")

        // then
        // This call will fetch from DB and cache the result.
        // If it fails here, the problem is with fetching and serializing to cache.

        // when
        val result2 = stageCachedRepository.getById("stage1")

        // then
        // This call should hit the cache.
        // If it fails here, the problem is with deserializing from cache.
    }
}
