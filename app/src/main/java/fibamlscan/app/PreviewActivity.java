// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package fibamlscan.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityCompat.OnRequestPermissionsResultCallback;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import fibamlscan.library.BarcodeScanningProcessor;
import fibamlscan.library.BarcodeType;
import fibamlscan.library.CameraSource;
import fibamlscan.library.CameraSourcePreview;
import fibamlscan.library.GraphicOverlay;

/** This class is used to set up continuous frame processing on frames from a camera source. */
public final class PreviewActivity extends AppCompatActivity implements OnRequestPermissionsResultCallback {
  private static final String TAG = "PreviewActivity";
  private static final String EXTRA_BARCODE_FORMAT = "EXTRA_BARCODE_FORMAT";
  public final static String RETURN_BARCODE = "RETURN_BARCODE";
  private static final int PERMISSION_REQUESTS = 1;

  private CameraSource cameraSource = null;
  private CameraSourcePreview preview;
  private GraphicOverlay graphicOverlay;
  private Button toggleFlash, setFlash;

  public static Intent getStartingIntent(Context context){
    return new Intent(context, PreviewActivity.class);
  }

  public static Intent getStartingIntent(Context context, int barcodeFormat){
    return getStartingIntent(context)
            .putExtra(EXTRA_BARCODE_FORMAT,barcodeFormat);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(fibamlscan.library.R.layout.activity_live_preview);

    preview = (CameraSourcePreview) findViewById(fibamlscan.library.R.id.preview);
    graphicOverlay = (GraphicOverlay) findViewById(fibamlscan.library.R.id.overlay);
    toggleFlash = (Button) findViewById(R.id.toggleFlash);
    setFlash = (Button) findViewById(R.id.setFlash);

    toggleFlash.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        if (cameraSource!=null) {
          cameraSource.toggleFlash();
          updateFlashUi();
        }
      }
    });

    setFlash.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        if (cameraSource!=null){
          cameraSource.setFlash(!cameraSource.getFlash());
          updateFlashUi();
        }
      }
    });

    if (allPermissionsGranted()) {
      createCameraSource();
    } else {
      getRuntimePermissions();
    }
  }

  private void updateFlashUi(){
    if (cameraSource==null){
      toggleFlash.setVisibility(View.GONE);
      setFlash.setVisibility(View.GONE);
    } else {
      toggleFlash.setVisibility(View.VISIBLE);
      setFlash.setVisibility(View.VISIBLE);
      setFlash.setText(cameraSource.getFlash()
              ? "Turn flashlight off"
              : "Turn flashlight on"
      );
    }
  }

  private void createCameraSource() {
    // If there's no existing cameraSource, create one.
    if (cameraSource == null) {
      cameraSource = new CameraSource(this, graphicOverlay);
    }

    int barcodeFormat = BarcodeType.ALL_FORMATS.type;
    if (getIntent()!=null) {
      barcodeFormat = getIntent().getIntExtra(EXTRA_BARCODE_FORMAT,barcodeFormat);
    }

    cameraSource.setMachineLearningFrameProcessor(new BarcodeScanningProcessor(new BarcodeScanningProcessor.OnBarcode() {
      @Override public void onBarcode(String barcode) {
        setResult(Activity.RESULT_OK, new Intent().putExtra(RETURN_BARCODE,barcode));
        finish();
      }
    },barcodeFormat));

    updateFlashUi();
  }

  /**
   * Starts or restarts the camera source, if it exists. If the camera source doesn't exist yet
   * (e.g., because onResume was called before the camera source was created), this will be called
   * again when the camera source is created.
   */
  private void startCameraSource() {
    if (cameraSource != null) {
      try {
        preview.start(cameraSource, graphicOverlay);
      } catch (IOException e) {
        Log.e(TAG, "Unable to start camera source.", e);
        cameraSource.release();
        cameraSource = null;
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.d(TAG, "onResume");
    startCameraSource();
  }

  /** Stops the camera. */
  @Override
  protected void onPause() {
    super.onPause();
    preview.stop();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (cameraSource != null) {
      cameraSource.release();
    }
  }

  private String[] getRequiredPermissions() {
    try {
      PackageInfo info =
          this.getPackageManager()
              .getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
      String[] ps = info.requestedPermissions;
      if (ps != null && ps.length > 0) {
        return ps;
      } else {
        return new String[0];
      }
    } catch (Exception e) {
      return new String[0];
    }
  }

  private boolean allPermissionsGranted() {
    for (String permission : getRequiredPermissions()) {
      if (!isPermissionGranted(this, permission)) {
        return false;
      }
    }
    return true;
  }

  private void getRuntimePermissions() {
    List<String> allNeededPermissions = new ArrayList<>();
    for (String permission : getRequiredPermissions()) {
      if (!isPermissionGranted(this, permission)) {
        allNeededPermissions.add(permission);
      }
    }

    if (!allNeededPermissions.isEmpty()) {
      ActivityCompat.requestPermissions(
          this, allNeededPermissions.toArray(new String[0]), PERMISSION_REQUESTS);
    }
  }

  @Override
  public void onRequestPermissionsResult(
          int requestCode, String[] permissions, int[] grantResults) {
    Log.i(TAG, "Permission granted!");
    if (allPermissionsGranted()) {
      createCameraSource();
    }
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  private static boolean isPermissionGranted(Context context, String permission) {
    if (ContextCompat.checkSelfPermission(context, permission)
        == PackageManager.PERMISSION_GRANTED) {
      Log.i(TAG, "Permission granted: " + permission);
      return true;
    }
    Log.i(TAG, "Permission NOT granted: " + permission);
    return false;
  }
}
