package com.dev2.offlineauthentication.bean

enum class Status(val n: String) {
    AUTHORIZE_CODE_EMPTY("授权码为空!"),
    ILLEGAL_AUTHORIZATION_CODE("非法授权码!"),
    PASS("验证通过!");

    override fun toString(): String {
        return this.n
    }

    fun isPass(): Boolean {
        return this == PASS
    }
}