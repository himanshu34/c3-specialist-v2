package co.nayan.c3specialist_v2.wallet.invoice

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.config.Tag.RECEIPT_DOWNLOAD_FOLDER
import co.nayan.c3specialist_v2.config.Tag.TAG_RECEIPT_DOWNLOADER_TAG
import co.nayan.c3specialist_v2.databinding.ActivityInvoiceBinding
import co.nayan.c3specialist_v2.startDownloadingInvoice
import co.nayan.c3specialist_v2.utils.FileDownloadWorker
import co.nayan.c3specialist_v2.utils.FileDownloadWorker.Companion.WORK_PROGRESS_VALUE
import co.nayan.c3specialist_v2.utils.string
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.models.c3_module.Transaction
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.parcelable
import co.nayan.c3v2.core.utils.setupActionBar
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.io.File

@AndroidEntryPoint
class InvoiceActivity : BaseActivity() {

    private var transactionReference: String = ""
    private var transaction: Transaction? = null
    private val binding: ActivityInvoiceBinding by viewBinding(ActivityInvoiceBinding::inflate)
    private val directory: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        RECEIPT_DOWNLOAD_FOLDER
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.actionBar.appToolbar, true)
        title = getString(R.string.payout_receipt)

        intent.parcelable<Transaction>(Extras.TRANSACTION)?.let {
            transaction = it
            setUpInvoiceDetails(it)
        }

        if (transaction?.receiptUrl.isNullOrEmpty()) binding.fabSave.gone()
        binding.fabSave.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) downloadReceipt()
            else requestPermission.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (granted) downloadReceipt()
        }

    private fun openFile() {
        try {
            val file = File(directory, "$transactionReference.pdf")
            val target = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(file), "application/pdf")
                flags = Intent.FLAG_ACTIVITY_NO_HISTORY
            }
            startActivity(Intent.createChooser(target, "Open File"))
        } catch (e: ActivityNotFoundException) {
            Firebase.crashlytics.recordException(e)
            showMessage(getString(R.string.pdf_reader_warning))
        }
    }

    private fun isFileExists() = File(directory, "$transactionReference.pdf").exists()

    private fun downloadReceipt() {
        val receiptUrl = transaction?.receiptUrl
        when {
            receiptUrl.isNullOrEmpty() -> showMessage(getString(R.string.empty_receipt_link))
            isFileExists() -> openFile()
            else -> {
                showProgressDialog(
                    getString(R.string.download_invoice_message),
                    isCancelable = true
                )
                val data = workDataOf(
                    FileDownloadWorker.FILE_URL to receiptUrl,
                    FileDownloadWorker.FOLDER_NAME to RECEIPT_DOWNLOAD_FOLDER,
                    FileDownloadWorker.FILE_EXTENSION to ".pdf",
                    FileDownloadWorker.FILE_NAME to transactionReference
                )

                val workRequestId = startDownloadingInvoice(data, TAG_RECEIPT_DOWNLOADER_TAG)
                WorkManager.getInstance(this)
                    .getWorkInfoByIdLiveData(workRequestId) // requestId is the WorkRequest id
                    .observe(this) { workInfo ->
                        if (workInfo?.state == null) return@observe
                        when (workInfo.state) {
                            WorkInfo.State.SUCCEEDED -> {
                                hideProgressDialog()
                                showMessage(getString(R.string.file_downloaded))
                            }

                            WorkInfo.State.RUNNING -> {
                                val progress = workInfo.progress.getInt(WORK_PROGRESS_VALUE, 0)
                                Timber.d("######### File Downloader ########## -> $progress")
                            }

                            WorkInfo.State.FAILED, WorkInfo.State.BLOCKED, WorkInfo.State.CANCELLED -> {
                                hideProgressDialog()
                                showMessage(getString(R.string.file_cant_be_downloaded))
                            }

                            else -> {}
                        }
                    }
            }
        }
    }

    private fun setUpInvoiceDetails(transaction: Transaction) {
        transactionReference = transaction.transactionReference ?: ""
        binding.dateTxt.text = String.format("Date: ${transaction.updatedAt?.string()}")
        binding.userDetails.visible()
        val panNumber = transaction.user?.panNumber
        if (panNumber.isNullOrEmpty()) {
            binding.panTxt.gone()
        } else {
            binding.panTxt.text = String.format("(PAN Number: $panNumber)")
        }
        binding.userNameTxt.text = String.format("Name : ${transaction.user?.name ?: ""}")
        binding.phoneTxt.text = String.format("Phone : ${transaction.user?.phoneNumber ?: ""}")
        binding.addressTxt.text = String.format("Address : ${transaction.user?.address ?: ""}")

        binding.companyNameTxt.text = transaction.companyName ?: "--"
        binding.companyAddressTxt.text = transaction.companyAddress ?: "--"
        binding.companyGstTxt.text = String.format("(GST number ${transaction.companyGst ?: "--"})")

        binding.payoutAmountTxt.text =
            String.format("₹ ${transaction.amountBeforeAdjustments ?: 0f}")
        binding.payoutFeeTxt.text = String.format("₹ ${transaction.payoutFee ?: 0f}")
        binding.payoutTdsTxt.text = String.format("₹ ${transaction.payoutTds ?: 0f}")

        binding.referralAmountTxt.text = String.format("₹ ${transaction.referralAmount ?: 0f}")
        binding.referralFeeTxt.text = String.format("₹ ${transaction.referralFee ?: 0f}")
        binding.referralTdsTxt.text = String.format("₹ ${transaction.referralTds ?: 0f}")

        binding.totalAmountTxt.text = String.format("₹ ${transaction.amountAfterAdjustments ?: 0f}")
        binding.totalFeeTxt.text = String.format("₹ ${transaction.totalFee ?: 0f}")
        binding.totalTdsTxt.text = String.format("₹ ${transaction.totalTds ?: 0f}")

        binding.finalAmountTxt.text = String.format("₹ ${transaction.netAmount ?: 0f}")
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.svReceipt, message, Snackbar.LENGTH_LONG).show()
    }
}