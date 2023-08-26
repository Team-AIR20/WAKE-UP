package kro.kr.rhya_network.wakeup.core;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.skt.Tmap.TMapData;
import com.skt.Tmap.TMapPoint;
import com.skt.Tmap.TMapTapi;
import com.skt.Tmap.poi_item.TMapPOIItem;

import java.util.ArrayList;

public class TMapLauncher {
  private final Context context;

  public TMapLauncher(Context context) {
    this.context = context;
  }

  public void auth(TMapTapi.OnAuthenticationListenerCallback callback) {
    TMapTapi tmaptapi = new TMapTapi(context);
    tmaptapi.setSKTMapAuthentication("EG7syRzr7q1Ongi9lZDZja7Oxrbg9gzK77mnHpg3");
    tmaptapi.setOnAuthenticationListener(callback);
  }

  public void getAroundLocation(TMapData.FindAroundKeywordPOIListenerCallback callback) {
    TMapData tmapdata = new TMapData();

    LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    if (ActivityCompat.checkSelfPermission(context,
            Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      return;
    }
    Location loc_Current = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

    assert loc_Current != null;

    tmapdata.findAroundKeywordPOI(
            new TMapPoint(
                    loc_Current.getLatitude(),
                    loc_Current.getLongitude()
            ),
            "휴개소",
            100,
            1,
            callback
    );
  }

  public void startTMap(String goalx, String goaly, String goalName) {
    Intent intent = new Intent(
            Intent.ACTION_VIEW,
            Uri.parse(
                    String.format("tmap://route?goalx=%s&goaly=%s&goalname=%s", goalx, goaly, goalName)
            )
    );
    intent.setPackage("com.skt.tmap.ku");
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    context.startActivity(intent);
  }
}
