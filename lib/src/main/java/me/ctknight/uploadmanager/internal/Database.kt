/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.internal

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonWriter
import com.squareup.sqldelight.ColumnAdapter
import com.squareup.sqldelight.EnumColumnAdapter
import com.squareup.sqldelight.android.AndroidSqliteDriver
import me.ctknight.uploadmanager.FileInfo
import me.ctknight.uploadmanager.Part
import me.ctknight.uploadmanager.UploadDatabase
import me.ctknight.uploadmanager.UploadRecord
import me.ctknight.uploadmanager.thirdparty.SingletonHolder
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import java.io.StringReader
import java.io.StringWriter

internal class Database {
  companion object {
    private object DatabaseHolder: SingletonHolder<UploadDatabase, Context>({
      buildDatabase(it)
    })
    internal val getInstance = DatabaseHolder::getInstance
    private fun buildDatabase(context: Context): UploadDatabase {
      val driver = AndroidSqliteDriver(UploadDatabase.Schema, context, "upload.db")
      val httpUrlAdapter = object : ColumnAdapter<HttpUrl, String> {
        override fun decode(databaseValue: String) =
            HttpUrl.parse(databaseValue)!!

        override fun encode(value: HttpUrl) =
            value.toString()
      }
      return UploadDatabase(driver, UploadRecord.Adapter(
          StatusAdapter = EnumColumnAdapter(),
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
                    var name: String? = null
                    var value: String? = null
                    var fileInfo: FileInfo? = null
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
                        "fileInfo" -> {
                          var fileName: String? = null
                          var mimeType: MediaType? = null
                          var fileUri: Uri? = null
                          beginObject()
                          while (hasNext()) {
                            when (nextName()) {
                              "fileName" -> {
                                fileName = nextString()
                              }
                              "mimeType" -> {
                                mimeType = MediaType.parse(nextString())
                              }
                              "fileUri" -> {
                                fileUri = Uri.parse(nextString())
                              }
                            }
                          }
                          endObject()
                          if (fileUri == null) {
                            throw NullPointerException("the fileUri is null, with:\n $databaseValue")
                          }
                          fileInfo = FileInfo(fileUri, mimeType, fileName)
                        }
                        else -> {
                          skipValue()
                        }
                      }
                    }
                    if (name == null) {
                      throw NullPointerException("the name is null in: $databaseValue ")
                    }
                    result.add(Part(name, value, fileInfo))
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
                    name("fileInfo")
                    if (it.fileInfo == null) {
                      nullValue()
                    } else {
                      beginObject()
                      name("fileName").value(it.fileInfo.fileName)
                      name("mimeType").value(it.fileInfo.mimeType?.toString())
                      name("fileUri").value(it.fileInfo.fileUri.toString())
                      endObject()
                    }
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

