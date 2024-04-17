package co.nayan.c3specialist_v2.search

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.*
import androidx.fragment.app.activityViewModels
import co.nayan.c3specialist_v2.BuildConfig
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseFragment
import co.nayan.c3specialist_v2.config.CurrentRole
import co.nayan.c3specialist_v2.dashboard.DashboardActivity
import co.nayan.c3specialist_v2.databinding.FragmentSearchBinding
import co.nayan.c3specialist_v2.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class SearchFragment : BaseFragment(R.layout.fragment_search) {

    private val viewModel: NayanSearchViewModel by activityViewModels()
    private val binding by viewBinding(FragmentSearchBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (activity is DashboardActivity) {
            (activity as DashboardActivity).updateHomeBackground(CurrentRole.SEARCH)
        }

        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            databaseEnabled = true
            allowFileAccess = true
        }

        binding.webView.webViewClient = WebClient(viewModel.getLocalStorageForWebView())

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Timber.tag("WebView ConsoleMessage").e("-->>>>>>>>>>>>>>>>>>")
                Timber.tag("Message").e(" -- ${consoleMessage?.message()}")
                Timber.tag("Line Number").e(" -- ${consoleMessage?.lineNumber()}")
                Timber.tag("Source ID").e(" -- ${consoleMessage?.sourceId()}")
                Timber.tag("WebView ConsoleMessage").e("-->>>>>>>>>>>>>>>>>>")
                return super.onConsoleMessage(consoleMessage)
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
                super.onGeolocationPermissionsShowPrompt(origin, callback)
            }
        }

        binding.webView.loadUrl(BuildConfig.NAYAN_SEARCH)
    }

    private class WebClient(val userDetails: String?) : WebViewClient() {

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            view?.evaluateJavascript("window.localStorage.setItem('token','$userDetails');") {
                Timber.e("evaluateJavascript: $it")
            }
            view?.evaluateJavascript("javascript:console.log(window.localStorage.getItem('token'))") {
                Timber.e("evaluateJavascript: $it")
            }
            Timber.e("onPageStarted: $url")

            super.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            Timber.e("onPageFinished: $url")

            view?.loadUrl(
                "javascript:(function() { " +
                        "document.querySelectorAll('[data-mobile-hide=\"true\"]').forEach(el => el.style=\"display: none\")" +
                        "})()"
            )
            view?.evaluateJavascript("window.localStorage.setItem('token','$userDetails');") {
                Timber.e("evaluateJavascript: $it")
            }
            view?.evaluateJavascript("javascript:console.log(window.localStorage.getItem('token'))") {
                Timber.e("evaluateJavascript: $it")
            }

            super.onPageFinished(view, url)
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            request?.let {
                it.url.let { url -> Timber.e("shouldOverrideUrlLoading: $url") }
            }
            return super.shouldOverrideUrlLoading(view, request)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            Timber.e("onReceivedError request: ${request?.url}")
            Timber.e("onReceivedError error: ${error?.description}")
            Timber.e("onReceivedError error: ${error?.errorCode}")
            super.onReceivedError(view, request, error)
        }
    }

    fun canGoBack(): Boolean {
        val canGoBack = binding.webView.canGoBack()
        if (canGoBack) binding.webView.goBack()
        return canGoBack
    }

}