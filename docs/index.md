The Open Media Streamer Client Android SDK Documentation
===============================
# 1 Introduction {#section1}
The Open Media Streamer Client SDK for Android, OMS Android briefly, provides helpful tools (including a sample Android application) for developing Android native WebRTC applications using Java APIs. The SDK is distributed in the `CS_WebRTC_Client_SDK_Android.<ver>.zip` release package.

Please refer to the Release Notes for the latest information in the SDK release package, including new features, fixed bugs and known issues.

# 2 Supported platforms {#section2}
OMS Android requires using Android SDK API level 16 or above. In order to use hardware media codec, API level 19 is recommended.

The following devices have been validated with our SDK:

   + Huawei* Nexus 6P (Android 8.0.0)
   + OPPO* R11 (Android 7.1.1)
   + LG* Nexus 5X (Android 7.1.1)
   + Sumsung* S8 (Android 7.0)
   + Huawei* Mate9 (Android 7.0)
   + Sumsung* S6 (Android 6.0.1)
   + Huawei* NXT-AL10 (Android 6.0)
   + Xiaomi* MI5 (Android 6.0)
   + vivo* X7 (Android 5.1.1)
   + LG Nexus 5 (Android 5.1.1)
   + Xiaomi* pad2 (Android 5.1)
   + Huawei* P8 (Android 5.0)
   + Xiaomi* MI4 (Android 4.4.4)
   + OPPO* R7 (Android 4.4.4)

# 3 Development {#section3}

## 3.1 Prerequisite {#section3_1}
First, be sure to install the prerequisite software.

1. [Download Android Studio*](http://developer.android.com/sdk/index.html)
2. [Install the Android SDK with target API level 16 or above](http://developer.android.com/tools/sdk/tools-notes.html)
3. Configure Android SDK path for gradle: create `local.properties` file in project root directory and add the following line to it:

        sdk.dir=/path/to/your/android/sdk/location

## 3.2 Sample applications {#section3_2}
Sample Android application projects are provided along with OMS Android release package, including a peer-to-peer application and a conference application. To build and run sample apps, please follow below steps:
Please notice that there are some linux format link files in sample dependency folders, modify them before you run on a different platform.

### 3.2.1 Run samples with Android Studio {#section3_2_1}

1.  Open Android Studio
2.  Import sample project by "Open an existing Android Studio project"
3.  Build and run samples

### 3.2.2 Run samples with Gradle in the console {#section3_2_2}

      cd /path/to/dist/samples/folder
      ./gradlew assembleRelease

Apk files will be located in `build/outputs/apk` folder.

## 3.3 Developing Android applications with OMS Android {#section3_3}
Follow these general steps to create an Android application using OMS Android:

1. Create an Android Application project in Android Studio.
2. Create a libs/ folder under the project directory.
3. Copy oms_base.jar and oms_p2p.jar(or oms_conference.jar) from the libs/ folder in release package to the libs/ folder created in step 2.
4. Create jniLibs/ folder under project/src/main directory.
5. Copy the libjingle_peerconnection_so.so from libs/webrtc/<target_arch> of our Android client SDKto jniLibs/<target_arch> folders.
6. Add all the JAR (Java archive) files dependencies in your project build.gradle.
7. Now start to develop your own Android application using the P2P and/or Conference APIs.

> **Note:** Upon initialization, ContextInitialization.initialize() is required to be called. The Context parameter should be the launch activity your application runs, and you should create an EglBase object in your launch activity and then pass the EGLContext to the second parameter. More details please refer to the sample applications.

> **Note:** You may need to add or require specific permissions for your application.

# 4 Media codecs {#section4}

## 4.1 Video codecs {#section4_1}

OMS Android supports VP8, VP9, H.264 and HEVC video codecs depending on the device hardware. Use the following APIs to set the preferred video codec:

  + `oms.conference.PublishOptions.addVideoParameter`
  + `oms.conference.SubscribeOptions.setVideoOption`
  + `oms.p2p.P2PClientConfiguration.addVideoParameters`

> **Note:** Hardware encoder/decoder requires Android API level 19 or above.

## 4.2 Audio codecs {#section4_2}

OMS Android supports PCMU, OPUS and other codecs depending on the device hardware. Use the following API to set the preferred audio codec for recording:

  + `oms.conference.PublishOptions.addAudioParameter`
  + `oms.conference.SubscribeOptions.setAudioOption`
  + `oms.p2p.P2PClientConfiguration.addAudioParameters`

# 5 NAT and firewall traversal {#section5}

OMS Android fully supports NAT and firewall traversal with STUN / TURN / ICE. [The Coturn TURN server](https://github.com/coturn/coturn) could be one choice.

# 6 Customize signaling channel {#section6}

Signaling channel is an implementation to transmit signaling data for creating a WebRTC session. Signaling channel for P2P sessions can be customized by implementing `SignalingChannelInterface`. We provide a default `SocketSignalingChannel` in sample which works with PeerServer in the release package.

`P2PClient` implements `SignalingChannelObserver`, so you can invoke its methods to notify `P2PClient` when a new message is coming or the connection is lost.

# 7 Customize renderer {#section7}

OMS Android uses `org.webrtc.SurfaceViewRenderer` in the sample apps, which can be used to render a stream on a SurfaceView.  If you would like to customize your own renderer, follow these steps:

1.  Create your own renderer class which implements the interface `org.webrtc.VideoRenderer.Callbacks`;
2.  In the renderer class you have to implement the method `renderFrame(org.webrtc.VideoRenderer.I420Frame)`;
3.  The frames will be passed to renderFrame(). So in this method you can use your own way to render each frame;
4.  Every time you finish rendering a frame, you must call org.webrtc.VideoRenderer.renderFrameDone(frame).

# 8 Customize SSL context {#section8}

Conference SDK allows customized SSL context when connecting to MCU server. That means you can change the behavior of how client verifies server's certificate. This may be useful if you want to use self-signed certificate during development. Make sure your SSL context is secure enough when using it in production environment.
To use a customized SSL context, please use APIs below:

  + `oms.conference.ConferenceClientConfiguration.setSSLContext`
  + `oms.conference.ConferenceClientConfiguration.setHostnameVerifier`

Please refer to [detailed description of SSL/TLS](https://developer.android.com/training/articles/security-ssl.html).

# 9 Customize video input {#section9}

Instead of getting video frames from device camera, OMS Android allows customizing video input which enables media stream to get video frames from the source that application customizes. To set a customized video input, please follow these steps:

1. Implement `oms.base.VideoCapturer` interface;
2. Create `oms.base.LocalStream` with the object instance of the class implemented in step 1;

# 10 Customize video encoder/decoder {#section10}

Instead of using Android mediacodec APIs to utilize hardware codes on the devices, OMS Android allows customizing video encoder/decoder to encode/decode video streams.

  + `oms.base.ContextInitialization.setCustomizedVideoEncoderFactory`
  + `oms.base.ContextInitialization.setCustomizedVideoDecoderFactory`

# 11 Customize audio input {#section11}

Instead of getting audio data from device microphone, our Android client SDK allows customizing audio input which enables media stream to get audio data from the source that application customizes. To set a customized audio input, please follow these steps:

1. Implement `oms.base.IcsAudioRecord.AudioFrameGeneratorInterface`;
2. Call WebRtcUtils.setCustomizedAudioInput(AudioFrameGeneratorInterface) before instantiating PeerClient or ConferenceClient;

> **Note:** \* Other names and brands may be claimed as the property of others.
