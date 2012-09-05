package com.ysaito.runningtrainer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
	private static final String TAG = "HealthGraphUtil";
    public static String utcMillisToString(long time) {
        // TODO: fix
        return "Sat 1 Jan 2012 00:00:00";
    }

    static private Authenticator mSingletonAuthenticator;
    static public Authenticator getAuthenticator() {
    	return mSingletonAuthenticator;
    }

    private static void cacheAccessToken(Context context, String token) {
    	SharedPreferences pref = context.getSharedPreferences("HealthGraphAuthCache", Context.MODE_PRIVATE);
    	SharedPreferences.Editor editor = pref.edit();
    	editor.putString("AccessToken", token);
    	if (!editor.commit()) {
    		Log.e(TAG, "Failed to write oauth token to preferences");
    	}
    }

    private static String readCachedAccessToken(Context context) {
    	SharedPreferences pref = context.getSharedPreferences("HealthGraphAuthCache", Context.MODE_PRIVATE);
    	return pref.getString("AccessToken", null);
    }
    
    static public Authenticator newAuthenticator(Context context, String clientId, String clientSecret, String redirectUri) {
    	mSingletonAuthenticator = new Authenticator(context, clientId, clientSecret, redirectUri);
    	return mSingletonAuthenticator;
    }
    
    // client_id=0808ef781c68449298005c8624d3700b
    // client_secret=dda5888cd8d64760a044dc61ae4f44db
    public static class Authenticator {
    	private final String mClientId;
    	private final String mClientSecret;
    	private final String mRedirectUri;
    	private final Context mContext;  // should be activity context, not the process context
    	public String mCode;  // one-time per-client code
    	
    	/**
    	 * @param context The Activity object that's calling this method. 
    	 * Should not be the process context (i.e., shall not be the value of
    	 * context.getApplicationContext()).
    	 */
    	public Authenticator(Context context, String clientId, String clientSecret, String redirectUri) {
    		mContext = context;
    		mClientId = clientId;
    		mClientSecret = clientSecret;
    		mRedirectUri = redirectUri;
    	}
    	
    	public void startAuthorization() {
    		try {
    			OAuthClientRequest request = OAuthClientRequest
    					.authorizationLocation("https://runkeeper.com/apps/authorize")
    					.setClientId(mClientId).setRedirectURI(mRedirectUri)
    					.buildQueryMessage();
    			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(request.getLocationUri() + "&response_type=code"));
    			mContext.startActivity(intent);
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
    		private final Context mContext;
    		public HttpClientThread(Context context) { mContext = context; }
    		
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
    			if (token != null) {
    				Log.d(TAG, "Acquired HealthGraph token: " + token);
    				cacheAccessToken(mContext, token);
    			}
    		}
    		
    	}

    	public void startTokenAcquisition() {
    		HttpClientThread thread = new HttpClientThread(mContext);
    		thread.execute((Integer[])null);
    	}
    }
}