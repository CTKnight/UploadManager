# UploadManager (Alpha)
A Multipart upload solution, including notification and network change.
This is a project based on AOSP DownloadManager, I implement this using Okhttp3.

------

## Usage: Much like AOSP's DownloadManger, but you should specify your `ContentDisposition` and `DataFieldName`
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
    long id = UploadManager.getUploadManger(context.getApplicationContext()).enqueue(request);
```

* cancel or delete a upload task
```
    UploadManager.getUploadManger(context.getApplicationContext()).remove(uploadId);
```
* get the uri to a upload task
```
    UploadManager.getUploadManger(context.getApplicationContext()).getUriForUploadedFile(uploadId);
```
* restart a upload task
```
    UploadManager.getUploadManger(context.getApplicationContext()).restartUpload（uploadid);
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

````
