package co.nayan.c3specialist_v2.videogallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.databinding.VideoItemBinding
import co.nayan.c3v2.core.models.Video

class VideoGalleryAdapter(
    private val onGalleryItemClickListener: OnLearningVideoClickListener
) : RecyclerView.Adapter<VideoGalleryAdapter.LearningVideoPlayListHolder>() {

    private val learningVideoPlayList = mutableListOf<Video>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LearningVideoPlayListHolder {
        return LearningVideoPlayListHolder(
            VideoItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: LearningVideoPlayListHolder, position: Int) {
        val videoData = learningVideoPlayList[position]
        holder.binding.videoData = videoData
        holder.binding.videoThumbUrl = if (videoData.code.isNullOrEmpty()) videoData.gcpUrl
        else "https://img.youtube.com/vi/${videoData.code}/0.jpg"
    }

    override fun getItemCount(): Int {
        return learningVideoPlayList.size
    }

    fun addAll(toAdd: MutableList<Video>) {
        learningVideoPlayList.clear()
        learningVideoPlayList.addAll(toAdd)

        notifyDataSetChanged()
    }

    inner class LearningVideoPlayListHolder(
        val binding: VideoItemBinding
    ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        init {
            binding.root.setOnClickListener(this)
        }

        override fun onClick(view: View?) {
            onGalleryItemClickListener.onClicked(learningVideoPlayList[adapterPosition])
        }
    }
}

interface OnLearningVideoClickListener {
    fun onClicked(video: Video)
}