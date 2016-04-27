package edu.gatech.watertracker;


import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

//create new water log - POST https://api.fitbit.com/1/user/[user-id]/foods/log/water.json
//amount, date, unit
public class RestHandler {
    private URL postURL = null;
    private HttpsURLConnection conn = null;
    public RestHandler() {
        try {
            postURL = new URL("https://api.fitbit.com/1/user/-/foods/log/water.json");
        } catch (MalformedURLException e) {
            //this should never happen
            postURL = null;
        }
    }
    
    public int post(JSONObject data, String authToken) {
        try {
            byte[] toSend = data.toString().getBytes();
            conn = (HttpsURLConnection) postURL.openConnection();
            conn.setRequestProperty("Authorization", authToken);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Content-Length", Integer.toString(toSend.length));
            conn.setRequestProperty("Accept-Language", "en-US");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            OutputStream out = conn.getOutputStream();
            out.write(toSend);
            out.close();
            return conn.getResponseCode();
        } catch (IOException e) {
            return -1;
        }
    }
    
    public JSONObject parseMbedString(String mbedString) {
        //TODO forreal
        JSONObject ret = new JSONObject();
        try {
            ret.put("amount", 10.0);
            ret.put("date", "2016-04-26");
            ret.put("unit", "fl oz");
        } catch (JSONException e) {
            //this shouldn't happen

        }

        return ret;
    }
    
    
    
}