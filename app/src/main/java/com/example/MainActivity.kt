package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.R
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PratikaCyan
import com.example.ui.theme.PratikaDeepBlue
import com.example.ui.theme.PratikaTeal
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val isOnline = MutableStateFlow(true)
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    // Register Activity File Chooser for File Upload Support inside the WebView
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data: Intent? = result.data
        val resultUriArray = if (result.resultCode == RESULT_OK && data != null) {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
        } else {
            null
        }
        fileChooserCallback?.onReceiveValue(resultUriArray)
        fileChooserCallback = null
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize and monitor real-time internet connectivity status
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        isOnline.value = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline.value = true
            }

            override fun onLost(network: Network) {
                isOnline.value = false
            }
        }

        networkCallback?.let {
            connectivityManager.registerNetworkCallback(networkRequest, it)
        }

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WebViewScreen(
                        isOnlineFlow = isOnline,
                        fileChooserLauncher = fileChooserLauncher,
                        setFileChooserCallback = { fileChooserCallback = it },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister Network Callback to avoid resource leaks
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let {
                connectivityManager.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            // Callback might not be registered, safe ignore
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun WebViewScreen(
    isOnlineFlow: MutableStateFlow<Boolean>,
    fileChooserLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    setFileChooserCallback: (ValueCallback<Array<Uri>>?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isOnlineState by isOnlineFlow.collectAsState()
    
    // Web load progress & loading status state
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var isPageLoading by remember { mutableStateOf(true) }
    
    // Track page errors (such as host unreachable)
    var hasPageError by remember { mutableStateOf(false) }
    
    // Back gesture tracking
    var lastBackPressTime by remember { mutableStateOf(0L) }

    // Initial load URL
    val appUrl = "https://pratika.app.br"

    // Instantiate and remember WebView
    val webViewInstance = remember {
        WebView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Advanced fluid performance settings for web app
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                allowFileAccess = true
                allowContentAccess = true
                javaScriptCanOpenWindowsAutomatically = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
    }

    var swipeLayoutInstance: SwipeRefreshLayout? = null

    // Register Web Clients
    LaunchedEffect(webViewInstance) {
        webViewInstance.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isPageLoading = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isPageLoading = false
                swipeLayoutInstance?.isRefreshing = false
                
                // If loaded successfully, clear error state
                if (!hasPageError && isOnlineState) {
                    hasPageError = false
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // Filter essential main frame load failures
                if (request?.isForMainFrame == true) {
                    hasPageError = true
                    swipeLayoutInstance?.isRefreshing = false
                    isPageLoading = false
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                // Clean handling of standard external intents
                if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("whatsapp:") || url.startsWith("https://api.whatsapp.com")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        Toast.makeText(context, "Não foi possível abrir o link externo", Toast.LENGTH_SHORT).show()
                        return true
                    }
                }
                return false
            }
        }

        webViewInstance.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                loadProgress = newProgress / 100f
                if (newProgress == 100) {
                    isPageLoading = false
                    swipeLayoutInstance?.isRefreshing = false
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                setFileChooserCallback(filePathCallback)
                return try {
                    val intent = fileChooserParams?.createIntent()
                    if (intent != null) {
                        fileChooserLauncher.launch(intent)
                    }
                    true
                } catch (e: Exception) {
                    setFileChooserCallback(null)
                    filePathCallback?.onReceiveValue(null)
                    false
                }
            }
        }

        webViewInstance.loadUrl(appUrl)
    }

    // Auto-reload on connection recovery
    LaunchedEffect(isOnlineState) {
        if (isOnlineState && (hasPageError || webViewInstance.url == null)) {
            hasPageError = false
            webViewInstance.reload()
        }
    }

    // Handles the back gesture smoothly, exactly like a native app
    BackHandler(enabled = true) {
        if (webViewInstance.canGoBack()) {
            webViewInstance.goBack()
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                (context as? ComponentActivity)?.finish()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, context.getString(R.string.back_to_exit), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Elegant linear loading progress indicator layered seamlessly on top
            AnimatedVisibility(
                visible = isPageLoading && isOnlineState && !hasPageError,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(
                    progress = { loadProgress },
                    color = PratikaCyan,
                    trackColor = PratikaDeepBlue.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Android SwipeRefreshLayout for beautiful, native-feel pulling refresh action
            AndroidView(
                factory = { ctx ->
                    SwipeRefreshLayout(ctx).apply {
                        setColorSchemeColors(
                            android.graphics.Color.parseColor("#00B4D8"),
                            android.graphics.Color.parseColor("#0077B6")
                        )
                        setProgressBackgroundColorSchemeColor(android.graphics.Color.parseColor("#FFFFFF"))
                        setOnRefreshListener {
                            if (isOnlineState) {
                                webViewInstance.reload()
                            } else {
                                isRefreshing = false
                                Toast.makeText(context, context.getString(R.string.error_offline_title), Toast.LENGTH_SHORT).show()
                            }
                        }
                        swipeLayoutInstance = this
                        addView(webViewInstance)
                    }
                },
                update = {
                    swipeLayoutInstance = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        // Animated overlay to handle offline or error states elegantly and natively
        AnimatedVisibility(
            visible = !isOnlineState || hasPageError,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PratikaDeepBlue),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Offline icon",
                        tint = PratikaCyan,
                        modifier = Modifier
                            .size(72.dp)
                            .padding(bottom = 16.dp)
                    )

                    Text(
                        text = stringResource(id = R.string.error_offline_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = stringResource(id = R.string.error_offline_desc),
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Button(
                        onClick = {
                            // Run dynamic check
                            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                            val activeNetwork = connectivityManager.activeNetwork
                            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                            val currentlyConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                            
                            if (currentlyConnected) {
                                isOnlineFlow.value = true
                                hasPageError = false
                                webViewInstance.reload()
                            } else {
                                Toast.makeText(context, "Ainda sem sinal. Verifique sua conexão.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PratikaCyan,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.btn_retry),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
