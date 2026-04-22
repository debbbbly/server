package com.debbly.server.settings

import com.debbly.server.settings.repository.SettingsJpaRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class SettingsServiceTest {

    private val repo: SettingsJpaRepository = mock()
    private val mapper = ObjectMapper()
    private val service = SettingsService(repo, mapper)

    @Test
    fun `getHlsParallelLimit returns default when no setting`() {
        whenever(repo.findById(SettingsName.HLS_PARALLEL_LIMIT)).thenReturn(Optional.empty())
        assertEquals(SettingsService.HLS_PARALLEL_LIMIT_DEFAULT, service.getHlsParallelLimit())
    }

    @Test
    fun `getHlsParallelLimit returns stored value`() {
        whenever(repo.findById(SettingsName.HLS_PARALLEL_LIMIT))
            .thenReturn(Optional.of(SettingsEntity(SettingsName.HLS_PARALLEL_LIMIT, "5")))
        assertEquals(5, service.getHlsParallelLimit())
    }

    @Test
    fun `getMatchTtl returns 60`() {
        assertEquals(60L, service.getMatchTtl())
    }

    @Test
    fun `getLivekitClientConfig returns default on empty`() {
        whenever(repo.findById(SettingsName.LIVEKIT_CLIENT_CONFIG)).thenReturn(Optional.empty())
        assertEquals(LivekitConfig.DEFAULT, service.getLivekitClientConfig())
    }

    @Test
    fun `updateSetting saves new entity when none exists`() {
        whenever(repo.findById(SettingsName.HLS_PARALLEL_LIMIT)).thenReturn(Optional.empty())
        service.updateSetting(SettingsName.HLS_PARALLEL_LIMIT, "7")
        verify(repo).save(any())
    }
}
