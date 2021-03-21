package com.example.imagegallerysaver;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

public class ImageGallerySaverPlugin implements MethodChannel.MethodCallHandler {

  private PluginRegistry.Registrar registrar;
  Handler handler;
  private final ExecutorService executorService;

  ImageGallerySaverPlugin(PluginRegistry.Registrar registrar) {
    this.registrar = registrar;
    handler = new Handler(Looper.getMainLooper());
    executorService = Executors.newCachedThreadPool();
  }

  public static void registerWith(PluginRegistry.Registrar registrar) {
    MethodChannel channel = new MethodChannel(registrar.messenger(), "image_gallery_saver");
    channel.setMethodCallHandler(new ImageGallerySaverPlugin(registrar));
  }

  @Override
  public void onMethodCall(MethodCall call, MethodChannel.Result result) {
    switch (call.method) {
      case "saveImageToGallery":
        byte[] image = call.argument("imageBytes");
        int quality = call.argument("quality");
        String name = call.argument("name");

        //todo
//                result.success(saveImageToGallery(BitmapFactory.decodeByteArray(image, 0, image.length), quality, name));
        break;
      case "saveFileToGallery":
        String path = call.argument("file");
        executorService.execute(() -> {
          try {
            String s = saveFileToGallery(path, result);
            if (s==null){
              return;
            }
            handler.post(() -> {
              result.success(result("".equals(s), path, s));
            });
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
        break;
    }
  }

  private String insertImage(String imagePath, Context context) {
    ContentValues values = new ContentValues();
    File file = new File(imagePath);
    values.put(MediaStore.Images.Media.DISPLAY_NAME, file.getName());
    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
    ContentResolver contentResolver = context.getContentResolver();
    Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
    Uri item = contentResolver.insert(collection, values);
    try {
      OutputStream outputStream = contentResolver.openOutputStream(item);
      FileInputStream fileInputStream = new FileInputStream(file);
      byte[] buffer = new byte[2048];
      while (fileInputStream.read(buffer) != -1) {
        outputStream.write(buffer);
        outputStream.flush();
      }
      outputStream.close();
      fileInputStream.close();
      contentResolver.update(item, values, null, null);
      return "";
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      return e.getMessage();
    } catch (IOException e) {
      e.printStackTrace();
      return e.getMessage();
    }
  }

  private String saveFileToGallery(String filePath, MethodChannel.Result result) {
    Context context = registrar.activeContext();
    if (Build.VERSION.SDK_INT >= 23) {
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,}, 9001);

        registrar.addRequestPermissionsResultListener((int requestCode, String[] permissions, int[] grantResults) -> {
          if (requestCode!=9001){
            return true;
          }
          boolean granted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
          if (granted) {
            executorService.execute(() -> {
              String s = saveFileToGallery(filePath, result);
              handler.post(()->{
                result.success(result("".equals(s), filePath, s));
              });
            });
          } else {
            result.success(result(false, filePath, "No permission~"));
          }
          return false;
        });
        return null;
      }
    }
    if (Build.VERSION.SDK_INT >= 28) {
      return insertImage(filePath, context);
    }
    return lowVersion(filePath, context);
  }

  private String lowVersion(String filePath, Context context) {
    File originalFile = new File(filePath);
    File file = generateFile(".jpg", null);
    try {
      FileInputStream fileInputStream = new FileInputStream(originalFile);
      FileOutputStream fileOutputStream = new FileOutputStream(file);
      byte[] buffer = new byte[2048];
      while (fileInputStream.read(buffer) != -1) {
        fileOutputStream.write(buffer);
        fileOutputStream.flush();
      }
      fileOutputStream.close();
      fileInputStream.close();
      Uri uri = Uri.fromFile(file);
      context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
      return "";
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      return e.getMessage();
    } catch (IOException e) {
      e.printStackTrace();
      return e.getMessage();
    }
  }


  private File generateFile(String extension, String name) {
    String storePath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + Environment.DIRECTORY_PICTURES;
    File appDir = new File(storePath);
    if (!appDir.exists()) {
      appDir.mkdir();
    }
    String fileName = TextUtils.isEmpty(name) ? String.valueOf(System.currentTimeMillis()) : name;
    if (!TextUtils.isEmpty(extension)) {
      fileName += (".$extension");
    }
    return new File(appDir, fileName);
  }


  public HashMap<String, Object> result(boolean isSuccess, String filePath, String errorMessage) {
    HashMap<String, Object> hashMap = new HashMap();
    hashMap.put("isSuccess", isSuccess);
    hashMap.put("filePath", filePath);
    hashMap.put("errorMessage", errorMessage);
    return hashMap;
  }

}
