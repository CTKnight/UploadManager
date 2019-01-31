# UploadManager (Alpha)
A Multipart upload solution on Android, including progress notification and network change.
This is a project based on AOSP DownloadManager, I implement this using OkHttp3.

------

## Usage: Much like AOSP's DownloadManger, but you should specify your `ContentDisposition` and `DataFieldName`

* get a instance of UploadManager
```
    UploadManager uploadManager = UploadManager.getUploadManger(context);
```

* upload
```
    UploadManager.Request request = new UploadManager.Request(BoxUtils.UPLOAD_URI, context)
    request.setFileUri(uri)
          .setDataFieldName("file")
          .setMimeType(info.mimeType)
          .addContentDisposition(name1, value1)
          .addContentDisposition(name2, value2)
          .addUserAgent(yourUA);
          // and much more options!
        // return a upload id and you can find the upload task
    long uploadId = uploadManger.enqueue(request);
```

* cancel or delete a upload task
```
    uploadManger.remove(uploadId);
```

* get the uri to a upload task
```
    uploadManger.getUriForUploadedFile(uploadId);
```

* restart a upload task
```
    uploadManger.restartUpload(uploadId);
```

* query a upload task
```
    UploadManager.Query query = new UploadManager.Query().setFilterById(id);
    Cursor cursor = UploadManager.getUploadManger(context).query(query);
    // you can get title, file uri, total bytes, current uploaded bytes etc.
    // Please view columns in UploadManager source!
```
------

## proguard

This project already add `consumerProguardFiles`, so feel free to use proguard.

## how to get a binary


Sorry but at this moment you have to build one from this source,
I will build one as soon as API is stable.

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
