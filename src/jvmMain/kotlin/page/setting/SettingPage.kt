package page.setting

import adbInfoFlow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import ext.covertStr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import localCache
import localConfigFilename
import localConfigPath
import page.common.chooseFileItem
import page.common.subtitleText
import page.common.titleText
import signFlow
import utils.adb.AdbInfo
import utils.adb.AdbProcess
import utils.FileUtil
import utils.ProcessManager
import utils.sign.KeyInfo
import utils.sign.SignInfo
import utils.sign.SignProcess

@Composable
fun SettingScreen() {
    val coroutine = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxSize()
            .padding(end = 12.dp)
    ) {
        adbView(coroutine)
        signView(coroutine)
    }
}

@Composable
fun adbView(coroutine: CoroutineScope) {
    val adbInfo = adbInfoFlow.collectAsState(initial = AdbInfo()).value
    var path by remember(adbInfo) { mutableStateOf(adbInfo.path) }
    Column {
        titleText(
            "ADB",
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp)
        )
        chooseFileItem(
            openFolder = false,
            tips = "ADB路径",
            path = path,
            filename = "",
            onPathChanged = {
                path = it
                if (it.isNotEmpty()) {
                    val newInfo = AdbInfo(path = it)
                    adbInfoFlow.tryEmit(newInfo)
                    AdbProcess.adbPath = it
                    localCache = localCache.copy(adb = newInfo)
                    coroutine.launch { FileUtil.write(localCache.covertStr(), localConfigPath, localConfigFilename) }
                }
            },
            onValueChanged = {},
            modifier = Modifier
                .fillMaxWidth()
        )
    }
}

@Composable
fun signView(coroutine: CoroutineScope) {
    val signInfo = signFlow.collectAsState(initial = SignInfo()).value
    var addKeyDialog by remember { mutableStateOf(false) }
    var createKeyDialog by remember { mutableStateOf(false) }
    var zipalignPath by remember(signInfo) { mutableStateOf(signInfo.zipalignPath) }
    var apksignerPath by remember(signInfo) { mutableStateOf(signInfo.apksignerPath) }
    Column {
        titleText(
            "SIGN",
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp)
        )
        chooseFileItem(
            openFolder = false,
            tips = "Zipalign路径",
            path = zipalignPath,
            filename = "",
            onPathChanged = {
                if (it.isNotEmpty() && !it.contains("null")) {
                    zipalignPath = it
                    val newInfo = signInfo.copy(zipalignPath = zipalignPath)
                    signFlow.tryEmit(newInfo)
                    SignProcess.zipalignPath = zipalignPath
                    val lastInfo = localCache.sign.copy(zipalignPath = zipalignPath)
                    localCache = localCache.copy(sign = lastInfo)
                    coroutine.launch { FileUtil.write(localCache.covertStr(), localConfigPath, localConfigFilename) }
                }
            },
            onValueChanged = {},
            modifier = Modifier
                .fillMaxWidth()
        )
        chooseFileItem(
            openFolder = false,
            tips = "Apksigner路径",
            path = apksignerPath,
            filename = "",
            onPathChanged = {
                if (it.isNotEmpty() && !it.contains("null")) {
                    apksignerPath = it
                    val newInfo = signInfo.copy(apksignerPath = apksignerPath)
                    signFlow.tryEmit(newInfo)
                    SignProcess.apksignerPath = apksignerPath
                    val lastInfo = localCache.sign.copy(apksignerPath = apksignerPath)
                    localCache = localCache.copy(sign = lastInfo)
                    coroutine.launch { FileUtil.write(localCache.covertStr(), localConfigPath, localConfigFilename) }
                }
            },
            onValueChanged = {},
            modifier = Modifier
                .fillMaxWidth()
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                buildAnnotatedString {
                    append("共存在 ")
                    withStyle(SpanStyle(color = Color.Blue)) {
                        append("${signInfo.keys.size}")
                    }
                    append(" 个签名文件")
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            ClickableText(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline)) {
                        append("选择已存在签名")
                    }
                }
            ) {
                addKeyDialog = true
            }
            Spacer(modifier = Modifier.width(12.dp))
            ClickableText(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline)) {
                        append("创建新签名")
                    }
                }
            ) {
                createKeyDialog = true
            }
        }
        signInfo.keys.forEach { key ->
            Text(
                buildAnnotatedString {
                    append("签名: ")
                    withStyle(SpanStyle(color = Color.Blue)) {
                        append(key.tag)
                    }
                }
            )
        }
    }
    if (addKeyDialog) {
        addNewKeyDialog(coroutine) { addKeyDialog = false }
    }
    if (createKeyDialog) {
        createKeyDialog(coroutine) { createKeyDialog = false }
    }
}

