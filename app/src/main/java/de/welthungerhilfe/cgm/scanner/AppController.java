/**
 *  Child Growth Monitor - quick and accurate data on malnutrition
 *  Copyright (c) $today.year Welthungerhilfe Innovation
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.welthungerhilfe.cgm.scanner;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.StrictMode;

//import com.amitshekhar.DebugDB;
import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.crashlytics.android.core.CrashlyticsListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;

import de.welthungerhilfe.cgm.scanner.helper.AppConstants;
import de.welthungerhilfe.cgm.scanner.helper.LanguageHelper;
import de.welthungerhilfe.cgm.scanner.helper.service.UploadService;
import de.welthungerhilfe.cgm.scanner.utils.Utils;
import io.fabric.sdk.android.Fabric;

public class AppController extends Application {
    public static final String TAG = AppController.class.getSimpleName();

    private static AppController mInstance;

    public FirebaseAuth firebaseAuth;
    public FirebaseUser firebaseUser;

    public FirebaseStorage firebaseStorage;
    public StorageReference storageRootRef;

    public FirebaseFirestore firebaseFirestore;

    public FirebaseRemoteConfig firebaseConfig;

    @Override
    public void onCreate() {
        super.onCreate();

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());

        CrashlyticsCore core = new CrashlyticsCore
                .Builder()
                .listener(new CrashlyticsListener() {
                    @Override
                    public void crashlyticsDidDetectCrashDuringPreviousExecution() {
                        // TODO: do something when crash occurs
                    }
                })
                .build();

        final Fabric fabric = new Fabric.Builder(this)
                .kits(new Crashlytics.Builder().core(core).build())
                .debuggable(true)
                .build();
        Fabric.with(fabric);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        Utils.overrideFont(getApplicationContext(), "SERIF", "roboto.ttf");

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();

        // firebase_database_url is generated by google services plugin
        // https://developers.google.com/android/guides/google-services-plugin
        firebaseStorage = FirebaseStorage.getInstance("gs://"+R.string.google_storage_bucket);

        storageRootRef = firebaseStorage.getReference();

        firebaseFirestore = FirebaseFirestore.getInstance();

        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false)
                .build();
        firebaseFirestore.setFirestoreSettings(settings);

        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder().setDeveloperModeEnabled(true).build();
        firebaseConfig = FirebaseRemoteConfig.getInstance();
        firebaseConfig.setConfigSettings(configSettings);
        firebaseConfig.setDefaults(R.xml.remoteconfig);

        //Log.e("Offline DB", DebugDB.getAddressLog());

        notifyUpload();

        mInstance = this;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LanguageHelper.onAttach(base));
    }

    public boolean isAdmin() {
        boolean isAdmin = false;

        String currentUser = AppController.getInstance().firebaseAuth.getCurrentUser().getEmail();
        String[] admins = AppController.getInstance().firebaseConfig.getString(AppConstants.CONFIG_ADMINS).split(",");

        for (int i = 0; i < admins.length; i++) {
            if (admins[i].equals(currentUser)) {
                isAdmin = true;
                break;
            }
        }

        return isAdmin;
    }

    public static synchronized AppController getInstance() {
        return mInstance;
    }

    public void prepareFirebaseUser() {
        firebaseUser = firebaseAuth.getCurrentUser();
    }

    public String getPersonId(String name) {
        return Utils.getAndroidID(getContentResolver()) + "_" + name + "_" + Utils.getUniversalTimestamp() + "_" + Utils.getSaltString(16);
    }

    public String getMeasureId() {
        return Utils.getAndroidID(getContentResolver()) + "_measure_" + Utils.getUniversalTimestamp() + "_" + Utils.getSaltString(16);
    }

    public String getArtefactId(String type) {
        return Utils.getAndroidID(getContentResolver()) + "_" + type + "_" + Utils.getUniversalTimestamp() + "_" + Utils.getSaltString(16);
    }

    public String getArtefactId(String type, long timestamp) {
        return Utils.getAndroidID(getContentResolver()) + "_" + type + "_" + String.valueOf(timestamp) + "_" + Utils.getSaltString(16);
    }

    public File getRootDirectory() {
        File mExtFileDir;
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            mExtFileDir = new File(Environment.getExternalStorageDirectory(), getString(R.string.app_name_long));
        } else {
            mExtFileDir = getApplicationContext().getFilesDir();
        }

        return mExtFileDir;
    }

    public void notifyUpload() {
        startService(new Intent(getApplicationContext(), UploadService.class));
    }
}
