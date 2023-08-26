package kro.kr.rhya_network.wakeup.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.normal.TedPermission;

import java.util.List;

import kro.kr.rhya_network.wakeup.R;

public class SplashActivity extends AppCompatActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_splash);

    checkPermission();
  }

  protected void permissionNextTask() {
    ImageView appLogoImageView = findViewById(R.id.appLogoImageView);

    // 애니메이션 실행
    appLogoImageView.animate()
            .setStartDelay(500)
            .alpha(1f)
            .translationY(-50f)
            .setDuration(1000)
            .withEndAction(() -> {
              // 메인 페이지 전환
              Intent intent = new Intent(getApplicationContext(), MainActivity.class);
              startActivity(intent);

              overridePendingTransition(R.anim.slide_right_enter, R.anim.scale_small);
              finish();
            })
            .start();
  }

  /**
   * 안드로이드 권한 부여 확인
   */
  private void checkPermission() {
    TedPermission
            .create()
            .setGotoSettingButton(false)
            .setPermissionListener(permissionListener)
            .setPermissions(
                    android.Manifest.permission.CAMERA,
                    android.Manifest.permission.RECORD_AUDIO,
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION)
            .check();
  }

  // Permission 요청 결과 리스너
  private final PermissionListener permissionListener = new PermissionListener() {
    @Override
    public void onPermissionGranted() {
      // 권한 부여됨
      permissionNextTask();
    }

    @Override
    public void onPermissionDenied(List<String> deniedPermissions) {
      // 권한 거부됨 - 오류 메시지 출력 및 종료
      Toast.makeText(getApplicationContext(), "권한 허용을 하지 않으면 서비스를 이용할 수 없습니다.", Toast.LENGTH_SHORT).show();
      finish();
    }
  };
}