package co.nayan.c3specialist_v2.videogallery

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseFragment
import co.nayan.c3specialist_v2.databinding.FragmentLearningVideosBinding
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.NoVideosState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.Video
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.tutorial.LearningVideoPlayerActivity
import co.nayan.tutorial.config.LearningVideosExtras.LEARNING_VIDEO
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LearningVideosFragment : BaseFragment(R.layout.fragment_learning_videos) {

    companion object {
        private const val SELECTED_TAB = "selected_tab"

        @JvmStatic
        fun newInstance(
            selectedTab: Int
        ) = LearningVideosFragment().apply {
            arguments = Bundle().apply {
                putInt(SELECTED_TAB, selectedTab)
            }
        }
    }

    @Inject
    lateinit var errorUtils: ErrorUtils
    private val binding by viewBinding(FragmentLearningVideosBinding::bind)
    private val viewModel: VideoGalleryViewModel by activityViewModels()
    private lateinit var videoGalleryAdapter: VideoGalleryAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.selectedTab = arguments?.getInt(SELECTED_TAB) ?: 0
        setupViews()

        viewModel.state.observe(viewLifecycleOwner, stateObserver)
        viewModel.fetchVideos()

        binding.pullToRefresh.setOnRefreshListener {
            viewModel.fetchVideos()
        }
    }

    private fun setupViews() {
        videoGalleryAdapter = VideoGalleryAdapter(onGalleryItemClickListener)
        binding.learningVideosView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = videoGalleryAdapter
        }
    }

    private val onGalleryItemClickListener = object : OnLearningVideoClickListener {
        override fun onClicked(video: Video) {
            Intent(
                requireActivity(),
                LearningVideoPlayerActivity::class.java
            ).apply {
                putExtra(LEARNING_VIDEO, video)
                startActivity(this)
            }
        }
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                if (!binding.pullToRefresh.isRefreshing) {
                    binding.shimmerViewContainer.visible()
                    binding.shimmerViewContainer.startShimmer()
                    binding.learningVideosView.gone()
                    binding.noVideoAvailableContainer.gone()
                    binding.shimmerViewContainer.visible()
                }
            }
            NoVideosState -> {
                binding.shimmerViewContainer.gone()
                binding.shimmerViewContainer.stopShimmer()
                binding.pullToRefresh.isRefreshing = false
                binding.noVideoAvailableContainer.visible()
                binding.learningVideosView.gone()
                binding.noVideoAvailableContainer.text = when (viewModel.selectedTab) {
                    0 -> getString(R.string.no_learning_video_is_present)
                    else -> getString(R.string.no_video_is_present)
                }
            }
            is VideoGalleryViewModel.FetchLearningVideosPlaylistSuccessState -> {
                binding.shimmerViewContainer.gone()
                binding.shimmerViewContainer.stopShimmer()
                binding.pullToRefresh.isRefreshing = false
                binding.noVideoAvailableContainer.gone()
                binding.learningVideosView.visible()

                videoGalleryAdapter.addAll(it.learningVideos ?: mutableListOf())
                binding.learningVideosView.scheduleLayoutAnimation()
            }
            is ErrorState -> {
                binding.shimmerViewContainer.gone()
                binding.shimmerViewContainer.stopShimmer()
                binding.pullToRefresh.isRefreshing = false
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private fun showMessage(string: String) {
        Snackbar.make(binding.shimmerViewContainer, string, Snackbar.LENGTH_LONG).show()
    }
}