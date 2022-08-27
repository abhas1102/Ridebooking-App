package com.mindorks.ridesharing.ui.maps

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.maps.model.LatLng
import com.mindorks.ridesharing.R
import com.mindorks.ridesharing.data.network.NetworkService
import com.mindorks.ridesharing.utils.AnimationUtils
import com.mindorks.ridesharing.utils.MapUitls
import com.mindorks.ridesharing.utils.PermissionUtils
import com.mindorks.ridesharing.utils.ViewUtils
import kotlinx.android.synthetic.main.activity_maps.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, MapsView {

    companion object{
        private const val TAG = "MapsActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 999
        private const val PICKUP_REQUEST_CODE = 1
        private const val DROP_REQUEST_CODE = 2

    }


    private lateinit var presenter: MapsPresenter
    private lateinit var googleMap: GoogleMap
    private var fusedLocationProviderClient:FusedLocationProviderClient? = null
    private lateinit var locationCallback: LocationCallback
    private var currentLatLng: com.google.android.gms.maps.model.LatLng? = null
    private val nearbyCabMarkerList = arrayListOf<Marker>()
    private var pickUpLatLng:com.google.android.gms.maps.model.LatLng?= null
    private var dropLatLng:com.google.android.gms.maps.model.LatLng?= null
    private var greyPolyLine:Polyline? = null
    private var blackPolyLine:Polyline? = null
    private var originMarker:Marker? = null
    private var destinationMarker:Marker? = null
    private var movingCabMarker: Marker? = null
    private var previousLatLngFromServer: com.google.android.gms.maps.model.LatLng? = null
    private var currentLatLngFromServer: com.google.android.gms.maps.model.LatLng? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        ViewUtils.enableTransparentStatusBar(window)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        presenter = MapsPresenter(NetworkService())
        presenter.onAttach(this)
        setUpClickListener()
    }

    private fun setUpClickListener() {
        pickUpTextView.setOnClickListener{
        launchLocationAutoCompleteActivity(PICKUP_REQUEST_CODE)

        }
        dropTextView.setOnClickListener{
            launchLocationAutoCompleteActivity(DROP_REQUEST_CODE)
        }
        requestCabButton.setOnClickListener {
            statusTextView.visibility = View.VISIBLE
            statusTextView.text = getString(R.string.requesting_your_cab)
            requestCabButton.isEnabled = false
            pickUpTextView.isEnabled = false
            dropTextView.isEnabled = false
            presenter.requestCab(pickUpLatLng!!,dropLatLng!!)
        }

        nextRideButton.setOnClickListener {
            reset()
        }
    }

    private fun reset() {
        statusTextView.visibility = View.GONE
        nextRideButton.visibility = View.GONE
        nearbyCabMarkerList.forEach{
            it.remove()
        }
        nearbyCabMarkerList.clear()
        currentLatLngFromServer = null
        previousLatLngFromServer = null
        if (currentLatLng!=null) {
            moveCamera(currentLatLng)
            animateCamera(currentLatLng)
            setCurrentLocationAsPickup()
            presenter.requestNearbyCabs(currentLatLng!!)

        } else{
            pickUpTextView.text = ""
        }
        pickUpTextView.isEnabled = true
        dropTextView.isEnabled = true
        dropTextView.text = ""
        movingCabMarker?.remove()
        greyPolyLine?.remove()
        blackPolyLine?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
        dropLatLng = null
        greyPolyLine = null
        blackPolyLine = null
        originMarker = null
        destinationMarker = null
        movingCabMarker = null
    }

    private fun launchLocationAutoCompleteActivity(requestCode: Int){
        val fields: List<Place.Field> = listOf(Place.Field.ID,Place.Field.NAME,Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).build(this)
        startActivityForResult(intent, requestCode)


    }

    private fun moveCamera(latLng: com.google.android.gms.maps.model.LatLng?) {
       // googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
    }

    private fun animateCamera(latLng: com.google.android.gms.maps.model.LatLng?){
        //Animating the camera position as per the need, animate the camera and animate to the position we want to
        val cameraPosition = CameraPosition.Builder().target(latLng).zoom(15.5f).build()
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    //Adding a car image and show that as a marker on google map
    private fun addCarMarkerAndGet(latLng: com.google.android.gms.maps.model.LatLng): Marker{
    val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapUitls.getCarBitmap(this))
        return googleMap.addMarker(MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor))

    }

    private fun addOriginDestinationMarkerAndGet(latLng:com.google.android.gms.maps.model.LatLng):Marker{
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapUitls.getDestinationBitmap())
        return googleMap.addMarker(MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor))
    }

    private fun setCurrentLocationAsPickup(){
        pickUpLatLng = currentLatLng
        pickUpTextView.text = "Current Location"
    }

    private fun enableMyLocationOnMap() {
        googleMap.setPadding(0, ViewUtils.dpToPx(48f), 0,0)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        googleMap.isMyLocationEnabled = true

    }

    private fun setUpLocationListener() {
        fusedLocationProviderClient = FusedLocationProviderClient(this)

        // For getting the current location update after every 2 seconds
        val locationRequest = LocationRequest().setInterval(2000).setFastestInterval(2000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        locationCallback = object : LocationCallback(){
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                if (currentLatLng == null){
                    for (location in locationResult.locations) {
                        if (currentLatLng == null){
                            currentLatLng = com.google.android.gms.maps.model.LatLng(location.latitude, location.longitude)
                            setCurrentLocationAsPickup()
                            enableMyLocationOnMap()
                            moveCamera(currentLatLng)
                            animateCamera(currentLatLng)
                            presenter.requestNearbyCabs(currentLatLng!!)
                        }
                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationProviderClient?.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
    }

    private fun checkAndShowRequestButton(){
        if (pickUpLatLng != null && dropLatLng != null){
            requestCabButton.visibility = View.VISIBLE
            requestCabButton.isEnabled = true
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
    }

    override fun onStart() {
        super.onStart()
        when{
            PermissionUtils.isAccessFineLocationGranted(this) -> {
                when{
                    PermissionUtils.isLocationEnabled(this) ->{
                        // fetch the location
                        setUpLocationListener()

                    }
                    else ->{
                        PermissionUtils.showGPSNotEnabledDialog(this)
                    }
                }

            }
            else ->{
                PermissionUtils.requestAccessFineLocationPermission(this, LOCATION_PERMISSION_REQUEST_CODE)

            }
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE ->{
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    when{
                        PermissionUtils.isLocationEnabled(this) ->{
                            //fetch the location
                            setUpLocationListener()
                        }
                        else -> {
                            PermissionUtils.showGPSNotEnabledDialog(this)
                        }
                    }
                } else {
                    Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICKUP_REQUEST_CODE || requestCode == DROP_REQUEST_CODE){
            when(resultCode){
                Activity.RESULT_OK -> {
                    val place = Autocomplete.getPlaceFromIntent(data!!)
                    when(requestCode){
                        PICKUP_REQUEST_CODE ->{
                            pickUpTextView.text = place.name
                            pickUpLatLng = place.latLng
                            checkAndShowRequestButton()
                        }
                        DROP_REQUEST_CODE->{
                            dropTextView.text = place.name
                            dropLatLng = place.latLng
                            checkAndShowRequestButton()
                        }
                    }
                }
                AutocompleteActivity.RESULT_ERROR ->{
                    val status: Status = Autocomplete.getStatusFromIntent(data!!)
                    Log.d(TAG, status.statusMessage!!)
                }
                Activity.RESULT_CANCELED->{

                }
            }
        }
    }

    override fun onDestroy() {
        presenter.onDetach()
        fusedLocationProviderClient?.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun showNearbyCabs(latLngList: List<com.google.android.gms.maps.model.LatLng>) {
        nearbyCabMarkerList.clear()
        for (latLng in latLngList){
            val nearbyCabMarker = addCarMarkerAndGet(latLng)
            nearbyCabMarkerList.add(nearbyCabMarker)
        }
    }

    override fun informCabBooked() {
        nearbyCabMarkerList.forEach{
            it.remove()
        }
        nearbyCabMarkerList.clear()
        requestCabButton.visibility = View.GONE
        statusTextView.text = "Your Cab Is Booked"
    }

    override fun showPath(latLngList: List<com.google.android.gms.maps.model.LatLng>) {
        val builder = LatLngBounds.Builder()
        for (latLng in latLngList){
            builder.include(latLng)
        }
        val bounds = builder.build()
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds,2))
        //Will create the polyline options with gray color width 5 and it will have all the points for which we have to draw
        val polylineOptions = PolylineOptions()
        polylineOptions.color(Color.GRAY)
        polylineOptions.width(5f)
        polylineOptions.addAll((latLngList))
        greyPolyLine = googleMap.addPolyline(polylineOptions)

        val blackPolylineOptions = PolylineOptions()
        blackPolylineOptions.color(Color.GRAY)
        blackPolylineOptions.width(5f)
        blackPolyLine = googleMap.addPolyline(blackPolylineOptions)

        originMarker = addOriginDestinationMarkerAndGet(latLngList[0])
        originMarker?.setAnchor(0.5f,0.5f)
        destinationMarker = addOriginDestinationMarkerAndGet(latLngList[latLngList.size-1])
        destinationMarker?.setAnchor(0.5f,0.5f)

        val polylineAnimator = AnimationUtils.polyLineAnimator()
        polylineAnimator.addUpdateListener {valueAnimator ->
            val percentValue = (valueAnimator.animatedValue as Int)
            val index = (greyPolyLine?.points!!.size) * (percentValue/100.0f).toInt()
            blackPolyLine?.points = greyPolyLine?.points!!.subList(0, index)

        }
        polylineAnimator.start()
        //booking a cab
        //showing the driver pickup path
        //updating the status in status text vieww

    }

    override fun updateCabLocation(latLng: com.google.android.gms.maps.model.LatLng) {
        if (movingCabMarker == null){
            movingCabMarker = addCarMarkerAndGet(latLng)
        }
        if(previousLatLngFromServer == null){
            currentLatLngFromServer = latLng
            previousLatLngFromServer = currentLatLng
            movingCabMarker?.position = currentLatLngFromServer
            movingCabMarker?.setAnchor(0.5f,0.5f)
            animateCamera(currentLatLngFromServer)
        }else{
            previousLatLngFromServer = currentLatLngFromServer
            currentLatLngFromServer = latLng
            val valueAnimator = AnimationUtils.cabAnimator()
            valueAnimator.addUpdateListener {va->
                if (currentLatLngFromServer != null && previousLatLngFromServer != null){
                    val multiplier = va.animatedFraction
                    val nextLocation = com.google.android.gms.maps.model.LatLng(
                        multiplier * currentLatLngFromServer!!.latitude + (1-multiplier) * previousLatLngFromServer!!.latitude,
                        multiplier * currentLatLngFromServer!!.longitude + (1-multiplier) * previousLatLngFromServer!!.longitude

                    )
                    movingCabMarker?.position = nextLocation
                    val rotation = MapUitls.getRotation(previousLatLngFromServer!!,nextLocation)
                    if (!rotation.isNaN()){
                        movingCabMarker?.rotation = rotation
                    }
                    movingCabMarker?.setAnchor(0.5f,0.5f)
                    animateCamera(nextLocation)
                }

            }
            valueAnimator.start()
        }

    }

    override fun informCabIsArriving() {
        statusTextView.text = "Your cab is arriving"
    }

    override fun informCabArrived() {
         statusTextView.text = "Your cab has arrived"
        greyPolyLine?.remove()
        originMarker?.remove()
        blackPolyLine?.remove()
        destinationMarker?.remove()
    }

    override fun informTripStart() {
        statusTextView.text = "You are on a trip"
        previousLatLngFromServer = null
    }

    override fun informTripEnd() {
        statusTextView.text = "Trip Ended"
        nextRideButton.visibility = View.VISIBLE
        greyPolyLine?.remove()
        blackPolyLine?.remove()
        originMarker?.remove()
        destinationMarker?.remove()


    }

    override fun showRoutesNotAvailableError() {
        val error = "Route not available, choose different location"
        Toast.makeText(this,error,Toast.LENGTH_LONG).show()
        reset()
    }

    override fun showDirectionFailedError(error: String) {
        Toast.makeText(this,error,Toast.LENGTH_LONG).show()
        reset()
    }
}
