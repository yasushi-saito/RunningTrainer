package com.ysaito.runningtrainer;

import java.io.InputStream;
import java.util.Scanner;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
	
	/**
	 * Record of a run log. Corresponds to "newly completed activites" json described in
	 * http://developer.runkeeper.com/healthgraph/fitness-activities.
	 *
	 * This object is converted to a JSON string using GSON.
	 *
	 */
	static public class JsonActivity {
	    public String type; // "Running", "Cycling", etc.
	    public String start_time;      // "Sat, 1 Jan 2011 00:00:00"
	    public double total_distance;  // total distance, in meters
	    public double duration;        // duration, in seconds
	    public String notes;
	    public JsonWGS84[] path;
	    public Boolean post_to_facebook;
	    public Boolean post_to_twitter;
	    public Boolean detect_pauses;
	}
	
	static public class JsonWGS84 {
		public double timestamp;  // The number of seconds since the start of the activity
		public double latitude;  // The latitude, in degrees (values increase northward and decrease southward)
		public double longitude; // The longitude, in degrees (values increase eastward and decrease westward)
		public double altitude;  //	The altitude of the point, in meters
		public String type; // One of the following values: start, end, gps, pause, resume, manual
	};

	static public class JsonUser {
		public String userID;
		public String profile;
		public String settings;
		public String fitness_activities;
		public String strength_training_activities;
		public String background_activities;
		public String sleep;
		public String nutrition;
		public String weight;
		public String general_measurements;
		public String diabetes;
		public String records;
		public String team;
	}
	
	static public class JsonFitnessActivities {
		public int size;
		public JsonActivity[] items; // doesn't have path[] set
		public String previous;
	}
	
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
    		Log.d(TAG, "Creating singleton!!");
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
    
    public abstract static interface JsonResponseListener { 
    	public abstract void onFinish(Exception e, Object o);
    }

    public void executeGet(String path, String acceptType, JsonResponseListener listener, Object destObject) {
    	GetRequestRecord r = new GetRequestRecord();
    	r.path = path;
    	r.acceptType = acceptType;
    	HttpGetJsonTask task = new HttpGetJsonTask(this, listener, destObject);
    	task.execute(r);
    }
    
    public void getUser(JsonResponseListener listener) {
    	executeGet("fitnessActivities", "application/vnd.com.runkeeper.User+json", listener, new JsonUser());
    }
    
    public void getFitnessActivities(JsonResponseListener listener) {
    	executeGet("fitnessActivities", "application/vnd.com.runkeeper.FitnessActivityFeed+json",
    			listener, new JsonFitnessActivities());
    }
    
    private void cacheAccessToken(Context context, String token) {
    	synchronized(this) {
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
    		this.notifyAll();
    	}
    }

    private static class GetRequestRecord {
    	public String path; 
    	public String acceptType;
    }
    
    private static class HttpGetJsonTask extends AsyncTask<GetRequestRecord, Void, HttpResponse> {
    	private final HealthGraphClient mParent;
    	private final JsonResponseListener mListener;
    	private final Object mDestObject;
    	private Exception mException = null;
    	
    	public HttpGetJsonTask(HealthGraphClient parent, JsonResponseListener listener, Object destObject) {
    		mParent = parent;
    		mListener = listener;
    		mDestObject = destObject;
    	}
    	
    	@Override protected HttpResponse doInBackground(GetRequestRecord... r) {
    	    DefaultHttpClient httpClient = new DefaultHttpClient();
    	    HttpGet request = new HttpGet("https://api.runkeeper.com/" + r[0].path);
    	    if (true) return null;
    		synchronized(mParent) {
    			while (mParent.mAccessTokenState != TOKEN_ERROR && mParent.mAccessTokenState != TOKEN_OK) {
    				// Wait for the AuthenticatorImpl to finish and call cacheAccessToken.
    				try {
    					Log.d(TAG, "Waiting for auth token");
    					mParent.wait();
    				} catch (InterruptedException e) {
    					;
    				}
    			}
    			final String token = mParent.mCachedAccessToken;
    			request.addHeader("Authorization", "Bearer " + token);
    			Log.d(TAG, "Acquired token: " + mParent.mAccessTokenState);
    		}
    		request.addHeader("Accept", r[0].acceptType);
    		HttpResponse response = null;
    		try {
    			response = httpClient.execute(request);
    		} catch (Exception e) {
    			mException = e;
    			Log.d(TAG, "Http GET exception: " + e.toString());
    		}
    		return response;
    	}
    	
    	@Override protected void onPostExecute(HttpResponse response) {
    		Object parseResult = null;
    		HttpEntity entity = (response != null ? response.getEntity() : null);
    		if (entity != null) {
    			// A Simple JSON Response Read
    			InputStream instream = null;
    			String result = "";
    			try {
    				instream = entity.getContent();
    				result = new Scanner(instream).useDelimiter("\\A").next();
    			} catch (Exception e2) {
    				Log.d(TAG, "E: " + e2.toString());
    				mException = e2;
    			} finally {
    				try {
    					if (instream != null) instream.close();
    				} catch (Exception e3) {
    					mException = e3;
    				}
    			}
    			Gson gson = new GsonBuilder().create();
    			Log.d(TAG, "GOT JSON: " + result);
    			parseResult = gson.fromJson(result, mDestObject.getClass());
    			Log.d(TAG, "RESP: " + parseResult.toString());
    		}
    		mListener.onFinish(mException, parseResult);
    	}
    }
    	
    
    private void startAuthentication(Context context, String clientId, String clientSecret, String redirectUri) {
    	synchronized(this) {
    		mAccessTokenState = TOKEN_AUTHENTICATING;
    		mAuthenticator = new AuthenticatorImpl(context, clientId, clientSecret, redirectUri);
    		mAuthenticator.start();
    	}
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
    	
    	private class OauthTask extends AsyncTask<Void, Void, String> {
    		public Context mContext;

    		@Override protected String doInBackground(Void... unused) {
    			try {
    				Log.d(TAG, "Start token acquisition thread(0): ");
    				OAuthClientRequest request = OAuthClientRequest.tokenLocation("https://runkeeper.com/apps/token")
    						.setGrantType(GrantType.AUTHORIZATION_CODE)
    						.setClientId(mClientId)
    						.setClientSecret(mClientSecret)
    						.setRedirectURI(mRedirectUri)
    						.setCode(mCode)
    						.buildBodyMessage();
    				Log.d(TAG, "Start token acquisition thread: " + request.getLocationUri());
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
    		Log.d(TAG, "Start token acquistion (main): " + mCode);
    		OauthTask thread = new OauthTask();
    		thread.mContext = mContext;
    		thread.execute();
    	}
    }
}