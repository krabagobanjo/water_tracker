package edu.gatech.watertracker;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.util.logging.Logger;

public class RestActivity extends AppCompatActivity {
    static final Logger LOGGER = Logger.getAnonymousLogger();
    private RestHandler rest = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rest);
        rest = new RestHandler();
        int resp = rest.post(rest.parseMbedString("str"), Auth_Constants.auth_token);
        LOGGER.info("Response: " + resp);
    }
}
