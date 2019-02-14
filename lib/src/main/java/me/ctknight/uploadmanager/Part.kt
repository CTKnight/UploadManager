/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager

// if fileInfo != null, treat it as a file part
// value and fileInfo can not be both null
data class Part(
    internal val name: String,
    internal val value: String? = null,
    internal val fileInfo: FileInfo? = null
)