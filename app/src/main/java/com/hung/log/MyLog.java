package com.hung.log;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Calendar;

/**
 * @author Hung
 * Class nay lay log va ghi vao file tren external storage.
 * LogCat chi can thiet khi ung dung chay. Neu ko chay thi nen close lai.
 * + qua trinh ghi tren thread doc lap, ko lien quan toi mainthread.
 * + ghi from StartLogLabel to endLogLabel
 * + Log file name is base on the time start write to file
 * + neu log file size > 10M thi ghi ra file moi
 */
public class MyLog {
	private final String TAG= MyLog.class.getSimpleName();
	final static int ERR = 0;
	final static int WARN = 1;
	final static int INFO = 2;
	final static int DEBUG = 3;
	final static int VERB = 4;

	static int LOG_LEVEL=4;

	//ko hien thi threadid
	private final String[] COMMAND_TIME = {"logcat","-v","time"};
	//hien thi time, PID, threadID
	private final String[] COMMAND_THREAD = {"logcat","-v","threadtime"};
	//clear buffer

	private String mFolderSaveLog = "MyLog";
	//
	public String mLoggingFolder;  // for test
	/**
	 * find this value in outputLog of runtime to interrupt getting log
	 */
	public static String mPosLogInterrupt;
	/**
	 * find this value in outputLog of runtime to start get log
	 */
	public static String mPosLogStart;

	private final int SIZE_LOGFILE = 10240000;//10MB
	//so nay ko chinh xac neu chuyen sang UTF8, vi char = 2byte
	private int mSizeLogFile; //fileSize < SIZE_LOGFILE

	private Process mProcess; //to close file
	private BufferedWriter mBuffWriter;//write to file, close
	private BufferedReader mBufferedReader;// read from Logcat to buffer

	public volatile Thread mThread; //thread to run log file
	public Object mLockStartLog = new Object();//Application wait mThread run

	private volatile boolean mIsRuningInterupt = false;

	private int mCountFile = 0;

	/**
	 * Tao logfile theo thoi gian tren may:
	 * vd: 20160416_1807_08330.log => 2016/04/16 18:17 08:330ms
	 * */
	private String nameLogFile(){
        //set a file
        Calendar iTime = Calendar.getInstance();
        int year, month, day, hour, minute, second, mini;

        year = iTime.get(Calendar.YEAR);
        month = iTime.get(Calendar.MONTH)+1;
        day = iTime.get(Calendar.DAY_OF_MONTH);
        hour = iTime.get(Calendar.HOUR_OF_DAY);
        minute = iTime.get(Calendar.MINUTE);
        second = iTime.get(Calendar.SECOND);
        mini = iTime.get(Calendar.MILLISECOND);

        String fileName = String.format("%d%02d%02d_%02d%02d_%02d%d_f%d", year,month,day,hour,minute,second,mini,mCountFile) + ".log";

        return fileName;
	}

	/**
	 * Tao ra mot string from current time
	 * Lay string nay de xac dinh Moc bat dau lay Log va ket thuc Log.
	 * @return
	 */
	private String getLogLabel(){
        Calendar iTime = Calendar.getInstance();
        int hour, minute, second;

        hour = iTime.get(Calendar.HOUR_OF_DAY);
        minute = iTime.get(Calendar.MINUTE);
        second = iTime.get(Calendar.SECOND);

        return String.format("x%02d%02d%02dx", hour,minute,second);

	}

	/**
	 * lay Moc thoi gian ghi log truc tiep vao File khong qua Log.v().
	 * @return
	 */
	private String getCurrentTime(){
        Calendar iTime = Calendar.getInstance();
        int year, month, day, hour, minute, second, mini;

        year = iTime.get(Calendar.YEAR);
        month = iTime.get(Calendar.MONTH)+1; //thang bat dau tu 0,1,...11
        day = iTime.get(Calendar.DAY_OF_MONTH);
        hour = iTime.get(Calendar.HOUR_OF_DAY);
        minute = iTime.get(Calendar.MINUTE);
        second = iTime.get(Calendar.SECOND);
        mini = iTime.get(Calendar.MILLISECOND);

        String st = String.format("%d%02d%02d %02d:%02d:%02d.%d ", year,month,day,hour,minute,second,mini);
        return st;
	}
	/**
	 *  Checks if external storage is available for read and write
	 * @return
	 */
	private boolean isExternalStorageWritable() {
	    String state = Environment.getExternalStorageState();
	    if (Environment.MEDIA_MOUNTED.equals(state)) {
	        return true;
	    }
	    Log.e(TAG,"permission to write to External Storage is false");
	    return false;
	}

