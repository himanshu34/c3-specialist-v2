package co.nayan.c3v2.core.models

import co.nayan.c3v2.core.config.DEFAULT_BUFFER_LENGTH

/**
 * configuration for camera initialization
 *
 * @property shouldShowOverlay
 * @property shouldShowBoundingBox
 * @property isNightModeEnabled
 * @property isSwitchCameraEnabled
 * @property bufferLength (in sec)
 * @property isDubaiPoliceEnabled
 * @property shouldShowSettingsOnPreview
 */
class CameraConfig private constructor(
    private val shouldShowOverlay: Boolean?,
    private val shouldShowBoundingBox: Boolean?,
    private val isNightModeEnabled: Boolean?,
    val isSwitchCameraEnabled: Boolean?,
    private val bufferLength: Int,
    val isDubaiPoliceEnabled: Boolean?,
    val shouldShowSettingsOnPreview: Boolean?
) {
    data class Builder(
        var shouldShowOverlay: Boolean? = null,
        var shouldShowBoundingBox: Boolean? = null,
        var isNightModeEnabled: Boolean? = null,
        var isSwitchCameraEnabled: Boolean? = null,
        var bufferLength: Int = DEFAULT_BUFFER_LENGTH,
        var isDubaiPoliceEnabled: Boolean? = false,
        var shouldShowSettingsOnPreview: Boolean? = false
    ) {

        fun isDubaiPoliceEnabled(isDpEnabled: Boolean?) =
            apply { this.isDubaiPoliceEnabled = isDpEnabled }

        fun showBoundingBox(shouldShowBoundingBox: Boolean?) =
            apply { this.shouldShowBoundingBox = shouldShowBoundingBox }

        fun showOverlay(shouldShowOverlay: Boolean?) =
            apply { this.shouldShowOverlay = shouldShowOverlay }

        fun shouldShowSettingsOnPreview(shouldShowSettingsOnPreview: Boolean) =
            apply { this.shouldShowSettingsOnPreview = shouldShowSettingsOnPreview }


        fun setNightModeEnabled(isNightModeEnabled: Boolean?) =
            apply { this.isNightModeEnabled = isNightModeEnabled }

        fun setSwitchCameraEnabled(isSwitchCameraEnabled: Boolean?) =
            apply { this.isSwitchCameraEnabled = isSwitchCameraEnabled }

        fun setBufferLength(bufferLength: Int) =
            apply { this.bufferLength = bufferLength }

        fun build() = CameraConfig(
            shouldShowOverlay,
            shouldShowBoundingBox,
            isNightModeEnabled,
            isSwitchCameraEnabled,
            bufferLength,
            isDubaiPoliceEnabled,
            shouldShowSettingsOnPreview
        )
    }
}