package com.qrlife.android

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.camera.core.ImageProxy
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { QrLifeApp() }
    }

}

@Composable
private fun QrLifeApp() {
    val context = LocalContext.current
    var cameraAllowed by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { cameraAllowed = it }
    var board by remember { mutableStateOf<RustLife?>(null) }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }
    var previousCells by remember { mutableStateOf<List<Boolean>>(emptyList()) }
    var speed by remember { mutableStateOf(120f) }
    var seek by remember { mutableStateOf(0f) }
    var requestedWidth by remember { mutableStateOf(0f) }
    var requestedHeight by remember { mutableStateOf(0f) }
    var aliveColor by remember { mutableStateOf(Color(0xFF00C853)) }
    var deadColor by remember { mutableStateOf(Color.Black) }
    var bornColor by remember { mutableStateOf(Color.Transparent) }
    var diedColor by remember { mutableStateOf(Color.Transparent) }
    var background by remember { mutableStateOf<ImageBitmap?>(null) }
    var exportDialog by remember { mutableStateOf(false) }
    var helpDialog by remember { mutableStateOf(false) }
    var showSimulation by remember { mutableStateOf(false) }
    var exportStepsText by remember { mutableStateOf("30") }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/gif")
    ) { uri ->
        if (uri != null && board != null) {
            val steps = exportStepsText.toIntOrNull()?.coerceAtLeast(0) ?: 30
            val data = board!!.gif(steps, 8, aliveColor.toArgb(), deadColor.toArgb())
            context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
        }
    }
    val mp4Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri ->
        if (uri != null && board != null) {
            runCatching {
                val steps = exportStepsText.toIntOrNull()?.coerceAtLeast(0) ?: 30
                Mp4Exporter.export(context, uri, board!!, steps, 8, aliveColor.toArgb(), deadColor.toArgb())
            }.onFailure { error = "MP4出力に失敗しました: ${it.message}" }
        }
    }
    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use {
                background = BitmapFactory.decodeStream(it)?.asImageBitmap()
            }
        }
    }

    LaunchedEffect(running, speed, board) {
        while (running && board != null) {
            kotlinx.coroutines.delay(speed.toLong())
            previousCells = board?.cells ?: emptyList()
            board?.step()
            seek = board?.generation?.toFloat() ?: 0f
            revision++
        }
    }

    val loadImage: (ByteArray) -> Unit = { bytes ->
        board?.close()
        board = RustLife.createFromImage(bytes)
        previousCells = board?.cells ?: emptyList()
        revision++
        running = false
        error = if (board == null) "画像からQRコードを検出できませんでした" else null
    }
    val loadGray: (Int, Int, ByteArray) -> Unit = { width, height, bytes ->
        board?.close()
        board = RustLife.createFromGray(width, height, bytes)
        previousCells = board?.cells ?: emptyList()
        revision++
        running = false
        error = if (board == null) "カメラ画像からQRコードを検出できませんでした" else null
    }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) {
                error = "画像を読み込めませんでした"
            } else {
                loadImage(bytes)
            }
        }
    }

    MaterialTheme {
        if (!showSimulation) {
            HomeScreen(
                onStart = { showSimulation = true },
                onHelp = { helpDialog = true }
            )
        } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("QR Life", style = MaterialTheme.typography.headlineMedium)
                Text("QRコードを初期配置としてライフゲームを実行します。")
            }
            item {
                if (!cameraAllowed) {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("カメラを許可")
                    }
                } else if (board == null) {
                    CameraScanner(
                        onImage = { bytes, width, height -> loadGray(width, height, bytes) },
                        onError = { error = it }
                    )
                } else {
                    Text("QRコードを読み込みました。リセットまたは画像選択で別の盤面を読み込めます。")
                }
            }
            item {
                OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                    Text("画像から読み込む")
                }
            }
            item {
                board?.let { current ->
                    key(revision) {
                        BoardView(current, previousCells, aliveColor, deadColor, bornColor, diedColor, background)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Rustで実行中 / Step ${current.generation} / 盤面 ${current.width}×${current.height}")
                    Text("速度: ${speed.toInt()} ms")
                    Slider(value = speed, onValueChange = { speed = it }, valueRange = 30f..1000f)
                    Text("シーク: ${seek.toInt()}")
                    Slider(
                        value = seek,
                        onValueChange = {
                            seek = it
                            previousCells = current.cells
                            current.seek(it.toInt())
                            revision++
                        },
                        valueRange = 0f..300f
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { running = !running }) {
                            Text(if (running) "一時停止" else "再生")
                        }
                        OutlinedButton(onClick = {
                            current.resetInPlace()
                            previousCells = current.cells
                            seek = 0f
                            revision++
                            board = current
                            running = false
                        }) {
                            Text("リセット")
                        }
                    }
                    Text("盤面サイズ")
                    Row {
                        Slider(
                            value = requestedWidth.coerceAtLeast(current.width.toFloat()),
                            onValueChange = { requestedWidth = it },
                            valueRange = current.width.toFloat()..(current.width + 100).toFloat(),
                            modifier = Modifier.weight(1f)
                        )
                        Slider(
                            value = requestedHeight.coerceAtLeast(current.height.toFloat()),
                            onValueChange = { requestedHeight = it },
                            valueRange = current.height.toFloat()..(current.height + 100).toFloat(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedButton(onClick = {
                        val width = requestedWidth.toInt().coerceAtLeast(current.width)
                        val height = requestedHeight.toInt().coerceAtLeast(current.height)
                        if (current.resize(width, height)) {
                            previousCells = current.cells
                            revision++
                        }
                    }) { Text("サイズを適用") }
                    ColorControl("生存セル", aliveColor) { aliveColor = it }
                    ColorControl("死滅セル", deadColor) { deadColor = it }
                    ColorControl("誕生セル", bornColor) { bornColor = it }
                    ColorControl("死亡セル", diedColor) { diedColor = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { backgroundPicker.launch("image/*") }) {
                            Text("背景画像")
                        }
                        Button(onClick = { exportDialog = true }) { Text("出力") }
                        OutlinedButton(onClick = { helpDialog = true }) { Text("使い方") }
                        OutlinedButton(onClick = {
                            running = false
                            board?.close()
                            board = null
                            background = null
                            showSimulation = false
                        }) { Text("起動画面へ") }
                    }
                }
            }
            item { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
        }
        if (exportDialog) {
            AlertDialog(
                onDismissRequest = { exportDialog = false },
                title = { Text("シミュレーション出力") },
                text = {
                    Column {
                        Text("指定したステップ数をGIFまたはMP4として保存します。")
                        OutlinedTextField(
                            value = exportStepsText,
                            onValueChange = { value ->
                                if (value.all(Char::isDigit)) exportStepsText = value
                            },
                            label = { Text("出力ステップ数") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        exportDialog = false
                        exportLauncher.launch("qrlife.gif")
                    }) { Text("GIF") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            exportDialog = false
                            mp4Launcher.launch("qrlife.mp4")
                        }) { Text("MP4") }
                        TextButton(onClick = { exportDialog = false }) { Text("キャンセル") }
                    }
                }
            )
        }
        if (helpDialog) {
            AlertDialog(
                onDismissRequest = { helpDialog = false },
                title = { Text("使い方") },
                text = {
                    Text(
                        "1. 起動画面で「シミュレーション開始」を押します。\n" +
                            "2. カメラにQRコードを映すか、画像ファイルを選びます。\n" +
                            "3. 再生・一時停止・リセット・シークで動かします。\n" +
                            "4. 速度、盤面サイズ、色、背景画像を変更できます。\n" +
                            "5. 出力ではステップ数を指定してGIFまたはMP4を保存できます。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = { helpDialog = false }) { Text("閉じる") }
                }
            )
        }
    }

}

