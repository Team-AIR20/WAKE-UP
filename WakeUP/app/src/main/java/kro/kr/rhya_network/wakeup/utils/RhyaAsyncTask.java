package kro.kr.rhya_network.wakeup.utils;

import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;

public abstract class RhyaAsyncTask<T1,T2> implements Runnable {
  // Argument
  public T1 mArgument;

  // Result
  public T2 mResult;

  // Handle the result
  public final int WORK_DONE = 0;
  public Handler mResultHandler = new Handler(new Handler.Callback() {
    @Override
    public boolean handleMessage(@NonNull Message msg) {
      // Call onPostExecute
      onPostExecute(mResult);

      return true;
    }
  });

  // Execute
  final public void execute(final T1 arg) {
    // Store the argument
    mArgument = arg;

    // Call onPreExecute
    onPreExecute();

    // Begin thread work
    Thread thread = new Thread(this);
    thread.start();
  }

  @Override
  public void run() {
    // Call doInBackground
    mResult = doInBackground(mArgument);

    // Notify main thread that the work is done
    mResultHandler.sendEmptyMessage(WORK_DONE);
  }

  // onPreExecute
  protected abstract void onPreExecute();

  // doInBackground
  protected abstract T2 doInBackground(T1 arg);

  // onPostExecute
  protected abstract void onPostExecute(T2 result);
}
