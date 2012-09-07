package com.ysaito.runningtrainer;

import java.io.InputStream;
import java.util.Scanner;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;
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
	    public String equipment; // usually "None"
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
    
    static private final String CLIENT_ID = "0808ef781c68449298005c8624d3700b";
    static private final String CLIENT_SECRET = "dda5888cd8d64760a044dc61ae4f44db";
    static private final String REDIRECT_URI = "ysaito://oauthresponse";
    
    /**
     *  This method is not thread safe. It can be called only by the main thread
     *  @param application The application context. The one obtained by calling Activity::getApplicationContext().
     */
    static HealthGraphClient getSingleton() {
    	if (mSingleton == null) {
    		Log.d(TAG, "Creating singleton!!");
    		mSingleton = new HealthGraphClient();
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
    
    public HealthGraphClient() {
    	mAccessTokenState = TOKEN_UNSET;
    }
    
    public void executeGet(String path, String acceptType, GetResponseListener listener, Object destObject) {
    	HttpGetTask task = new HttpGetTask(this, path, acceptType, destObject, listener);
    	task.execute();
    }
    
    public void executePut(String path, String contentType, Object object, PutResponseListener listener) {
    	HttpPutTask task = new HttpPutTask(this, path, contentType, object, listener);
    	task.execute();
    }

    public void executePost(String path, String contentType, Object object, PutResponseListener listener) {
    	HttpPostTask task = new HttpPostTask(this, path, contentType, object, listener);
    	task.execute();
    }
    
    public void getUser(GetResponseListener listener) {
    	executeGet("fitnessActivities", "application/vnd.com.runkeeper.User+json", listener, new JsonUser());
    }
    
    public void getFitnessActivities(GetResponseListener listener) {
    	executeGet("fitnessActivities", "application/vnd.com.runkeeper.FitnessActivityFeed+json",
    			listener, new JsonFitnessActivities());
    }
    
    public void putNewFitnessActivity(
    		JsonActivity activity,
    		PutResponseListener listener) {
    	// activity.equipment = "None";
    	activity.start_time = "Sat, 1 Jan 2011 00:00:00";
    	/*
    	JsonWGS84[] xx = new JsonWGS84[2];
    	xx[0] = new JsonWGS84();
    	xx[1] = new JsonWGS84();    	
    	xx[0].timestamp = 0;
    	xx[0].altitude = 0;
    	xx[0].latitude = 37.399835865944624;
    	xx[0].longitude = -122.0777643006295;
    	xx[0].type = "start";
    	xx[1].timestamp = 10;
    	xx[1].altitude = 0;
    	xx[1].latitude = 38.399835865944624;
    	xx[1].longitude = -123.0777643006295;
    	xx[1].type = "end";
    	activity.path = xx;
    	*/
    	executePost("fitnessActivities", "application/vnd.com.runkeeper.NewFitnessActivity+json", 
    			activity, listener);
    }
    
    private void cacheAccessToken(Context context, String token) {
    	synchronized(this) {
    		mCachedAccessToken = token;
    		if (token == null) {
    			mAccessTokenState = TOKEN_ERROR;
    			Toast.makeText(context,  "Failed to sign into runkeeper", Toast.LENGTH_LONG).show();
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
    			Toast.makeText(context,  "Created new signin token into runkeeper", Toast.LENGTH_SHORT).show();
    		}
    		this.notifyAll();
    	}
    }

    public abstract static interface GetResponseListener { 
    	public abstract void onFinish(Exception e, Object o);
    }
    public abstract static interface PutResponseListener {
    	public abstract void onFinish(Exception e);
    }

    public static class MyException extends Exception {
    	// Autogenerated
		private static final long serialVersionUID = -268033828160301959L;
		private final String mMessage;
		public MyException(String s) { mMessage = "HealthGraph API failed: " + s; }
		@Override public String toString() { return mMessage; }
    }

    private String getAccessToken() throws MyException {
    	synchronized(this) {
    		if (mAccessTokenState != TOKEN_OK) {
    			// TODO: provide more precise message
    			throw new MyException("HealthGraph token retrieval failed");
    		}
    		return mCachedAccessToken;
    	}
    }
    
    private static class HttpGetTask extends AsyncTask<Void, Void, HttpResponse> {
    	private final HealthGraphClient mParent;
    	private final String mPath;
    	private final String mAcceptType;
    	private final Object mDestObject;
    	private final GetResponseListener mListener;
    	private Exception mException = null;
    	
    	public HttpGetTask(
    			HealthGraphClient parent, 
    			String path,
    			String acceptType,
    			Object destObject,
    			GetResponseListener listener) {
    		mParent = parent;
    		mPath = path;
    		mAcceptType = acceptType;
    		mDestObject = destObject;
    		mListener = listener;
    	}
    	
    	@Override protected HttpResponse doInBackground(Void...unused) {
    	    DefaultHttpClient httpClient = new DefaultHttpClient();
    		HttpResponse response = null;
    		try {
    			String token = mParent.getAccessToken();
    			HttpGet get = new HttpGet("https://api.runkeeper.com/" + mPath);
    			get.addHeader("Authorization", "Bearer " + token);
    			get.addHeader("Accept", mAcceptType);
    			response = httpClient.execute(get);
    		} catch (Exception e) {
    			mException = e;
    			Log.d(TAG, "Http GET exception: " + e.toString());
    		}
    		return response;
    	}
    	
    	@Override protected void onPostExecute(HttpResponse response) {
    		if (mException != null) {
    			mListener.onFinish(mException, null);
    			return;
    		}
    		if (response != null) {
    			StatusLine status = response.getStatusLine(); 
    			Log.d(TAG, "Get finished: " + status.getStatusCode() + ":" + status.getReasonPhrase());
    			if (status.getStatusCode() / 100 != 2) {
    				mListener.onFinish(new MyException(
    						"HTTP Get failed: " + status.getStatusCode() + ": " + status.getReasonPhrase()),
    						null);
    				return;
    			}
    		}
    		HttpEntity entity = (response != null ? response.getEntity() : null);
    		if (entity == null) {
    			mListener.onFinish(new MyException(
    					"HTTP Get returned an empty body"),
    					null);
    			return;
    		}
    		Object parseResult = null;
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
    		mListener.onFinish(mException, parseResult);
    	}
    }
    	
    private static class HttpPutTask extends AsyncTask<Void, Void, HttpResponse> {
    	private final HealthGraphClient mParent;
    	private final String mPath;
    	private final String mAcceptType;
    	private final Object mSourceObject;
    	private final PutResponseListener mListener;
    	private Exception mException = null;
    	
    	public HttpPutTask(
    			HealthGraphClient parent, 
    			String path,
    			String acceptType,
    			Object sourceObject,
    			PutResponseListener listener) {
    		mParent = parent;
    		mPath = path;
    		mAcceptType = acceptType;
    		mSourceObject = sourceObject;
    		mListener = listener;
    	}
    	
    	@Override protected HttpResponse doInBackground(Void...unused) {
    	    DefaultHttpClient httpClient = new DefaultHttpClient();
    		HttpResponse response = null;
    		try {
    			String token = mParent.getAccessToken();
    			HttpPut put = new HttpPut("https://api.runkeeper.com/" + mPath);
    				
    			Gson gson = new GsonBuilder().create();
    			final String json = gson.toJson(mSourceObject);
    			put.setEntity(new StringEntity(json));
    			put.addHeader("Authorization", "Bearer " + token);
    			put.addHeader("Accept", mAcceptType);
    			response = httpClient.execute(put);
    		} catch (Exception e) {
    			mException = e;
    			Log.d(TAG, "Http PUT exception: " + e.toString());
    		}
    		return response;
    	}
    	
    	@Override protected void onPostExecute(HttpResponse response) {
    		if (mException != null) {
    			mListener.onFinish(mException);
    			return;
    		}
    		if (response != null) {
    			StatusLine status = response.getStatusLine(); 
    			Log.d(TAG, "PUT finished: " + status.getStatusCode() + ":" + status.getReasonPhrase());
    			if (status.getStatusCode() / 100 != 2) {
    				mListener.onFinish(new MyException(
    						"HTTP Get failed: " + status.getStatusCode() + ": " + status.getReasonPhrase()));
    				return;
    			}
    		}
    		mListener.onFinish(null);
    	}
    }

    private static class HttpPostTask extends AsyncTask<Void, Void, HttpResponse> {
    	private final HealthGraphClient mParent;
    	private final String mPath;
    	private final String mAcceptType;
    	private final Object mSourceObject;
    	private final PutResponseListener mListener;
    	private Exception mException = null;
    	
    	public HttpPostTask(
    			HealthGraphClient parent, 
    			String path,
    			String acceptType,
    			Object sourceObject,
    			PutResponseListener listener) {
    		mParent = parent;
    		mPath = path;
    		mAcceptType = acceptType;
    		mSourceObject = sourceObject;
    		mListener = listener;
    	}
    	
    	@Override protected HttpResponse doInBackground(Void...unused) {
    	    DefaultHttpClient httpClient = new DefaultHttpClient();
    		HttpResponse response = null;
    		try {
    			String token = mParent.getAccessToken();
    			HttpPost post = new HttpPost("https://api.runkeeper.com/" + mPath);
    				
    			post.setHeader("Authorization", "Bearer " + token);
    			post.setHeader("Content-Type", mAcceptType);
    			Gson gson = new GsonBuilder().create();
    			final String json = gson.toJson(mSourceObject);
    			post.setEntity(new StringEntity(json, "UTF-8"));
    			Log.d(TAG, "POST: " + json);
    			response = httpClient.execute(post);
    		} catch (Exception e) {
    			mException = e;
    			Log.d(TAG, "Http PUT exception: " + e.toString());
    		}
    		return response;
    	}
    	
    	@Override protected void onPostExecute(HttpResponse response) {
    		if (mException != null) {
    			mListener.onFinish(mException);
    			return;
    		}
    		if (response != null) {
    			StatusLine status = response.getStatusLine(); 
    			Log.d(TAG, "PUT finished: " + status.getStatusCode() + ":" + status.getReasonPhrase());
    			if (status.getStatusCode() / 100 != 2) {
    				mListener.onFinish(new MyException(
    						"HTTP Get failed: " + status.getStatusCode() + ": " + status.getReasonPhrase()));
    				return;
    			}
    		}
    		mListener.onFinish(null);
    	}
    }

    /**
     * Start the runkeeper authentication protocol. If a token had already been acquired, this function is a noop.
     * 
     * @param context Must be an Activity context, not the global application context
     */
    public void startAuthentication(Context context) {
    	synchronized(this) {
    		SharedPreferences pref = context.getSharedPreferences("HealthGraphAuthCache", Context.MODE_PRIVATE);
    		mAccessTokenState = TOKEN_UNSET;
    		mCachedAccessToken = pref.getString("AccessToken", null);
    		if (mCachedAccessToken != null) {
    			Log.d(TAG, "Found cached token: " + mCachedAccessToken);
    			mAccessTokenState = TOKEN_OK;
    			Toast.makeText(context,  "Signed into runkeeper", Toast.LENGTH_SHORT).show();
    		}
    		if (mAccessTokenState != TOKEN_OK) {
    			mAccessTokenState = TOKEN_AUTHENTICATING;
    			mAuthenticator = new AuthenticatorImpl(context);
    			mAuthenticator.start();
    		}
    	}
    }

    private class AuthenticatorImpl {
    	private final Context mContext;  // should be activity context, not the process context
    	public String mCode;  // one-time per-client code
    	
    	/**
    	 * @param context The Activity object that's calling this method. 
    	 * Should not be the process context (i.e., shall not be the value of
    	 * context.getApplicationContext()).
    	 */
    	public AuthenticatorImpl(Context context) {
    		mContext = context;
    	}
    	
    	public void start() {
    		try {
    			OAuthClientRequest request = OAuthClientRequest
    					.authorizationLocation("https://runkeeper.com/apps/authorize")
    					.setClientId(CLIENT_ID).setRedirectURI(REDIRECT_URI)
    					.buildQueryMessage();
    			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(request.getLocationUri() + "&response_type=code"));
    			mContext.startActivity(intent);
    		} catch (OAuthSystemException e) {
    			Log.e(TAG, "HealthGraph OAuth failed: " + e.toString());
    		}
    	}
    	
    	public void onRedirect(Uri uri) {
    		if (uri.toString().startsWith(REDIRECT_URI)) {
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
    						.setClientId(CLIENT_ID)
    						.setClientSecret(CLIENT_SECRET)
    						.setRedirectURI(REDIRECT_URI)
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