	/**
	 * Checks if external storage is available to at least read
	 * @return
	 */
	private boolean isExternalStorageReadable() {
	    String state = Environment.getExternalStorageState();
	    if (Environment.MEDIA_MOUNTED.equals(state) ||
	        Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
	        return true;
	    }
	    Log.e(TAG,"permission to read from External Storage is false");
	    return false;
	}

	/**
	 * neu size cua file vuot qua gioi han thi se tao file moi.
	 * 1. check external storage
	 * 2. check time configuration change => close old file then create new file
	 * 3. ghi mBuffWriter vao file
	 */
	private boolean createLogFile(){
		String fileName = nameLogFile();
		String st;

		if (!isExternalStorageWritable()){
			return false;
		}

		try {

			File folder = new File(Environment.getExternalStorageDirectory() + File.separator + mFolderSaveLog);
			mLoggingFolder = folder.getAbsolutePath();
			Log.d(TAG,folder.getAbsolutePath());
			if(!folder.exists()){
				if(!folder.mkdirs()){
					return false;
				}
			}

			//if file exist, it will be replace in default
			File file = new File(folder, fileName);
			st = Environment.getExternalStorageDirectory().getPath();

			FileOutputStream OutputStream= new FileOutputStream(file); //sequence byte of file no buffer
			OutputStreamWriter writer = new OutputStreamWriter(OutputStream); //byte to charset convert 8k char buffer
			mBuffWriter= new BufferedWriter(writer, 1024); //buffer for char 1024 char buffer
			mCountFile++;
			mSizeLogFile = 0;
			return true;
		} catch (Exception e) {
			Log.e(TAG, Log.getStackTraceString(e));

		}

		return false;
	}

	/**
	 * function nay lay thong tin ve logcat tu he thong. chưa ghi ra file
	 * Lay thong tin tu LogCat ra {@code mBufferedReader}
	 */
	private boolean readSystemLog(){

		try {
			mProcess = Runtime.getRuntime().exec(COMMAND_THREAD);

			mBufferedReader = new BufferedReader(new InputStreamReader(mProcess.getInputStream()));
			return true;
		} catch (IOException e) {
			try {
				//phan log ghi truc tiep ra file
				mBuffWriter.write("exception: readSystemLog()");
				mBuffWriter.newLine();
				mBuffWriter.write(Log.getStackTraceString(e));
				mBuffWriter.flush();
				mBuffWriter.close();

			} catch (IOException e1) {
				Log.e(TAG, Log.getStackTraceString(e1));
			}
			Log.e(TAG,"Runtime.getRuntime().exec");
			Log.e(TAG, Log.getStackTraceString(e));
		}

		return false;
	}

	public void setFolderLog(String folderName){
		mFolderSaveLog = folderName;
	}

	/**
	 * Call this function from other thread de tao Thread doc Log
	 * Chi duy nhat 1 thread dc chay de lay logFile
	 * + goi ham nay tu appService, hoac Application class (Activivity.onCreate() ) neu ko co Service
	 *
	 * @return true if success
	 * */
	public void startGetLogCat(){
		if(mThread == null){
			createThreadLogCat();
		}else {
			//thread da khoi tao roi, ko goi nua
//			if (!mThread.isAlive()) {
//				mThread.start();
//			}
			return;
		}

		try {
			// stop app to waiting for thread running
			synchronized (mLockStartLog) {
				mPosLogStart = getLogLabel();//danh dau vi tri bat dau
				mLockStartLog.wait(500);	//cho thread start xong moi ghi log thi moi tiep tuc
				Log.v(TAG,"start="+ mPosLogStart );	//thread da started nen bat dc dong nay
			}

		} catch (InterruptedException e) {
			Log.e(TAG, Log.getStackTraceString(e));
		}

	}
	/**
	 * Call this function from other thread
	 * co the goi ham nay nhieu lan => ok
	 * + ket thuc thread nay khi Destroy Service hoac Destroy Activity (neu ko co service)
	 */
	public void stopThread(){
		if(mThread == null){
			return;
		}
		if (mIsRuningInterupt) {
			return;
		}
		if (mThread.isAlive()) {
			mThread.interrupt(); //check in source code of thread: mThread.Interupted() or mThread.IsInterupted
		}

	}

