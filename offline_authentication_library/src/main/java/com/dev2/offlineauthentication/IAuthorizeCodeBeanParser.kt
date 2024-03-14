package com.dev2.offlineauthentication

import com.dev2.offlineauthentication.bean.AuthorizeCodeBean

interface IAuthorizeCodeBeanParser {
    fun deserialization(data: String): AuthorizeCodeBean
    fun serialize(bean: AuthorizeCodeBean): String
}