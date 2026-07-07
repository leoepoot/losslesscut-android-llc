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

private fun parseLlcContent(jsonContent: String): Result<LlcProjectData> {
    return try {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val data = json.decodeFromString<LlcProjectData>(jsonContent)
        Result.success(data)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

public class GenerateSegmentFileUseCase @Inject constructor(
    private val repository: IVideoEditingRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    public suspend fun execute(projectData: LlcProjectData): Result<String> = withContext(ioDispatcher) {
        if (projectData.clips.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No media clips available"))
        }

        val content = generateLlcContent(projectData)
        val fileName = deriveLlcFileName(projectData.clips.first().fileName)
        repository.writeTextFile(fileName, content)
    }

    public suspend fun findLlcForMedia(mediaFileName: String): String? = withContext(ioDispatcher) {
        val llcFileName = deriveLlcFileName(mediaFileName)
        repository.findTextFileByName(llcFileName)
    }

    public suspend fun loadLlcProject(llcUri: String): Result<LlcProjectData> = withContext(ioDispatcher) {
        val textResult = repository.readTextFile(llcUri)
        textResult.fold(
            onSuccess = { jsonContent -> parseLlcContent(jsonContent) },
            onFailure = { Result.failure(it) }
        )
    }
}
