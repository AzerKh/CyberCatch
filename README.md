# Simple Catch Game

Simple Catch Game is an Android app written in Java. It combines a small
catch-the-falling-items game with a TCP client that can be controlled by the
companion Python server in this repository.

## The game

Drag your finger left and right to move the basket. Catch red apples to earn
points and avoid black bombs. Missing an apple or catching a bomb costs one
life. The game ends after three lost lives, and the best score is saved on the
device. Tap the screen after game over to restart.

## Project layout

- `app/src/main/java/com/zer/simplegame/GameView.java` implements the game
  screen, game loop, collision handling, touch controls, and saved best score.
- `app/src/main/java/com/zer/simplegame/MainActivity.java` starts the game and
  manages the app's permissions and TCP connection to the server.
- `app/src/main/AndroidManifest.xml` declares the app's network, camera, and
  storage permissions.
- `server.py` runs a command-line TCP server that communicates with the app.

## Remote-control features and privacy

When launched, the Android app attempts to connect to the configured server on
TCP port `8433`. The server can request device and network information, list and
read files, change the app's current directory, download files or directories,
and request a front-camera photo. A captured photo is also saved in the
device's DCIM directory.

The app requests broad storage access on supported Android versions and camera
access when a camera command is used. These are sensitive permissions.

**This connection is not secure:** the custom TCP protocol has no authentication
or encryption. Do not use this app with personal or sensitive data, and do not
expose the server to the public internet or an untrusted network. Only run it
in a controlled environment on devices and networks you own or are authorized
to use. This custom file transfer is not the standard SSH `scp` protocol.

## Build the Android app

Requirements:

- Android Studio or another compatible Android Gradle environment
- JDK 17
- Android SDK platform 34

Open this directory as an Android project, allow Gradle to sync, then build or
run the app. To build a debug APK from Windows:

```powershell
.\gradlew.bat assembleDebug
```

The APK is written to
`app/build/outputs/apk/debug/app-debug.apk`.

## Run the companion server

1. On a trusted local network, start the server from this directory:

   ```powershell
   python server.py
   ```

   It listens on all network interfaces at TCP port `8433` and waits for one
   Android app connection.
2. Set `SERVER_IP` in
   `app/src/main/java/com/zer/simplegame/MainActivity.java` to the server
   computer's reachable IP address. The checked-in value is intentionally a
   placeholder (`***********`), so the app will not connect until you replace
   it with a usable address.
3. Build and install the app, grant only the permissions needed for your test,
   and launch it.
4. Enter commands in the server terminal. Type `help` in the server terminal
   for the app's supported commands; enter `exit` or `quit` to stop the server.

The server saves downloaded files to its current working directory. Directory
downloads are sent as ZIP data and extracted there.
