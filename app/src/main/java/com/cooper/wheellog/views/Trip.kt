package com.cooper.wheellog.views

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

class Trip {
    var mediaId = ""
    var title = ""
    var description = ""

    constructor(title: String, description: String, mediaId: String) {
        this.title = title
        this.description = description
        this.mediaId = mediaId
    }

    var uri: Uri
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return Uri.fromFile(File(mediaId))
            }
            val downloads = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
            return Uri.withAppendedPath(downloads, mediaId)
        }
        set(value) {}
}