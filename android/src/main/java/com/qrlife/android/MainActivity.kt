package com.qrlife.android

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.platform.LocalContext
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

    LaunchedEffect(running, board) {
        while (running && board != null) {
            kotlinx.coroutines.delay(120)
            board?.step()
            revision++
        }
    }

    val loadImage: (ByteArray) -> Unit = { bytes ->
        board?.close()
        board = RustLife.createFromImage(bytes)
        revision++
        running = false
        error = if (board == null) "画像からQRコードを検出できませんでした" else null
    }
    val loadGray: (Int, Int, ByteArray) -> Unit = { width, height, bytes ->
        board?.close()
        board = RustLife.createFromGray(width, height, bytes)
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
                } else {
                    CameraScanner(
                        onImage = { bytes, width, height -> loadGray(width, height, bytes) },
                        onError = { error = it }
                    )
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
                        BoardView(current)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Rustで実行中 / 盤面 ${current.width}×${current.height}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { running = !running }) {
                            Text(if (running) "一時停止" else "再生")
                        }
                        OutlinedButton(onClick = {
                            board = current.reset()
                            running = false
                        }) {
                            Text("リセット")
                        }
                    }
                }
            }
            item { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
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
private fun BoardView(board: RustLife) {
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val cellWidth = size.width / board.width
        val cellHeight = size.height / board.height
        board.cells.forEachIndexed { index, alive ->
            if (alive) {
                val x = index % board.width
                val y = index / board.width
                drawRect(
                    color = Color(0xFF2E7D32),
                    topLeft = Offset(x * cellWidth, y * cellHeight),
                    size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
                )
            }
        }
    }
}
