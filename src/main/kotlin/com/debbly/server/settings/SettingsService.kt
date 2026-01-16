package com.debbly.server.settings

import com.debbly.server.settings.SettingsName.*
import com.debbly.server.settings.repository.SettingsJpaRepository
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class SettingsService(
    private val settingsRepository: SettingsJpaRepository
) {
    companion object {
        const val HLS_PARALLEL_LIMIT_DEFAULT = 2;
        const val HLS_SEGMENT_DURATION_DEFAULT = 5;
        const val DEBATE_STAGE_DURATION_DEFAULT = 5 * 60L;
        const val DEBATE_STAGE_RECORDED_THRESHOLD_DEFAULT = 4 * 60L;
        const val CLEANUP_OLD_EGRESSES_DEFAULT = false;
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    private val cache: LoadingCache<SettingsName, String> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(10))
        .build { key ->
            settingsRepository.findById(key)
                .map { it.value }
                .orElse("")
                .also { logger.debug("Loaded setting {} from database: {}", key, it) }
        }

    fun getHlsParallelLimit(): Int {
        val value = cache.get(HLS_PARALLEL_LIMIT)
        return value.toIntOrNull() ?: HLS_PARALLEL_LIMIT_DEFAULT
    }

    fun getHlsSegmentDuration(): Int {
        val value = cache.get(HLS_SEGMENT_DURATION)
        return value.toIntOrNull() ?: HLS_SEGMENT_DURATION_DEFAULT
    }

    fun getMatchTtl(): Long {
        return 20;
    }

    fun getStageDuration(): Long {
        val value = cache.get(DEBATE_STAGE_DURATION)
        return value.toLongOrNull() ?: DEBATE_STAGE_DURATION_DEFAULT
    }

    fun getStageRecordedThreshold(): Long {
        val value = cache.get(DEBATE_STAGE_RECORDED_THRESHOLD)
        return value.toLongOrNull() ?: DEBATE_STAGE_RECORDED_THRESHOLD_DEFAULT
    }

    fun isCleanupOldEgresses(): Boolean {
        val value = cache.get(CLEANUP_OLD_EGRESSES)
        return value.toBooleanStrictOrNull() ?: CLEANUP_OLD_EGRESSES_DEFAULT
    }

    fun getSetting(name: SettingsName): String? {
        return cache.get(name)
    }

    fun updateSetting(name: SettingsName, value: String) {
        val setting = settingsRepository.findById(name)
            .map { it.copy(value = value) }
            .orElse(SettingsEntity(name = name, value = value))

        settingsRepository.save(setting)
        cache.invalidate(name)
        logger.info("Updated setting $name to $value")
    }

    fun invalidateCache() {
        cache.invalidateAll()
        logger.info("Invalidated settings cache")
    }
}