@Composable
private fun createKeyDialog(
    coroutine: CoroutineScope,
    onClose: () -> Unit,
) {
    var keyName by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var storePwd by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var keyPwd by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    Dialog(
        state = rememberDialogState(position = WindowPosition(Alignment.Center)),
        onCloseRequest = { onClose() },
        title = "Create New Sign"
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            subtitleText("文件名")
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = keyName,
                    onValueChange = {
                        keyName = it
                    },
                    label = {
                        Text("文件名")
                    }
                )
                Text(".jks")
            }
            subtitleText("路径")
            Text(path.ifEmpty { "未选择" })
            Button(
                onClick = {
                    val mPath = FileUtil.openCommonFolderDialog()
                    if (!mPath.contains("null")) {
                        path = mPath
                    }
                }
            ) {
                Text("选择")
            }
            subtitleText("store pwd")
            OutlinedTextField(
                value = storePwd,
                onValueChange = {
                    storePwd = it
                }
            )
            subtitleText("key pwd")
            OutlinedTextField(
                value = alias,
                onValueChange = {
                    alias = it
                }
            )
            subtitleText("alias")
            OutlinedTextField(
                value = keyPwd,
                onValueChange = {
                    keyPwd = it
                }
            )
            Text(
                err,
                color = Color.Red,
            )
            Button(
                onClick = {
                    coroutine.launch {
                        val result = ProcessManager.signHelper.createJks(
                            path + keyName,
                            storePwd,
                            alias,
                            keyPwd
                        )
                        println(result)
                        if (result.exitCode != 0) {
                            err = result.stderr
                        } else {
                            onClose()
                        }
                    }
                }
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun addNewKeyDialog(
    coroutine: CoroutineScope,
    onClose: () -> Unit
) {
    var tag by remember { mutableStateOf("") }
    var keyStore by remember { mutableStateOf("") }
    var keyStorePwd by remember { mutableStateOf("") }
    var keyAlias by remember { mutableStateOf("") }
    var keyPwd by remember { mutableStateOf("") }
    Dialog(
        state = rememberDialogState(position = WindowPosition(Alignment.Center)),
        onCloseRequest = { onClose() },
        title = "Sign Config"
    ) {
        Column {
            OutlinedTextField(
                label = { Text("起个名字吧") },
                value = tag,
                onValueChange = {
                    tag = it
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                label = { Text("key store") },
                value = keyStore,
                onValueChange = {
                    keyStore = it
                }
            )
            Button(
                onClick = {
                    coroutine.launch {
                        val file = FileUtil.openCommonFileDialog()
                        keyStore = file
                    }
                }
            ) {
                Text("choose")
            }
            OutlinedTextField(
                label = { Text("key store pwd") },
                value = keyStorePwd,
                onValueChange = {
                    keyStorePwd = it
                }
            )
            OutlinedTextField(
                label = { Text("key alias") },
                value = keyAlias,
                onValueChange = {
                    keyAlias = it
                }
            )
            OutlinedTextField(
                label = { Text("key pwd") },
                value = keyPwd,
                onValueChange = {
                    keyPwd = it
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val keyInfo = KeyInfo(
                        tag = tag,
                        jksPath = keyStore,
                        jksKeyStorePwd = keyStorePwd,
                        jksKeyAlias = keyAlias,
                        jksKeyPwd = keyPwd
                    )
                    val cacheKey = localCache.sign.keys
                    val newKeys = mutableListOf<KeyInfo>().apply {
                        addAll(cacheKey)
                        add(keyInfo)
                    }
                    val newSign = localCache.sign.copy(keys = newKeys)
                    localCache = localCache.copy(
                        sign = newSign
                    )
                    signFlow.tryEmit(localCache.sign)
                    coroutine.launch { FileUtil.write(localCache.covertStr(), localConfigPath, localConfigFilename) }
                    onClose()
                }
            ) {
                Text("save")
            }
        }
    }
}