@Composable
private fun HomeScreen(onStart: () -> Unit, onHelp: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("QR Life", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text("QRコードを初期配置としてライフゲームを実行します。")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("シミュレーション開始")
        }
        OutlinedButton(onClick = onHelp, modifier = Modifier.fillMaxWidth()) {
            Text("使い方")
        }
    }
}

@Composable
private fun ColorControl(label: String, color: Color, onChange: (Color) -> Unit) {
    Text("$label: #${color.toArgb().toUInt().toString(16).padStart(8, '0')}")
    listOf(
        color.red to { value: Float -> onChange(color.copy(red = value)) },
        color.green to { value: Float -> onChange(color.copy(green = value)) },
        color.blue to { value: Float -> onChange(color.copy(blue = value)) },
        color.alpha to { value: Float -> onChange(color.copy(alpha = value)) }
    ).forEach { (value, update) ->
        Slider(value = value, onValueChange = update, valueRange = 0f..1f)
    }
}

@Composable
private fun CameraScanner(
    onImage: (ByteArray, Int, Int) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val processing = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
        }
    }
    Box {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            factory = { viewContext ->
                val previewView = PreviewView(viewContext)
                val providerFuture = ProcessCameraProvider.getInstance(viewContext)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { proxy ->
                        if (!processing.compareAndSet(false, true)) {
                            proxy.close()
                            return@setAnalyzer
                        }
                        val image = proxy.image
                        if (image == null) {
                            processing.set(false)
                            proxy.close()
                            return@setAnalyzer
                        }
                        try {
                            val bytes = packedLuma(image, proxy)
                            ContextCompat.getMainExecutor(context).execute {
                                onImage(bytes, proxy.width, proxy.height)
                            }
                        } catch (_: RuntimeException) {
                            onError("カメラ画像を読み取れませんでした")
                        } finally {
                            processing.set(false)
                            proxy.close()
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }, ContextCompat.getMainExecutor(context))
                previewView
            }
        )
        Text("QRコードをカメラに向けてください", modifier = Modifier.padding(12.dp))
    }
}

