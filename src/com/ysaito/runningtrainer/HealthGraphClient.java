package com.ysaito.runningtrainer;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

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

class HealthGraphClient {
	private static final String TAG = "HealthGraphUtil";
    public static String utcMillisToString(long time) {
        // TODO: fix
        return "Sat 1 Jan 2012 00:00:00";
    }

    static private HealthGraphClient mSingleton;
    
    /**
     *  This method is not thread safe. It can be called only by the main thread
     */
    static HealthGraphClient getSingleton(Context activity, String clientId, String clientSecret, String redirectUri) {
    	if (mSingleton == null) {
    		mSingleton = new HealthGraphClient(activity);
    		if (mSingleton.mAccessTokenState == TOKEN_UNSET) {
    			mSingleton.startAuthentication(activity, clientId, clientSecret, redirectUri);
    		}
    	}
    	return mSingleton;
    }

    /**
     *  This method is not thread safe. It can be called only by the main thread
     */
    public static void onRedirect(Uri uri) {
    	if (mSingleton == null) {
    		Log.d(TAG, "???");  // this shall never happen
    	} else {
    		synchronized(mSingleton) {
    			if (mSingleton.mAuthenticator != null) {
    				mSingleton.mAuthenticator.onRedirect(uri);
    			}
    		}
    	}
    }
    
    private static final int TOKEN_UNSET = 0;
    private static final int TOKEN_AUTHENTICATING = 1;
    private static final int TOKEN_ERROR = 2;
    private static final int TOKEN_OK = 3;
    
    private int mAccessTokenState;  // one of TOKEN_XXX
    private String mCachedAccessToken;
    private AuthenticatorImpl mAuthenticator;
    
    public HealthGraphClient(Context context) {
    	SharedPreferences pref = context.getSharedPreferences("HealthGraphAuthCache", Context.MODE_PRIVATE);
    	mAccessTokenState = TOKEN_UNSET;
    	mCachedAccessToken = pref.getString("AccessToken", null);
    	if (mCachedAccessToken != null) {
    		Log.d(TAG, "Found cached token: " + mCachedAccessToken);
    		mAccessTokenState = TOKEN_OK;
    	}
    }
    
    private synchronized void cacheAccessToken(Context context, String token) {
    	mCachedAccessToken = token;
    	if (token == null) {
    		mAccessTokenState = TOKEN_ERROR;
    	} else {
    		mAccessTokenState = TOKEN_OK;
    		SharedPreferences pref = context.getSharedPreferences("HealthGraphAuthCache", Context.MODE_PRIVATE);
    		SharedPreferences.Editor editor = pref.edit();
    		editor.putString("AccessToken", token);
    		if (!editor.commit()) {
    			Log.e(TAG, "Failed to write oauth token to preferences");
    		} else {
    			Log.d(TAG, "Save access token " + token);
    		}
    	}
    	notifyAll();
    }

    public abstract static interface HttpResponseListener {
    	public abstract void onFinish(Exception e, HttpResponse response);
    }
    
    public void executeGet(String path, HttpResponseListener listener) {
    	HttpGetTask task = new HttpGetTask(this, listener);
    	task.execute(path);
    }

    private static class HttpGetTask extends AsyncTask<String, Integer, HttpResponse> {
    	private final HealthGraphClient mParent;
    	private final HttpResponseListener mListener;
    	private Exception mException = null;
    	
    	public HttpGetTask(HealthGraphClient parent, HttpResponseListener listener) {
    		mParent = parent;
    		mListener = listener;
    	}
    	
    	@Override protected HttpResponse doInBackground(String... path) {
    	    DefaultHttpClient httpClient = new DefaultHttpClient();
    	    HttpGet request = new HttpGet("https://api.runkeeper.com/" + path[0]);
    		synchronized(mParent) {
    			while (mParent.mAccessTokenState != TOKEN_ERROR && mParent.mAccessTokenState != TOKEN_OK) {
    				// Wait for the AuthenticatorImpl to finish and call cacheAccessToken.
    				try {
    					mParent.wait();
    				} catch (InterruptedException e) {
    					;
    				}
    			}
    			final String token = mParent.mCachedAccessToken;
    			request.addHeader("Authorization", "Bearer " + token);
    		}
    		request.addHeader("Accept", "application/vnd.com.runkeeper.User+json");
    		HttpResponse response = null;
    		try {
    			response = httpClient.execute(request);
    		} catch (Exception e) {
    			mException = e;
    			Log.d(TAG, "Http GET exception: " + e.toString());
    		}
    		return response;
    	}
    	
    	@Override protected void onPostExecute(HttpResponse resp) {
    		mListener.onFinish(mException, resp);
    	}
    }
    	
    
    private synchronized void startAuthentication(Context context, String clientId, String clientSecret, String redirectUri) {
    	mAccessTokenState = TOKEN_AUTHENTICATING;
    	mAuthenticator = new AuthenticatorImpl(context, clientId, clientSecret, redirectUri);
    	mAuthenticator.start();
    }

    // client_id=0808ef781c68449298005c8624d3700b
    // client_secret=dda5888cd8d64760a044dc61ae4f44db
    private class AuthenticatorImpl {
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
    	public AuthenticatorImpl(Context context, String clientId, String clientSecret, String redirectUri) {
    		mContext = context;
    		mClientId = clientId;
    		mClientSecret = clientSecret;
    		mRedirectUri = redirectUri;
    	}
    	
    	public void start() {
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
    		if (uri.toString().startsWith(mRedirectUri)) {
    			Log.d(TAG, "OnRedirect: " + uri.toString());
    			mCode = uri.getQueryParameter("code");
    			startTokenAcquisition();
    		} else {
    			Log.e(TAG, "OnRedirect: ignore URI: " + uri.toString());
    		}
    	}
    	
    	private class OauthTask extends AsyncTask<Integer, Integer, String> {
    		private final Context mContext;
    		public OauthTask(Context context) { mContext = context; }
    		
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
    		OauthTask thread = new OauthTask(mContext);
    		thread.execute((Integer[])null);
    	}
    }
}