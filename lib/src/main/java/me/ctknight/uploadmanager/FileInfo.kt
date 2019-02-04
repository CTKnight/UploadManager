/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager

import android.net.Uri
import okhttp3.MediaType

data class FileInfo(
    val fileUri: Uri,
    // TODO: default to be application/octet-stream
    // according to https://tools.ietf.org/html/rfc7578#section-4.4
    val mimeType: MediaType?,
    val fileName: String?
)