package com.nayan.nayancamv2.extcam.common

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import co.nayan.nayancamv2.R
import co.nayan.nayancamv2.databinding.ActivityDashcamConnectionBinding
import com.nayan.nayancamv2.BaseActivity
import com.nayan.nayancamv2.extcam.dashcam.DashCamConnectionViewModel
import java.lang.ref.WeakReference

class ExtCamConnectionActivity : BaseActivity() {

    private lateinit var binding: ActivityDashcamConnectionBinding
    private lateinit var navController: NavController
    private val viewModel: DashCamConnectionViewModel by viewModels { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashcamConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel.apply {
            shouldInitWiFiReceiver = false
            shouldHover = sharedPrefManager.isDefaultHoverMode()
            mContext = WeakReference(applicationContext)
        }
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.navHostConnector) as NavHostFragment
        navController = navHostFragment.navController
        when (intent.getStringExtra("selected")) {
            "drone" -> {
                navController.navigate(R.id.action_scanningFragment_to_droneConnectionFragment)
            }

            "dashcam" -> {
                viewModel.shouldInitWiFiReceiver = true
            }
        }

        onBackPressedDispatcher.addCallback(
            this@ExtCamConnectionActivity,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (intent.hasExtra("coming_from") && "hover" == intent.getStringExtra("coming_from"))
                        nayanCamModuleInteractor.startDashboardActivity(
                            this@ExtCamConnectionActivity,
                            false
                        )
                    finish()
                }
            })
    }
}
