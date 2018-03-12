package com.example.android.pinpin;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import android.content.Context;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private GoogleMap mMap;
    private GoogleApiClient client;
    public static final int REQUEST_LOCATION_CODE = 99;


    // For alert dialog
    final Context context = this;

    // Reads in the coordinates from the database and adds/removes pins from the map
    final Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            System.out.println("NEW CYCLE");

            // Read and Send new coords in new thread
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Read in coordinates from the HTML Server
                        URLConnection c = new URL("http://129.65.221.101/php/getPinPinGPSdata.php").openConnection();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream()));
                        final Set<LatLng> dbCoords = new HashSet<>();

                        // Read in each coordinate from database
                        for (String line; (line = reader.readLine()) != null;) {
                            // Separate the given line by whitespace
                            String[] coords = line.split("\\s");

                            try {

                                Double lat = Double.parseDouble(coords[0]);
                                Double lng = Double.parseDouble(coords[1]);
                                LatLng l = new LatLng(lat, lng);


                            // TODO: Only add coords within x miles of user
                            dbCoords.add(l);

                            } catch (NumberFormatException e) {
                                System.out.println("The coord in the database is not formatted correctly: " + line);
                            }
                        }


                        // Have to add and remove markers from map on main thread
                        Handler mainHandler = new Handler(Looper.getMainLooper());
                        Runnable myRunnable = new Runnable() {
                            @Override
                            public void run() {
                                // Clear all markers on the map first
                                mMap.clear();

                                addMarkers(dbCoords);
                            }
                        };
                        mainHandler.post(myRunnable);
                    } catch (Exception e) {
                        System.out.println("THERE WAS AN ERROR");
                        e.printStackTrace();
                    }
                }
            });

            thread.start();
            timerHandler.postDelayed(this, 15000); // Update every 15 seconds
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Read in coordinates from the database
        timerHandler.postDelayed(timerRunnable, 0);
    }

    // Adds all the markers from the database onto the map
    private void addMarkers(Set<LatLng> newDBCoords) {
        for (LatLng l : newDBCoords) {
            mMap.addMarker(new MarkerOptions().position(l));
        }
    }

    // For handling permission request response
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch(requestCode) {
            case REQUEST_LOCATION_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Permission is granted
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        if (client == null) {
                            buildGoogleApiClient();
                        }

                        mMap.setMyLocationEnabled(true);
                    }
                    //Permission denied
                    else {
                        Toast.makeText(this, "Permission Denied!", Toast.LENGTH_LONG).show();
                    }
                }
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            buildGoogleApiClient();
            mMap.setMyLocationEnabled(true);
        }

        // Adds a marker on tap
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng pin) {
                // Add the marker to the map
                mMap.addMarker(new MarkerOptions().position(pin));
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pin, 18));

                // Add the lat and lng to database
                final String entry = "http://129.65.221.101/php/sendPinPinGPSdata.php?gps=" + pin.latitude + " " + pin.longitude;
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            URL send = new URL(entry);
                            URLConnection connection = send.openConnection();
                            InputStream in = connection.getInputStream();
                            in.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

                thread.start();

//                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Coordinates/");
//
//                // Make a unique id based on lat and lng
//                String hash = String.valueOf(pin.latitude) + String.valueOf(pin.longitude);
//                String id = String.valueOf(hash.hashCode());
//
//                // Write lat, long, and current time to database
//                ref.child(id).child("Lat").setValue(pin.latitude);
//                ref.child(id).child("Lng").setValue(pin.longitude);
//                ref.child(id).child("Time").setValue(Calendar.getInstance().getTimeInMillis());
            }
        });

        // Delete a marker on tap
        // TODO: Might have to also add users unique id to firebase so user cant delete other users pins
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                final Marker m = marker;

                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                alertDialogBuilder.setTitle("Confirm Delete?");
                alertDialogBuilder
                    .setMessage("Delete this Pin?")
                    .setCancelable(true)
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int i) {
                            // Delete the lat & long entry from database
//                            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Coordinates/");
//
//                            // Get the unique id based on lat and lng
//                            double lat = m.getPosition().latitude;
//                            double lng = m.getPosition().longitude;
//                            String hash = String.valueOf(lat) + String.valueOf(lng);
//                            String id = String.valueOf(hash.hashCode());
//
//                            // Remove the entry from the database
//                            ref.child(id).removeValue();

                            // Remove marker from map
                            m.remove();
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int i) {
                            dialog.cancel();
                        }
                    });

                // Show alert dialog
                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();

                return false;
            }
        });
    }

    protected synchronized void buildGoogleApiClient() {
        client = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        client.connect();
    }

    // When search button is clicked
    public void onClick(View v) {
        if (v.getId() == R.id.B_search) {
            EditText tf_location = findViewById(R.id.TF_location);
            String location = tf_location.getText().toString();
            List<Address> addressList = null;
            MarkerOptions mo = new MarkerOptions();

            if (! location.equals("")) {
                Geocoder geo = new Geocoder(this);

                try {
                    addressList = geo.getFromLocationName(location, 5);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //Put marker on addresses
                for (int i = 0; i < addressList.size(); i++) {
                    Address myAddress = addressList.get(i);
                    LatLng latLng = new LatLng(myAddress.getLatitude(), myAddress.getLongitude());
                    mo.position(latLng);
                    mo.title("TEST");
                    mMap.addMarker(mo);
                }
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        // Move map to current location
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17));

        // Stop location updates after setting. Prob comment out after
        if (client != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(client, this);
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        LocationRequest locationRequest = new LocationRequest();

        locationRequest.setInterval(1000); //1000 milliseconds
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(client, locationRequest, this);
        }
    }

    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Check if user has given permission previously and denied request
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_CODE);
            }
            // Ask user for permission
            else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_CODE);
            }
            return false;
        }
        else {
            return true;
        }
    }


    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