	/**
	 * goi function nay de thuc hien toan bo qua trinh lay log va ghi ra file
	 * 1. doc tu LogCat ra mBufferedReader
	 * 2. doc tu mBufferReader => mBuffWriter vào file
	 *
	 * */
	private void createThreadLogCat(){
		mThread = new Thread(){

			@Override
			public void run() {
				String st;
				Exception iException=null;
				boolean iFindLogStart = true;
				boolean iFindLogInterrupt = false;
                mIsRuningInterupt = false;

				if(!createLogFile()){
					return;
				}

				if(!readSystemLog()){
					return;
				}

				try {
					synchronized (mLockStartLog) {
						mLockStartLog.notify();
						mBuffWriter.write(getCurrentTime()+ " start thread write log");//dong nay ghi dau tien (first line)
						mBuffWriter.newLine();
						Log.v(TAG,"****start thread***"); //(second line) bi filter boi mPosLogStart
					}

					while(true){
						if (interrupted()) {
							if(mIsRuningInterupt == false){
								mIsRuningInterupt = true;
								mPosLogInterrupt = getLogLabel();
								Log.v(TAG,"interupt="+mPosLogInterrupt);
								iFindLogInterrupt = true;
								//chua stop, cho den khi ghi het ra logFile moi stop
							}
						}
						//pending here
						st = mBufferedReader.readLine();

						if(st == null){//ket thuc doc log
							//da doc den cuoi roi => buffer is empty
							mBufferedReader.close();

							if (mIsRuningInterupt) {
								mBuffWriter.write(getCurrentTime() +" Log2File thread is interupted");
							} else {
								mBuffWriter.write(getCurrentTime() +"mProcess runtime end without thread interupt");
							}

							mBuffWriter.newLine();
							Log.v(TAG,"thread stop");
							break;//stop thread
						}else{
							if(iFindLogStart){
								//bat dau ghi system log ra file
								if(st.contains(mPosLogStart)){
									iFindLogStart = false;//ko tim nua
								}else{
									continue;//ko ghi vao BuffWriter
								}

							}

							if(iFindLogInterrupt){
								if(st.contains(mPosLogInterrupt)){
									mBuffWriter.write(st);
									break;//ket thuc ghi system log ra file
								}
							}

							mBuffWriter.write(st);
							mBuffWriter.newLine();
							mSizeLogFile += st.length();
							if(mSizeLogFile > SIZE_LOGFILE){
								mBuffWriter.write(getCurrentTime() +" LogFile is over size: close this file then log to new file");
								mBuffWriter.newLine();
								//close current file because over size
								mBuffWriter.flush();
								mBuffWriter.close();
								//create new file
								if(!createLogFile()){
									break;
								}
							}
						}
					}//end while


				}catch (IOException e) {
					Log.v(TAG, Log.getStackTraceString(e));
					iException = e;
				}catch (Exception e) {
					Log.v(TAG, Log.getStackTraceString(e));
					iException = e;
				}

				//tranh exception cua phan read mProcess
				try {
					mThread = null;
					mBuffWriter.newLine();
					mBuffWriter.write(getCurrentTime()+ " mProcess.destroy()");
					mProcess.destroy();
					mBufferedReader.close();

					mBuffWriter.newLine();
					mBuffWriter.write(getCurrentTime()+ " stop thread write log");
					//ghi data trong buffer vao file va xoa het du lieu
					mBuffWriter.flush();
					mBuffWriter.close();//dong giao tiep file lai

				} catch (IOException e) {
					Log.e(TAG, Log.getStackTraceString(e));
					mProcess.destroy();
				}


			}//run()

		};//thread;

		mThread.start();
	}


	/**
	 *Error
	 */
	public static void e(String tag, String string)
	{
	    if(LOG_LEVEL >= ERR)
	    {
	    	android.util.Log.e(tag, string);
	    }

	}

	/**
	 * Warn
	 */
	public static void w(String tag, String string)
	{
	    if(LOG_LEVEL >= WARN)
	    {
	    	android.util.Log.w(tag, string);
	    }

	}

	/**
	 * Info
	 */
	public static void i(String tag, String string)
	{
	    if(LOG_LEVEL >= INFO)
	    {
	        android.util.Log.i(tag, string);
	    }
	}

	/**
	 * Debug
	 */
	public static void d(String tag, String string)
	{
	    if(LOG_LEVEL >= DEBUG)
	    {
	        android.util.Log.d(tag, string);
	    }
	}

	/**
	 * Verbose
	 */
	public static void v(String tag, String string)
	{
	    if(LOG_LEVEL >= VERB)
	    {
	        android.util.Log.v(tag, string);
	    }
	}
	

}
