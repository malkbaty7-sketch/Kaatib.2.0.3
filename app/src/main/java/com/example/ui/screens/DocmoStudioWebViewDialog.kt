package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppThemeData
import com.example.util.DocmoDocumentBridge
import java.io.ByteArrayInputStream

private class DocmoJsBridge(
    private val context: Context,
    private val onRequestSaveFile: (filename: String, mimeType: String, bytes: ByteArray) -> Unit,
    private val onRequestOpenExternal: (filename: String, mimeType: String, bytes: ByteArray) -> Unit
) {
    @JavascriptInterface
    fun saveBase64File(filename: String, mimeType: String, base64Data: String) {
        try {
            val cleanBase64 = base64Data.substringAfter(",", base64Data)
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            onRequestSaveFile(filename, mimeType.ifBlank { DocmoDocumentBridge.guessMimeType(filename) }, bytes)
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "تعذر حفظ الملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @JavascriptInterface
    fun openBase64External(filename: String, mimeType: String, base64Data: String) {
        try {
            val cleanBase64 = base64Data.substringAfter(",", base64Data)
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            onRequestOpenExternal(filename, mimeType.ifBlank { DocmoDocumentBridge.guessMimeType(filename) }, bytes)
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "تعذر فتح المستند: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DocmoStudioWebViewDialog(
    theme: AppThemeData,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    var pendingSaveFilename by remember { mutableStateOf("document.docx") }
    var pendingSaveMimeType by remember { mutableStateOf("application/octet-stream") }
    var pendingSaveBytes by remember { mutableStateOf<ByteArray?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val callback = fileUploadCallback
        fileUploadCallback = null
        if (uris.isNotEmpty()) {
            callback?.onReceiveValue(uris.toTypedArray())
        } else {
            callback?.onReceiveValue(null)
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri: Uri? ->
        val bytes = pendingSaveBytes
        pendingSaveBytes = null
        if (destUri != null && bytes != null) {
            val res = DocmoDocumentBridge.saveBytesToDestinationUri(context, destUri, bytes)
            if (res.isSuccess) {
                Toast.makeText(context, "تم حفظ الملف ($pendingSaveFilename) بنجاح", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, res.exceptionOrNull()?.message ?: "تعذر حفظ الملف", Toast.LENGTH_LONG).show()
            }
        }
    }

    Dialog(
        onDismissRequest = {
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = null
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .fillMaxHeight(0.95f)
                .clip(RoundedCornerShape(20.dp)),
            color = Color(0xFF0B0F19)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111827))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2563EB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                text = "استوديو DOCMO المتكامل للمستندات",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "PDF • Word • Excel • CSV • Access — معالجة محلية وتفاعلية",
                                fontSize = 10.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { webViewRef?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = Color.White)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                        }
                    }
                }

                // Embedded DOCMO WebView
                AndroidView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewRef = this
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                mediaPlaybackRequiresUserGesture = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }

                            addJavascriptInterface(
                                DocmoJsBridge(
                                    context = ctx,
                                    onRequestSaveFile = { filename, mimeType, bytes ->
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            pendingSaveFilename = filename
                                            pendingSaveMimeType = mimeType
                                            pendingSaveBytes = bytes
                                            createDocumentLauncher.launch(filename)
                                        }
                                    },
                                    onRequestOpenExternal = { filename, mimeType, bytes ->
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            val result = if (filename.lowercase().endsWith(".pdf")) {
                                                DocmoDocumentBridge.openPdfFromBytes(ctx, filename, bytes)
                                            } else {
                                                DocmoDocumentBridge.openDocumentFromBytes(ctx, filename, mimeType, bytes)
                                            }
                                            if (result.isFailure) {
                                                Toast.makeText(
                                                    ctx,
                                                    result.exceptionOrNull()?.message ?: "تعذر فتح المستند",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                ),
                                "DocmoAndroidBridge"
                            )

                            webChromeClient = object : WebChromeClient() {
                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    fileUploadCallback?.onReceiveValue(null)
                                    fileUploadCallback = filePathCallback
                                    fileChooserLauncher.launch(arrayOf("*/*"))
                                    return true
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val url = request?.url ?: return null
                                    if (url.host == "docmo.local") {
                                        val rawPath = url.path?.trimStart('/')?.ifEmpty { "index.html" } ?: "index.html"
                                        val assetPath = "docmo/$rawPath"
                                        return try {
                                            val stream = ctx.assets.open(assetPath)
                                            val mime = when {
                                                rawPath.endsWith(".html") -> "text/html"
                                                rawPath.endsWith(".js") || rawPath.endsWith(".mjs") -> "application/javascript"
                                                rawPath.endsWith(".css") -> "text/css"
                                                rawPath.endsWith(".json") -> "application/json"
                                                rawPath.endsWith(".svg") -> "image/svg+xml"
                                                rawPath.endsWith(".png") -> "image/png"
                                                else -> "application/octet-stream"
                                            }
                                            WebResourceResponse(mime, "UTF-8", stream)
                                        } catch (_: Exception) {
                                            WebResourceResponse(
                                                "text/plain",
                                                "UTF-8",
                                                404,
                                                "Not Found",
                                                emptyMap(),
                                                ByteArrayInputStream(ByteArray(0))
                                            )
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    // Hook blob downloads (<a download>) so they trigger Android native SAF save dialog
                                    val hookJs = """
                                        (function() {
                                            if (window.__docmoDownloadHooked) return;
                                            window.__docmoDownloadHooked = true;
                                            const origClick = HTMLAnchorElement.prototype.click;
                                            HTMLAnchorElement.prototype.click = function() {
                                                const href = this.getAttribute('href') || '';
                                                const downloadName = this.getAttribute('download') || 'document';
                                                if (href.startsWith('blob:') && window.DocmoAndroidBridge) {
                                                    fetch(href)
                                                        .then(r => r.blob())
                                                        .then(blob => {
                                                            const reader = new FileReader();
                                                            reader.onloadend = function() {
                                                                window.DocmoAndroidBridge.saveBase64File(
                                                                    downloadName,
                                                                    blob.type || 'application/octet-stream',
                                                                    String(reader.result || '')
                                                                );
                                                            };
                                                            reader.readAsDataURL(blob);
                                                        })
                                                        .catch(() => origClick.apply(this, arguments));
                                                    return;
                                                }
                                                return origClick.apply(this, arguments);
                                            };
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(hookJs, null)
                                }
                            }

                            loadUrl("https://docmo.local/index.html")
                        }
                    }
                )
            }
        }
    }
}
