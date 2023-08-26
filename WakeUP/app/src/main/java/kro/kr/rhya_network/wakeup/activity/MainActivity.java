package kro.kr.rhya_network.wakeup.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.mackhartley.roundedprogressbar.RoundedProgressBar;
import com.skt.Tmap.TMapTapi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.tensorflow.lite.task.vision.classifier.Classifications;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kro.kr.rhya_network.wakeup.R;
import kro.kr.rhya_network.wakeup.core.ImageClassifierHelper;
import kro.kr.rhya_network.wakeup.core.TMapLauncher;
import kro.kr.rhya_network.wakeup.utils.CustomAdapter;
import kro.kr.rhya_network.wakeup.utils.RhyaAsyncTask;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements ImageClassifierHelper.ClassifierListener {
  private static final String TAG = "Image Classifier";

  private MediaPlayer mediaPlayer;

  public interface GPTEndListener {
    void endListener();
  }
  private final ArrayList<String> responses = new ArrayList<>();
  private final ArrayList<String> messageList = new ArrayList<>();

  private TextView isEmptyRecyclerViewTextView;
  private TextView selectModeTextView;

  // Context
  private Context context;
  private TextToSpeech tts;
  boolean isGPTMode = false;
  private boolean isCycle = false;
  private Intent intentSTT;

  // 프로그레스 바
  private RoundedProgressBar roundedProgressBar;
  private int roundedProgressBarCountChecker = 0;

  // Tenserflow Lite Model Classifier
  // ---------------------------------------------------
  private CustomAdapter customAdapter;
  private ImageClassifierHelper imageClassifierHelper;
  private Bitmap bitmapBuffer;
  private ImageAnalysis imageAnalyzer;
  private ProcessCameraProvider cameraProvider;
  private final Object task = new Object();
  private androidx.camera.view.PreviewView previewView;
  /**
   * Blocking camera operations are performed using this executor
   */
  private ExecutorService cameraExecutor;
  // ---------------------------------------------------

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    this.context = this;

    mediaPlayer = MediaPlayer.create(this, R.raw.ding_ding);

    // UI Object 설정
    previewView = findViewById(R.id.previewView);
    roundedProgressBar = findViewById(R.id.roundedProgressBar);
    selectModeTextView = findViewById(R.id.selectModeTextView);
    isEmptyRecyclerViewTextView = findViewById(R.id.isEmptyRecyclerViewTextView);

    intentSTT=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    intentSTT.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,getPackageName());
    intentSTT.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR");

    messageList.add("system@#@당신은 운전 중인 사람과 지속적으로 대화하며 그의 졸림 상태를 평가하는 시스템입니다. 사용자의 대화 내용을 기반으로 졸림의 정도를 0(전혀 졸리지 않음)부터 10(매우 졸림)까지의 숫자로 판단해주세요. 응답은 반드시 '숫자#(GPT답변)' 형식을 지켜야 합니다. 다른 형식으로 응답하지 마세요.");

    selectModeTextView.setText("졸음 운전 감지 모드");


    RecyclerView recyclerView = findViewById(R.id.recyclerView);

    //--- LayoutManager는 아래 3가지중 하나를 선택하여 사용 ---
    // 1) LinearLayoutManager()
    // 2) GridLayoutManager()
    // 3) StaggeredGridLayoutManager()
    //---------------------------------------------------------
    LinearLayoutManager linearLayoutManager = new LinearLayoutManager((Context) this);
    recyclerView.setLayoutManager(linearLayoutManager);  // LayoutManager 설정

    customAdapter = new CustomAdapter(responses);
    recyclerView.setAdapter(customAdapter); // 어댑터 설정

    // 카메라 기본 설정
    // ---------------------------------------------------
    cameraExecutor = Executors.newSingleThreadExecutor();
    imageClassifierHelper = ImageClassifierHelper.create(this, this);
    previewView.post(this::setUpCamera);
    // ---------------------------------------------------
    tts = new TextToSpeech(this, status -> {
      if(status!= TextToSpeech.ERROR) {
        tts.setLanguage(Locale.KOREAN);
      }
    });

    new Thread(() -> {
      while (true) {
        runOnUiThread(() -> {
          if (roundedProgressBarCountChecker >= 40) {
            roundedProgressBar.setBackgroundDrawableColor(getResources().getColor(R.color.red_2, context.getTheme()));
            roundedProgressBar.setProgressDrawableColor(getResources().getColor(R.color.red_1, context.getTheme()));

            if (!isGPTMode) {
              isGPTMode = true;
              // TTS 실행
              selectModeTextView.setText("대화 모드");
              tts.setPitch(1.0f);
              tts.setSpeechRate(1.0f);

              mediaPlayer.start();
              mediaPlayer.setOnCompletionListener(mp -> {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;

                Handler handler = new Handler();
                handler.postDelayed(() -> runOnUiThread(() -> {
                  SpeechRecognizer mRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
                  mRecognizer.setRecognitionListener(listener);
                  mRecognizer.startListening(intentSTT);
                }), 2000L); //딜레이 타임 조절
                isEmptyRecyclerViewTextView.setVisibility(View.GONE);
                responses.add("졸음운전이 감지되었어요! 혹시 졸리신가요?");
                customAdapter.notifyItemInserted(responses.size() - 1);
                tts.speak("졸음운전이 감지되었어요! 혹시 졸리신가요?", TextToSpeech.QUEUE_ADD, null, null);

              });
            }
          }else {
            roundedProgressBar.setBackgroundDrawableColor(getResources().getColor(R.color.blue_3, context.getTheme()));
            roundedProgressBar.setProgressDrawableColor(getResources().getColor(R.color.blue_1, context.getTheme()));
          }
        });

        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }).start();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();

    // Shut down our background executor
    cameraExecutor.shutdown();
    synchronized (task) {
      imageClassifierHelper.clearImageClassifier();
    }
  }

  public void sendChatGPTRequest(GPTEndListener listener){
    // 토큰 생성 성공!
    new RhyaAsyncTask<String, String>() {

      @Override
      protected void onPreExecute() {

      }

      @Override
      protected String doInBackground(String arg) {
        try {
          JSONObject json = new JSONObject();

          json.put("accessKey", "5fc12702bc0785ee6655d3268693cbfbbd3fe44e5d5cc484edcaffcd8fce3d32d87c2aa0489bab51ed37eff2f9159bc2f316bafd6724ef80e4e2451b51c0b642");

          JSONArray messages = new JSONArray();
          for (String msg : messageList) {
            JSONObject obj = new JSONObject();
            obj.put("role", msg.split("@#@")[0]);
            obj.put("content", msg.split("@#@")[1]);

            messages.put(obj);
          }

          json.put("messages", messages);

          OkHttpClient client = new OkHttpClient();
          RequestBody body = RequestBody.create(MediaType.get("application/json; charset=utf-8"), String.valueOf(json));
          Request request = new Request.Builder()
                  .url("https://chat-gpt-api.rhya-network.kro.kr/chat-gpt-api/request-chat-gpt-message")
                  .post(body)
                  .build();
          try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            JSONObject result = new JSONObject(response.body().string());
            Log.e("result", ((JSONObject) result.get("data")).toString());
            JSONArray array = ((JSONObject) result.get("data")).getJSONArray("choices");

            String msg = ((JSONObject) ((JSONObject) array.get(array.length() - 1)).get("message")).get("content").toString();

            if (Integer.parseInt(msg.split("#")[0].replaceAll("[^0-9]", "")) > 6) {
              return "TMAP_MODE";
            }else {
              return msg.split("#")[1];
            }
          }
        }catch (Exception ex) {
          ex.printStackTrace();
        }

        return null;
      }

      @Override
      protected void onPostExecute(String result) {
        if (result != null && result.equals("TMAP_MODE")) {
          Toast.makeText(getApplicationContext(), "TMap!!", Toast.LENGTH_SHORT).show();

          tts.setPitch(1.0f);
          tts.setSpeechRate(1.0f);
          tts.speak("TMap 으로 가까운 휴개소 안내를 시작 할께요.", TextToSpeech.QUEUE_FLUSH, null, null);

          TMapLauncher tMapLauncher = new TMapLauncher(context);
          tMapLauncher.auth(new TMapTapi.OnAuthenticationListenerCallback() {
            @Override
            public void SKTMapApikeySucceed() {
              tMapLauncher.getAroundLocation(arrayList -> {
                if (arrayList.size() > 0) {
                  tMapLauncher.startTMap(
                          String.valueOf(arrayList.get(0).getPOIPoint().getLongitude()),
                          String.valueOf(arrayList.get(0).getPOIPoint().getLatitude()),
                          arrayList.get(0).getPOIName()
                  );
                  arrayList.get(0).getPOIPoint().getLatitude();
                }

                runOnUiThread(MainActivity.this::finish);
              });
            }

            @Override
            public void SKTMapApikeyFailed(String s) {
              Toast.makeText(getApplicationContext(), "TMap 인증 실패!", Toast.LENGTH_SHORT).show();
            }
          });
          return;
        }

        responses.add(result);
        customAdapter.notifyItemInserted(responses.size() - 1);

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
          @Override
          public void run() {
            runOnUiThread(() -> {
              if (result != null)
                messageList.add("user@#@" + result);

              listener.endListener();
            });
          }
        }, 130L * (result == null ? 1000 : result.length())); //딜레이 타임 조절

        tts.setPitch(1.0f);
        tts.setSpeechRate(1.0f);
        tts.speak(result == null ? "알 수 없는 오류입니다.": result, TextToSpeech.QUEUE_FLUSH, null, null);
      }
    }.execute("");
  }

  private void setUpCamera() {
    ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
            ProcessCameraProvider.getInstance(this);
    cameraProviderFuture.addListener(() -> {
      try {
        cameraProvider = cameraProviderFuture.get();

        // Build and bind the camera use cases
        bindCameraUseCases();
      } catch (ExecutionException | InterruptedException e) {
        e.printStackTrace();
      }
    }, ContextCompat.getMainExecutor(this));
  }

  private void bindCameraUseCases() {
    CameraSelector.Builder cameraSelectorBuilder = new CameraSelector.Builder();
    CameraSelector cameraSelector = cameraSelectorBuilder
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT).build();
    Preview preview = new Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(
                    previewView
                            .getDisplay().getRotation()
            )
            .build();

    imageAnalyzer = new ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(previewView.getDisplay().getRotation())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build();

    imageAnalyzer.setAnalyzer(cameraExecutor, image -> {
      if (bitmapBuffer == null) {
        bitmapBuffer = Bitmap.createBitmap(
                image.getWidth(),
                image.getHeight(),
                Bitmap.Config.ARGB_8888);
      }
      classifyImage(image);
    });

    cameraProvider.unbindAll();

    try {
      cameraProvider.bindToLifecycle(
              this,
              cameraSelector,
              preview,
              imageAnalyzer
      );

      preview.setSurfaceProvider(
              previewView.getSurfaceProvider()
      );
    } catch (Exception exc) {
      Log.e(TAG, "Use case binding failed", exc);
    }
  }

  private void classifyImage(@NonNull ImageProxy image) {
    // Copy out RGB bits to the shared bitmap buffer
    bitmapBuffer.copyPixelsFromBuffer(image.getPlanes()[0].getBuffer());

    int imageRotation = image.getImageInfo().getRotationDegrees();
    image.close();
    synchronized (task) {
      // Pass Bitmap and rotation to the image classifier helper for
      // processing and classification
      imageClassifierHelper.classify(bitmapBuffer, imageRotation);
    }
  }

  @Override
  public void onError(String error) {
    Log.e(TAG, "Image classification error: " + error);
  }

  @Override
  public void onResults(List<Classifications> results, long inferenceTime) {
    for (Classifications item : results) {
      // 1번째 카테고리가 1이면 깨어있음, 0이면 잠자는 중
      final boolean isNoSleep = item
              .getCategories()
              .get(0)
              .getLabel()
              .equals("1");
      // 0 ~ 100 사이의 값
      final int percentage = isNoSleep
              ? (int) (item.getHeadIndex() * 100)
              : 100 - (int) (item.getHeadIndex() * 100);

      if (!isNoSleep && percentage > 75) {
        roundedProgressBarCountChecker = roundedProgressBarCountChecker + 1;
      }else {
        roundedProgressBarCountChecker = 0;
      }

      runOnUiThread(() -> {
        roundedProgressBar.setAnimationLength(300);
        roundedProgressBar.setProgressPercentage(percentage, true);
      });
    }
  }

  private RecognitionListener listener = new RecognitionListener() {
    @Override
    public void onReadyForSpeech(Bundle params) {
      Toast.makeText(getApplicationContext(), "음성인식을 시작합니다.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBeginningOfSpeech() {}

    @Override
    public void onRmsChanged(float rmsdB) {}

    @Override
    public void onBufferReceived(byte[] buffer) {}

    @Override
    public void onEndOfSpeech() {}

    @Override
    public void onError(int error) {
      String message;

      switch (error) {
        case SpeechRecognizer.ERROR_AUDIO:
          message = "오디오 에러";
          break;
        case SpeechRecognizer.ERROR_CLIENT:
          message = "클라이언트 에러";
          break;
        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
          message = "퍼미션 없음";
          break;
        case SpeechRecognizer.ERROR_NETWORK:
          message = "네트워크 에러";
          break;
        case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
          message = "네트웍 타임아웃";
          break;
        case SpeechRecognizer.ERROR_NO_MATCH:
          message = "찾을 수 없음";
          break;
        case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
          message = "RECOGNIZER가 바쁨";
          break;
        case SpeechRecognizer.ERROR_SERVER:
          message = "서버가 이상함";
          break;
        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
          message = "말하는 시간초과";
          break;
        default:
          message = "알 수 없는 오류임";
          break;
      }

      SpeechRecognizer mRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
      mRecognizer.setRecognitionListener(listener);
      mRecognizer.startListening(intentSTT);

      Toast.makeText(getApplicationContext(), "에러가 발생하였습니다. : " + message,Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResults(Bundle results) {
      // 말을 하면 ArrayList에 단어를 넣고 textView에 단어를 이어준다.
      ArrayList<String> matches =
              results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

      StringBuilder builder = new StringBuilder();
      for(int i = 0; i < matches.size() ; i++){
        builder.append(matches.get(i));
      }

      String result = builder.toString();
      messageList.add("user@#@운전자: 사용자의 대화 내용을 분석하여 졸린 정도를 판단합니다. 반드시 졸린 정도를 0부터 10까지의 숫자로 표현합니다. 출력 형식은 졸린정도#대화내용의 형태로 합니다."+ result);

      sendChatGPTRequest(() -> {
        Toast.makeText(getApplicationContext(), "음성인식을 시작합니다.", Toast.LENGTH_SHORT).show();
        SpeechRecognizer mRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        mRecognizer.setRecognitionListener(listener);
        mRecognizer.startListening(intentSTT);
      });
    }

    @Override
    public void onPartialResults(Bundle partialResults) {}

    @Override
    public void onEvent(int eventType, Bundle params) {}
  };
}