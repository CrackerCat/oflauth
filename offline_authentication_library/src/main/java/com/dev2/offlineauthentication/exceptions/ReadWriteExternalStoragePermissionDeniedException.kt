package com.dev2.offlineauthentication.exceptions

class ReadWriteExternalStoragePermissionDeniedException :
    RuntimeException("android.permission.WRITE_EXTERNAL_STORAGE or android.permission.READ_EXTERNAL_STORAGE permission denied!")