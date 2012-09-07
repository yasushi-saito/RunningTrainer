package com.ysaito.runningtrainer;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

import android.app.Activity;
import android.app.ListFragment;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

/**
 * Activity that lists available game logs.
 *
 */
public class RecordListFragment extends ListFragment {
	private static final String TAG = "RecordList";
	private RecordManager mRecordManager;
	private Activity mActivity;
	private ArrayAdapter<RecordSummary> mAdapter;

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
		  mAdapter.clear();
		  mAdapter.addAll(records);
		  mAdapter.notifyDataSetChanged();
		  Log.d(TAG, "ADDED: " + mAdapter.getCount());
	  }
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		//requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		//setContentView(R.layout.record_list);
		
		//setTitle("Record list");  // TODO: externalize
		mActivity = getActivity();
		mRecordManager = new RecordManager(mActivity);
		mAdapter = new ArrayAdapter<RecordSummary>(mActivity, android.R.layout.simple_list_item_1);
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

	private final GregorianCalendar mTmpCalendar = new GregorianCalendar();

	
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
		switch (item.getItemId()) {
		case R.id.record_list_resend:
			
			HealthGraphClient.getSingleton(getActivity()).putNewFitnessActivity(
    		JsonActivity activity,
    		PutResponseListener listener) {
			
			Toast.makeText(getActivity(), "RESEND", Toast.LENGTH_SHORT).show();
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
