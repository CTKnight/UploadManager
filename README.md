# UploadManager (Alpha)
A Multipart upload solution on Android, including progress notification and network change.
This is a project based on AOSP DownloadManager and I made this with kotlin and OkHttp3.

------

## Usage

* get a instance of UploadManager
```kotlin
    val uploadManager: UploadManager = UploadManager.getUploadManger(context);
    // init it when your app boots
    uploadManager.init()
```
The best practise is to call `UploadManager.init()` as early as you need to display
notifications related to upload tasks and to clear/resume the crashed tasks

* upload
```kotlin
    val uri = Uri.parse(fileUri)
    val parts = listOf(
    // parts with name-value pairs
        Part("filecount", "1"),
        Part("predefine", "1"),
        Part("token", code),
        Part("secret", password),
    // parts with files
        Part("file", fileInfo =  FileInfo(uri, MediaType.parse(info.mimeType
                    ?: "application/octet-stream"), info.fileName))
    )
    // customized headers
    val headers = Headers.of(mutableMapOf(Pair("User-Agent", "Box Android")))
    val request = UploadManager.Request.Builder(
        HttpUrl.get(BoxUtils.UPLOAD_URI.toString()),
        parts,
        headers
    )

    uploadManage.enqueue(request.build())
```

* cancel a upload task
```kotlin
    uploadManger.cancel(uploadId);
```

* restart a upload task
```kotlin
    uploadManger.restartUpload(uploadId);
```

* query a upload task

TODO

------

## Proguard

This project already add `consumerProguardFiles`, so feel free to use proguard.

## How to install

Check the release tab, choose latest tag, download the aar attachment and setup your .gradle file

## License

```
Copyright 2018 Lai Jiewen.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License
is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
or implied. See the License for the specific language governing permissions and limitations under
the License.
```

see also: LICENSE file

## Reference

- [AOSP DownloadProvider](https://android.googlesource.com/platform/packages/providers/DownloadProvider/)
    [AOSP license](https://source.android.com/setup/licenses)

- [OkHttp](https://github.com/square/okhttp)
    ```
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
    ```
