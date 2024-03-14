package com.dev2.admin_sample

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.SimpleStorageHelper
import com.dev2.offlineauthentication.OfflineAuthentication
import com.dev2.offlineauthentication.bean.AuthorizeCodeBean
import com.google.gson.Gson
import com.tbruyelle.rxpermissions2.RxPermissions
import com.xuexiang.xqrcode.XQRCode
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import java.io.FileInputStream
import java.io.InputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec

private var privateKey: PrivateKey? by mutableStateOf(null)
private var id: String by mutableStateOf("")
private var qrCode: ImageBitmap by mutableStateOf(
    XQRCode.newQRCodeBuilder("123").setDataDotScale(1f).setSize(1400).setWhiteMargin(true).build()
        .asImageBitmap()
)
val offlineAuthentication: OfflineAuthentication by lazy {
    OfflineAuthentication.Builder().build()
}

class MainActivity : AppCompatActivity() {
    private val rxPermissions: RxPermissions by lazy {
        RxPermissions(this)
    }
    private lateinit var storageHelper: SimpleStorageHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storageHelper = SimpleStorageHelper(this)
        setContent {
            Greeting(rxPermissions, storageHelper, this)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        //处理二维码扫描结果
        if (requestCode == 123 && resultCode == RESULT_OK) {
            //处理扫描结果（在界面上显示）
            handleScanResult(data);
        }
    }

    @SuppressLint("CheckResult")
    private fun handleScanResult(data: Intent?) {
        if (data != null) {
            val bundle = data.extras
            if (bundle != null) {
                if (bundle.getInt(XQRCode.RESULT_TYPE) == XQRCode.RESULT_SUCCESS) {
                    val result = bundle.getString(XQRCode.RESULT_DATA)
                    Toast.makeText(this@MainActivity, "解析结果:$result", Toast.LENGTH_LONG).show()
                    if (!result.isNullOrEmpty()) {
                        id = result
                    }
                } else if (bundle.getInt(XQRCode.RESULT_TYPE) == XQRCode.RESULT_FAILED) {
                    Toast.makeText(this@MainActivity, "解析二维码失败", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@SuppressLint("CheckResult")
fun selectFile(
    rxPermissions: RxPermissions,
    storageHelper: SimpleStorageHelper,
    activity: AppCompatActivity,
    onSelected: ((doc: DocumentFile) -> Unit)?
) {
    Observable.create { emitter -> emitter.onNext(Unit) }
        .subscribeOn(AndroidSchedulers.mainThread())
        .compose(rxPermissions.ensureEach(Manifest.permission.READ_EXTERNAL_STORAGE)).subscribe {
            if (it.granted) {
                storageHelper.onFileSelected = { _, files ->
                    onSelected?.let { it1 -> it1(files.first()) }
                }
                storageHelper.openFilePicker(3, true)
            } else if (it.shouldShowRequestPermissionRationale) {
                // Denied permission without ask never again
                Toast.makeText(
                    activity, "Denied permission without ask never again", Toast.LENGTH_SHORT
                ).show();
            } else {
                // Denied permission with ask never again
                // Need to go to the settings
                Toast.makeText(
                    activity, "Permission denied, can't enable the camera", Toast.LENGTH_SHORT
                ).show();
            }
        }
}

fun getPrivateKeyFromDocumentFile(activity: AppCompatActivity, df: DocumentFile): PrivateKey? {
    var pfd: ParcelFileDescriptor? = null
    var inputStream: InputStream? = null
    try {
        pfd = activity.contentResolver.openFileDescriptor(df.uri, "r")
        if (pfd != null) {
            inputStream = FileInputStream(pfd.fileDescriptor)
            val keyBytes = ByteArray(inputStream.available())
            inputStream.read(keyBytes)
            val spec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            return keyFactory.generatePrivate(spec)
        }
    } catch (e: Throwable) {
        e.printStackTrace()
    } finally {
        pfd?.close()
        inputStream?.close()
    }
    return null
}

fun generateAuthenticationQrCode() {
    if (privateKey == null) {
        return
    }
    if (id.isEmpty()) {
        return
    }
    val b = AuthorizeCodeBean(id, -1, -1, "", -1)
    val m = offlineAuthentication.md5(offlineAuthentication.parser.serialize(b))
    b.signature = offlineAuthentication.signString(m, privateKey).toString()
    qrCode = XQRCode.newQRCodeBuilder(Gson().toJson(b)).setDataDotScale(1f).setSize(1400)
        .setWhiteMargin(true).build().asImageBitmap()
}

@Composable
fun Greeting(
    rxPermissions: RxPermissions? = null,
    storageHelper: SimpleStorageHelper? = null,
    activity: AppCompatActivity? = null
) {
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(10.dp)
    ) {
        Column {
            ListItemSample(if (privateKey == null) "请选择私钥" else "已选择私钥") {
                selectFile(rxPermissions!!, storageHelper!!, activity!!) {
                    privateKey = getPrivateKeyFromDocumentFile(activity, it)
                }
            }

            ListItemSample(id.ifEmpty { "扫码获取identityNumber" }, "扫码") {
                XQRCode.startScan(activity, 123);
            }

            Image(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp, 10.dp),
                bitmap = qrCode,
                contentDescription = "QrCode",
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = { generateAuthenticationQrCode() }) {
                Text(text = "生成授权码")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Greeting()
}

@Composable
fun ListItemSample(text: String, bt: String = "选择", onSelectBtnClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp, 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            maxLines = 1,
            modifier = Modifier
                .weight(3f)
                .padding(0.dp, 0.dp, 4.dp, 0.dp),
            text = text
        )
        Button(modifier = Modifier.weight(1f), onClick = {
            onSelectBtnClick()
        }) {
            Text(text = bt)
        }
    }
}