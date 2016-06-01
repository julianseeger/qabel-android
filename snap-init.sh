#!/bin/bash

# raise an error if any command fails!
set -e
set -x

# existance of this file indicates that all dependencies were previously installed, and any changes to this file will use a different filename.
INITIALIZATION_FILE="$ANDROID_HOME/.initialized-dependencies-ndk-$(git log -n 1 --format=%h -- $0)"

if [ ! -e ${INITIALIZATION_FILE} ]; then

    if [ -d ${SNAP_CACHE_DIR}/.androidy ]; then
        cp -r ${SNAP_CACHE_DIR}/.android ${ANDROID_HOME}/
    else
        # fetch and initialize $ANDROID_HOME
        download-android
        # Use the latest android sdk tools
        echo y | android update sdk --no-ui --filter platform-tool > /dev/null
        echo y | android update sdk --no-ui --filter tool > /dev/null
      
        # The BuildTools version used by your project
        echo y | android update sdk --no-ui --filter build-tools-23.0.1 --all > /dev/null
      
        # The SDK version used to compile your project
        echo y | android update sdk --no-ui --filter android-23 > /dev/null
      
        # uncomment to install the Extra/Android Support Library
         echo y | android update sdk --no-ui --filter extra-android-support --all > /dev/null
      
      
      
        # uncomment these if you are using maven/gradle to build your android project
         echo y | android update sdk --no-ui --filter extra-google-m2repository --all > /dev/null
         echo y | android update sdk --no-ui --filter extra-android-m2repository --all > /dev/null
      
        # Specify at least one system image if you want to run emulator tests
        echo y | android update sdk --no-ui --filter sys-img-armeabi-v7a-android-23 --all > /dev/null
    
      cp -r ${ANDROID_HOME}/.android ${SNAP_CACHE_DIR}/
    fi
    
    if [ ! -d ~/android-ndk-r11c ]; then
        if [ -d ${SNAP_CACHE_DIR}/android-ndk-r11c ]; then
            if [ "${SNAP_CACHE_DIR}" != "${HOME}" ]; then
                cp -r ${SNAP_CACHE_DIR}/android-ndk-r11c ~/android-ndk-r11c
            fi
        else
            wget http://dl.google.com/android/repository/android-ndk-r11c-linux-x86_64.zip -O ndk.zip
            unzip -q ndk.zip
            mv android-ndk-r11c ~/android-ndk-r11c
            if [ "${SNAP_CACHE_DIR}" != "${HOME}" ]; then
                cp -r ~/android-ndk-r11c ${SNAP_CACHE_DIR}/android-ndk-r11c
            fi
        fi
    fi
    export PATH=${PATH}:~/android-ndk-r11c
    touch ${INITIALIZATION_FILE}
fi
