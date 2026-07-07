package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.UuidSerializer
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

@Serializable
public data class LlcProjectData(
    val savedAt: String,
    val selectedClipIndex: Int,
    @Serializable(with = UuidSerializer::class)
    val selectedSegmentId: UUID?,
    val playbackSpeed: Float,
    val isPitchCorrectionEnabled: Boolean,
    val lastMinSegmentMs: Long,
    val clips: List<MediaClip>
)

internal fun deriveLlcFileName(mediaFileName: String): String {
    val baseName = if (mediaFileName.contains('.')) {
        mediaFileName.substringBeforeLast('.')
    } else {
        mediaFileName
    }
    return "${baseName}_llc.json"
}

private fun generateLlcContent(projectData: LlcProjectData): String {
    val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }
    return json.encodeToString(projectData)
}

public class GenerateSegmentFileUseCase @Inject constructor(
    private val repository: IVideoEditingRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    public suspend fun execute(
        clips: List<MediaClip>,
        selectedClipIndex: Int,
        selectedSegmentId: UUID?,
        playbackSpeed: Float,
        isPitchCorrectionEnabled: Boolean,
        lastMinSegmentMs: Long
    ): Result<String> = withContext(ioDispatcher) {
        if (clips.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No media clips available"))
        }

        val projectData = LlcProjectData(
            savedAt = java.time.Instant.now().toString(),
            selectedClipIndex = selectedClipIndex,
            selectedSegmentId = selectedSegmentId,
            playbackSpeed = playbackSpeed,
            isPitchCorrectionEnabled = isPitchCorrectionEnabled,
            lastMinSegmentMs = lastMinSegmentMs,
            clips = clips
        )

        val content = generateLlcContent(projectData)
        val fileName = deriveLlcFileName(clips.first().fileName)
        repository.writeTextFile(fileName, content)
    }
}