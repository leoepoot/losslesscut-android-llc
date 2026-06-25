package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SegmentAction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import javax.inject.Inject

private const val MS_TO_SEC = 1000.0
private const val LLC_VERSION = 2

@Serializable
private data class LlcSegment(
    val start: Double,
    val end: Double,
    val name: String = "",
    val selected: Boolean = true
)

@Serializable
private data class LlcFile(
    val version: Int = LLC_VERSION,
    val mediaFileName: String,
    val cutSegments: List<LlcSegment>
)

private fun generateLlcContent(clips: List<MediaClip>): String {
    val allKeepSegments = clips
        .flatMap { clip -> clip.segments }
        .filter { segment -> segment.action == SegmentAction.KEEP }
        .sortedBy { segment -> segment.startMs }
        .map { segment ->
            LlcSegment(
                start = segment.startMs / MS_TO_SEC,
                end = segment.endMs / MS_TO_SEC
            )
        }

    val primaryFileName = clips.firstOrNull()?.fileName ?: "media"

    val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }
    return json.encodeToString(
        LlcFile(
            version = LLC_VERSION,
            mediaFileName = primaryFileName,
            cutSegments = allKeepSegments
        )
    )
}

private fun deriveLlcFileName(mediaFileName: String): String {
    val baseName = if (mediaFileName.contains('.')) {
        mediaFileName.substringBeforeLast('.')
    } else {
        mediaFileName
    }
    return "$baseName.llc"
}

public class GenerateSegmentFileUseCase @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    public suspend fun execute(
        clips: List<MediaClip>,
        outputDir: File
    ): Result<File> = withContext(ioDispatcher) {
        try {
            if (clips.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No media clips available"))
            }

            val content = generateLlcContent(clips)
            val fileName = deriveLlcFileName(clips.first().fileName)
            val outputFile = File(outputDir, fileName)

            if (!outputDir.exists() && !outputDir.mkdirs()) {
                return@withContext Result.failure(IOException("Cannot create output directory"))
            }

            outputFile.writeText(content)

            if (!outputFile.exists() || outputFile.length() == 0L) {
                return@withContext Result.failure(IOException("Output file is empty or missing"))
            }

            Result.success(outputFile)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }
}
