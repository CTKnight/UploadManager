/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.internal

data class Part(
    private val name: String,
    val value: String,
    val fileName: String?
)