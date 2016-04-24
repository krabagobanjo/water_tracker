package edu.gatech.watertracker;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void authClick(View view) {
        startActivity(new Intent(this, Auth_Activity.class));
    }

    public void syncClick(View view) {
        startActivity(new Intent(this, Sync_Activity.class));
    }
}
