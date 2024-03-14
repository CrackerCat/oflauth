package com.dev2.offlineauthentication.bean

data class AuthorizeCodeBean(
    val identityNumber: String,
    val authorizationDate: Number,
    val deadline: Number,
    var signature: String,
    val duration: Number,
)