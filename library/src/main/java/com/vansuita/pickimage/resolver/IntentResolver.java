package com.vansuita.pickimage.resolver;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.vansuita.pickimage.R;
import com.vansuita.pickimage.bundle.PickSetup;
import com.vansuita.pickimage.enums.EPickType;
import com.vansuita.pickimage.keep.Keep;
import com.vansuita.pickimage.listeners.IPickError;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jrvansuita build 07/02/17.
 */

public class IntentResolver {

    public static final int REQUESTER = 99;
    public static final String SAVE_FILE_PATH_TAG = "savePath";

    private AppCompatActivity activity;

    private PickSetup setup;
    private Intent galleryIntent;
    private Intent cameraIntent;
    private File saveFile;

    private IPickError onErrorListener;

    public IntentResolver(AppCompatActivity activity, PickSetup setup, Bundle savedInstanceState) {
        this.activity = activity;
        this.setup = setup;

        if (savedInstanceState != null) {
            onRestoreInstanceState(savedInstanceState);
        }
    }

    private Intent loadSystemPackages(Intent intent) {
        List<ResolveInfo> resInfo = activity.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_SYSTEM_ONLY);

        if (!resInfo.isEmpty()) {
            String packageName = resInfo.get(0).activityInfo.packageName;
            intent.setPackage(packageName);
        }

        return intent;
    }

    public boolean isCamerasAvailable() {
        String feature = PackageManager.FEATURE_CAMERA;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            feature = PackageManager.FEATURE_CAMERA_ANY;
        }

        return activity.getPackageManager().hasSystemFeature(feature);
    }

    private Intent getCameraIntent() {
        if (cameraIntent == null) {
            if (setup.isVideo()) {
                cameraIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            } else {
                cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            }
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUriForProvider());

            applyProviderPermission();
        }

        return cameraIntent;
    }

    public void launchCamera(Fragment listener) {

            cameraFile().delete();

            Intent chooser = Intent.createChooser(getCameraIntent(), setup.getCameraChooserTitle());
            listener.startActivityForResult(chooser, REQUESTER);
    }

    /**
     * Granting permissions to write and read for available cameras to file provider.
     */
    private void applyProviderPermission() {
        List<ResolveInfo> resInfoList = activity.getPackageManager().queryIntentActivities(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo resolveInfo : resInfoList) {
            String packageName = resolveInfo.activityInfo.packageName;
            activity.grantUriPermission(packageName, cameraUriForProvider(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
    }

    private File cameraFile() {
        if (saveFile != null) {
            return saveFile;
        }

        File directory = new File(activity.getFilesDir(), "picked");
        String fileName = activity.getString(R.string.image_file_name);

        directory.mkdirs();
        saveFile = new File(directory, fileName);
        Log.i("File-PickImage", saveFile.getAbsolutePath());

        return saveFile;
    }

    public Uri cameraUri() {
        return Uri.fromFile(cameraFile());
    }

    private String getAuthority() {
        return activity.getApplication().getPackageName() + activity.getString(R.string.provider_package);
    }

    private Uri cameraUriForProvider() {
        try {
            return FileProvider.getUriForFile(activity, getAuthority(), cameraFile());
        } catch (Exception e) {
            if (e.getMessage().contains("ProviderInfo.loadXmlMetaData")) {
                throw new Error(activity.getString(R.string.wrong_authority));
            } else {
                throw e;
            }
        }
    }

    private Intent getGalleryIntent() {
        if (galleryIntent == null) {
            galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            if (setup.isVideo()) {
                galleryIntent.setType(activity.getString(R.string.video_content_type));
            } else {
                galleryIntent.setType(activity.getString(R.string.image_content_type));
            }
        }

        return galleryIntent;
    }

    public void launchGallery(Fragment listener) {
        try {
            listener.startActivityForResult(loadSystemPackages(getGalleryIntent()), REQUESTER);
        } catch (Exception exception) {
            if (onErrorListener != null) {
                onErrorListener.onErrorLaunchingGallery(exception);
            } else {
                throw exception;
            }
        }
    }

    public void launchSystemChooser(Fragment listener) {
        Intent chooserIntent;
        List<Intent> intentList = new ArrayList<>();

        boolean showCamera = EPickType.CAMERA.inside(setup.getPickTypes());
        boolean showGallery = EPickType.GALLERY.inside(setup.getPickTypes());

        if (showGallery)
            intentList.add(getGalleryIntent());

        if (showCamera && isCamerasAvailable() && !wasCameraPermissionDeniedForever())
            intentList.add(getCameraIntent());

        if (intentList.size() > 0) {
            chooserIntent = Intent.createChooser(intentList.remove(intentList.size() - 1), setup.getTitle());
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentList.toArray(new Parcelable[]{}));
            listener.startActivityForResult(chooserIntent, REQUESTER);
        }
    }

    public boolean wasCameraPermissionDeniedForever() {
        if (Keep.with(activity).neverAskedForPermissionYet())
            return false;

        if (((ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED)
                && (!ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)))) {
            return true;
        }

        return false;
    }

    public boolean requestCameraPermissions(Fragment listener) {
        return requestPermissions(listener, Manifest.permission.CAMERA);
    }

    /**
     * resquest permission to use camera and write files
     */
    public boolean requestPermissions(Fragment listener, String... permissionsNeeded) {
        List<String> list = new ArrayList<>();

        for (String permission : permissionsNeeded)
            if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_DENIED)
                list.add(permission);


        if (list.isEmpty())
            return true;


        listener.requestPermissions(list.toArray(new String[list.size()]), REQUESTER);
        return false;
    }

    public boolean fromCamera(Intent data) {
        return (data == null || data.getData() == null || data.getData().toString().contains(cameraFile().toString()) || data.getData().toString().toString().contains("to_be_replaced"));
    }

    public AppCompatActivity getActivity() {
        return activity;
    }

    private void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        final String saveFilePath = savedInstanceState.getString(SAVE_FILE_PATH_TAG);

        if (saveFilePath != null) {
            saveFile = new File(saveFilePath);
        }
    }

    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (saveFile != null) {
            outState.putString(SAVE_FILE_PATH_TAG, saveFile.getAbsolutePath());
        }
    }

    public void setErrorListener(IPickError onError) {
        this.onErrorListener = onError;
    }
}
