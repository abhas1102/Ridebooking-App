package com.mindorks.ridesharing.ui.maps

import com.google.android.gms.maps.model.LatLng
import java.lang.Error

interface MapsView {

    fun showNearbyCabs(latLngList:List<LatLng>)
    fun informCabBooked()
    fun showPath(latLngList: List<LatLng>)
    fun updateCabLocation(latLng: LatLng)
    fun informCabIsArriving()
    fun informCabArrived()
    fun informTripStart()
    fun informTripEnd()
    fun showRoutesNotAvailableError()
    fun showDirectionFailedError(error: String)
}