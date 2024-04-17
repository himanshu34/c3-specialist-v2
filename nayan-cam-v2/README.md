# nayan-cam-v2
Camera module for nayan app

## Integrating the Driver module

Note: This module needs certain dependencies to work, those dependencies should be provides by caller module


* Override NayanCamModuleInteractor in the caller module
* Provide CameraConfig by using Builder from Caller Module
* Call moveToDriverApp() method of NayanCamModuleInteractor to start the camera


## CameraConfig - This class is used to set different type parameter for camera

 * The same module is being used for Dubai Police Driver APP
 * For dubai police driver APP switch nayan-cam-v2 branch to feature/dp-support and  and  core to feature/dp_support
 * set isDubaiPoliceEnabled inside cameraConfig to "true"


## NayanCamModuleInteractor- this class is used to interact between camera modules and other modules

  * caller module needs to override this class
  * Provide the Impl class instance inside the DI module class


## CameraHelper
  * This class handles all the functionality related to camera
  * The instance of this class is being provided using Dagger
  * setUpNayanCamera() should be called to initialize the camera to start the camera


## CameraPreviewListener
  * This class overrides the OnImageAvailableListener from the Image Reader
  * inside onImageAvailable() the image will be processed

## ObjectOfInterestHandler(Abstract class to handle all the image processing)
   -> CameraProcessor
      * This class will process image coming from camera
      
   -> VideoProcessor(Only For testing)
      * This class will process the image coming from video frames




