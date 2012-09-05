package com.ysaito.runningtrainer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import net.smartam.leeloo.client.OAuthClient;
import net.smartam.leeloo.client.URLConnectionClient;
import net.smartam.leeloo.client.request.OAuthClientRequest;
import net.smartam.leeloo.client.response.OAuthJSONAccessTokenResponse;
import net.smartam.leeloo.common.exception.OAuthProblemException;
import net.smartam.leeloo.common.exception.OAuthSystemException;
import net.smartam.leeloo.common.message.types.GrantType;

class HealthGraphUtil {
    public static String utcMillisToString(long time) {
        // TODO: fix
        return "Sat 1 Jan 2012 00:00:00";
    }

    static private Authenticator mSingletonAuthenticator;
    static public Authenticator getAuthenticator() {
    	return mSingletonAuthenticator;
    }

    static public Authenticator newAuthenticator(String clientId, String clientSecret, String redirectUri) {
    	mSingletonAuthenticator = new Authenticator(clientId, clientSecret, redirectUri);
    	return mSingletonAuthenticator;
    }
    
    // client_id=0808ef781c68449298005c8624d3700b
    // client_secret=dda5888cd8d64760a044dc61ae4f44db
    public static class Authenticator {
    	private static final String TAG = "Authenticator";
    	public final String mClientId;
    	public final String mClientSecret;
    	public final String mRedirectUri;
    	public String mCode;  // one-time per-client code
    	public String mToken; // per-session token
    	
    	public Authenticator(String clientId, String clientSecret, String redirectUri) {
    		mClientId = clientId;
    		mClientSecret = clientSecret;
    		mRedirectUri = redirectUri;
    	}
    	
    	public void startAuthorization(Context context) {
    		try {
    			OAuthClientRequest request = OAuthClientRequest
    					.authorizationLocation("https://runkeeper.com/apps/authorize")
    					.setClientId(mClientId).setRedirectURI(mRedirectUri)
    					.buildQueryMessage();
    			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(request.getLocationUri() + "&response_type=code"));
    			context.startActivity(intent);
    		} catch (OAuthSystemException e) {
    			Log.e(TAG, "HealthGraph OAuth failed: " + e.toString());
    		}
    	}
    	
    	public void onRedirect(Uri uri) {
    		Log.d(TAG, "OnRedirect: " + uri.toString());
    		if (uri.toString().startsWith(mRedirectUri)) {
    			mCode = uri.getQueryParameter("code");
    			startTokenAcquisition();
    		}
    	}
    	
    	private class HttpClientThread extends AsyncTask<Integer, Integer, String> {
    		@Override protected String doInBackground(Integer... unused) {
    			try {
    				OAuthClientRequest request = OAuthClientRequest.tokenLocation("https://runkeeper.com/apps/token")
    						.setGrantType(GrantType.AUTHORIZATION_CODE)
    						.setClientId(mClientId)
    						.setClientSecret(mClientSecret)
    						.setRedirectURI(mRedirectUri)
    						.setCode(mCode)
    						.buildBodyMessage();
    				Log.d(TAG, "Start token acquisition: " + request.getLocationUri());
    				OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
    				OAuthJSONAccessTokenResponse response = oAuthClient.accessToken(request);
    				Log.d(TAG, "Recv: " + response.toString());
    				return response.getAccessToken();
    			} catch (OAuthSystemException e) {
    				Log.e(TAG, "HealthGraph OAuth failed: " + e.toString());
    				return null;
    			} catch (OAuthProblemException e) {
    				Log.e(TAG, "HealthGraph OAuth failed: " + e.toString());
    				return null;
    			}
    		}
    		
    		@Override protected void onPostExecute(String token) {
    			mToken = token;
    			Log.d(TAG, "Acquired HealthGraph token: " + token);
    		}
    		
    	}

    	public void startTokenAcquisition() {
    		HttpClientThread thread = new HttpClientThread();
    		thread.execute((Integer[])null);
    	}
    }
}