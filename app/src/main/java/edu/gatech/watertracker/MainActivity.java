package edu.gatech.watertracker;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    boolean mAuthenticated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuthenticated = Auth_Constants.auth_token != null;
        Button syncButton = (Button) findViewById(R.id.sync_button);
        syncButton.setEnabled(mAuthenticated);
    }

    public void authClick(View view) {
        startActivity(new Intent(this, Auth_Activity.class));
    }

    public void syncClick(View view) {

        AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
        dlgAlert.setCancelable(true);
        dlgAlert.setPositiveButton("Ok",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });


        dlgAlert.setMessage("Starting your all-day sync! You can now close this app.");
        dlgAlert.create().show();

        Intent backgroundSync = new Intent(this, SyncService.class);
        startService(backgroundSync);
    }
}
