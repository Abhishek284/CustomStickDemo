package com.dji.importSDKDemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.remotecontroller.AircraftMapping;
import dji.common.remotecontroller.AircraftMappingStyle;
import dji.common.remotecontroller.AircraftStickMapping;
import dji.common.remotecontroller.AircraftStickMappingTarget;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getName();
    public static final String FLAG_CONNECTION_CHANGE = "dji_sdk_connection_change";
    private static BaseProduct mProduct;
    private Handler mHandler;

    private Button customButton;
    private Button modeTwoButton;
    private Button modeOneButton;

    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
    };
    private List<String> missingPermission = new ArrayList<>();
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private static final int REQUEST_PERMISSION_CODE = 12345;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // When the compile and target version is higher than 22, please request the following permission at runtime to ensure the SDK works well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkAndRequestPermissions();
        }

        setContentView(R.layout.activity_main);

        //Initialize DJI SDK Manager
        mHandler = new Handler(Looper.getMainLooper());


        //Set buttons
        setLeftStickHorizontalMapping(AircraftStickMappingTarget.ROLL, false);
        setLeftStickVerticalMapping(AircraftStickMappingTarget.PITCH, false);
        setRightStickHorizontalMapping(AircraftStickMappingTarget.YAW, false);
        setRightStickVerticalMapping(AircraftStickMappingTarget.THROTTLE, false);
        customButton = findViewById(R.id.button_custom);
        modeTwoButton = findViewById(R.id.button_mode_two);
        modeOneButton = findViewById(R.id.button_mode_one);
        customButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setNewAircraftStickMapping();
            }
        });

        modeTwoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setModeTwo();
            }
        });

        modeOneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setModeOne();
            }
        });
    }

    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            showToast("Need to grant the permissions!");
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }

    }

    /**
     * Result of runtime permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            showToast("Missing permissions!!!");
        }
    }

    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    showToast("registering, pls wait...");
                    DJISDKManager.getInstance().registerApp(MainActivity.this.getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                        @Override
                        public void onRegister(DJIError djiError) {
                            if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                                showToast("Register Success");
                                DJISDKManager.getInstance().startConnectionToProduct();
                            } else {
                                showToast("Register sdk fails, please check the bundle id and network connection!");
                            }
                            Log.v(TAG, djiError.getDescription());
                        }

                        @Override
                        public void onProductChange(BaseProduct oldProduct, BaseProduct newProduct) {

                            mProduct = newProduct;
                            if (mProduct != null) {
                                mProduct.setBaseProductListener(mDJIBaseProductListener);
                            }

                            notifyStatusChange();
                        }
                    });
                }
            });
        }
    }

    private BaseProduct.BaseProductListener mDJIBaseProductListener = new BaseProduct.BaseProductListener() {
        @Override
        public void onComponentChange(BaseProduct.ComponentKey key, BaseComponent oldComponent, BaseComponent newComponent) {
            if (newComponent != null) {
                newComponent.setComponentListener(mDJIComponentListener);
            }
            notifyStatusChange();
        }

        @Override
        public void onConnectivityChange(boolean isConnected) {
            notifyStatusChange();
        }
    };
    private BaseComponent.ComponentListener mDJIComponentListener = new BaseComponent.ComponentListener() {
        @Override
        public void onConnectivityChange(boolean isConnected) {
            notifyStatusChange();
        }
    };

    private void notifyStatusChange() {
        mHandler.removeCallbacks(updateRunnable);
        mHandler.postDelayed(updateRunnable, 500);
    }

    private Runnable updateRunnable = new Runnable() {

        @Override
        public void run() {
            Intent intent = new Intent(FLAG_CONNECTION_CHANGE);
            sendBroadcast(intent);
        }
    };

    private void showToast(final String toastMsg) {

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
            }
        });

    }


    ////Custom stick Setting code///////////

    private AircraftStickMapping leftStickHorizontalMapping;
    private AircraftStickMapping leftStickVerticalMapping;
    private AircraftStickMapping rightStickHorizontalMapping;
    private AircraftStickMapping rightStickVerticalMapping;


    public void setLeftStickHorizontalMapping(AircraftStickMappingTarget aircraftStickMappingTarget, boolean isReversed) {
        leftStickHorizontalMapping = buildAircraftStickMapping(aircraftStickMappingTarget, isReversed);
    }

    public void setLeftStickVerticalMapping(AircraftStickMappingTarget aircraftStickMappingTarget, boolean isReversed) {
        leftStickVerticalMapping = buildAircraftStickMapping(aircraftStickMappingTarget, isReversed);
    }

    public void setRightStickHorizontalMapping(AircraftStickMappingTarget aircraftStickMappingTarget, boolean isReversed) {
        rightStickHorizontalMapping = buildAircraftStickMapping(aircraftStickMappingTarget, isReversed);
    }

    public void setRightStickVerticalMapping(AircraftStickMappingTarget aircraftStickMappingTarget, boolean isReversed) {
        rightStickVerticalMapping = buildAircraftStickMapping(aircraftStickMappingTarget, isReversed);
    }


    public void setNewAircraftStickMapping() {
        final Aircraft aircraft = getAircraft();
        final AircraftMapping aircraftMapping = new AircraftMapping(leftStickVerticalMapping, leftStickHorizontalMapping, rightStickVerticalMapping, rightStickHorizontalMapping);
        aircraftMapping.aircraftMappingStyle = AircraftMappingStyle.STYLE_CUSTOM;

        if (aircraft != null && aircraft.getRemoteController() != null) {
            aircraft.getRemoteController().setCustomAircraftMapping(aircraftMapping, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(final DJIError djiError) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (djiError == null) {
                                showMessage("Successfully set to custom");

                            } else {
                                showMessage(djiError.getDescription());
                            }
                        }
                    });
                }
            });
        }
    }

    private AircraftStickMapping buildAircraftStickMapping(AircraftStickMappingTarget aircraftStickMappingTarget, boolean isReversed) {
        AircraftStickMapping.Builder aircraftStickMappingBuilder = new AircraftStickMapping.Builder();
        aircraftStickMappingBuilder.mappingTarget(aircraftStickMappingTarget);
        aircraftStickMappingBuilder.isReversed(true);
        return aircraftStickMappingBuilder.build();
    }

    public Aircraft getAircraft() {
        BaseProduct product = DJISDKManager.getInstance().getProduct();
        if (product != null && (product instanceof Aircraft)) {
            return ((Aircraft) product);
        }
        return null;
    }

    public void showMessage(String message) {

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    public void setModeTwo() {
        Aircraft aircraft = getAircraft();
        if (aircraft != null && aircraft.getRemoteController() != null) {
            aircraft.getRemoteController().setAircraftMappingStyle(AircraftMappingStyle.STYLE_2, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(final DJIError djiError) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (djiError == null) {
                                showMessage("set To mode 2");

                            } else {
                                showMessage(djiError.getDescription());
                            }
                        }
                    });
                }
            });
        }
    }

    public void setModeOne() {
        Aircraft aircraft = getAircraft();
        if (aircraft != null && aircraft.getRemoteController() != null) {
            aircraft.getRemoteController().setAircraftMappingStyle(AircraftMappingStyle.STYLE_1, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(final DJIError djiError) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (djiError == null) {
                                showMessage("set To mode 1");

                            } else {
                                showMessage(djiError.getDescription());
                            }
                        }
                    });
                }
            });
        }
    }


}