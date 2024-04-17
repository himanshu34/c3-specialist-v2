package co.nayan.c3specialist_v2.profile.details

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.config.ProfileConstants
import co.nayan.c3specialist_v2.databinding.ActivityPhotoidDetailsBinding
import co.nayan.c3specialist_v2.profile.ProfileViewModel
import co.nayan.c3specialist_v2.profile.widgets.ImageChooserDialogFragment
import co.nayan.c3specialist_v2.profile.widgets.OnImageChooserSelectListener
import co.nayan.c3specialist_v2.utils.TextChangedListener
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.User
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
class PhotoIdDetailsActivity : BaseActivity() {

    private val profileViewModel: ProfileViewModel by viewModels()
    private val binding: ActivityPhotoidDetailsBinding by viewBinding(ActivityPhotoidDetailsBinding::inflate)
    private lateinit var photoIdAdapter: ArrayAdapter<String>
    private var photoIdList = mutableListOf("Aadhar Number", "Passport")

    @Inject
    lateinit var errorUtils: ErrorUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.actionBar.appToolbar, true)
        title = getString(R.string.photo_id_details)

        profileViewModel.state.observe(this, stateObserver)
        val userInfo = profileViewModel.getUserInfo()
        initViews(userInfo)
        setupView(userInfo)
        profileViewModel.initImagePickerManager(this)

        binding.imageContainer.setOnClickListener { chooseImagePicker() }
        binding.photoIdInput.editText?.addTextChangedListener(onTextChangeListener)
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
                .add(imageChooserDialog, getString(R.string.pick_image))
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
                binding.spinner.post { binding.spinner.setSelection(0) }
                if (it.response.user != null) {
                    val intent = Intent().apply {
                        val message = String.format(
                            getString(R.string.update_message), ProfileConstants.PHOTO_ID
                        )
                        putExtra(Extras.UPDATED_MESSAGE, message)
                    }
                    setResult(Activity.RESULT_OK, intent)
                    finish()
                } else showMessage(it.response.message ?: getString(co.nayan.c3v2.core.R.string.something_went_wrong))
            }
            is ProfileViewModel.ImagePickerMessageState -> {
                showMessage(it.message)
            }
            is ProfileViewModel.ImagePickerSetImageState -> {
                val uri = it.uri
                if (uri == null) showBrowseContainer(true)
                else {
                    binding.photoIdImageView.setImageURI(uri)
                    showBrowseContainer(false)
                }
            }
            ProfileViewModel.UpdateImageErrorState -> {
                popupErrorMessage(
                    binding.photoIdImageView,
                    String.format(
                        getString(R.string.update_photoid_image_first),
                        binding.spinner.selectedItem.toString().trim()
                    )
                )
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

    private fun initViews(userInfo: User?) {
        if (photoIdList.contains(getString(R.string.select_photo_id)).not())
            photoIdList.add(0, getString(R.string.select_photo_id))
        photoIdAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, photoIdList)
        binding.spinner.adapter = photoIdAdapter

        if (userInfo != null) {
            if (userInfo.identificationType != null && photoIdList.contains(userInfo.identificationType))
                binding.spinner.post { binding.spinner.setSelection(photoIdList.indexOf(userInfo.identificationType)) }
            else binding.spinner.post { binding.spinner.setSelection(0) }
        } else binding.spinner.post { binding.spinner.setSelection(0) }

        binding.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                invalidateOptionsMenu()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupView(userInfo: User?) {
        val idNumber = userInfo?.photoIdNumber
        if (idNumber.isNullOrEmpty()) {
            showBrowseContainer(true)
        } else {
            binding.photoIdInput.editText?.setText(idNumber)
            val idImage = userInfo.photoIdImage?.url
            if (!idImage.isNullOrEmpty()) {
                showBrowseContainer(false)
                binding.imageProgressBar.visible()
                loadImage(idImage)
            } else showBrowseContainer(true)
        }
    }

    private fun loadImage(idImage: String) {
        lifecycleScope.launch {
            try {
                binding.photoIdImageView.setImageBitmap(getOriginalBitmapFromUrl(idImage))
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
            binding.photoIdImageView.setImageURI(null)
            binding.photoIdImageView.gone()
        } else {
            binding.browseImageContainer.gone()
            binding.photoIdImageView.visible()
        }
        invalidateOptionsMenu()
    }

    private suspend fun getOriginalBitmapFromUrl(url: String): Bitmap =
        withContext(Dispatchers.IO) {
            Glide.with(this@PhotoIdDetailsActivity)
                .asBitmap()
                .load(url)
                .submit()
                .get()
        }

    private fun validateInputs(idType: String, idNumber: String): Boolean {
        return if (idType.isEmpty() && idType == getString(R.string.select_photo_id)) {
            popupErrorMessage(binding.spinner, getString(R.string.id_type_cant_be_blank))
            false
        } else if (idNumber.isEmpty()) {
            popupErrorMessage(binding.photoIdInput, getString(R.string.id_number_cant_be_blank))
            false
        } else if (!binding.photoIdImageView.isVisible) {
            popupErrorMessage(binding.photoIdImageView, getString(R.string.update_id_image_first))
            false
        } else true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.let {
            val idType = binding.spinner.selectedItem.toString().trim()
            val idNumber = binding.photoIdInput.editText?.text.toString()
            it.findItem(R.id.save)?.isEnabled = !binding.progressOverlay.isVisible &&
                    idType.isNotEmpty() && idType != getString(R.string.select_photo_id) &&
                    idNumber.isNotEmpty() && binding.photoIdImageView.isVisible
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.update_info_menu_item, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.save) {
            val idType = binding.spinner.selectedItem.toString().trim()
            val idNumber = binding.photoIdInput.editText?.text.toString().trim()

            if (validateInputs(idType, idNumber))
                profileViewModel.updateKYCDetails(idType, idNumber, isForPAN = false)
            true
        } else super.onOptionsItemSelected(item)
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.progressBar, message, Snackbar.LENGTH_SHORT).show()
    }
}