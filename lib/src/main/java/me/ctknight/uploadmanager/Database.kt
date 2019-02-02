/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager

import android.content.Context
import android.util.JsonReader
import android.util.JsonWriter
import com.squareup.sqldelight.ColumnAdapter
import com.squareup.sqldelight.EnumColumnAdapter
import com.squareup.sqldelight.android.AndroidSqliteDriver
import okhttp3.*
import java.io.StringReader
import java.io.StringWriter
import java.io.Writer

internal class Database {
  companion object {
    lateinit var INSTANCE: UploadDatabase
    fun buildDatabase(context: Context): UploadDatabase {
      val driver = AndroidSqliteDriver(UploadDatabase.Schema, context, "upload.db")
      return UploadDatabase(driver, UploadInfo.Adapter(
          STATUSAdapter = EnumColumnAdapter(),
          MIME_TYPEAdapter = object : ColumnAdapter<MediaType, String> {
            override fun decode(databaseValue: String) =
                MediaType.parse(databaseValue)

            override fun encode(value: MediaType) =
                value.toString()
          },
          VISIBILITYAdapter = EnumColumnAdapter(),
          REFERERAdapter = object : ColumnAdapter<HttpUrl, String> {
            override fun decode(databaseValue: String) =
                HttpUrl.parse(databaseValue)

            override fun encode(value: HttpUrl) =
                value.toString()
          },
          HEADERAdapter = object : ColumnAdapter<Headers, String> {
            override fun decode(databaseValue: String) : Headers {
              val builder = Headers.Builder()
              databaseValue.lines().forEach { builder.add(it) }
              return builder.build()
            }

            override fun encode(value: Headers): String {
              return value.toString()
            }
          },
          PARTAdapter = object : ColumnAdapter<List<MultipartBody.Part>, String> {
            override fun decode(databaseValue: String): List<MultipartBody.Part> {
              val jsonReader = JsonReader(StringReader(databaseValue))
              val result = ArrayList<MultipartBody.Part>(5)
              with(jsonReader) {
                beginArray()
                while (hasNext()) {
                  nextString()
                }
                endArray()
                close()
              }
              return result
            }

            override fun encode(value: List<MultipartBody.Part>): String {
              val stringWriter = StringWriter()
              val jsonWriter = JsonWriter(stringWriter)
              with(jsonWriter) {
                beginArray()
                value.forEach {
                  value(it.toString())
                }
                endArray()
                close()
              }
              return stringWriter.toString()
            }
          }
      ))
    }
  }
}