package com.hung.log;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

//import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    private static String TAG = MainActivity.class.getSimpleName();

    //chỉ lấy log sau khi đc cấp permission
    public MyLog mLog2File = new MyLog();

    private TextView txtOutput;
    private TextView txtLogFolder;
    private Button btnStartThread;
    private Button btnStopThread, btnCreateLog;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();

        // sdk = 23 => Android 6.0, check permission at runtime
        if (Build.VERSION.SDK_INT >= 23) {
            askForPermissions(new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.READ_LOGS,
                    },
                    REQUEST_PERMISSIONS_CODE);
        }



    }

    static final int REQUEST_PERMISSIONS_CODE = 1982;

    protected final void askForPermissions(String[] permissions, int requestCode) {
        List<String> permissionsToRequest = new ArrayList<>();

        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                //permision which have not been granted need to be request again
                permissionsToRequest.add(permission);
            }
        }
        if (!permissionsToRequest.isEmpty()) {
            //request permissions which have not been yet granted
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new
                    String[permissionsToRequest.size()]), requestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSIONS_CODE) {//dùng chung permission code hoặc riêng thì tùy
            //check xem permission nào đc granted/deny
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                int grantResult = grantResults[i];

                if (permission.equals(Manifest.permission.READ_LOGS)) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        //
                    } else {
                        //vòng lặp vô hạn sẽ làm cheo mainthread
//                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_LOGS}, REQUEST_PERMISSIONS_CODE);
                    }
                }
            }
        }
    }


    @Override
    protected void onDestroy() {
        Log.v(TAG, "onDestroy()");
        super.onDestroy();

        // Khi app stop thì các thread sẽ bị giải phóng mà ko chờ worker thread kết thúc
       mLog2File.stopThread();
    }


    private void initView() {
        txtOutput = (TextView) findViewById(R.id.txt_output);
        txtLogFolder = (TextView) findViewById(R.id.txt_log_folder);
        btnStartThread = (Button) findViewById(R.id.btn_start_thread);
        btnStopThread = (Button) findViewById(R.id.btn_stop_thread);
        btnCreateLog = (Button) findViewById(R.id.btn_create_log);
        //
        txtLogFolder.setText( mLog2File.mLoggingFolder);


        btnStartThread.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                txtOutput.setText("Thread was started");
                mLog2File.startGetLogCat();
                Log.d(TAG,"start Thread");
            }
        });

        btnStopThread.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG,"stop Thread");
                txtOutput.setText("Thread was stop");
                mLog2File.stopThread();
            }
        });

        btnCreateLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG,"create Log");
                txtOutput.setText("create Log");
            }
        });
    }
}
