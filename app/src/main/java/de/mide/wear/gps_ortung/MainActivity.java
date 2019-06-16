package de.mide.wear.gps_ortung;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;


/**
 * Haupt-Activity für eine WearOS-App, die die Entfernung zwischen der aktuellen
 * GPS-Ortung und einer bestimmten Koordinate ("Heimat-Stadt") berechnet.
 * <br>
 *
 * This file is licensed under the terms of the BSD 3-Clause License.
 */
public class MainActivity extends WearableActivity
                          implements View.OnClickListener,
                                     LocationListener {

    /** Tag für Log-Messages von dieser App. */
    protected static final String TAG4LOGGING = "GpsOrtung";

    /** Wiederkennungs-Code für asynchrone Abfrage, ob App aktuell die Runtime-Permission hat. */
    public static final int REQUEST_CODE_LOCATION_PERMISSIONS = 1234;

    /** Koordinaten von Heimat-Ort, zu dem die aktuelle Entfernung berechnet wird,
     *  wird in Methode {@link MainActivity#fuelleHeimatLocation()}. */
    protected Location _heimatLocation = null;

    /** Fortschrittsanzeige; wird nur während warten auf GPS-Ortung auf sichtbar geschaltet. */
    protected ProgressBar _progressBar = null;


    /**
     * Lifecycle-Methode, lädt Layout-Datei und setzt Event-Handler-Objekt
     * für TextView-Element.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView tv = findViewById(R.id.berechnungStartenTextView);
        tv.setOnClickListener(this);

        _progressBar = findViewById(R.id.fortschrittsanzeige);

        fuelleHeimatLocation();

        setAmbientEnabled(); // Enables Always-on
    }


    /**
     * Erzeugt Location-Objekt mit Heimt-Koordinaten, zu dem die Entfernung berechnet
     * werden soll.
     */
    protected void fuelleHeimatLocation() {

        _heimatLocation = new Location("DummyProvider");
        _heimatLocation.setLongitude(  8.4043 ); // geografische Länge (positives Vorzeichen, also östlich)
        _heimatLocation.setLatitude ( 49.0140 ); // geografische Breite (positives Vorzeichen, also nördlich)

        Log.i(TAG4LOGGING, "Heimat-Location-Objekt erzeugt.");
    }


    /**
     * Event-Handler-Methode für das TextView-Element.
     *
     * @param view  TextView-Element, welche das Event ausgelöst hat.
     */
    @Override
    public void onClick(View view) {

        // Sicherstellen, dass nicht mehrere Ortungsvorgänge gleichzeitig laufen.
        if (_progressBar.getVisibility() == View.VISIBLE) {
            Log.i(TAG4LOGGING, "Es läuft schon ein Ortungsvorgang.");
            return;
        }


        int apiLevel = android.os.Build.VERSION.SDK_INT;
        Log.i(TAG4LOGGING, "API-Level=" + apiLevel);

        if (apiLevel < 23) {

            ortungAnfordern();
            return;
        }

        // Wenn wir in diese Zeile kommen, dann müssen wir die Runtime-Permission überprüfen

        if ( checkSelfPermission( Manifest.permission.ACCESS_FINE_LOCATION ) == PackageManager.PERMISSION_GRANTED ) {

            // App hat schon die Permission
            ortungAnfordern();

        } else {

            String[] permissionArray = { Manifest.permission.ACCESS_FINE_LOCATION };
            requestPermissions( permissionArray, REQUEST_CODE_LOCATION_PERMISSIONS );
            // Callback-Methode: onRequestPermissionsResult
        }
    }


    /**
     * Callback-Methode für Erhalt Ergebnis der Berechtigungsanfrage.
     *
     * @param requestCode  Request-Code zur Unterscheidung verschiedener Requests.
     *
     * @param permissionsArray  Array angeforderter Runtime-Permissions.
     *
     * @param grantResultsArry  Ergebnis für angeforderte Runtime-Permissions.
     */
    @Override
    public void onRequestPermissionsResult(int      requestCode,
                                           String[] permissionsArray,
                                           int[]    grantResultsArry) {

        if (requestCode != REQUEST_CODE_LOCATION_PERMISSIONS ) {

            Log.e(TAG4LOGGING, "Unerwarteter Request-Code: " + requestCode);
            zeigeDialog( getString( R.string.dialog_interner_fehler_berechtigung ), true );
            return;
        }


        if (permissionsArray.length != 1) {

            Log.e(TAG4LOGGING, "PermissionsArray hat nicht genau ein Element.");
            zeigeDialog( getString( R.string.dialog_interner_fehler_berechtigung ), true );
            return;
        }

        if ( ! permissionsArray[0].equals(Manifest.permission.ACCESS_FINE_LOCATION) ) {

            Log.e(TAG4LOGGING, "Unterwartete Permission an Position 1: \"" + permissionsArray[0] + "\"");
            zeigeDialog( getString( R.string.dialog_interner_fehler_berechtigung ), true );
            return;
        }


        if ( grantResultsArry[0] == PackageManager.PERMISSION_GRANTED ) {

            ortungAnfordern();

        } else {

            zeigeDialog( getString( R.string.dialog_berechtigung_verweigert ), true );
        }
    }


    /**
     * Methode startet Abfrage der aktuellen (GPS-)Ortung -- asynchron!
     * <br><br>
     *
     * <b>Bevor diese Methode aufgerufen wird muss sichergestellt sein, dass die App die
     * Berechtigung <i>android.permission.ACCESS_FINE_LOCATION</i> hat!</b>
     */
    @SuppressLint("MissingPermission")
    protected void ortungAnfordern() {

        LocationManager locationManager = null;

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {

            zeigeDialog( getString(R.string.dialog_location_manager_nicht_gefunden), true );
            return;
        }


        locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, null);
        // letztes Argument looper=null (hiermit kann Thread angegeben werden, in dem die Callback-
        // Methode onLocationChanged() ausgeführt werden soll.

        _progressBar.setVisibility( View.VISIBLE );
    }


    /**
     * Methode aus Interface {@link LocationListener}, ist
     * Callback-Methode für {@code LocationManager#requestSingleUpdate()}.
     *
     * @param location  Aktuelle Ortung.
     */
    @Override
    public void onLocationChanged(Location location) {

        Log.i(TAG4LOGGING, "Neue Ortung: " + location.getLongitude() + ", " + location.getLatitude());

        float distanzMeter = location.distanceTo(_heimatLocation);

        int distanzKilometer = (int)(distanzMeter / 1000.0);

        _progressBar.setVisibility( View.INVISIBLE );

        zeigeDialog( getString(R.string.dialog_ergebnis) + distanzKilometer + " km", false);
    }


    /**
     * Methode aus Interface {@link LocationListener}.
     *
     * @param provider  Location-Provider.
     *
     * @param status  Neuer Status der Ortung.
     *
     * @param extras  Key-Value-Paare.
     */
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

        Log.i(TAG4LOGGING, "Neuer Ortungs-Status: " + status);
    }


    /**
     * Methode aus Interface {@link LocationListener}.
     *
     * @param provider  Location-Provider, der gerade aktiviert wurde.
     */
    @Override
    public void onProviderEnabled(String provider) {

        Log.i(TAG4LOGGING, "Ortungs-Provider aktiviert: " + provider);
    }


    /**
     * Methode aus Interface {@link LocationListener}.
     *
     * @param provider  Location-Provider, der gerade deaktiviert wurde.
     */
    @Override
    public void onProviderDisabled(String provider) {

        Log.i(TAG4LOGGING, "Ortungs-Provider deaktiviert: " + provider);
    }


    /**
     * Methode um Fehlermeldung in Dialog anzuzeigen.
     *
     * @param nachricht  Anzuzeigende Nachricht.
     *
     * @param istFehler  {@code true} gdw. die darzustellende Nachricht eine Fehlermeldung ist.
     */
    protected void zeigeDialog(String nachricht, boolean istFehler) {

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        if(istFehler) {
            dialogBuilder.setTitle(getString(R.string.dialog_titel_fehlermeldung));
        } else {
            dialogBuilder.setTitle(getString(R.string.dialog_titel_ergebnis));
        }
        dialogBuilder.setMessage(nachricht);
        dialogBuilder.setPositiveButton( getString(R.string.dialog_button_ok), null);

        AlertDialog dialog = dialogBuilder.create();
        dialog.show();
    }

}
