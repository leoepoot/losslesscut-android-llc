package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.UiText
import com.tazztone.losslesscut.domain.usecase.ClipManagementUseCase
import com.tazztone.losslesscut.domain.usecase.SilenceDetectionUseCase
import java.util.UUID
import javax.inject.Inject

public data class SegmentOpResult(
    val clips: List<MediaClip>,
    val selectedClipIndex: Int,
    val message: UiText? = null,
    val shouldSaveHistory: Boolean = true,
    val shouldMarkDirty: Boolean = true,
    val success: Boolean = true,
    val clearDetectionRanges: Boolean = false
)

public class SegmentController @Inject constructor(
    private val clipManagementUseCase: ClipManagementUseCase,
    private val silenceDetectionUseCase: SilenceDetectionUseCase
) {

    public fun selectClip(clips: List<MediaClip>, currentIndex: Int, targetIndex: Int): SegmentOpResult {
        if (targetIndex == currentIndex || targetIndex !in clips.indices) {
            return SegmentOpResult(clips, currentIndex, success = false, shouldSaveHistory = false, shouldMarkDirty = false)
        }
        return SegmentOpResult(clips, targetIndex, shouldSaveHistory = false, shouldMarkDirty = false)
    }

    public fun addClips(clips: List<MediaClip>, currentIndex: Int, newClips: List<MediaClip>): SegmentOpResult {
        return SegmentOpResult(clips + newClips, currentIndex)
    }

    public fun removeClip(clips: List<MediaClip>, currentIndex: Int, targetIndex: Int): SegmentOpResult {
        if (clips.size <= 1) {
            return SegmentOpResult(
                clips, currentIndex,
                message = UiText.StringResource(R.string.error_cannot_delete_last),
                success = false, shouldSaveHistory = false, shouldMarkDirty = false
            )
        }
        val newList = clips.toMutableList()
        newList.removeAt(targetIndex)
        var newSelectedIndex = currentIndex
        if (targetIndex < currentIndex) {
            newSelectedIndex--
        } else if (currentIndex >= newList.size) {
            newSelectedIndex = newList.size - 1
        }
        return SegmentOpResult(newList, newSelectedIndex)
    }

    public fun reorderClips(clips: List<MediaClip>, currentIndex: Int, from: Int, to: Int): SegmentOpResult {
        if (from == to || from !in clips.indices || to !in clips.indices) {
            return SegmentOpResult(clips, currentIndex, success = false, shouldSaveHistory = false, shouldMarkDirty = false)
        }
        val reordered = clipManagementUseCase.reorderClips(clips, from, to)
        var newSelectedIndex = currentIndex
        if (currentIndex == from) {
            newSelectedIndex = to
        } else if (from < currentIndex && to >= currentIndex) {
            newSelectedIndex--
        } else if (from > currentIndex && to <= currentIndex) {
            newSelectedIndex++
        }
        return SegmentOpResult(reordered, newSelectedIndex)
    }

    public fun splitSegment(clips: List<MediaClip>, currentIndex: Int, positionMs: Long): SegmentOpResult {
        val currentClip = clips.getOrNull(currentIndex)
            ?: return SegmentOpResult(clips, currentIndex, success = false, shouldSaveHistory = false, shouldMarkDirty = false)
        val updatedClip = clipManagementUseCase.splitSegment(currentClip, positionMs)
            ?: return SegmentOpResult(
                clips, currentIndex,
                message = UiText.StringResource(R.string.error_segment_too_small_split),
                success = false, shouldSaveHistory = false, shouldMarkDirty = false
            )
        val newClips = clips.toMutableList().apply { this[currentIndex] = updatedClip }
        return SegmentOpResult(newClips, currentIndex)
    }

    public fun markSegmentDiscarded(clips: List<MediaClip>, currentIndex: Int, segmentId: UUID): SegmentOpResult {
        val currentClip = clips.getOrNull(currentIndex)
            ?: return SegmentOpResult(clips, currentIndex, success = false, shouldSaveHistory = false, shouldMarkDirty = false)
        val updatedClip = clipManagementUseCase.markSegmentDiscarded(currentClip, segmentId)
            ?: return SegmentOpResult(
                clips, currentIndex,
                message = UiText.StringResource(R.string.error_cannot_discard_last),
                success = false, shouldSaveHistory = false, shouldMarkDirty = false
            )
        val newClips = clips.toMutableList().apply { this[currentIndex] = updatedClip }
        return SegmentOpResult(newClips, currentIndex)
    }

    public fun updateSegmentBounds(clips: List<MediaClip>, currentIndex: Int, segmentId: UUID, startMs: Long, endMs: Long): SegmentOpResult {
        val currentClip = clips.getOrNull(currentIndex)
            ?: return SegmentOpResult(clips, currentIndex, success = false, shouldSaveHistory = false, shouldMarkDirty = false)
        val updatedClip = clipManagementUseCase.updateSegmentBounds(currentClip, segmentId, startMs, endMs)
        val newClips = clips.toMutableList().apply { this[currentIndex] = updatedClip }
        return SegmentOpResult(newClips, currentIndex, shouldSaveHistory = false, shouldMarkDirty = false)
    }

    public fun applyDetection(
        clips: List<MediaClip>,
        currentIndex: Int,
        ranges: List<LongRange>,
        minKeepSegmentDurationMs: Long,
        mode: SilenceDetectionUseCase.DetectionMode
    ): SegmentOpResult {
        if (ranges.isEmpty()) {
            return SegmentOpResult(clips, currentIndex, success = false, shouldSaveHistory = false, shouldMarkDirty = false)
        }
        val currentClip = clips.getOrNull(currentIndex)
            ?: return SegmentOpResult(clips, currentIndex, success = false, shouldSaveHistory = false, shouldMarkDirty = false)
        val updatedClip = silenceDetectionUseCase.applyDetectionRanges(
            currentClip, ranges, minKeepSegmentDurationMs, mode
        )
        val newClips = clips.toMutableList().apply { this[currentIndex] = updatedClip }
        return SegmentOpResult(newClips, currentIndex, clearDetectionRanges = true)
    }
}
