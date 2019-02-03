/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager

data class FileInfo(
    val fileUri: String,
    val mimeType: String,
    val fileName: String?
)