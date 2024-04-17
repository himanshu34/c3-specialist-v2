package com.nayan.nayancamv2.helper

import android.hardware.camera2.CameraCharacteristics
import android.util.Range
import com.google.gson.Gson
import com.nayan.nayancamv2.storage.SharedPrefManager
import org.json.JSONObject

@Suppress("FunctionName")
object CameraParamsHelper {

    @Suppress("IMPLICIT_CAST_TO_ANY", "LocalVariableName", "SpellCheckingInspection")
    fun setCameraParams(
        characteristics: CameraCharacteristics,
        sharedPrefManager: SharedPrefManager
    ) {
        val CONTROL_AE_AVAILABLE_ANTIBANDING_MODES =
            characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES)
        val CONTROL_AE_AVAILABLE_MODES =
            characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
        val CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES =
            characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        val CONTROL_AE_COMPENSATION_RANGE =
            characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val CONTROL_AE_COMPENSATION_STEP =
            characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
        val CONTROL_AF_AVAILABLE_MODES =
            characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        val CONTROL_AVAILABLE_EFFECTS =
            characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS)
        val CONTROL_AVAILABLE_SCENE_MODES =
            characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)
        val CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES =
            characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
        val CONTROL_AWB_AVAILABLE_MODES =
            characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
        val CONTROL_MAX_REGIONS_AE =
            characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE)
        val CONTROL_MAX_REGIONS_AWB =
            characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB)
        val CONTROL_MAX_REGIONS_AF =
            characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)
        val CONTROL_AE_LOCK_AVAILABLE =
            characteristics.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE)
        val CONTROL_AWB_LOCK_AVAILABLE =
            characteristics.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE)
        val CONTROL_AVAILABLE_MODES =
            characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_MODES)
        val CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                characteristics.get(CameraCharacteristics.CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE)
            else "VERSION.SDK_INT < N"
        val CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_CAPABILITIES =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_CAPABILITIES)
            else "VERSION.SDK_INT < R"
        val CONTROL_ZOOM_RATIO_RANGE =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            else "VERSION.SDK_INT < R"
        val EDGE_AVAILABLE_EDGE_MODES =
            characteristics.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)
        val FLASH_INFO_AVAILABLE = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
        val HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES =
            characteristics.get(CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES)
        val JPEG_AVAILABLE_THUMBNAIL_SIZES =
            characteristics.get(CameraCharacteristics.JPEG_AVAILABLE_THUMBNAIL_SIZES)
        val LENS_INFO_AVAILABLE_APERTURES =
            characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
        val LENS_INFO_AVAILABLE_FILTER_DENSITIES =
            characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FILTER_DENSITIES)
        val LENS_INFO_AVAILABLE_FOCAL_LENGTHS =
            characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION =
            characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
        val LENS_INFO_HYPERFOCAL_DISTANCE =
            characteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE)
        val LENS_INFO_MINIMUM_FOCUS_DISTANCE =
            characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        val LENS_INFO_FOCUS_DISTANCE_CALIBRATION =
            characteristics.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION)
        val LENS_FACING = characteristics.get(CameraCharacteristics.LENS_FACING)
        val LENS_POSE_ROTATION =
            characteristics.get(CameraCharacteristics.LENS_POSE_ROTATION)
        val LENS_POSE_TRANSLATION =
            characteristics.get(CameraCharacteristics.LENS_POSE_TRANSLATION)
        val LENS_INTRINSIC_CALIBRATION =
            characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)
        val LENS_RADIAL_DISTORTION =
            characteristics.get(CameraCharacteristics.LENS_RADIAL_DISTORTION)
        val LENS_POSE_REFERENCE =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                characteristics.get(CameraCharacteristics.LENS_POSE_REFERENCE)
            else "VERSION.SDK_INT < P"
        val LENS_DISTORTION =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                characteristics.get(CameraCharacteristics.LENS_DISTORTION) else "VERSION.SDK_INT < P"
        val NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES =
            characteristics.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
        val REQUEST_MAX_NUM_OUTPUT_RAW =
            characteristics.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_RAW)
        val REQUEST_MAX_NUM_OUTPUT_PROC =
            characteristics.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC)
        val REQUEST_MAX_NUM_OUTPUT_PROC_STALLING =
            characteristics.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC_STALLING)
        val REQUEST_MAX_NUM_INPUT_STREAMS =
            characteristics.get(CameraCharacteristics.REQUEST_MAX_NUM_INPUT_STREAMS)
        val REQUEST_PIPELINE_MAX_DEPTH =
            characteristics.get(CameraCharacteristics.REQUEST_PIPELINE_MAX_DEPTH)
        val REQUEST_PARTIAL_RESULT_COUNT =
            characteristics.get(CameraCharacteristics.REQUEST_PARTIAL_RESULT_COUNT)
        val REQUEST_AVAILABLE_CAPABILITIES =
            characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        val SCALER_AVAILABLE_MAX_DIGITAL_ZOOM =
            characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
        val SCALER_STREAM_CONFIGURATION_MAP =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val SCALER_CROPPING_TYPE = characteristics.get(CameraCharacteristics.SCALER_CROPPING_TYPE)
        val SCALER_MANDATORY_STREAM_COMBINATIONS =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                characteristics.get(CameraCharacteristics.SCALER_MANDATORY_STREAM_COMBINATIONS)
            } else {
                "VERSION.SDK_INT < Q"
            }
        val SCALER_MANDATORY_CONCURRENT_STREAM_COMBINATIONS =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                characteristics.get(CameraCharacteristics.SCALER_MANDATORY_CONCURRENT_STREAM_COMBINATIONS)
            } else {
                "VERSION.SDK_INT < R"
            }
        val SENSOR_INFO_ACTIVE_ARRAY_SIZE =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val SENSOR_INFO_SENSITIVITY_RANGE =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val SENSOR_INFO_COLOR_FILTER_ARRANGEMENT =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)
        val SENSOR_INFO_EXPOSURE_TIME_RANGE =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val SENSOR_INFO_MAX_FRAME_DURATION =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION)
        val SENSOR_INFO_PHYSICAL_SIZE =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val SENSOR_INFO_PIXEL_ARRAY_SIZE =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val SENSOR_INFO_WHITE_LEVEL =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL)
        val SENSOR_INFO_TIMESTAMP_SOURCE =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)
        val SENSOR_INFO_LENS_SHADING_APPLIED =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_LENS_SHADING_APPLIED)
        val SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)
        val SENSOR_REFERENCE_ILLUMINANT1 =
            characteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1)
        val SENSOR_REFERENCE_ILLUMINANT2 =
            characteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)
        val SENSOR_CALIBRATION_TRANSFORM1 =
            characteristics.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1)
        val SENSOR_CALIBRATION_TRANSFORM2 =
            characteristics.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2)
        val SENSOR_COLOR_TRANSFORM1 =
            characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1)
        val SENSOR_COLOR_TRANSFORM2 =
            characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2)
        val SENSOR_FORWARD_MATRIX2 =
            characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2)
        val SENSOR_BLACK_LEVEL_PATTERN =
            characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)
        val SENSOR_MAX_ANALOG_SENSITIVITY =
            characteristics.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY)
        val SENSOR_ORIENTATION = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
        val SENSOR_AVAILABLE_TEST_PATTERN_MODES =
            characteristics.get(CameraCharacteristics.SENSOR_AVAILABLE_TEST_PATTERN_MODES)
        val SENSOR_OPTICAL_BLACK_REGIONS =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                characteristics.get(CameraCharacteristics.SENSOR_OPTICAL_BLACK_REGIONS)
            else "VERSION.SDK_INT < N"
        val SHADING_AVAILABLE_MODES =
            characteristics.get(CameraCharacteristics.SHADING_AVAILABLE_MODES)
        val STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES =
            characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)
        val STATISTICS_INFO_MAX_FACE_COUNT =
            characteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT)
        val STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES =
            characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES)
        val STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES =
            characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES)
        val STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES)
            else "VERSION.SDK_INT < P"
        val TONEMAP_MAX_CURVE_POINTS =
            characteristics.get(CameraCharacteristics.TONEMAP_MAX_CURVE_POINTS)
        val TONEMAP_AVAILABLE_TONE_MAP_MODES =
            characteristics.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES)
        val INFO_SUPPORTED_HARDWARE_LEVEL =
            characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        val INFO_VERSION = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
            characteristics.get(CameraCharacteristics.INFO_VERSION) else "VERSION.SDK_INT < P"
        val SYNC_MAX_LATENCY = characteristics.get(CameraCharacteristics.SYNC_MAX_LATENCY)
        val REPROCESS_MAX_CAPTURE_STALL =
            characteristics.get(CameraCharacteristics.REPROCESS_MAX_CAPTURE_STALL)
        val DEPTH_DEPTH_IS_EXCLUSIVE =
            characteristics.get(CameraCharacteristics.DEPTH_DEPTH_IS_EXCLUSIVE)
        val LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                characteristics.get(CameraCharacteristics.LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE)
            else "VERSION.SDK_INT < P"
        val DISTORTION_CORRECTION_AVAILABLE_MODES =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                characteristics.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES)
            else "VERSION.SDK_INT < P"

        val config = JSONObject()
        config.put(
            "CONTROL_AE_AVAILABLE_ANTIBANDING_MODES",
            Gson().toJson(CONTROL_AE_AVAILABLE_ANTIBANDING_MODES)
        )
        config.put("CONTROL_AE_AVAILABLE_MODES", Gson().toJson(CONTROL_AE_AVAILABLE_MODES))
        config.put(
            "CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES",
            getStringFPS(CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        )
        config.put(
            "CONTROL_AE_COMPENSATION_RANGE",
            AE_COMPENSATION_RANGE(CONTROL_AE_COMPENSATION_RANGE)
        )
        config.put("CONTROL_AE_COMPENSATION_STEP", Gson().toJson(CONTROL_AE_COMPENSATION_STEP))
        config.put("CONTROL_AF_AVAILABLE_MODES", Gson().toJson(CONTROL_AF_AVAILABLE_MODES))
        config.put("CONTROL_AVAILABLE_EFFECTS", Gson().toJson(CONTROL_AVAILABLE_EFFECTS))
        config.put("CONTROL_AVAILABLE_SCENE_MODES", Gson().toJson(CONTROL_AVAILABLE_SCENE_MODES))
        config.put(
            "CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES",
            Gson().toJson(CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
        )
        config.put("CONTROL_AWB_AVAILABLE_MODES", Gson().toJson(CONTROL_AWB_AVAILABLE_MODES))
        config.put("CONTROL_MAX_REGIONS_AE", Gson().toJson(CONTROL_MAX_REGIONS_AE))
        config.put("CONTROL_MAX_REGIONS_AWB", Gson().toJson(CONTROL_MAX_REGIONS_AWB))
        config.put("CONTROL_MAX_REGIONS_AF", Gson().toJson(CONTROL_MAX_REGIONS_AF))
        config.put("CONTROL_AE_LOCK_AVAILABLE", Gson().toJson(CONTROL_AE_LOCK_AVAILABLE))
        config.put("CONTROL_AWB_LOCK_AVAILABLE", Gson().toJson(CONTROL_AWB_LOCK_AVAILABLE))
        config.put("CONTROL_AVAILABLE_MODES", Gson().toJson(CONTROL_AVAILABLE_MODES))
        config.put(
            "CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE",
            Gson().toJson(CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE)
        )
        config.put(
            "CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_CAPABILITIES",
            Gson().toJson(CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_CAPABILITIES)
        )
        config.put("CONTROL_ZOOM_RATIO_RANGE", Gson().toJson(CONTROL_ZOOM_RATIO_RANGE))
        config.put("EDGE_AVAILABLE_EDGE_MODES", Gson().toJson(EDGE_AVAILABLE_EDGE_MODES))
        config.put("FLASH_INFO_AVAILABLE", Gson().toJson(FLASH_INFO_AVAILABLE))
        config.put(
            "HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES",
            Gson().toJson(HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES)
        )
        config.put("JPEG_AVAILABLE_THUMBNAIL_SIZES", Gson().toJson(JPEG_AVAILABLE_THUMBNAIL_SIZES))
        config.put("LENS_INFO_AVAILABLE_APERTURES", Gson().toJson(LENS_INFO_AVAILABLE_APERTURES))
        config.put(
            "LENS_INFO_AVAILABLE_FILTER_DENSITIES",
            Gson().toJson(LENS_INFO_AVAILABLE_FILTER_DENSITIES)
        )
        config.put(
            "LENS_INFO_AVAILABLE_FOCAL_LENGTHS",
            Gson().toJson(LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        )
        config.put(
            "LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION",
            Gson().toJson(LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
        )
        config.put("LENS_INFO_HYPERFOCAL_DISTANCE", Gson().toJson(LENS_INFO_HYPERFOCAL_DISTANCE))
        config.put(
            "LENS_INFO_MINIMUM_FOCUS_DISTANCE",
            Gson().toJson(LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        )
        config.put(
            "LENS_INFO_FOCUS_DISTANCE_CALIBRATION",
            Gson().toJson(LENS_INFO_FOCUS_DISTANCE_CALIBRATION)
        )
        config.put("LENS_FACING", Gson().toJson(LENS_FACING))
        config.put("LENS_POSE_ROTATION", Gson().toJson(LENS_POSE_ROTATION))
        config.put("LENS_POSE_TRANSLATION", Gson().toJson(LENS_POSE_TRANSLATION))
        config.put("LENS_INTRINSIC_CALIBRATION", Gson().toJson(LENS_INTRINSIC_CALIBRATION))
        config.put("LENS_RADIAL_DISTORTION", Gson().toJson(LENS_RADIAL_DISTORTION))
        config.put("LENS_POSE_REFERENCE", Gson().toJson(LENS_POSE_REFERENCE))
        config.put("LENS_DISTORTION", Gson().toJson(LENS_DISTORTION))
        config.put(
            "NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES",
            Gson().toJson(NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
        )
        config.put("REQUEST_MAX_NUM_OUTPUT_RAW", Gson().toJson(REQUEST_MAX_NUM_OUTPUT_RAW))
        config.put("REQUEST_MAX_NUM_OUTPUT_PROC", Gson().toJson(REQUEST_MAX_NUM_OUTPUT_PROC))
        config.put(
            "REQUEST_MAX_NUM_OUTPUT_PROC_STALLING",
            Gson().toJson(REQUEST_MAX_NUM_OUTPUT_PROC_STALLING)
        )
        config.put("REQUEST_MAX_NUM_INPUT_STREAMS", Gson().toJson(REQUEST_MAX_NUM_INPUT_STREAMS))
        config.put("REQUEST_PIPELINE_MAX_DEPTH", Gson().toJson(REQUEST_PIPELINE_MAX_DEPTH))
        config.put("REQUEST_PARTIAL_RESULT_COUNT", Gson().toJson(REQUEST_PARTIAL_RESULT_COUNT))
        config.put("REQUEST_AVAILABLE_CAPABILITIES", Gson().toJson(REQUEST_AVAILABLE_CAPABILITIES))
        config.put(
            "SCALER_AVAILABLE_MAX_DIGITAL_ZOOM",
            Gson().toJson(SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
        )
        config.put(
            "SCALER_STREAM_CONFIGURATION_MAP",
            Gson().toJson(SCALER_STREAM_CONFIGURATION_MAP)
        )
        config.put("SCALER_CROPPING_TYPE", Gson().toJson(SCALER_CROPPING_TYPE))
        config.put(
            "SCALER_MANDATORY_STREAM_COMBINATIONS",
            Gson().toJson(SCALER_MANDATORY_STREAM_COMBINATIONS)
        )
        config.put(
            "SCALER_MANDATORY_CONCURRENT_STREAM_COMBINATIONS",
            Gson().toJson(SCALER_MANDATORY_CONCURRENT_STREAM_COMBINATIONS)
        )
        config.put("SENSOR_INFO_ACTIVE_ARRAY_SIZE", Gson().toJson(SENSOR_INFO_ACTIVE_ARRAY_SIZE))
        config.put("SENSOR_INFO_SENSITIVITY_RANGE", Gson().toJson(SENSOR_INFO_SENSITIVITY_RANGE))
        config.put(
            "SENSOR_INFO_COLOR_FILTER_ARRANGEMENT",
            Gson().toJson(SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)
        )
        config.put(
            "SENSOR_INFO_EXPOSURE_TIME_RANGE",
            Gson().toJson(SENSOR_INFO_EXPOSURE_TIME_RANGE)
        )
        config.put("SENSOR_INFO_MAX_FRAME_DURATION", Gson().toJson(SENSOR_INFO_MAX_FRAME_DURATION))
        config.put("SENSOR_INFO_PHYSICAL_SIZE", Gson().toJson(SENSOR_INFO_PHYSICAL_SIZE))
        config.put("SENSOR_INFO_PIXEL_ARRAY_SIZE", Gson().toJson(SENSOR_INFO_PIXEL_ARRAY_SIZE))
        config.put("SENSOR_INFO_WHITE_LEVEL", Gson().toJson(SENSOR_INFO_WHITE_LEVEL))
        config.put("SENSOR_INFO_TIMESTAMP_SOURCE", Gson().toJson(SENSOR_INFO_TIMESTAMP_SOURCE))
        config.put(
            "SENSOR_INFO_LENS_SHADING_APPLIED",
            Gson().toJson(SENSOR_INFO_LENS_SHADING_APPLIED)
        )
        config.put(
            "SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE",
            Gson().toJson(SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)
        )
        config.put("SENSOR_REFERENCE_ILLUMINANT1", Gson().toJson(SENSOR_REFERENCE_ILLUMINANT1))
        config.put("SENSOR_REFERENCE_ILLUMINANT2", Gson().toJson(SENSOR_REFERENCE_ILLUMINANT2))
        config.put("SENSOR_CALIBRATION_TRANSFORM1", Gson().toJson(SENSOR_CALIBRATION_TRANSFORM1))
        config.put("SENSOR_CALIBRATION_TRANSFORM2", Gson().toJson(SENSOR_CALIBRATION_TRANSFORM2))
        config.put("SENSOR_COLOR_TRANSFORM1", Gson().toJson(SENSOR_COLOR_TRANSFORM1))
        config.put("SENSOR_COLOR_TRANSFORM2", Gson().toJson(SENSOR_COLOR_TRANSFORM2))
        config.put("SENSOR_FORWARD_MATRIX2", Gson().toJson(SENSOR_FORWARD_MATRIX2))
        config.put("SENSOR_BLACK_LEVEL_PATTERN", Gson().toJson(SENSOR_BLACK_LEVEL_PATTERN))
        config.put("SENSOR_MAX_ANALOG_SENSITIVITY", Gson().toJson(SENSOR_MAX_ANALOG_SENSITIVITY))
        config.put("SENSOR_ORIENTATION", Gson().toJson(SENSOR_ORIENTATION))
        config.put(
            "SENSOR_AVAILABLE_TEST_PATTERN_MODES",
            Gson().toJson(SENSOR_AVAILABLE_TEST_PATTERN_MODES)
        )
        config.put("SENSOR_OPTICAL_BLACK_REGIONS", Gson().toJson(SENSOR_OPTICAL_BLACK_REGIONS))
        config.put("SHADING_AVAILABLE_MODES", Gson().toJson(SHADING_AVAILABLE_MODES))
        config.put(
            "STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES",
            Gson().toJson(STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)
        )
        config.put("STATISTICS_INFO_MAX_FACE_COUNT", Gson().toJson(STATISTICS_INFO_MAX_FACE_COUNT))
        config.put(
            "STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES",
            Gson().toJson(STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES)
        )
        config.put(
            "STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES",
            Gson().toJson(STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES)
        )
        config.put(
            "STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES",
            Gson().toJson(STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES)
        )
        config.put("TONEMAP_MAX_CURVE_POINTS", Gson().toJson(TONEMAP_MAX_CURVE_POINTS))
        config.put(
            "TONEMAP_AVAILABLE_TONE_MAP_MODES",
            Gson().toJson(TONEMAP_AVAILABLE_TONE_MAP_MODES)
        )
        config.put("INFO_SUPPORTED_HARDWARE_LEVEL", Gson().toJson(INFO_SUPPORTED_HARDWARE_LEVEL))
        config.put("INFO_VERSION", Gson().toJson(INFO_VERSION))
        config.put("SYNC_MAX_LATENCY", Gson().toJson(SYNC_MAX_LATENCY))
        config.put("REPROCESS_MAX_CAPTURE_STALL", Gson().toJson(REPROCESS_MAX_CAPTURE_STALL))
        config.put("DEPTH_DEPTH_IS_EXCLUSIVE", Gson().toJson(DEPTH_DEPTH_IS_EXCLUSIVE))
        config.put(
            "LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE",
            Gson().toJson(LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE)
        )
        config.put(
            "DISTORTION_CORRECTION_AVAILABLE_MODES",
            Gson().toJson(DISTORTION_CORRECTION_AVAILABLE_MODES)
        )

        sharedPrefManager.setCameraParams(config.toString())
    }

    private fun getStringFPS(CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES: Array<android.util.Range<Int>>?): String {
        CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES?.let {
            var data = "{"
            for (x in it) {
                data = data + "(" + x.lower + "," + x.upper + ")"
            }
            data = "$data}"
            return data
        } ?: run {
            return "null"
        }
    }

    private fun AE_COMPENSATION_RANGE(CONTROL_AE_COMPENSATION_RANGE: Range<Int>?): String {
        CONTROL_AE_COMPENSATION_RANGE?.let {
            return "{(" + it.lower + "," + it.upper + ")}"
        } ?: run {
            return "null"
        }
    }
}