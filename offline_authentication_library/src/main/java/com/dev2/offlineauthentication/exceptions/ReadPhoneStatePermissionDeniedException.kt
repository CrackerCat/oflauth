package com.dev2.offlineauthentication.exceptions

class ReadPhoneStatePermissionDeniedException :
    RuntimeException("Couldn't get imei because \"android.permission.READ_PHONE_STATE\" permission and \"android.permission.READ_PRIVILEGED_PHONE_STATE\" both denied! if you can't generate this permission, the useImei parameter of OfflineAuthentication\$Builder must not be true!")