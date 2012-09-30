package com.ysaito.runningtrainer;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
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
import net.smartam.leeloo.client.OAuthClient;
import net.smartam.leeloo.client.URLConnectionClient;
import net.smartam.leeloo.client.request.OAuthClientRequest;
import net.smartam.leeloo.client.response.OAuthJSONAccessTokenResponse;
import net.smartam.leeloo.common.exception.OAuthProblemException;
import net.smartam.leeloo.common.exception.OAuthSystemException;
import net.smartam.leeloo.common.message.types.GrantType;

public class HealthGraphClient {
	private static final String TAG = "HealthGraphUtil";
	
	/** 
	 * Convert time into a JsonActivity.startTime string that runkeeper expects.
	 *  
	 *  @param seconds Seconds since 1/1/1970
	 *  @return A string like 
	 * "Tue, 1 Mar 2011 07:00:00"
	 *	regardless of the user's locale.
	 */
	static public String generateStartTimeString(double seconds) {
		GregorianCalendar cal = new GregorianCalendar();
		cal.setTimeInMillis((long)(seconds * 1000));
		return String.format("%s, %d %s %04d %02d:%02d:%02d",
				cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.US),
				cal.get(Calendar.DAY_OF_MONTH),
				cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US),
				cal.get(Calendar.YEAR),
				cal.get(Calendar.HOUR_OF_DAY),
				cal.get(Calendar.MINUTE),
				cal.get(Calendar.SECOND));
	}
	
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
	
	/**
	 * GPS reading reported to runkeeper
	 */
	static public class JsonWGS84 {
		public double timestamp;  // The number of seconds since the start of the activity
		public double latitude;  // The latitude, in degrees (values increase northward and decrease southward)
		public double longitude; // The longitude, in degrees (values increase eastward and decrease westward)
		public double altitude;  //	The altitude of the point, in meters
		public String type; // One of the following values: start, end, gps, pause, resume, manual
	};

	static public class PathAggregator {
		private ArrayList<JsonWGS84> mPath = new ArrayList<JsonWGS84>();
		
		/**
		 * @param timestamp Number of seconds elapsed since the start of the activity
		 */
		public void addPoint(
			double timestamp, double latitude, double longitude, double altitude) {
			JsonWGS84 wgs = new HealthGraphClient.JsonWGS84();
			wgs.latitude = latitude;
			wgs.longitude = longitude;
			wgs.altitude = altitude;
			if (mPath.size() == 0) {
				wgs.type = "start";
			} else {
				wgs.type = "gps";
			}
			wgs.timestamp = timestamp;
			mPath.add(wgs);
		}
		
		public JsonWGS84 lastPoint() { 
			return mPath.get(mPath.size() - 1);
		}
		
		public ArrayList<JsonWGS84> getPath() { 
			return mPath; 
		}
	}
	
	/**
	 * User profile on runkeeper
	 *
	 */
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
	
    static private HealthGraphClient mSingleton;
    
    static private final String OAUTH2_CLIENT_ID = "0808ef781c68449298005c8624d3700b";
    static private final String OAUTH2_CLIENT_SECRET = "dda5888cd8d64760a044dc61ae4f44db";
    static private final String OAUTH2_REDIRECT_URI = "ysaito://oauthresponse";
    
    /**
     *  This method is not thread safe. It can be called only by the main thread
     */
    static HealthGraphClient getSingleton() {
    	if (mSingleton == null) {
    		Log.d(TAG, "Creating singleton!!");
    		mSingleton = new HealthGraphClient();
    	}
    	return mSingleton;
    }

    /**
     * This method should be called from the main Activity when the web browser is redirected to OAUTH2_REDIRECT_URI.
     * 
     * This method must be called by the main thread.
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
    
    /**
     * Runkeeper oauth2 access token. null if not yet acquired. Once acquired, it is
     * cached as part of a hidden application preference.
     */
    private String mToken;
    
    private AuthenticatorImpl mAuthenticator;
    
    public HealthGraphClient() {
    	mAccessTokenState = TOKEN_UNSET;
    }

    /**
     * Get the user profile from runkeeper asynchronously. The result will be passed through the listener in the main thread.  
     * @param listener Its onFinish callback delivers a JsonUser object on success
     */
    public void getUser(GetResponseListener listener) {
    	executeGet("fitnessActivities", "application/vnd.com.runkeeper.User+json", listener, new JsonUser());
    }
    
    /**
     * Get the fitness activities by a user from runkeeper asynchronously. 
     * The result will be passed through the listener in the main thread.  
     * @param listener Its onFinish callback delivers a JsonFitnessActivities object on success
     */
    public void getFitnessActivities(GetResponseListener listener) {
    	executeGet("fitnessActivities", "application/vnd.com.runkeeper.FitnessActivityFeed+json",
    			listener, new JsonFitnessActivities());
    }

    public interface PutNewFitnessActivityListener {
    	public void onFinish(Exception e, String runkeeperPath);
    };
    
    /**
     * Save a new fitness activity in runkeeper.
     */
    public void putNewFitnessActivity(
    		JsonActivity activity,
    		final PutNewFitnessActivityListener listener) {
    	executePost("fitnessActivities", "application/vnd.com.runkeeper.NewFitnessActivity+json", 
    			activity, 
    			new PutResponseListener() {
    				public void onFinish(Exception e, HttpResponse response) {
    					if (response != null) {
    						org.apache.http.Header[] headers = response.getHeaders("Location");
    						String runkeeperPath = null;
    						if (headers != null && headers.length > 0) runkeeperPath = headers[0].getValue();
    						listener.onFinish(e, runkeeperPath);
    					} else {
    						listener.onFinish(e, null);
    					}
    				}
    	});
    }
    
    private void cacheAccessToken(Context context, String token) {
    	synchronized(this) {
    		mToken = token;
    		if (token == null) {
    			mAccessTokenState = TOKEN_ERROR;
    			Util.error(context,  "Failed to sign into runkeeper");
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
    			Util.info(context,  "Created new signin token into runkeeper");
    		}
    		this.notifyAll();
    	}
    }

    private void executeGet(String path, String acceptType, GetResponseListener listener, Object destObject) {
    	HttpGetTask task = new HttpGetTask(this, path, acceptType, destObject, listener);
    	task.execute();
    }
    
    private void executePut(String path, String contentType, Object object, PutResponseListener listener) {
    	HttpPutTask task = new HttpPutTask(this, path, contentType, object, listener);
    	task.execute();
    }

    private void executePost(String path, String contentType, Object object, PutResponseListener listener) {
    	HttpPostTask task = new HttpPostTask(this, path, contentType, object, listener);
    	task.execute();
    }
    
    public abstract static interface GetResponseListener { 
    	public abstract void onFinish(Exception e, Object o);
    }
    
    private abstract static interface PutResponseListener {
    	public abstract void onFinish(Exception e, HttpResponse response);
    }

    private String getAccessToken() throws Exception {
    	synchronized(this) {
    		if (mAccessTokenState != TOKEN_OK) {
    			// TODO: provide more precise message
    			throw new Exception("HealthGraph token retrieval failed");
    		}
    		return mToken;
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
    				mListener.onFinish(new Exception(
    						"HTTP Get failed: " + status.getStatusCode() + ": " + status.getReasonPhrase()),
    						null);
    				return;
    			}
    		}
    		HttpEntity entity = (response != null ? response.getEntity() : null);
    		if (entity == null) {
    			mListener.onFinish(new Exception(
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
    			mListener.onFinish(mException, response);
    			return;
    		}
    		if (response != null) {
    			StatusLine status = response.getStatusLine(); 
    			Log.d(TAG, "PUT finished: " + status.getStatusCode() + ":" + status.getReasonPhrase());
    			if (status.getStatusCode() / 100 != 2) {
    				mListener.onFinish(new Exception(
    						"HTTP Get failed: " + status.getStatusCode() + ": " + status.getReasonPhrase()),
    						response);
    				return;
    			}
    		}
    		mListener.onFinish(null, response);
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
    			mListener.onFinish(mException, response);
    			return;
    		}
    		if (response != null) {
    			StatusLine status = response.getStatusLine(); 
    			Log.d(TAG, "PUT finished: " + status.getStatusCode() + ":" + status.getReasonPhrase());
    			if (status.getStatusCode() / 100 != 2) {
    				mListener.onFinish(new Exception(
    						"HTTP Get failed: " + status.getStatusCode() + ": " + status.getReasonPhrase()),
    						response);
    				return;
    			}
    		}
    		mListener.onFinish(null, response);
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
    		mToken = pref.getString("AccessToken", null);
    		if (mToken != null) {
    			Log.d(TAG, "Found cached token: " + mToken);
    			mAccessTokenState = TOKEN_OK;
    			Util.info(context,  "Signed into runkeeper");
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
    					.setClientId(OAUTH2_CLIENT_ID).setRedirectURI(OAUTH2_REDIRECT_URI)
    					.buildQueryMessage();
    			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(request.getLocationUri() + "&response_type=code"));
    			mContext.startActivity(intent);
    		} catch (OAuthSystemException e) {
    			Log.e(TAG, "HealthGraph OAuth failed: " + e.toString());
    		}
    	}
    	
    	public void onRedirect(Uri uri) {
    		if (uri.toString().startsWith(OAUTH2_REDIRECT_URI)) {
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
    						.setClientId(OAUTH2_CLIENT_ID)
    						.setClientSecret(OAUTH2_CLIENT_SECRET)
    						.setRedirectURI(OAUTH2_REDIRECT_URI)
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