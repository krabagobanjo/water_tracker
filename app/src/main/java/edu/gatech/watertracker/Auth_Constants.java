package edu.gatech.watertracker;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Kyle on 4/19/2016.
 */
public class Auth_Constants {
    public static final String CLIENT_ID = "227Q8R";
    public static final String CLIENT_SECRET = "83f4eefcd054b13117e50e07208ec6b7";
    public static final String AUTH_URL = "https://www.fitbit.com/oauth2/authorize";
    public static final String TOKEN_URL = "https://api.fitbit.com/oauth2/token";
    public static final String REDIRECT_URL = "https://www.google.com/";
    public static final List<String> SCOPES = Arrays.asList("nutrition");
    private Auth_Constants() {}
}
