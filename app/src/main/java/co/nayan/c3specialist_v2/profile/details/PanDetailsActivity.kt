package co.nayan.c3specialist_v2.profile.details

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.config.ProfileConstants
import co.nayan.c3specialist_v2.databinding.ActivityPanDetailsBinding
import co.nayan.c3specialist_v2.profile.ProfileViewModel
import co.nayan.c3specialist_v2.profile.utils.isValidPan
import co.nayan.c3specialist_v2.profile.widgets.ImageChooserDialogFragment
import co.nayan.c3specialist_v2.profile.widgets.OnImageChooserSelectListener
import co.nayan.c3specialist_v2.utils.TextChangedListener
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.setupActionBar
import co.nayan.c3v2.core.utils.visible
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketException
import java.util.concurrent.ExecutionException
import javax.inject.Inject

@AndroidEntryPoint
class PanDetailsActivity : BaseActivity() {

    private val profileViewModel: ProfileViewModel by viewModels()
    private val binding: ActivityPanDetailsBinding by viewBinding(ActivityPanDetailsBinding::inflate)

    @Inject
    lateinit var errorUtils: ErrorUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.actionBar.appToolbar, true)
        title = getString(R.string.pan_details)

        profileViewModel.state.observe(this, stateObserver)
        setupView()
        profileViewModel.initImagePickerManager(this)

        binding.imageContainer.setOnClickListener { chooseImagePicker() }
        binding.panNumberInput.editText?.addTextChangedListener(onTextChangeListener)
    }

    private val onImageChooserSelectListener = object : OnImageChooserSelectListener {
        override fun onSelect(picker: Int) {
            profileViewModel.setImagePickerType(picker)
            profileViewModel.requestPermissions()
        }
    }
    private val imageChooserDialog =
        ImageChooserDialogFragment.newInstance(onImageChooserSelectListener)

    private fun chooseImagePicker() {
        if (imageChooserDialog.isAdded.not()) {
            supportFragmentManager.beginTransaction()
                .add(imageChooserDialog, getString(R.string.pan_image))
                .commitAllowingStateLoss()
        }
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                binding.progressOverlay.visible()
            }
            is ProfileViewModel.UpdateInfoSuccessState -> {
                binding.progressOverlay.gone()
                if (it.response.user != null) {
                    val intent = Intent().apply {
                        val message = String.format(
                            getString(R.string.update_message), ProfileConstants.PAN
                        )
                        putExtra(Extras.UPDATED_MESSAGE, message)
                    }
                    setResult(Activity.RESULT_OK, intent)
                    finish()
                } else {
                    showMessage(it.response.message ?: getString(co.nayan.c3v2.core.R.string.something_went_wrong))
                }
            }
            is ProfileViewModel.ImagePickerMessageState -> {
                showMessage(it.message)
            }
            is ProfileViewModel.ImagePickerSetImageState -> {
                val uri = it.uri
                if (uri == null) {
                    showBrowseContainer(true)
                } else {
                    binding.panImageView.setImageURI(uri)
                    showBrowseContainer(false)
                }
            }
            ProfileViewModel.UpdateImageErrorState -> {
                popupErrorMessage(binding.panImageView, getString(R.string.update_pan_image_first))
            }
            is ErrorState -> {
                binding.progressOverlay.gone()
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }

        invalidateOptionsMenu()
    }

    private val onTextChangeListener = object : TextChangedListener() {
        override fun onTextChanged(char: CharSequence?, start: Int, before: Int, count: Int) {
            invalidateOptionsMenu()
        }
    }

    private fun setupView() {
        val userInfo = profileViewModel.getUserInfo()
        val panNumber = userInfo?.panNumber
        if (panNumber.isNullOrEmpty()) {
            showBrowseContainer(true)
        } else {
            binding.panNumberInput.editText?.setText(panNumber)
            val panImage = userInfo.panImage?.url
            if (!panImage.isNullOrEmpty()) {
                showBrowseContainer(false)
                loadImage(panImage)
            } else showBrowseContainer(true)
        }
    }

    private fun loadImage(panImage: String) {
        lifecycleScope.launch {
            try {
                binding.imageProgressBar.visible()
                binding.panImageView.setImageBitmap(getOriginalBitmapFromUrl(panImage))
                binding.imageProgressBar.gone()
            } catch (e: ExecutionException) {
                Firebase.crashlytics.recordException(e)
                showBrowseContainer(true)
                binding.imageProgressBar.gone()
            } catch (e: SocketException) {
                Firebase.crashlytics.recordException(e)
                showBrowseContainer(true)
                binding.imageProgressBar.gone()
            }
        }
    }

    private fun showBrowseContainer(shouldShow: Boolean) {
        if (shouldShow) {
            binding.browseImageContainer.visible()
            binding.panImageView.setImageURI(null)
            binding.panImageView.gone()
        } else {
            binding.browseImageContainer.gone()
            binding.panImageView.visible()
        }
        invalidateOptionsMenu()
    }

    private suspend fun getOriginalBitmapFromUrl(url: String): Bitmap =
        withContext(Dispatchers.IO) {
            Glide.with(this@PanDetailsActivity)
                .asBitmap()
                .load(url)
                .submit()
                .get()
        }

    private fun validateInputs(panNumber: String): Boolean {
        return if (panNumber.isEmpty()) {
            popupErrorMessage(binding.panNumberInput, getString(R.string.pan_number_cant_be_blank))
            false
        } else if (!panNumber.isValidPan()) {
            popupErrorMessage(binding.panNumberInput, getString(R.string.invalid_pan_number))
            false
        } else if (!binding.panImageView.isVisible) {
            popupErrorMessage(binding.panImageView, getString(R.string.update_pan_image_first))
            false
        } else {
            true
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.let {
            val panNumber = binding.panNumberInput.editText?.text.toString()
            it.findItem(R.id.save)?.isEnabled = !binding.progressOverlay.isVisible &&
                    panNumber.length == 10 && binding.panImageView.isVisible
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.update_info_menu_item, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.save) {
            val panNumber = binding.panNumberInput.editText?.text.toString().trim()
            if (validateInputs(panNumber))
                profileViewModel.updateKYCDetails(
                    "Pan Number",
                    panNumber,
                    isForPAN = true
                )
            true
        } else super.onOptionsItemSelected(item)
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.progressBar, message, Snackbar.LENGTH_SHORT).show()
    }
}