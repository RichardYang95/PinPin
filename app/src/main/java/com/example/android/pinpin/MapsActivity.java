package com.example.android.pinpin;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Set;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener{

    private GoogleMap mMap;
    private GoogleApiClient client;
    private LatLng currLoc;
    private static final int REQUEST_LOCATION_CODE = 99;
    final Set<Pin> dbCoords = new HashSet<>();

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

//                        final Set<Pin> dbCoords = new HashSet<>();

                        // Read in each coordinate from database
                        for (String line; (line = reader.readLine()) != null;) {
                            // Separate the given line by whitespace
                            String[] coords = line.split("\\s");

                            try {
                                Double lat = Double.parseDouble(coords[0]);
                                Double lng = Double.parseDouble(coords[1]);
                                LatLng l = new LatLng(lat, lng);

                                Pin p = new Pin(l, coords[2]);

                                dbCoords.add(p);
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
            timerHandler.postDelayed(this, 5000); // Update every 5 seconds
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

        // Google Places
        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLng(), 17));
            }

            @Override
            public void onError(Status status) {
                System.out.println("GOOGLE PLACES ERROR");
            }
        });


        // Read in coordinates from the database
        timerHandler.postDelayed(timerRunnable, 0);
    }

    // Adds all the markers from the database onto the map
    private void addMarkers(Set<Pin> newDBCoords) {
        for (Pin p : newDBCoords) {
            if (currLoc != null) {
                // Only show Pins within 20 miles of user.
                if (20 >= getDistance(currLoc.latitude, currLoc.longitude, p.coords.latitude, p.coords.longitude)) {
                    MarkerOptions mo = new MarkerOptions();
                    mo.position(p.coords);

                    switch (p.need) {
                        case "Food":
                            mo.icon(BitmapDescriptorFactory.fromResource(R.drawable.foodpin));
                            mo.title("Food");
                            break;
                        case "Money":
                            mo.icon(BitmapDescriptorFactory.fromResource(R.drawable.moneypin));
                            mo.title("Money");
                            break;
                        case "FirstAid":
                            mo.icon(BitmapDescriptorFactory.fromResource(R.drawable.firstaidpin));
                            mo.title("FirstAid");
                            break;
                        case "Ride":
                            mo.icon(BitmapDescriptorFactory.fromResource(R.drawable.ridepin));
                            mo.title("Ride");
                            break;
                    }
                    mMap.addMarker(mo);
                }
            }
        }
    }

    private double degreesToRadians(double degrees) {
        return degrees * Math.PI / 180;
    }

    // Gets distance between 2 coords in km
    private double getDistance(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371;

        double dLat = degreesToRadians(lat2 - lat1);
        double dLon = degreesToRadians(lon2 - lon1);

        lat1 = degreesToRadians(lat1);
        lat2 = degreesToRadians(lat2);

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.sin(dLon/2) * Math.sin(dLon/2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return earthRadiusKm * c;
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
    public void onMapReady(final GoogleMap googleMap) {
        mMap = googleMap;
        final String need[] = {"Food"};

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            buildGoogleApiClient();
            mMap.setMyLocationEnabled(true);
        }

        // Adds a marker on tap
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(final LatLng pin) {
                final MarkerOptions mo = new MarkerOptions();
                mo.position(pin);
                String needsArr[] = {"\uD83C\uDF57     Food",
                        "\uD83D\uDCB5     Money",
                        "\uD83D\uDE91     First Aid",
                        "\uD83D\uDE95     Ride"};

                AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
                builder.setTitle("Pick a Need");
                builder.setItems(needsArr, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog,  int which) {
                       switch(which) {
                           case 0:
                               need[0] = "Food";
                               mo.icon(BitmapDescriptorFactory.fromResource(R.drawable.foodpin));
                               mo.title("Food");
                               break;
                           case 1:
                               need[0] = "Money";
                               mo.icon(BitmapDescriptorFactory.fromResource(R.drawable.moneypin));
                               mo.title("Money");
                               break;
                           case 2:
                               need[0] = "FirstAid";
                               mo.icon(BitmapDescriptorFactory.fromResource(R.drawable.firstaidpin));
                               mo.title("FirstAid");
                               break;
                           case 3:
                               need[0] = "Ride";
                               mo.icon(BitmapDescriptorFactory.fromResource(R.drawable.ridepin));
                               mo.title("Ride");
                               break;
                       }

                       mMap.addMarker(mo);
                       mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pin, 17));

                       // Add the lat and lng to database
                       final String entry = "http://129.65.221.101/php/sendPinPinGPSdata.php?gps=" + pin.latitude + " " + pin.longitude + " " + need[0];
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
                   }
                });

                AlertDialog alertDialog = builder.create();
                alertDialog.show();

            }
        });

        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(final LatLng latLng) {
                System.out.println("IN LONG CLICK");
                AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
                builder.setTitle("Do you want to flag this Pin?");
                builder.setMessage("Flag this Pin if the person is not at the location anymore.");
                builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // Can't get the marker's exact coords, so have to find the one nearest to the tap.
                        Pin temp = null;
                        double shortestDist = Double.POSITIVE_INFINITY;
                        for (Pin pin : dbCoords) {
                            double distance = getDistance(pin.coords.latitude, pin.coords.longitude, latLng.latitude, latLng.longitude);
                            if (distance < shortestDist) {
 ;                              shortestDist = distance;
                                temp = pin;
                            }
                        }
                        final Pin p = temp;

                        final String delete = "http://129.65.221.101/php/deleteFlaggedEntry.php?Delete=" + p.coords.latitude + " " + p.coords.longitude;
                        Thread thread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    System.out.println("IN SEND: " + p.coords.latitude + " " + p.coords.longitude + " " + p.need);
                                    URL send = new URL(delete);
                                    URLConnection connection = send.openConnection();
                                    InputStream in = connection.getInputStream();
                                    in.close();
                                    System.out.println("IN SEND 22");

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });

                        thread.start();

                    }
                });
                builder.setNegativeButton("No", null);

                AlertDialog alertDialog = builder.create();
                alertDialog.show();
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

    @Override
    public void onLocationChanged(Location location) {
        currLoc = new LatLng(location.getLatitude(), location.getLongitude());

        // Move map to current location
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currLoc, 17));

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

    public void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Check if user has given permission previously and denied request
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_CODE);
            }
            // Ask user for permission
            else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_CODE);
            }
        }
    }


    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
