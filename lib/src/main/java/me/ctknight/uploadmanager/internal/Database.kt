/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.internal

import android.content.Context
import android.util.JsonReader
import android.util.JsonWriter
import com.squareup.sqldelight.ColumnAdapter
import com.squareup.sqldelight.EnumColumnAdapter
import com.squareup.sqldelight.android.AndroidSqliteDriver
import me.ctknight.uploadmanager.Part
import me.ctknight.uploadmanager.UploadDatabase
import me.ctknight.uploadmanager.UploadInfo
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import java.io.StringReader
import java.io.StringWriter

internal class Database {
  companion object {
    internal lateinit var INSTANCE: UploadDatabase
    fun buildDatabase(context: Context): UploadDatabase {
      val driver = AndroidSqliteDriver(UploadDatabase.Schema, context, "upload.db")
      val httpUrlAdapter = object : ColumnAdapter<HttpUrl, String> {
        override fun decode(databaseValue: String) =
            HttpUrl.parse(databaseValue)

        override fun encode(value: HttpUrl) =
            value.toString()
      }
      return UploadDatabase(driver, UploadInfo.Adapter(
          StatusAdapter = EnumColumnAdapter(),
          MimeTypeAdapter = object : ColumnAdapter<MediaType, String> {
            override fun decode(databaseValue: String) =
                MediaType.parse(databaseValue)

            override fun encode(value: MediaType) =
                value.toString()
          },
          VisibilityAdapter = EnumColumnAdapter(),
          RefererAdapter = httpUrlAdapter,
          HeadersAdapter = object : ColumnAdapter<Headers, String> {
            override fun decode(databaseValue: String): Headers {
              val builder = Headers.Builder()
              databaseValue.lines().forEach { builder.add(it) }
              return builder.build()
            }

            override fun encode(value: Headers): String {
              return value.toString()
            }
          },
          PartsAdapter = object : ColumnAdapter<List<Part>, String> {
            override fun decode(databaseValue: String): List<Part> {
              val jsonReader = JsonReader(StringReader(databaseValue))
              val result = ArrayList<Part>(5)
              jsonReader.use {
                with(it) {
                  beginArray()
                  while (hasNext()) {
                    var name = ""
                    var value = ""
                    var fileName: String? = null
                    beginObject()
                    while (hasNext()) {
                      val tokenName = nextName()
                      when (tokenName) {
                        "name" -> {
                          name = nextString()
                        }
                        "value" -> {
                          value = nextString()
                        }
                        "fileName" -> {
                          fileName = nextString()
                        }
                        else -> {
                          skipValue()
                        }
                      }
                    }
                    result.add(Part(name, value, fileName))
                    endObject()
                  }
                  endArray()
                }
              }
              return result
            }

            override fun encode(value: List<Part>): String {
              val stringWriter = StringWriter()
              val jsonWriter = JsonWriter(stringWriter)
              jsonWriter.use { writer ->
                with(writer) {
                  setIndent("")
                  beginArray()
                  value.forEach {
                    beginObject()
                    name("name").value(it.name)
                    name("value").value(it.value)
                    name("fileName").value(it.fileName)
                    endObject()
                  }
                  endArray()
                }
              }
              return stringWriter.toString()
            }
          },
          TargeUrlAdapter = httpUrlAdapter
      ))
    }
  }
}

