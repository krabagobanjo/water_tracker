package edu.gatech.watertracker;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.widget.Button;
import android.widget.TextView;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;


public class Auth_Activity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthClass auth = new AuthClass(this);
        Credential cred;

        try {
            cred = auth.authorize();
        } catch (Exception oops) {
            cred = null;
            System.out.println(oops);
        }

        System.out.println(cred);


    }

    public static class AuthClass {
        private final OAuth oauth;
        public AuthClass(FragmentActivity activity) {
            oauth = OAuth.newInstance(activity.getApplicationContext(),
                    activity.getSupportFragmentManager(),
                    new ClientParametersAuthentication(Auth_Constants.CLIENT_ID, Auth_Constants.CLIENT_SECRET),
                    Auth_Constants.AUTH_URL,
                    Auth_Constants.TOKEN_URL,
                    Auth_Constants.REDIRECT_URL,
                    Auth_Constants.SCOPES
                    );
        }
        public Credential authorize() throws Exception {
            return oauth.authorizeImplicitly(Auth_Constants.CLIENT_ID).getResult();
        }
    }
}
