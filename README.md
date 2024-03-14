一套简单的离线（内网/无后台/纯单机）验证方法。consumer_sample用于演示校验认证码过程，admin_sampe用于演示管理员生成验证码。

[![](https://jitpack.io/v/wpvsyou/oflauth.svg)](https://jitpack.io/#wpvsyou/oflauth)
![Static Badge](https://img.shields.io/badge/License-MIT-blue?link=https%3A%2F%2Fgithub.com%2Fwpvsyou%2Foflauth%2Fblob%2Fmain%2FLICENSE)

### 项目结构：
├── LICENSE
├── README.md
├── admin_sample 生成授权码客户端
├── asserts 演示附件
├── build.gradle.kts
├── consumer_sample 验证授权码客户端
├── gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
├── jitpack.yml
├── local.properties
├── mykeystore 客户端签名&演示用的 keystore 文件密码 123456
├── offline_authentication_library SDK 源码
└── settings.gradle.kts

### asserts 附件说明：
├── AuthorizeCodeQrCode.png 生成授权码二维码图片
├── demo.json json配置
├── keystore.p12 从 mykeystore 中导出的 p12 文件
├── private_key.pem 从 p12 文件中导出的私钥
└── signature.bin 签名信息

### 一. 背景：
当客户端在某些情况下需要离线给用户授权，或某些功能需要提前植入（硬编码到 Apk 中），但这些功能又仅想部分设备可用。或部分设备单次/某时间段儿可用（P1 阶段仅提供部分设备可用能力）。又或者 Apk 公测体验时，测试版本只针对部分设备开放授权。但 Apk发布出去后，谁都可以安装，无法控制安装。又不想为了简单的授权逻辑搭建一套复杂/昂贵的后台服务器进行注册/认证。那么此时就需要有一套技术/工具，能够实现 Android Apk 在离线的时候，也能验证设备的合法性（P1 阶段）以及有效次数和有效时间（P2 阶段）
 注：这里说的离线，是指 Apk 从安装使用开始就不需要调用任何网络接口，纯本地 Apk。
### 二. 原理：
使用非对称加密，利用Android Apk 在打包签名时的 keystore 中的 RSA 密钥对中的私钥，进行签名。再使用 Apk 从自身获取到应用签名的公钥进行验签。验签通过即验证成功！
在这过程中，需要解决签名原材料唯一的问题。本工具使用 Android 手机的 ANDOIRD_ID 作为客户端唯一标识（恢复出厂设置才变）并提供了适配接口，可由开发者自由指定唯一标识。比如 IMEI/SN 等（老版本需要有 READ_PHONE_STATUS 权限，新版本 A10 以后需要有 android.permission.READ_PRIVILEGED_PHONE_STATE 权限或 DeviceOwner / ProfileOwner 权限）或其他唯一标识（有厂家接口或 SDK能够生成唯一标识）。
有了签名原材料后，客户端就可以将签名原材料交给管理员（开发者），让开发者使用私钥对原材料进行签名。因私钥不外泄，所以理论上只有管理员（开发者）有签名权限。
之后客户端使用自身公钥进行验签（因为客户端 Apk 是使用管理员/开发者自己创建的 keystore 文件签名的，所以私钥在管理员/开发者手中）并校验原材料是否正确来达到验证的目的。
### 三. 客户端使用过程简介：

1. 导入依赖：
```kotlin
//1. 在根目录settings.gradle.kt或build.gradle.kt中增加jitpack.io maven库配置
dependencyResolutionManagement {
		repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
		repositories {
			mavenCentral()
			maven { url 'https://jitpack.io' }
		}
	}

//2. 在具体项目中依赖oflauth aar包
dependencies {
	        implementation 'com.github.wpvsyou:oflauth:v1.0.1'
	}
```

2. 客户端通过 SDK 中 verify 接口来验证本地本地验证码是否正确
```kotlin
val offlineAuthentication: OfflineAuthentication by lazy {
    OfflineAuthentication.Builder().build()
}
val s: Status = offlineAuthentication.verify()
```

3. 其中 Status 为状态枚举

| No. | Status | 含义 |
| --- | --- | --- |
| 1 | AUTHORIZE_CODE_EMPTY | 没有认证码 |
| 2 | ILLEGAL_AUTHORIZATION_CODE | 本地存在非法的认证码，可能已过期（P2 阶段）或认证码错误 |
| 3 | PASS | 认证通过 |

4. 客户端上先通过 SDK 工具获取到客户端的唯一标识id：
```kotlin
val offlineAuthentication: OfflineAuthentication by lazy {
    OfflineAuthentication.Builder().build()
}
val id：String = offlineAuthentication.identityNumber
```

5. 将客户端上的 id 告知给管理员
6. 管理员使用 id 生成json配置文件（文件可从项目目录中下载），再对 json 文件的 md5 值进行签名。签名后将签名值(string字符串)进行 base64 编码，并将编码值添加到 json 配置文件中signature字段的值中。（该过程可参考“四. Linux/Macos命令行中，使用 KeyStore 文件生成离线验证码”章节的描述）
7. 最后将这个 json 配置文件的内容发给客户端用户验证。（推荐通过二维码传递）
```kotlin
//其中 result 为二维码解析后的 String 串。
val s = offlineAuthentication.verify(
    offlineAuthentication.parser.deserialization(result)
)
```
### 四. Linux/Macos 命令行中，使用 KeyStore 文件生成离线验证码：（通过Android应用生成授权码请看第五章）

1. 先将使用 keytool 命令将 keystore 转成 p12 文件。其中 app_key 是给 app 做签名的 keystore 文件（可由 AndroidStudio 生成，也可由命令行手动创建），keystore.p12 是将要生成的 p12 文件，key0 是 app_key keystore 中 RSA 密钥对的别名，是在创建 keystore 文件时指定的。当然还有 keystore 的密码，别名下密钥对的密码，这些都是在生成 keystore，生成密钥对时配置的。
```shell
keytool -importkeystore -srckeystore app_key -destkeystore keystore.p12 -deststoretype PKCS12 -srcalias key0
```

2. 再使用 openssl 工具，通过 p12 文件导出 RSA 密钥对中的私钥 private_key.pem 
```shell
openssl pkcs12 -in keystore.p12 -out private_key.pem -nocerts
```

3. 再打印之前准备好的 json 格式配置文件。（注意参数顺序不能错，因为 json 是一种无序的数据结构，若两端参数顺序对不齐，就会造成 md5 计算出来的结果不一致，导致认证失败！）demo.json 就是我本地根据设备 identityNumber （需要换成你自己设备的identityNumber）生成的 json 格式配置文件。
```shell
cat demo.json
{"authorizationDate":123456,"deadline":123456,"duration":123456,"identityNumber":"8ee0460b8d4731f9","signature":""}
```

   - 如果你使用 vi/vim 工具编辑 json 格式配置文件时，一定要确保结尾没有被编辑器自动增加结束符/换行符。否则你 PC 上 json 格式文件 bytes 会比 Android 客户端里的 json 串 bytes 多一个 0x0a ，导致 md5 不一致，进而认证失败！）
```shell
//可以通过 xxd 工具打印一下 PC 上 json 文件的 bytes 数据检查一下：
xxd -i demo.json
unsigned char demo_json[] = {
  0x7b, 0x22, 0x61, 0x75, 0x74, 0x68, 0x6f, 0x72, 0x69, 0x7a, 0x61, 0x74,
  0x69, 0x6f, 0x6e, 0x44, 0x61, 0x74, 0x65, 0x22, 0x3a, 0x31, 0x32, 0x33,
  0x34, 0x35, 0x36, 0x2c, 0x22, 0x64, 0x65, 0x61, 0x64, 0x6c, 0x69, 0x6e,
  0x65, 0x22, 0x3a, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x2c, 0x22, 0x64,
  0x75, 0x72, 0x61, 0x74, 0x69, 0x6f, 0x6e, 0x22, 0x3a, 0x31, 0x32, 0x33,
  0x34, 0x35, 0x36, 0x2c, 0x22, 0x69, 0x64, 0x65, 0x6e, 0x74, 0x69, 0x74,
  0x79, 0x4e, 0x75, 0x6d, 0x62, 0x65, 0x72, 0x22, 0x3a, 0x22, 0x38, 0x65,
  0x65, 0x30, 0x34, 0x36, 0x30, 0x62, 0x38, 0x64, 0x34, 0x37, 0x33, 0x31,
  0x66, 0x39, 0x22, 0x2c, 0x22, 0x73, 0x69, 0x67, 0x6e, 0x61, 0x74, 0x75,
  0x72, 0x65, 0x22, 0x3a, 0x22, 0x22, 0x7d, 0x0a
};
```
并与 Android 端上的 json 串 bytes 数据比较一下：
![image.png](https://cdn.nlark.com/yuque/0/2024/png/39006166/1709538563748-c4fbe385-87af-4585-a285-38ccba641800.png#averageHue=%232b2f34&clientId=uc730ba08-9743-4&from=paste&height=452&id=u47c71498&originHeight=452&originWidth=1230&originalType=binary&ratio=1&rotation=0&showTitle=false&size=42035&status=done&style=none&taskId=u3429c641-422a-40a9-b982-1478e6894b4&title=&width=1230)
通过对比发现，PC 上 json 格式配置文件比 Android 客户端上需要验证的 json 数据多了一个 0x0a。通	      过 BAIDU 发现，这个0x0a是 vi/vim自动创建的换行符/结束符。可在~/.vimrc 配置文件中添加如下配置
```shell
set noendofline binary
```
来取消 vi/vim 编辑器行尾的换行符/结束符。
配置完 vi/vim 配置文件后，需要 source 立即生效
```shell
source ~/.vimrc
```
最后再通过 vi/vim 重新创建一个 json 配置文件。再通过 xxd 检查 json 文件的 bytes 数组发现和 	      Android 端 json 字符串的 bytes 数据一致了。 
```shell
xxd -i demo.json
unsigned char demo_json[] = {
  0x7b, 0x22, 0x61, 0x75, 0x74, 0x68, 0x6f, 0x72, 0x69, 0x7a, 0x61, 0x74,
  0x69, 0x6f, 0x6e, 0x44, 0x61, 0x74, 0x65, 0x22, 0x3a, 0x31, 0x32, 0x33,
  0x34, 0x35, 0x36, 0x2c, 0x22, 0x64, 0x65, 0x61, 0x64, 0x6c, 0x69, 0x6e,
  0x65, 0x22, 0x3a, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x2c, 0x22, 0x64,
  0x75, 0x72, 0x61, 0x74, 0x69, 0x6f, 0x6e, 0x22, 0x3a, 0x31, 0x32, 0x33,
  0x34, 0x35, 0x36, 0x2c, 0x22, 0x69, 0x64, 0x65, 0x6e, 0x74, 0x69, 0x74,
  0x79, 0x4e, 0x75, 0x6d, 0x62, 0x65, 0x72, 0x22, 0x3a, 0x22, 0x38, 0x65,
  0x65, 0x30, 0x34, 0x36, 0x30, 0x62, 0x38, 0x64, 0x34, 0x37, 0x33, 0x31,
  0x66, 0x39, 0x22, 0x2c, 0x22, 0x73, 0x69, 0x67, 0x6e, 0x61, 0x74, 0x75,
  0x72, 0x65, 0x22, 0x3a, 0x22, 0x22, 0x7d
};
unsigned int demo_json_len = 115;
```

4.  再使用命令行中的 md5 工具计算当前 json 配置文件的 md5 值
```shell
cat demo.json| md5
ae944a1cb22c93bcd75504a5be18883d
```

5. 再使用第二步导出的私钥 private_key.pem 对 md5 值：ae944a1cb22c93bcd75504a5be18883d 进行签名并获取签名文件signature.bin
```shell
echo -n "ae944a1cb22c93bcd75504a5be18883d" | openssl dgst -sha256 -sign private_key.pem -out signature.bin
```

6. 获取签名值 base64
```shell
cat signature.bin| base64
JyJCIUc/jlKGCZvoenLqQa0yaXw1jtv60Vx0d3LjLFsI41gDEKtSbant5vvafzlHSGDVZbcW/hBLENW2UylGsOIo5ptO51ePUBxpT6wXpV3AFOyZdMlVq6lVwR0vtSm6ZahJ1jZqq8BY7cajIcjeTh8IFvZFcsJ5k86XEHE+yHQWfy6vcCgSnExIq0vR57DvED2iOUeJ7xFqWiBeEhxxihCYpUjMm3Dpl9KXJha76iLewbPrlgqP/VYSHi1FwvxosQmlCOZVr50xOsBWY4DOZcIyFeAQhRUnModyuS0M582XN+F+diU29yvVeaw3NDq1BEmnE52DGkI+0LABMbRrvA==
```

7. 将 base64 值添加到 json 配置文件的 "signature" 字段值，并获得如下配置文件。
```json
{
  "authorizationDate": 123456,
  "deadline": 123456,
  "duration": 123456,
  "identityNumber": "8ee0460b8d4731f9",
  "signature": "JyJCIUc/jlKGCZvoenLqQa0yaXw1jtv60Vx0d3LjLFsI41gDEKtSbant5vvafzlHSGDVZbcW/hBLENW2UylGsOIo5ptO51ePUBxpT6wXpV3AFOyZdMlVq6lVwR0vtSm6ZahJ1jZqq8BY7cajIcjeTh8IFvZFcsJ5k86XEHE+yHQWfy6vcCgSnExIq0vR57DvED2iOUeJ7xFqWiBeEhxxihCYpUjMm3Dpl9KXJha76iLewbPrlgqP/VYSHi1FwvxosQmlCOZVr50xOsBWY4DOZcIyFeAQhRUnModyuS0M582XN+F+diU29yvVeaw3NDq1BEmnE52DGkI+0LABMbRrvA=="
}
```

8. 最后，再通过命令行 qr 工具，将 json 配置文件转变为二维码图片 AuthorizeCodeQrCode.png，方便客户端扫码验证。
```json
cat demo.json | qr > AuthorizeCodeQrCode.png
```
 ![1.png](https://cdn.nlark.com/yuque/0/2024/png/39006166/1709539827875-29359674-4dc9-461d-929f-fc598baefe92.png#averageHue=%23939393&clientId=uc730ba08-9743-4&from=drop&id=u2984fd7a&originHeight=930&originWidth=930&originalType=binary&ratio=1&rotation=0&showTitle=false&size=3435&status=done&style=none&taskId=ue4bb4419-cbd1-46cd-bec4-69ad6fa0f34&title=)

### 五. 从客户端生成授权码二维码并交由客户端验证

1. 先将PEM格式私钥转成DER格式。

```shell
openssl rsa -in private_key.pem -outform DER -out private_key.der
```

2. 将 DER 格式的私钥 push 到手机中，并由程序加载至内存。

3. 用需要授权码授权的客户端生成的 identityNumber 去组装一个 AuthorizeCodeBean 对象并计算其 md5 值

```kotlin
val b = AuthorizeCodeBean(id, -1, -1, "", -1)
val m = offlineAuthentication.md5(offlineAuthentication.parser.serialize(b))
```

4. 通过 signString 接口，对 md5 进行签名

```kotlin
b.signature = offlineAuthentication.signString(m, privateKey).toString()
``` 

5. 最后将 AuthorizeCodeBean 对象序列化后传递给授权码授权的客户端进行验证即可。

### 六. DEMO:

1. 通过App演示授权码生成过程：

![1710410560868.gif](https://cdn.nlark.com/yuque/0/2024/gif/39006166/1710410628584-159eae0f-3196-4ea4-ac5e-a72ca7c24347.gif#averageHue=%23e3e2e2&clientId=u9bbc7acf-c467-4&from=drop&id=u8f73162a&originHeight=494&originWidth=240&originalType=binary&ratio=1&rotation=0&showTitle=false&size=5844375&status=done&style=none&taskId=u709e945e-612f-4edd-b18e-b9d05f86b37&title=)

2. 客户端验证授权码过程演示：

![1710410734519.gif](https://cdn.nlark.com/yuque/0/2024/gif/39006166/1710410773335-ce6e9dfc-8203-4de4-9843-ffc825720e66.gif#averageHue=%23bcbbbe&clientId=u9bbc7acf-c467-4&from=drop&id=u4532fc53&originHeight=426&originWidth=240&originalType=binary&ratio=1&rotation=0&showTitle=false&size=2299570&status=done&style=none&taskId=uf8b4746b-a07b-494b-98c1-ef72b4a2edb&title=)
