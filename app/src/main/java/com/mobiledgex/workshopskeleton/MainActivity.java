/**
 * Copyright 2019 MobiledgeX, Inc. All rights and licenses reserved.
 * MobiledgeX, Inc. 156 2nd Street #408, San Francisco, CA 94105
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mobiledgex.workshopskeleton;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.NetworkOnMainThreadException;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.maps.android.SphericalUtil;

// Matching Engine API:
import com.mobiledgex.matchingengine.MatchingEngine;
import com.mobiledgex.matchingengine.ChannelIterator;
import com.mobiledgex.matchingengine.DmeDnsException;
import com.mobiledgex.matchingengine.util.RequestPermissions;

import java.io.IOException;
import java.net.HttpRetryException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import distributed_match_engine.AppClient;
import distributed_match_engine.Appcommon;
import distributed_match_engine.LocOuterClass;

// Matching Engine API:


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "MainActivity";
    private static final int RC_SIGN_IN = 1;
    public static final int RC_STATS = 2;
    private MatchingEngine matchingEngine;
    private RequestPermissions mRpUtil;
    private String statusText = null;
    private AppClient.FindCloudletReply mClosestCloudlet;
    private Activity ctx;
    private String host;
    private int port;
    private String carrierName;
    private String appName;
    private String devName;
    private String appVersion;

    private TextView cloudletNameTv;
    private TextView appNameTv;
    private TextView fqdnTv;
    private TextView portNumberTv;
    private TextView carrierNameTv;
    private TextView distanceTv;
    private TextView latitudeTv;
    private TextView longitudeTv;
    private CheckBox checkboxRegistered;
    private CheckBox checkboxCloudletFound;
    private CheckBox checkboxLocationVerified;
    private ProgressBar progressBar;
    private String mClosestCloudletHostname;

    private GoogleSignInClient mGoogleSignInClient;
    private MenuItem signInMenuItem;
    private MenuItem signOutMenuItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ctx = this;

        setContentView(R.layout.activity_main);
        cloudletNameTv = findViewById(R.id.cloudletName);
        fqdnTv = findViewById(R.id.fqdn);
        portNumberTv = findViewById(R.id.portNumber);
        appNameTv = findViewById(R.id.appName);
        carrierNameTv = findViewById(R.id.carrierName);
        distanceTv = findViewById(R.id.distance);
        latitudeTv = findViewById(R.id.latitude);
        longitudeTv = findViewById(R.id.longitude);
        checkboxRegistered = findViewById(R.id.checkBoxRegistered);
        checkboxCloudletFound = findViewById(R.id.checkBoxCloudletFound);
        checkboxLocationVerified = findViewById(R.id.checkBoxLocationVerified);
        progressBar = findViewById(R.id.progressBar);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                registerClientAndFindCloudletInBackground();
            }
        });
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();

        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Check for existing Google Sign In account, if the user is already signed in
        // the GoogleSignInAccount will be non-null.
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);

        signInMenuItem = navigationView.getMenu().findItem(R.id.nav_google_signin);
        signOutMenuItem = navigationView.getMenu().findItem(R.id.nav_google_signout);

        if(account != null) {
            //This means we're already signed in.
            signInMenuItem.setVisible(false);
            signOutMenuItem.setVisible(true);
        } else {
            signInMenuItem.setVisible(true);
            signOutMenuItem.setVisible(false);
        }

        /**
         * MatchingEngine APIs require special user approved permissions to READ_PHONE_STATE and
         * one of the following:
         * ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION.
         *
         * The RequestPermissions utility creates a UI dialog, if needed.
         *
         * You can do this anywhere, MainApplication.onActivityResumed(), or a subset of permissions
         * onResume() on each Activity.
         *
         * Permissions must exist prior to API usage to avoid SecurityExceptions.
         */
        mRpUtil = new RequestPermissions();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_reset) {
            mClosestCloudlet = null;
            mClosestCloudletHostname = null;
            carrierNameTv.setText("none");
            appNameTv.setText("none");
            latitudeTv.setText("none");
            longitudeTv.setText("none");
            fqdnTv.setText("none");
            portNumberTv.setText("none");
            distanceTv.setText("none");
            cloudletNameTv.setText("none");
            checkboxRegistered.setChecked(false);
            checkboxRegistered.setText(R.string.client_registered_question);
            checkboxCloudletFound.setChecked(false);
            checkboxCloudletFound.setText(R.string.cloudlet_found_question);
            checkboxLocationVerified.setChecked(false);
            checkboxLocationVerified.setText(R.string.location_verified_question);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_face_detection) {
            // Handle the camera action
            Intent intent = new Intent(this, FaceProcessorActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_google_signin) {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        } else if (id == R.id.nav_google_signout) {
            mGoogleSignInClient.signOut()
                    .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            Toast.makeText(MainActivity.this, "Sign out successful.", Toast.LENGTH_LONG).show();
                            signInMenuItem.setVisible(true);
                            signOutMenuItem.setVisible(false);
                        }
                    });
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private boolean registerClient() throws ExecutionException, InterruptedException, io.grpc.StatusRuntimeException, DmeDnsException {
        // NOTICE: In a real app, these values would be determined by the SDK, but we are reusing
        // an existing app so we don't have to create new app provisioning data for this workshop.
        appName = "Face Detection Demo";
        devName = "HackathonTeam-6";
        carrierName = "Rogers";
        appVersion = "1.0";

        //NOTICE: A real app would request permission to enable this.
        MatchingEngine.setMatchingEngineLocationAllowed(true);

        /////////////////////////////////////////////////////////////////////////////////////
        // TODO: Copy/paste the code to register the client. Replace all "= null" lines here.
       // host = matchingEngine.generateDmeHostAddress();
        host = "rogers.dme.mobiledgex.net";
        if(host == null) {
            Log.e(TAG, "Could not generate host");
            host = "sdkdemo.dme.mobiledgex.net";   //fallback host
        }
        port = matchingEngine.getPort(); // Keep same port.
        AppClient.RegisterClientRequest registerClientRequest = matchingEngine.createRegisterClientRequest(ctx,
                devName, appName, appVersion, carrierName, null);
        AppClient.RegisterClientReply registerStatus = matchingEngine.registerClient (registerClientRequest, host,
                port, 10000);
        /////////////////////////////////////////////////////////////////////////////////////

        if(matchingEngine == null) {
            statusText = "matchingEngine uninitialized";
            Log.e(TAG, statusText);
            showErrorMsg(statusText);
            return false;
        }

        Log.i(TAG, "registerClientRequest="+registerClientRequest);
        Log.i(TAG, "registerStatus.getStatus()="+registerStatus.getStatus());

        if (registerStatus.getStatus() != AppClient.ReplyStatus.RS_SUCCESS) {
            statusText = "Registration Failed. Error: " + registerStatus.getStatus();
            Log.e(TAG, statusText);
            showErrorMsg(statusText);
            return false;
        }
        Log.i(TAG, "SessionCookie:" + registerStatus.getSessionCookie());
        return true;
    }

    public boolean findCloudlet() throws ExecutionException, InterruptedException {
        //(Blocking call, or use findCloudletFuture):
        Location location = new Location("MEX");
        ////////////////////////////////////////////////////////////
        // TODO: Change these coordinates to where you're actually located.
        // Of course a real app would use GPS to acquire the exact location.
        //location.setLatitude(52.52);
        //location.setLongitude(13.4040);    //Berlin
        location.setLatitude(49.26);
        location.setLongitude(123.24);

        ////////////////////////////////////////////////////////////////////////////////////////////
        // TODO: Copy/paste the code to find the cloudlet closest to you. Replace "= null" here.
        AppClient.FindCloudletRequest findCloudletRequest= matchingEngine.createFindCloudletRequest (ctx,
                carrierName, location);
        mClosestCloudlet = matchingEngine.findCloudlet(findCloudletRequest, host, port, 10000);
        ////////////////////////////////////////////////////////////////////////////////////////////

        Log.i(TAG, "mClosestCloudlet="+mClosestCloudlet);
        if(mClosestCloudlet == null) {
            statusText = "findCloudlet call is not successfully coded. Search for TODO in code.";
            Log.e(TAG, statusText);
            showErrorMsg(statusText);
            return false;
        }
        if(mClosestCloudlet.getStatus() != AppClient.FindCloudletReply.FindStatus.FIND_FOUND) {
            statusText = "findCloudlet Failed. Error: " + mClosestCloudlet.getStatus();
            Log.e(TAG, statusText);
            showErrorMsg(statusText);
            return false;
        }
        Log.i(TAG, "REQ_FIND_CLOUDLET mClosestCloudlet.uri=" + mClosestCloudlet.getFqdn());
        checkboxCloudletFound.setChecked(true);
        checkboxCloudletFound.setText(R.string.cloudlet_found);

        // Populate cloudlet details.
        latitudeTv.setText(""+mClosestCloudlet.getCloudletLocation().getLatitude());
        longitudeTv.setText(""+mClosestCloudlet.getCloudletLocation().getLongitude());
        fqdnTv.setText(mClosestCloudlet.getFqdn());
        LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        LatLng cloudletLatLng = new LatLng(mClosestCloudlet.getCloudletLocation().getLatitude(),
                mClosestCloudlet.getCloudletLocation().getLongitude());
        double distance = SphericalUtil.computeDistanceBetween(userLatLng, cloudletLatLng)/1000;
        distanceTv.setText(String.format("%.2f", distance)+" km");

        //Extract cloudlet name from FQDN
        String[] parts = mClosestCloudlet.getFqdn().split("\\.");
        cloudletNameTv.setText(parts[0]);

        //Find FqdnPrefix from Port structure.
        String FqdnPrefix = "";
        List<distributed_match_engine.Appcommon.AppPort> ports = mClosestCloudlet.getPortsList();
        String appPortFormat = "{Protocol: %d, FqdnPrefix: %s, Container Port: %d, External Port: %d, Public Path: '%s'}";
        for (Appcommon.AppPort aPort : ports) {
            FqdnPrefix = aPort.getFqdnPrefix();
            Log.i(TAG, String.format(Locale.getDefault(), appPortFormat,
                    aPort.getProto().getNumber(),
                    aPort.getFqdnPrefix(),
                    aPort.getInternalPort(),
                    aPort.getPublicPort(),
                    aPort.getPathPrefix()));
        }
        // Build full hostname.
        mClosestCloudletHostname = FqdnPrefix+mClosestCloudlet.getFqdn();

        // TODO: Copy/paste the output of this log into a terminal to test latency.
        Log.i("COPY_PASTE", "ping -c 4 "+mClosestCloudletHostname);

        verifyLocationInBackground(location);

        getQoSPositionKpiInBackground(location);

        return true;
    }

    private boolean verifyLocation(Location loc) throws InterruptedException, IOException, ExecutionException {
        AppClient.VerifyLocationRequest verifyLocationRequest = matchingEngine.createVerifyLocationRequest(ctx, carrierName, loc);
        if (verifyLocationRequest != null) {
            try {
                AppClient.VerifyLocationReply verifyLocationReply = matchingEngine.verifyLocation(verifyLocationRequest, host, port, 10000);
                Log.i(TAG, "[Location Verified: Tower: " + verifyLocationReply.getTowerStatus() +
                        ", GPS LocationStatus: " + verifyLocationReply.getGpsLocationStatus() +
                        ", Location Accuracy: " + verifyLocationReply.getGpsLocationAccuracyKm() + " ]\n");
                return true;
            } catch(NetworkOnMainThreadException ne) {
                Log.e(TAG, "Network thread exception");
                return false;
            }
        } else {
            Log.e(TAG, "Cannot verify location");
            return false;
        }
    }

    private boolean getQoSPositionKpi(LocOuterClass.Loc loc) throws InterruptedException, ExecutionException{
        List<AppClient.QosPosition> requests = createPositionList(loc, 45, 200, 1);
        if (requests.isEmpty()) {
            Log.e(TAG, "No items added to the position list");
            return false;
        }
        AppClient.QosPositionRequest qosPositionRequest = matchingEngine.createQoSPositionRequest(requests, 0, null);

        if(qosPositionRequest != null) {
            try {
                ChannelIterator<AppClient.QosPositionKpiReply> qosPositionKpiReplies = matchingEngine.getQosPositionKpi(qosPositionRequest, host, port, 20000);
                if (!qosPositionKpiReplies.hasNext()) {
                    Log.e(TAG, "Replies is empty");
                    return false;
                }
                while (qosPositionKpiReplies.hasNext()) {
                    Log.i(TAG, "Position results are " + qosPositionKpiReplies.next().getPositionResultsList());
                }
                return true;
            } catch (NetworkOnMainThreadException ne) {
                Log.e(TAG, "Network thread exception");
                return false;
            }
        } else {
            Log.e(TAG, "QoS request is null");
            return false;
        }
    }

    private void registerClientAndFindCloudletInBackground() {
        // Creates new BackgroundRequest object which will call registerClient (the findCloudlet if registerClient is successful) to run on background thread
        new RegisterClientAndFindCloudletBackgroundRequest().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void verifyLocationInBackground(Location loc) {
        // Creates new BackgroundRequest object which will call verifyLocation to run on background thread
        new VerifyLocBackgroundRequest().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, loc);
    }

    private void getQoSPositionKpiInBackground(Location loc) {
        // Creates new BackgroundRequest object which will call getQoSPositionKpi to run on background thread
        new QoSPosBackgroundRequest().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, loc);
    }

    private List<AppClient.QosPosition> createPositionList(LocOuterClass.Loc loc, double direction_degrees, double totalDistanceKm, double increment) {
        List<AppClient.QosPosition> positions = new ArrayList<>();
        long positionId = 1;

        AppClient.QosPosition firstPosition = AppClient.QosPosition.newBuilder()
                .setPositionid(positionId)
                .setGpsLocation(loc)
                .build();
        positions.add(firstPosition);

        LocOuterClass.Loc lastLocation = LocOuterClass.Loc.newBuilder()
                .setLongitude(loc.getLongitude())
                .setLatitude(loc.getLatitude())
                .build();

        double kmPerDegreeLat = 110.57; //at Equator
        double kmPerDegreeLong = 111.32; //at Equator
        double addLatitude = (Math.sin(direction_degrees * (Math.PI/180)) * increment)/kmPerDegreeLat;
        double addLongitude = (Math.cos(direction_degrees * (Math.PI/180)) * increment)/kmPerDegreeLong;
        for (double traverse = 0; traverse + increment < totalDistanceKm; traverse += increment) {
            LocOuterClass.Loc next = LocOuterClass.Loc.newBuilder()
                    .setLongitude(lastLocation.getLongitude() + addLongitude)
                    .setLatitude(lastLocation.getLatitude() + addLatitude)
                    .build();
            AppClient.QosPosition nextPosition = AppClient.QosPosition.newBuilder()
                    .setPositionid(++positionId)
                    .setGpsLocation(next)
                    .build();

            positions.add(nextPosition);
            Log.i(TAG, "Latitude is " + nextPosition.getGpsLocation().getLatitude() + " and Longitude is " + nextPosition.getGpsLocation().getLongitude());
            lastLocation = next;
        }
        return positions;
    }

    public void showErrorMsg(String msg) {
        Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "onActivityResult requestCode="+requestCode+" resultCode="+resultCode+" data="+data);
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            // The Task returned from this call is always completed, no need to attach a listener.
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        } else if (requestCode == RC_STATS && resultCode == RESULT_OK) {
            //Get preference
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean showDialog = prefs.getBoolean("fd_show_latency_stats_dialog", false);
            if(!showDialog) {
                Log.d(TAG, "Preference is to not show latency stats dialog");
                return;
            }

            String stats = data.getExtras().getString("STATS");
            // The TextView to show your Text
            TextView showText = new TextView(MainActivity.this);
            showText.setText(stats);
            showText.setTextIsSelectable(true);
            int horzPadding = (int) (25 * getResources().getDisplayMetrics().density);
            showText.setPadding(horzPadding, 0,horzPadding,0);
            new AlertDialog.Builder(MainActivity.this)
                    .setView(showText)
                    .setTitle("Stats")
                    .setCancelable(true)
                    .setPositiveButton("OK", null)
                    .show();
        }

    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            // Signed in successfully, show authenticated UI.
            signInMenuItem.setVisible(false);
            signOutMenuItem.setVisible(true);
            Toast.makeText(MainActivity.this, "Sign in successful. Welcome, "+account.getDisplayName(), Toast.LENGTH_LONG).show();
        } catch (ApiException e) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Error")
                    .setMessage("signInResult:failed code=" + e.getStatusCode())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*
         * Check permissions here, as the user has the ability to change them on the fly through
         * system settings
         */
        if (mRpUtil.getNeededPermissions(this).size() > 0) {
            // Opens a UI. When it returns, onResume() is called again.
            mRpUtil.requestMultiplePermissions(this);
            return;
        }
        // Ensures that user can switch from wifi to cellular network data connection (required to verifyLocation)
        matchingEngine = new MatchingEngine(ctx);
        matchingEngine.setNetworkSwitchingEnabled(true);  //false-> wifi (Use wifi for demo)
    }

    public class RegisterClientAndFindCloudletBackgroundRequest extends AsyncTask<Object, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Object... params) {
            try {
                return registerClient();
            } catch (ExecutionException | InterruptedException | io.grpc.StatusRuntimeException |  DmeDnsException e) {
                e.printStackTrace();
                statusText = "Registration Failed. Exception="+e.getLocalizedMessage();
                showErrorMsg(statusText);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean clientRegistered) {
            if(clientRegistered) {
                checkboxRegistered.setChecked(true);
                checkboxRegistered.setText(R.string.client_registered);
                // Populate app details.
                carrierNameTv.setText(carrierName);
                appNameTv.setText(appName);
                try {
                    findCloudlet();
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                    statusText = "FindCloudlet Failed. Exception=" + e.getLocalizedMessage();
                    showErrorMsg(statusText);
                }
            } else {
                statusText = "Failed to register client";
                Log.e(TAG, statusText);
                showErrorMsg(statusText);
            }
        }
    }

    public class VerifyLocBackgroundRequest extends AsyncTask<Object, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Object... params) {
            try {
                if (verifyLocation((Location) params[0])) {
                    return true;
                } else {
                    return false;
                }
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            } catch (IOException ioe) {
                statusText = ioe.getMessage();
                Log.e(TAG, statusText);
                showErrorMsg(statusText);
            } catch (ExecutionException ee) {
                statusText = ee.getMessage();
                Log.e(TAG, statusText);
                showErrorMsg(statusText);
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean locationVerified) {
            if (locationVerified) {
                checkboxLocationVerified.setChecked(true);
                checkboxLocationVerified.setText(R.string.location_verified);
            } else {
                statusText = "Failed to verify location";
                Log.e(TAG, statusText);
                showErrorMsg(statusText);
            }
        }
    }

    public class QoSPosBackgroundRequest extends AsyncTask<Object, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Object... params) {
            Location location = (Location) params[0];
            LocOuterClass.Loc loc = LocOuterClass.Loc.newBuilder()
                    .setLongitude(location.getLongitude())
                    .setLatitude(location.getLatitude())
                    .build();

            try {
                if (getQoSPositionKpi(loc)) {
                    return true;
                } else {
                    return false;
                }
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            } catch (ExecutionException ee) {
                statusText = ee.getMessage();
                Log.e(TAG, statusText);
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean gotQoSPositions) {
            if (!gotQoSPositions) {
                statusText = "Failed to get qosPositions";
                Log.e(TAG, statusText);
                showErrorMsg(statusText);
            }
        }
    }
}
