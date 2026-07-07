package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.usecase.GenerateSegmentFileUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

public data class LlcLoadedState(
    val clips: List<MediaClip>,
    val selectedClipIndex: Int,
    val selectedSegmentId: UUID?,
    val playbackSpeed: Float,
    val isPitchCorrectionEnabled: Boolean,
    val lastMinSegmentMs: Long
)

public class LlcLoadController @Inject constructor(
    private val useCase: GenerateSegmentFileUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    public suspend fun tryLoadAndApplyLlc(clips: List<MediaClip>): LlcLoadedState? = withContext(ioDispatcher) {
        val firstClip = clips.firstOrNull() ?: return@withContext null

        val llcUri = useCase.findLlcForMedia(firstClip.fileName)
            ?: return@withContext null

        val llcResult = useCase.loadLlcProject(llcUri)
        val llcData = llcResult.getOrNull() ?: return@withContext null

        val baseNameToLlcClip = llcData.clips.associateBy {
            it.fileName.substringBeforeLast('.')
        }

        val mergedClips = clips.map { clip ->
            val clipBaseName = clip.fileName.substringBeforeLast('.')
            val matchingLlcClip = baseNameToLlcClip[clipBaseName]
            if (matchingLlcClip != null) {
                val validatedSegments = matchingLlcClip.segments.map { segment ->
                    segment.copy(
                        startMs = segment.startMs.coerceIn(0L, clip.durationMs),
                        endMs = segment.endMs.coerceIn(0L, clip.durationMs)
                    )
                }.filter { it.startMs < it.endMs }
                if (validatedSegments.isNotEmpty()) {
                    clip.copy(segments = validatedSegments)
                } else {
                    clip
                }
            } else {
                clip
            }
        }

        LlcLoadedState(
            clips = mergedClips,
            selectedClipIndex = llcData.selectedClipIndex.coerceIn(0, mergedClips.size - 1),
            selectedSegmentId = llcData.selectedSegmentId,
            playbackSpeed = llcData.playbackSpeed,
            isPitchCorrectionEnabled = llcData.isPitchCorrectionEnabled,
            lastMinSegmentMs = llcData.lastMinSegmentMs
        )
    }
}