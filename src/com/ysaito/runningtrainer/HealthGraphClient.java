package com.ysaito.runningtrainer;

import java.io.InputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.Executor;

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
	
	static private final HealthGraphClient mSingleton = new HealthGraphClient();
    static private final String OAUTH2_CLIENT_ID = "0808ef781c68449298005c8624d3700b";
    static private final String OAUTH2_CLIENT_SECRET = "dda5888cd8d64760a044dc61ae4f44db";
    static private final String OAUTH2_REDIRECT_URI = "ysaito://oauthresponse";
    
    /**
     *  This method is not thread safe. It can be called only by the main thread
     */
    static HealthGraphClient getSingleton() {
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

    /**
     * Runkeeper oauth2 access token. null if not yet acquired. Once acquired, it is
     * cached as part of a hidden application preference.
     */
    private String mToken = null;
    private static final String ERROR_TOKEN = "ERROR_TOKEN"; 
    
    private AuthenticatorImpl mAuthenticator = null;

    /**
     * Get the user profile from runkeeper asynchronously. The result will be passed through the listener in the main thread.  
     * @param listener Its onFinish callback delivers a JsonUser object on success
     */
    public void getUser(GetResponseListener listener, Executor executor) {
    	executeGet("fitnessActivities", "application/vnd.com.runkeeper.User+json", listener, executor, new JsonUser());
    }
    
    /**
     * Get the fitness activities by a user from runkeeper asynchronously. 
     * The result will be passed through the listener in the main thread.  
     * @param listener Its onFinish callback delivers a JsonFitnessActivities object on success
     */
    public void getFitnessActivities(GetResponseListener listener, Executor executor) {
    	executeGet("fitnessActivities?pageSize=1000000", "application/vnd.com.runkeeper.FitnessActivityFeed+json",
    			listener, executor, new JsonFitnessActivities());
    }

    public void getFitnessActivity(String uri, GetResponseListener listener, Executor executor) {
    	executeGet(uri,
    			"application/vnd.com.runkeeper.FitnessActivity+json",
    			listener, executor, new JsonActivity());
    }

    public void getFitnessActivityAsString(String uri, GetResponseListener listener, Executor executor) {
    	executeGet(uri,
    			"application/vnd.com.runkeeper.FitnessActivity+json",
    			listener, executor, null);
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
    
    private void executeGet(String path, String acceptType, GetResponseListener listener, Executor executor, Object destObject) {
    	HttpGetTask task = new HttpGetTask(this, path, acceptType, destObject, listener);
    	task.executeOnExecutor(executor);
    }
    
    private void executePut(String path, String contentType, Object object, PutResponseListener listener) {
    	HttpPutTask task = new HttpPutTask(this, path, contentType, object, listener);
    	task.executeOnExecutor(Util.DEFAULT_THREAD_POOL);
    }

    private void executePost(String path, String contentType, Object object, PutResponseListener listener) {
    	HttpPostTask task = new HttpPostTask(this, path, contentType, object, listener);
    	task.executeOnExecutor(Util.DEFAULT_THREAD_POOL);
    }
    
    public abstract static interface GetResponseListener { 
    	public abstract void onFinish(Exception e, Object o);
    }
    
    private abstract static interface PutResponseListener {
    	public abstract void onFinish(Exception e, HttpResponse response);
    }

    private String getAccessToken() throws Exception {
    	synchronized(this) {
    		if (mToken == null || mToken.equals(ERROR_TOKEN)) {
    			throw new Exception("No sign-in token found");
    		}
    		return mToken;
    	}
    }
    
    private static class HttpGetTask extends AsyncTask<Void, Void, Object> {
    	private final HealthGraphClient mParent;
    	private final String mPath;
    	private final String mAcceptType;
    	private final Object mDestObject;  // may be null, in which case the result is returned unparsed
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
    	
    	@Override protected Object doInBackground(Void...unused) {
    	    DefaultHttpClient httpClient = new DefaultHttpClient();
    		Object parseResult = null;
    		try {
    			String token = mParent.getAccessToken();
    			HttpGet get = new HttpGet("https://api.runkeeper.com/" + mPath);
    			get.addHeader("Authorization", "Bearer " + token);
    			get.addHeader("Accept", mAcceptType);
    			final HttpResponse response = httpClient.execute(get);
    			if (response == null) {
    				throw new Exception("HTTP Get produced no response");
    			}
    			
    			final StatusLine status = response.getStatusLine(); 
    			Log.d(TAG, "Get finished: " + status.getStatusCode() + ":" + status.getReasonPhrase());
    			if (status.getStatusCode() / 100 != 2) {
    				throw new Exception(
    						"HTTP Get failed: " + status.getStatusCode() + ": " + status.getReasonPhrase());
    			}
    			final HttpEntity entity = response.getEntity();
    			if (entity == null) {
    				throw new Exception("HTTP Get returned an empty body");
    			}
    			// A Simple JSON Response Read
    			InputStream instream = null;
    			String result = "";

    			try {
    				instream = entity.getContent();
    				result = new Scanner(instream).useDelimiter("\\A").next();
    			} finally {
    				if (instream != null) instream.close();
    			}
    			if (mDestObject != null) {
    				Gson gson = new GsonBuilder().create();
    				parseResult = gson.fromJson(result, mDestObject.getClass());
    			} else {
    				parseResult = result;
    			}
    		} catch (Exception e) {
    			mException = e;
    			Log.d(TAG, "Http GET exception: " + e.toString());
    		}
    		return parseResult;
    	}
    	
    	@Override protected void onPostExecute(Object parseResult) {
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

    public void init(Context context) {
    	synchronized(this) {
    		if (mToken == null) {
    			SharedPreferences pref = context.getSharedPreferences("HealthGraphAuthCache", Context.MODE_PRIVATE);
    			mToken = pref.getString("AccessToken", null);
    		}
    	}
    }
    
    public boolean isSignedIn() {
    	synchronized(this) {
    		return mToken != null && !mToken.equals(ERROR_TOKEN);
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
    		mToken = pref.getString("AccessToken", null);
    		if (mToken != null && !mToken.equals(ERROR_TOKEN)) {
    			Util.info(context,  "Reusing a saved sign-in token for RunKeeper");
    			return;
    		}
    		if (mAuthenticator == null) {
    			Log.d(TAG, "Start Healthgraph authentication");
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
    			synchronized(this) {
    				if (token == null) {
    					mToken = ERROR_TOKEN;
    					Util.error(mContext,  "Failed to sign into runkeeper");
    				} else {
    					Log.d(TAG, "Acquired HealthGraph token: " + token);
    					mToken = token;
    					SharedPreferences pref = mContext.getSharedPreferences("HealthGraphAuthCache", Context.MODE_PRIVATE);
    					SharedPreferences.Editor editor = pref.edit();
    					editor.putString("AccessToken", token);
    					if (!editor.commit()) {
    						Log.e(TAG, "Failed to write oauth token to preferences");
    					} else {
    						Log.d(TAG, "Save access token " + token);
    					}
    					Util.info(mContext,  "Signed into RunKeeper");
    				}
    				mAuthenticator = null;
    				this.notifyAll();
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