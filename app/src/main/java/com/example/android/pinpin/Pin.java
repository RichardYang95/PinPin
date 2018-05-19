package com.example.android.pinpin;

import com.google.android.gms.maps.model.LatLng;

public class Pin {
    LatLng coords;
    String need;

    public Pin(LatLng coords, String need) {
        this.coords = coords;
        this.need = need;
    }
}