private fun packedLuma(image: android.media.Image, proxy: ImageProxy): ByteArray {
    val plane = image.planes[0]
    val source = plane.buffer
    val width = proxy.width
    val height = proxy.height
    val result = ByteArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            result[y * width + x] =
                source.get(y * plane.rowStride + x * plane.pixelStride)
        }
    }
    return result
}

@Composable
private fun BoardView(
    board: RustLife,
    previousCells: List<Boolean>,
    aliveColor: Color,
    deadColor: Color,
    bornColor: Color,
    diedColor: Color,
    background: ImageBitmap?
) {
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
        background?.let { image ->
            val scale = maxOf(size.width / image.width, size.height / image.height)
            val drawWidth = (image.width * scale).toInt()
            val drawHeight = (image.height * scale).toInt()
            drawImage(
                image,
                dstOffset = IntOffset(
                    ((size.width - drawWidth) / 2f).toInt(),
                    ((size.height - drawHeight) / 2f).toInt()
                ),
                dstSize = IntSize(drawWidth, drawHeight)
            )
        }
        drawRect(deadColor)
        val cellWidth = size.width / board.width
        val cellHeight = size.height / board.height
        board.cells.forEachIndexed { index, alive ->
            if (alive) {
                val x = index % board.width
                val y = index / board.width
                drawRect(
                    color = if (index < previousCells.size && !previousCells[index]) bornColor else aliveColor,
                    topLeft = Offset(x * cellWidth, y * cellHeight),
                    size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
                )
            } else if (index < previousCells.size && previousCells[index]) {
                val x = index % board.width
                val y = index / board.width
                drawRect(
                    color = diedColor,
                    topLeft = Offset(x * cellWidth, y * cellHeight),
                    size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
                )
            }
        }
    }
}
