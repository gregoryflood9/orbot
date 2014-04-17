package org.torproject.android;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.sufficientlysecure.rootcommands.Shell;
import org.sufficientlysecure.rootcommands.Toolbox;
import org.sufficientlysecure.rootcommands.command.SimpleCommand;
import org.torproject.android.service.TorResourceInstaller;
import org.torproject.android.service.TorServiceConstants;
import org.torproject.android.service.TorServiceUtils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;


public class OrbotDiagnosticsActivity extends Activity {

	private TextView mTextView = null;
	private final static String TAG = "OrbotDiag";
	private StringBuffer log = new StringBuffer();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) { 
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.layout_diag);
		
		mTextView = (TextView)findViewById(R.id.diaglog);
		
	}

	private String getFreeStorage ()
	{
		File path = Environment.getDataDirectory();
		StatFs stat = new StatFs(path.getPath());
		long blockSize = stat.getBlockSize();
		long availableBlocks = stat.getAvailableBlocks();
		return Formatter.formatFileSize(this, availableBlocks * blockSize);
	}
	
	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
	}

	@Override
	protected void onDestroy() {

		super.onDestroy();
		File appBinHome = this.getDir("bin", Context.MODE_PRIVATE);

		File fileTor= new File(appBinHome, TorServiceConstants.TOR_ASSET_KEY);
    		    	
    	try {    		
			killAllTor (fileTor);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();


		log("Hello, Orbot!");
		
		try
		{
			log(android.os.Build.DEVICE);
			log(android.os.Build.HARDWARE);
			log(android.os.Build.MANUFACTURER);
			log(android.os.Build.MODEL);
			log(android.os.Build.VERSION.CODENAME);
			log(android.os.Build.VERSION.RELEASE);
		}
		catch (Exception e)
		{
			log("error getting device info");
		}
		
		showFileTree ();
		
		runTorTest();
	}

	private void killAllTor (File fileTor) throws IOException
	{
		try
		{
			int maxTry = 5;
	    	int currTry = 0;
	    	
	    	Shell shell = Shell.startShell();	    	
	    	int procId;
	    	
			while ((procId = TorServiceUtils.findProcessId(fileTor.getAbsolutePath())) != -1 && currTry++ < maxTry)
			{
				
				log ("Found existing orphan Tor process; Trying to shutdown now (device restart may be needed)...");			
				log("Found Tor PID=" + procId + " - attempt to shutdown now...");
				
				SimpleCommand killCommand = new SimpleCommand("toolbox kill -9 " + procId);
				shell.add(killCommand);
				killCommand.waitForFinish();
				log ("kill output: " + killCommand.getExitCode() + "; " + killCommand.getOutput());
				killCommand = new SimpleCommand("kill -9 " + procId);
				shell.add(killCommand);
				killCommand.waitForFinish();
				log ("kill output: " + killCommand.getExitCode() + "; " + killCommand.getOutput());
			}
		}
		catch (Exception e)
		{
			log("error killing Tor: " + e.getLocalizedMessage());
			Log.d(TAG, "error killing Tor", e);
		}
		
	}
	
	private void runTorTest ()
	{
		try
		{
			File appBinHome = this.getDir("bin", Context.MODE_PRIVATE);
			File appDataHome = this.getDir("data", Context.MODE_PRIVATE);
	
	    	File fileTor= new File(appBinHome, TorServiceConstants.TOR_ASSET_KEY);
	    	enableBinExec (fileTor, appBinHome);	    	
	    	killAllTor (fileTor);
	
			InputStream is = getResources().openRawResource(R.raw.torrcdiag);
			File fileTorrc = new File(appBinHome, TorServiceConstants.TORRC_ASSET_KEY + "diag");
			TorResourceInstaller.streamToFile(is,fileTorrc, false, false);
		
			/**
			ArrayList<String> alEnv = new ArrayList<String>();
			alEnv.add("HOME=" + appBinHome.getAbsolutePath());
			Shell shell = Shell.startShell(alEnv,appBinHome.getAbsolutePath());
			SimpleCommand cmdTor = new SimpleCommand(fileTor.getAbsolutePath() + " DataDirectory " + appDataHome.getAbsolutePath() + " -f " + fileTorrc.getAbsolutePath());			
			shell.add(cmdTor);
			**/
			
			String cmd = fileTor.getAbsolutePath() + " DataDirectory " + appDataHome.getAbsolutePath() + " -f " + fileTorrc.getAbsolutePath();
			
			log ("Executing command> " + cmd);
			
			Process process = Runtime.getRuntime().exec(cmd);
			
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			StreamGobbler sg = new StreamGobbler();
			sg.reader = bufferedReader;
			sg.process = process;
			new Thread(sg).start();
			
			if (process.getErrorStream() != null)
			{
				bufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
				sg = new StreamGobbler();
				sg.reader = bufferedReader;
				sg.process = process;
				new Thread(sg).start();
			}
			
			
		}
		catch (Exception e)
		{
			Log.d(TAG,"runTorTest exception",e);
		}
        
	}
	
	class StreamGobbler implements Runnable
	{
		BufferedReader reader;
		Process process;
		
		public void run ()
		{
			String line = null;			
			try {
				while ( (line = reader.readLine()) != null)
				{
					Message msg = mHandler.obtainMessage(0);
					msg.getData().putString("log", line);
					mHandler.sendMessage(msg);
				}
				
			} catch (IOException e) {
				Log.d(TAG, "error reading line",e);
			}
			
			log("Tor exit code=" + process.exitValue() + ";");
		}
	}
	
	 private boolean enableBinExec (File fileBin, File appBinHome) throws Exception
	    {
	    	
	    	log(fileBin.getName() + ": PRE: Is binary exec? " + fileBin.canExecute());
	  
	    	if (!fileBin.canExecute())
	    	{
	    		log("(re)Setting permission on binary: " + fileBin.getAbsolutePath());	
				Shell shell = Shell.startShell(new ArrayList<String>(), appBinHome.getAbsolutePath());
			
				shell.add(new SimpleCommand("chmod " + TorServiceConstants.CHMOD_EXE_VALUE + ' ' + fileBin.getAbsolutePath())).waitForFinish();
				
				File fileTest = new File(fileBin.getAbsolutePath());
				log(fileTest.getName() + ": POST: Is binary exec? " + fileTest.canExecute());
				
				shell.close();
	    	}
	    	
			return fileBin.canExecute();
	    }
	
	private void showFileTree ()
	{
		
		File fileDir = this.getDir("bin", Context.MODE_PRIVATE);
		
		if (fileDir.exists())
		{
			log("checking file tree: " + fileDir.getAbsolutePath());
			printDir (fileDir.getName(), fileDir);
		}
		else
		{
			log("app_bin does not exist");
		}
		
		fileDir = this.getDir("data", Context.MODE_PRIVATE);
		if (fileDir.exists())
		{
			log("checking file tree: " + fileDir.getAbsolutePath());
			printDir (fileDir.getName(), fileDir);
		}
		else
		{
			log ("app_data does not exist");
		}
				

	}
	
	private void printDir (String path, File fileDir)
	{
		File[] files = fileDir.listFiles();
		
		if (files != null && files.length > 0)
		{
			for (File file : files)
			{

				try
				{
					if (file.isDirectory())
					{
						printDir(path + '/' + file.getName(), file);
					}
					else
					{
						log(path + '/' + file.getName() + " len:" + file.length() + " exec:" + file.canExecute());
						
					}
				}
				catch (Exception e)
				{
					log("problem printing out file information");
				}
			
			}
		}
	}
	
	Handler mHandler = new Handler ()
	{

		@Override
		public void handleMessage(Message msg) {
		
			super.handleMessage(msg);
			
			String logMsg = msg.getData().getString("log");
			log(logMsg);
		}
		
	};
	
	private void log (String msg)
	{
		Log.d(TAG, msg);
		mTextView.append(msg + '\n');
		log.append(msg + '\n');
	}
	

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    // Inflate menu resource file.
	    getMenuInflater().inflate(R.menu.share_menu, menu);
	
	    // Locate MenuItem with ShareActionProvider
	    MenuItem item = menu.findItem(R.id.menu_item_share);
	
	    return true;
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	
	    case R.id.menu_item_share:
	    	sendLog();
	        return true;
	
	    default:
	        return super.onOptionsItemSelected(item);
	    }
    }
	
	private void sendLog ()
	{
		int maxLength = 5000;
		
		String logShare = null;
		
		if (log.length() > maxLength)
			logShare = log.substring(0,  maxLength);
		else
			logShare = log.toString();
		
		Intent sendIntent = new Intent();
		sendIntent.setAction(Intent.ACTION_SEND);
		sendIntent.putExtra(Intent.EXTRA_TEXT, logShare);
		sendIntent.setType("text/plain");
		startActivity(sendIntent);
	}


}
