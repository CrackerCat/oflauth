package com.dev2.offlineauthentication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Base64
import com.dev2.offlineauthentication.bean.AuthorizeCodeBean
import com.dev2.offlineauthentication.bean.Status
import com.dev2.offlineauthentication.exceptions.IdentityNumberNullException
import com.dev2.offlineauthentication.exceptions.ReadPhoneStatePermissionDeniedException
import com.dev2.offlineauthentication.exceptions.ReadWriteExternalStoragePermissionDeniedException
import com.dev2.offlineauthentication.uitls.AppUtil
import com.google.gson.Gson
import com.google.gson.JsonElement
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.TreeMap


class OfflineAuthentication internal constructor(
    builder: Builder
) {
    private val useImei: Boolean = builder.useImei
    private val identityNumberGenerator: IIdentityNumberGenerator? = builder.imeiGenerator
    private val useExternalStorage: Boolean = builder.useExternalStorage
    val parser: IAuthorizeCodeBeanParser = builder.parser
    val identityNumber: String
        @SuppressLint("HardwareIds", "MissingPermission") get() {
            return if (useImei && identityNumberGenerator != null) {
                val it = identityNumberGenerator.generate()
                if (it.isEmpty()) {
                    throw IdentityNumberNullException();
                }
                it
            } else if (useImei && Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                val it: String
                if (AppUtil.hasPermission(
                        AppUtil.getApplication(), Manifest.permission.READ_PHONE_STATE
                    ) || AppUtil.hasPermission(
                        AppUtil.getApplication(), "android.permission.READ_PRIVILEGED_PHONE_STATE"
                    )
                ) {
                    val tm: TelephonyManager =
                        AppUtil.getApplication().getSystemService(TelephonyManager::class.java)
                    it = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        tm.imei
                    } else {
                        tm.deviceId
                    }
                } else {
                    throw ReadPhoneStatePermissionDeniedException()
                }
                if (it.isEmpty()) {
                    throw IdentityNumberNullException();
                }
                it
            } else {
                Settings.Secure.getString(
                    AppUtil.getApplication().contentResolver, Settings.Secure.ANDROID_ID
                )
            }
        }
    private val dir: File =
        File(Environment.getExternalStorageDirectory(), ".${AppUtil.getPackageName()}")

    private val authorizeCodeBean: AuthorizeCodeBean?
        get() {
            val content: String? = readFromFile()
            return if (content.isNullOrEmpty()) {
                null
            } else {
                this.parser.deserialization(content)
            }
        }

    private fun getLocalCacheFile(): File {
        return if (this.useExternalStorage) {
            if (AppUtil.hasPermission(
                    AppUtil.getApplication(), Manifest.permission.READ_EXTERNAL_STORAGE
                )
            ) {
                File(dir, ".authorize_code_data")
            } else {
                throw ReadWriteExternalStoragePermissionDeniedException()
            }
        } else {
            File(AppUtil.getApplication().externalCacheDir, ".authorize_code_data")
        }
    }

    private fun getLocalCacheFileEnsureParentExists(): File {
        val f = this.getLocalCacheFile()
        if (f.parentFile?.exists() != true) {
            f.mkdirs()
        }
        return f
    }

    private fun readFromFile(): String? {
        val f = getLocalCacheFileEnsureParentExists()
        if (!f.exists()) {
            return null
        }
        var fileReader: FileReader? = null
        var reader: BufferedReader? = null
        try {
            fileReader = FileReader(f)
            reader = BufferedReader(fileReader)
            val stringBuilder = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
            }
            fileReader.close()
            return stringBuilder.toString()
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        } finally {
            reader?.close()
            fileReader?.close()
        }
    }

    private fun writeToFile(bean: AuthorizeCodeBean) {
        val f = getLocalCacheFileEnsureParentExists()
        if (f.exists()) {
            f.delete()
        }
        val content: String = this.parser.serialize(bean)
        var fileWriter: FileWriter? = null
        var writer: BufferedWriter? = null
        try {
            fileWriter = FileWriter(f)
            writer = BufferedWriter(fileWriter)
            writer.write(content)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            writer?.close()
            fileWriter?.close()
        }
    }

    fun verify(): Status {
        return this.verify(this.authorizeCodeBean)
    }

    fun verify(data: AuthorizeCodeBean?): Status {
        return if (data == null) {
            Status.AUTHORIZE_CODE_EMPTY
        } else if (!verifySignature(data)) {
            Status.ILLEGAL_AUTHORIZATION_CODE
        } else {
            writeToFile(data)
            Status.PASS
        }
    }

    fun deleteLocalAuthorizeCode(): Unit {
        val f = getLocalCacheFileEnsureParentExists()
        if (f.exists()) {
            f.delete()
        }
    }

    @Throws(java.lang.Exception::class)
    private fun verifySignature(data: AuthorizeCodeBean): Boolean {
        val signature = Base64.decode(data.signature, Base64.NO_WRAP)
        val publicKey = getPublicKey(AppUtil.getApplication())
        val verifier: java.security.Signature = java.security.Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        val validationData: AuthorizeCodeBean = data.copy()
        validationData.signature = ""
        val md5: String = md5(this.parser.serialize(validationData))
        verifier.update(md5.toByteArray(StandardCharsets.UTF_8))
        return verifier.verify(signature)
    }

    fun signString(data: String, privateKey: PrivateKey?): String? {
        return try {
            val signature: java.security.Signature =
                java.security.Signature.getInstance("SHA256withRSA")
            signature.initSign(privateKey)
            signature.update(data.toByteArray())
            val signatureBytes: ByteArray = signature.sign()
            Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val byteArray = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        val hexChars = CharArray(byteArray.size * 2)

        for (i in byteArray.indices) {
            val v = byteArray[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789abcdef"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789abcdef"[v and 0x0F]
        }

        return String(hexChars)
    }

    private fun getPublicKey(context: Context): PublicKey? {
        try {
            val packageInfo: PackageInfo = context.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_SIGNATURES
            )
            val signatures: Array<android.content.pm.Signature> = packageInfo.signatures
            for (signature in signatures) {
                val signatureBytes: ByteArray = signature.toByteArray()
                val certFactory = CertificateFactory.getInstance("X.509")
                val x509Cert: X509Certificate =
                    certFactory.generateCertificate(ByteArrayInputStream(signatureBytes)) as X509Certificate
                return x509Cert.publicKey
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        } catch (e: CertificateException) {
            e.printStackTrace()
        }
        return null
    }

    class Builder constructor() {
        internal var useImei: Boolean = false
        internal var imeiGenerator: IIdentityNumberGenerator? = null
        internal var useExternalStorage: Boolean = false
        internal var parser: IAuthorizeCodeBeanParser = DefaultAuthorizeCodeBeanParser()

        /**
         * 是否使用 IMEI 作为设备唯一标识。
         * @param use true 必须在 AndroidManifest.xml文件中配置
         * <uses-permission android:name="android.permission.READ_PHONE_STATE" /> 权限或
         * <uses-permission android:name="android.permission.READ_PRIVILEGED_PHONE_STATE" />权限。
         */
        fun useImei(use: Boolean) = apply {
            this.useImei = use
        }

        /**
         * 添加 IMEI 生成接口，用户适配厂家定制接口获取当前设备 IMEI。
         * @param generator
         */
        fun addImeiGenerator(generator: IIdentityNumberGenerator?) = apply {
            this.imeiGenerator = generator
        }

        /**
         * 是否使用扩展存储保存授权码，默认不使用。开启后，SDK 使用非默认扩展存储路经保存授权码，可在客户端卸载/清除数
         * 据后，继续使用之前获取的授权吗，减少因频繁获取造成使用上的不便。使用此功能必须在 AndroidManifest.xml文件
         * 中配置<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />和
         * <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />权限。
         *
         * @param use ture 使用，否则不使用。
         */
        fun useExternalStorage(use: Boolean) = apply {
            this.useExternalStorage = use
        }

        /**
         * AuthorizeCodeBean 对象默认使用Gson格式序列化/反序列化。如需使用 csv,xml等格式，或Gson库依赖有冲突，请
         * 自行实现IAuthorizeCodeBeanParser接口并配置解析器。
         * 若没有自定义解析器，请在编译脚本中依赖 com.google.code.gson 库。
         */
        fun addAuthorizeCodeBeanParser(parser: IAuthorizeCodeBeanParser) = apply {
            this.parser = parser
        }

        fun build(): OfflineAuthentication = OfflineAuthentication(this)
    }

    class DefaultAuthorizeCodeBeanParser : IAuthorizeCodeBeanParser {
        override fun deserialization(data: String): AuthorizeCodeBean {
            return try {
                Gson().fromJson(data, AuthorizeCodeBean::class.java)
            } catch (e: Throwable) {
                e.printStackTrace()
                AuthorizeCodeBean("", -1, -1, "", -1)
            }
        }

        override fun serialize(bean: AuthorizeCodeBean): String {
            return try {
                Gson().toJson(bean)
            } catch (e: Throwable) {
                e.printStackTrace()
                ""
            }
        }

        private fun sort(e: JsonElement) {
            if (e.isJsonNull) {
                return
            }
            if (e.isJsonPrimitive) {
                return
            }
            if (e.isJsonArray) {
                val a = e.asJsonArray
                val it: Iterator<JsonElement> = a.iterator()
                while (it.hasNext()) {
                    sort(it.next())
                }
                return
            }
            if (e.isJsonObject) {
                val tm: MutableMap<String, JsonElement> =
                    TreeMap<String, JsonElement> { o1, o2 -> o1!!.compareTo(o2!!) }
                for ((key, value) in e.asJsonObject.entrySet()) {
                    tm[key] = value
                }
                for ((key, value) in tm) {
                    e.asJsonObject.remove(key)
                    e.asJsonObject.add(key, value)
                    sort(value)
                }
                return
            }
        }
    }
}