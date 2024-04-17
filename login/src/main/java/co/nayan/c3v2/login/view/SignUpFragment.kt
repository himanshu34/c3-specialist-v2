package co.nayan.c3v2.login.view

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.isKentCam
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorMessageState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.User
import co.nayan.c3v2.core.postDelayed
import co.nayan.c3v2.core.utils.colorSpannableStringWithUnderLineOne
import co.nayan.c3v2.core.utils.disabled
import co.nayan.c3v2.core.utils.enabled
import co.nayan.c3v2.login.LoginActivity
import co.nayan.c3v2.login.LoginViewModel
import co.nayan.c3v2.login.R
import co.nayan.c3v2.login.base.BaseFragment
import co.nayan.c3v2.login.config.LoginConfig
import co.nayan.c3v2.login.databinding.SignupFragmentBinding
import co.nayan.c3v2.login.hideKeyBoard
import co.nayan.c3v2.login.textToString
import co.nayan.c3v2.login.viewBinding
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.GraphRequest
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONException
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resumeWithException

@AndroidEntryPoint
class SignUpFragment : BaseFragment(R.layout.signup_fragment) {

    private val tag = SignUpFragment::class.simpleName.toString()
    private val loginViewModel: LoginViewModel by activityViewModels()
    private val binding by viewBinding(SignupFragmentBinding::bind)
    private lateinit var mGoogleSignInClient: GoogleSignInClient

    @Inject
    lateinit var loginConfig: LoginConfig

    @Inject
    lateinit var errorUtils: ErrorUtils

    @ExperimentalCoroutinesApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.server_client_id))
            .requestEmail()
            .build()
        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        loginViewModel.state.observe(viewLifecycleOwner, stateObserver)

        binding.tvCreateAccount.colorSpannableStringWithUnderLineOne(
            "${getString(R.string.already_account)} ",
            getString(R.string.sign_in),
            true,
            callback = {
                requireActivity().supportFragmentManager.popBackStack()
            })
        binding.tvCreateAccount.invalidate()

        binding.tvTermsConditions.colorSpannableStringWithUnderLineOne(
            "By signing up I agree to ",
            "Terms and Conditions",
            callback = {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://traffic.nayan.co/policies")
                    )
                )
            })
        binding.tvTermsConditions.invalidate()

        binding.confirmPasswordInput.setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener if (actionId == EditorInfo.IME_ACTION_SEND) {
                hideKeyBoard()
                loginViewModel.validateUserSignUp(
                    binding.nameInput.textToString(),
                    binding.emailInput.textToString(),
                    binding.passwordInput.textToString(),
                    binding.confirmPasswordInput.textToString()
                )
                true
            } else false
        }

        binding.buttonSubmit.setOnClickListener {
            loginViewModel.validateUserSignUp(
                binding.nameInput.textToString(),
                binding.emailInput.textToString(),
                binding.passwordInput.textToString(),
                binding.confirmPasswordInput.textToString()
            )
        }

        binding.googleSignInBtn.setOnClickListener {
            googleSignInLaunch.launch(mGoogleSignInClient.signInIntent)
        }

        binding.facebookBtn.setOnClickListener {
            beginLoginToFacebook()
            finishFacebookLogin { loginResult ->
                val request = GraphRequest.newMeRequest(loginResult.accessToken) { obj, response ->
                    Timber.tag(tag).v("LoginActivity Response $response")
                    try {
                        val id = obj?.optString("id")
                        val firstName = obj?.optString("first_name")
                        val lastName = obj?.optString("last_name")
                        val name = StringBuilder()
                        firstName?.let { name.append(it) }
                        lastName?.let { name.append(" ").append(it) }
                        val fetchedEmail = obj?.optString("email")
                        val email = if (fetchedEmail.isNullOrEmpty())
                            "$id@facebook.com" else fetchedEmail
                        val imageUrl = "http://graph.facebook.com/$id/picture?type=large"
                        loginViewModel.validateSocialLogin(
                            name.toString().trim(),
                            email,
                            imageUrl,
                            loginResult.accessToken.token,
                            "Facebook"
                        )
                    } catch (e: JSONException) {
                        Timber.tag(tag).e("Facebook error $e")
                        Firebase.crashlytics.recordException(e)
                    }
                }
                val parameters = Bundle()
                parameters.putString("fields", "id,name,first_name,last_name,email")
                request.parameters = parameters
                request.executeAsync()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            if (loginViewModel.getDeviceModel().isKentCam())
                loginViewModel.authenticateKentUser()
        }
    }

    private fun beginLoginToFacebook() {
        if (activity != null) {
            val accessToken = AccessToken.getCurrentAccessToken()
            val isLoggedIn = accessToken != null && !accessToken.isExpired
            if (isLoggedIn) LoginManager.getInstance().logOut()

            LoginManager.getInstance()
                .logInWithReadPermissions(requireActivity(), listOf("public_profile", "email"))
        }
    }

    private fun finishFacebookLogin(
        onCredential: suspend (LoginResult) -> Unit
    ) = lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                val loginResult: LoginResult = getFacebookToken(loginViewModel.callbackManager)
                onCredential(loginResult)
            } catch (e: FacebookException) {
                Timber.tag(tag).e("Facebook Error $e")
                Firebase.crashlytics.recordException(e)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getFacebookToken(callbackManager: CallbackManager): LoginResult =
        suspendCancellableCoroutine { continuation ->
            LoginManager.getInstance()
                .registerCallback(callbackManager,
                    object : FacebookCallback<LoginResult> {
                        override fun onSuccess(result: LoginResult) {
                            continuation.resume(result) {
                                Timber.tag(tag).e(it)
                            }
                        }

                        override fun onCancel() {
                            // handling cancelled flow (probably don't need anything here)
                            LoginManager.getInstance().logOut()
                            continuation.cancel()
                        }

                        override fun onError(error: FacebookException) {
                            LoginManager.getInstance().logOut()
                            // Facebook authorization error
                            continuation.resumeWithException(error)
                        }
                    })
        }

    private val googleSignInLaunch =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // The Task returned from this call is always completed, no need to attach a listener.
            val task = GoogleSignIn.getSignedInAccountFromIntent(it.data)
            handleSignInResult(task)
        }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            // Signed in successfully, show authenticated UI.
            loginViewModel.validateSocialLogin(
                account.displayName ?: "",
                account.email ?: "",
                account.photoUrl.toString(),
                account.idToken ?: "",
                "Google"
            )
        } catch (e: ApiException) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Timber.tag(tag).e("signInResult:failed code = ${e.statusCode}")
            showMessage(getString(R.string.google_sign_in_error))
            Firebase.crashlytics.recordException(e)
        }
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            InitialState -> {
                binding.nameInput.enabled()
                binding.passwordInput.enabled()
                binding.emailInput.enabled()
                binding.confirmPasswordInput.enabled()
                binding.buttonSubmit.enabled()
            }

            ProgressState -> {
                binding.nameInput.disabled()
                binding.passwordInput.disabled()
                binding.emailInput.disabled()
                binding.confirmPasswordInput.disabled()
                binding.buttonSubmit.disabled()
            }

            is LoginViewModel.SignUpSuccessState -> {
                logoutSocialLogins()
                showMessage(it.signUpResponse.message)
                if (it.signUpResponse.success) {
                    resetAllFields()
                    postDelayed(1000) {
                        moveToNextScreen(it.signUpResponse.data)
                    }
                }
            }

            is ErrorMessageState -> {
                binding.nameInput.enabled()
                binding.passwordInput.enabled()
                binding.emailInput.enabled()
                binding.confirmPasswordInput.enabled()
                binding.buttonSubmit.enabled()
                showMessage(
                    it.errorMessage ?: getString(co.nayan.c3v2.core.R.string.something_went_wrong)
                )
            }

            is ErrorState -> {
                binding.nameInput.enabled()
                binding.passwordInput.enabled()
                binding.emailInput.enabled()
                binding.confirmPasswordInput.enabled()
                binding.buttonSubmit.enabled()
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private fun logoutSocialLogins() = viewLifecycleOwner.lifecycleScope.launch {
        try {
            // Logout Google
            if (::mGoogleSignInClient.isInitialized) mGoogleSignInClient.signOut()
            // Logout Facebook
            LoginManager.getInstance().logOut()
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun resetAllFields() = viewLifecycleOwner.lifecycleScope.launch {
        binding.nameInput.setText("")
        binding.passwordInput.setText("")
        binding.emailInput.setText("")
        binding.confirmPasswordInput.setText("")
    }

    private fun moveToNextScreen(user: User) = viewLifecycleOwner.lifecycleScope.launch {
        Intent(requireActivity(), loginConfig.mainActivityClass()).apply {
            putExtra(LoginActivity.KEY_USER, user)
            startActivity(this)
            requireActivity().finishAffinity()
        }
    }
}