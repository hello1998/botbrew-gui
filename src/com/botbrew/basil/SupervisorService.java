package com.botbrew.basil;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class SupervisorService extends Service {
	private static final String TAG = "BotBrew";
	public class LocalBinder extends Binder {
		public SupervisorService getService() {
			return SupervisorService.this;
		}
	}
	private class SupervisorProcess implements Runnable {
		private int startId;
		public SupervisorProcess(int startId) {
			this.startId = startId;
		}
		@Override
		public void run() {
			if(!((BotBrewApp)getApplicationContext()).isInstalled(BotBrewApp.root)) {
				Log.v(TAG,"cannot start supervisor");
				return;
			}
			Log.v(TAG,"supervisor started");
			Shell.Pipe sh = null;
			try {
				sh = Shell.Pipe.getRootShell();
				sh.botbrew(BotBrewApp.root.getAbsolutePath(),"runsvdir -P /etc/sv/enabled 'log: ................................................................................................................................................................................................................................................................'");
				sh.stdin().close();
				sh.waitFor();
			} catch(IOException ex) {
			} catch(InterruptedException ex) {
			} finally {
				boolean exited = true;
				if(sh != null) {
					Process p = sh.proc;
					try {
						p.exitValue();
					} catch(IllegalThreadStateException ex0) {
						try {	// the process exists, so send SIGHUP
							Log.v(TAG,"sending SIGHUP to runsvdir...");
							Field f = p.getClass().getDeclaredField("id");
							f.setAccessible(true);
							f.get(p);
							sh = Shell.Pipe.getRootShell();
							sh.exec("kill -1 "+f.get(p));
							sh.stdin().close();
							if(sh.waitFor() == 0) {
								p.waitFor();
								exited = false;
							}
						} catch(NoSuchFieldException ex1) {
							Log.v(TAG,"NoSuchFieldException");
						} catch(IllegalAccessException ex1) {
							Log.v(TAG,"IllegalAccessException");
						} catch(IOException ex1) {
							Log.v(TAG,"IOException");
						} catch(InterruptedException ex1) {
							Log.v(TAG,"InterruptedException");
						}
					}
				}
				if(exited) {	// the process does not exist, so clean up offline
					try {
						Log.v(TAG,"sending SIGTERM to runsv...");
						sh = Shell.Pipe.getRootShell();
						sh.botbrew(BotBrewApp.root.getAbsolutePath());
						final OutputStream p_stdin = sh.stdin();
						p_stdin.write("killall -1 runsvdir || true\nkillall -15 runsv || true\n".getBytes());
						String[] enabled = (new File("/etc/sv/enabled")).list();
						if(enabled != null) for(String filename: enabled) p_stdin.write(("sv exit "+filename+" || true\n").getBytes());
						p_stdin.flush();
						p_stdin.close();
						sh.waitFor();
					} catch(IOException ex) {
					} catch(InterruptedException ex) {
					} finally {
						stopSelfResult(startId);
						Log.v(TAG,"supervisor stopped");
					}
				}
			}
		}
	}
	private static boolean mRunning = false;
	private static final int ID_RUNNING = 1;
	private final IBinder mBinder = new LocalBinder();
	private SupervisorProcess mSupervisorProcess;
	private Thread mSupervisorThread;
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	@Override
	public void onStart(Intent intent, int startId){
		super.onStart(intent,startId);
		mRunning = true;
		Notification notification = new Notification(R.drawable.ic_launcher,"BotBrew Supervisor started",System.currentTimeMillis());
		notification.setLatestEventInfo(getApplicationContext(),"BotBrew Supervisor running","tap to manage",PendingIntent.getActivity(this,0,new Intent(this,SupervisorActivity.class),0));
		startForeground(ID_RUNNING,notification);
		mSupervisorProcess = new SupervisorProcess(startId);
		mSupervisorThread = new Thread(mSupervisorProcess);
		mSupervisorThread.start();
	}
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		onStart(intent,startId);
		return START_STICKY;
	}
	@Override
	public void onDestroy() {
		if(mSupervisorThread != null) {
			mSupervisorThread.interrupt();
			mSupervisorThread = null;
			mSupervisorProcess = null;
		}
		mRunning = false;
		super.onDestroy();
	}
	public static boolean isRunning() {
		return mRunning;
	}
}