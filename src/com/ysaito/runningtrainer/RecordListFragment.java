package com.ysaito.runningtrainer;

import java.util.ArrayList;
import java.util.GregorianCalendar;

import android.app.Activity;
import android.app.ListFragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity that lists available game logs.
 *
 */
public class RecordListFragment extends ListFragment {
	private static final String TAG = "RecordList";
	private RecordManager mRecordManager;
	private Activity mActivity;
	private MyAdapter mAdapter;

	private class ListThread extends AsyncTask<Integer, Integer, ArrayList<RecordSummary>> {
	  /**
	   * @param mode not used
	   */
	  @Override
	  protected ArrayList<RecordSummary> doInBackground(Integer... mode) {
		  return mRecordManager.listRecords();
	  }

	  @Override
	  protected void onProgressUpdate(Integer... unused) {
	  }

	  @Override
	  protected void onPostExecute(ArrayList<RecordSummary> records) {
		  // setProgressBarIndeterminateVisibility(false);
		  mAdapter.reset(records);
	  }
	}

	private static class MyAdapter extends ArrayAdapter<RecordSummary> {
		public MyAdapter(Context activity) {
			super(activity, android.R.layout.simple_list_item_1);
		}

		private Settings mSettings;
		public void reset(ArrayList<RecordSummary> newRecords) {
			mSettings = Settings.getSettings(getContext());
			clear();
			addAll(newRecords);
			notifyDataSetChanged();
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TextView view = (TextView) super.getView(position, convertView, parent);
			view.setText(this.getItem(position).toString(mSettings));
			return view;
		}
	};
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		//requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		//setContentView(R.layout.record_list);
		
		//setTitle("Record list");  // TODO: externalize
		mActivity = getActivity();
		mRecordManager = new RecordManager(mActivity);
		mAdapter = new MyAdapter(mActivity);
		setListAdapter(mAdapter);
		startListing();
		
		registerForContextMenu(getListView());
	}

	@Override public void onResume() {
		super.onResume();
		startListing();
	}

	@Override public void onStart() {
		super.onStart();
		startListing();
	}
	
	private void startListing() {
		ListThread thread = new ListThread();
		// setProgressBarIndeterminateVisibility(true);
		thread.execute((Integer[])null);
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		Log.d(TAG,"PICKED: " + position);
	}
	
	@Override
	public void onCreateContextMenu(
			ContextMenu menu,
			View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		
		final MenuInflater inflater = getActivity().getMenuInflater();
		inflater.inflate(R.menu.record_list_context_menu, menu);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		RecordSummary summary = mAdapter.getItem(info.position);

		switch (item.getItemId()) {
		case R.id.record_list_resend:
			HealthGraphClient.JsonActivity activity = mRecordManager.readRecord(summary.basename);
			if (activity == null) {
				Toast.makeText(getActivity(), "Failed to read " + summary.basename, Toast.LENGTH_LONG).show();
			} else {
				HealthGraphClient hgClient = HealthGraphClient.getSingleton();
				hgClient.putNewFitnessActivity(
						activity,
						new HealthGraphClient.PutResponseListener() {
							public void onFinish(Exception e) {
								if (e != null) {
									Toast.makeText(getActivity(), "Failed to send activity: " + e.toString(), Toast.LENGTH_LONG).show();
								} else {
									Toast.makeText(getActivity(), "Send activity to runkeeper", Toast.LENGTH_SHORT).show();
								}
							}
						});
			}
			return true;
		}
		return super.onContextItemSelected(item);
	}

	private class DeleteRecordTask extends AsyncTask<RecordSummary, String, String> {
		@Override
		protected String doInBackground(RecordSummary... log) {
			mRecordManager.deleteRecord(log[0]);
			return null;
		}
		
		@Override
		protected void onPostExecute(String unused) {
			startListing();
		}
	}
}
