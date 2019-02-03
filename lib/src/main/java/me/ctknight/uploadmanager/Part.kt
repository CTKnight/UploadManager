/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager

data class Part(
    internal val name: String,
    internal val value: String,
    internal val fileInfo: FileInfo?
)