package edu.gatech.watertracker;


import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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
            Log.d("RestHandler", e.toString());
            postURL = null;
        }
    }
    
    public int post(JSONObject data, String authToken) {
        try {
            String amount = "amount=" + data.get("amount");
            String date = "date=" + data.get("date");
            String unit = "unit=" + data.get("unit");

            String urlParams = amount + "&" + date + "&" + unit;
            Log.d("RestHandler", urlParams);

            byte[] toSend = data.toString().getBytes();
            conn = (HttpsURLConnection) postURL.openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Length", urlParams.length() + "");
            conn.setRequestProperty("Accept-Language", "en-US");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            OutputStream out = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(out, "UTF-8"));
            writer.write(urlParams);
            writer.flush();
            writer.close();
            //out.write(toSend);
            out.close();
            conn.connect();
            int respCode = conn.getResponseCode();
            Log.d("RestHandler", conn.getResponseMessage());
            Log.d("RestHandler", conn.toString());
            conn.disconnect();
            return respCode;
        } catch (IOException|JSONException e) {
            Log.d("RestHandler", e.toString());
            return -1;
        }
    }
    
    public JSONObject parseMbedString(double amt, String units) {
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