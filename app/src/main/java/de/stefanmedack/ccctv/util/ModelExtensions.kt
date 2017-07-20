package de.stefanmedack.ccctv.util

import info.metadude.kotlin.library.c3media.models.Event
import info.metadude.kotlin.library.c3media.models.MimeType

fun Event.playableVideoUrl(): String? {
    if (this.recordings != null) {
        for (recording in this.recordings) {
            if (recording.mimeType?.equals(MimeType.MP4) ?: false) {
                return recording.recordingUrl
            }
        }
    }
    return null
}