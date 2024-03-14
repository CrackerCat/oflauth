package com.dev2.offline_authentication_consumer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dev2.offlineauthentication.OfflineAuthentication
import com.dev2.offlineauthentication.bean.Status
import com.tbruyelle.rxpermissions2.Permission
import com.tbruyelle.rxpermissions2.RxPermissions
import com.xuexiang.xqrcode.XQRCode
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer

val offlineAuthentication: OfflineAuthentication by lazy {
    OfflineAuthentication.Builder().useExternalStorage(true)
        .useImei(Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q).build()
}

private var status by mutableStateOf(Status.ILLEGAL_AUTHORIZATION_CODE)
private var id by mutableStateOf("123456")

class MainActivity : AppCompatActivity() {
    private val rxPermissions: RxPermissions by lazy {
        RxPermissions(this)
    }

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
            ) {
                Greeting(
                    context = this, id = id, s = status
                )
            }
        }
        getRequestPermissionsObserver().subscribe(requestPermissionsConsumer {
            id = offlineAuthentication.identityNumber
            status = offlineAuthentication.verify()
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        //处理二维码扫描结果
        if (requestCode == 123 && resultCode == RESULT_OK) {
            //处理扫描结果（在界面上显示）
            handleScanResult(data);
        }
    }

    private fun getRequestPermissionsObserver(): Observable<Permission> {
        val permissions: Array<String> =
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_PHONE_STATE
            ) else arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
        return Observable.create { emitter -> emitter.onNext(Unit) }
            .subscribeOn(AndroidSchedulers.mainThread()).compose(
                rxPermissions.ensureEach(*permissions)
            )
    }

    private fun requestPermissionsConsumer(onPermissionGranted: () -> Unit): Consumer<Permission> {
        return Consumer<Permission> {
            if (it.granted) {
                onPermissionGranted()
            } else if (it.shouldShowRequestPermissionRationale) {
                // Denied permission without ask never again
                Toast.makeText(
                    this, "Denied permission without ask never again", Toast.LENGTH_SHORT
                ).show();
            } else {
                // Denied permission with ask never again
                // Need to go to the settings
                Toast.makeText(
                    this, "Permission denied, can't work!", Toast.LENGTH_SHORT
                ).show();
            }
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
                        getRequestPermissionsObserver().subscribe(requestPermissionsConsumer {
                            status = offlineAuthentication.verify(
                                offlineAuthentication.parser.deserialization(result)
                            )
                        })
                        Log.i("wptest", "status: $status")
                    } else {
                        Log.i("wptest", "qrcode result error!")
                    }
                } else if (bundle.getInt(XQRCode.RESULT_TYPE) == XQRCode.RESULT_FAILED) {
                    Toast.makeText(this@MainActivity, "解析二维码失败", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@Composable
fun Greeting(
    modifier: Modifier = Modifier,
    id: String = "123456",
    s: Status = Status.AUTHORIZE_CODE_EMPTY,
    context: Context? = null
) {
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxHeight()
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "status: $s")
            Text(
                text = "identityNumber: $id", modifier = modifier
            )

            Text(text = "identityNumber qrcode:", modifier = Modifier.padding(0.dp, 10.dp))
            Image(
                bitmap = XQRCode.newQRCodeBuilder(id).setSize(800).setWhiteMargin(true).build()
                    .asImageBitmap(),
                contentDescription = "QrCode",
            )
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                XQRCode.startScan(context as Activity, 123);
            }) {
                Text(text = "扫码验证")
            }

            Button(onClick = {
                offlineAuthentication.deleteLocalAuthorizeCode()
                status = offlineAuthentication.verify()
            }) {
                Text(text = "清空验证码")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Greeting()
}