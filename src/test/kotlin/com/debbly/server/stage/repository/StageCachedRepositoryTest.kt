package com.debbly.server.stage.repository

import com.debbly.server.category.CategoryEntity
import com.debbly.server.claim.repository.ClaimEntity
import com.debbly.server.stage.model.StageType
import com.debbly.server.stage.repository.entities.StageEntity
import com.debbly.server.stage.repository.entities.StageStatus
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
        val category = CategoryEntity("cat1", "Category 1", null, "url", true)
        entityManager.persist(category)

        val claim = ClaimEntity(
            claimId = "claim1",
            title = "Test Claim",
            category = category,
            tags = emptySet(),
            popularity = 0,
            createdAt = Instant.now()
        )
        entityManager.persist(claim)

        val stage = StageEntity(
            stageId = "stage1",
            type = StageType.SOLO,
            title = "Test Topic",
            hosts = emptyList(),
            claimId = "claim1",
            createdAt = Instant.now(),
            status = StageStatus.PENDING,
            openedAt = null,
            closedAt = null
        )

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
