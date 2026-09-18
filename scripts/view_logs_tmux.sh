#!/bin/bash
# sudo apt install tmux
# brew install tmux
#etc

if [[ "$1" == "--service" ]]; then
        adb logcat -v color --pid=$(adb shell pidof -s "org.torproject.android.debug:tor")
        exit
fi

tmux new-session -s 'App&Tor' -d 'adb logcat  --pid=$(adb shell pidof -s "org.torproject.android.debug") -v color'\; split-window -v 'adb logcat  --pid=$(adb shell pidof -s "org.torproject.android.debug:tor") -v color'\; set -g mouse on\; attach
