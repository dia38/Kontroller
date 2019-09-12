package com.github.roarappstudio.btkontroller

import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.github.roarappstudio.btkontroller.reports.FeatureReport


@Suppress("MemberVisibilityCanBePrivate")
object BluetoothController: BluetoothHidDevice.Callback(), BluetoothProfile.ServiceListener {

    //
    //  params
    //
    val featureReport = FeatureReport()

    //
    //  onSetReport
    //
    override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
        Log.i("BluetoothController","onSetReport")
        super.onSetReport(device, type, id, data)
        Log.i("BluetoothController","onSetReport $device and $type and $id and $data")
    }

    //
    //  onGetReport
    //
    override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
        Log.i("BluetoothController", "onGetReport(1)")
        super.onGetReport(device, type, id, bufferSize)

        Log.i("BluetoothController", "onGetReport(2)")
         if( type == BluetoothHidDevice.REPORT_TYPE_FEATURE ) {
            featureReport.wheelResolutionMultiplier = true
            featureReport.acPanResolutionMultiplier = true
            Log.i("BluetoothController","hid=$btHid")

             var wasrs=btHid?.replyReport(device, type, FeatureReport.ID, featureReport.bytes)
             Log.i("BluetoothController", "replysuccess flag " + wasrs.toString())
         }
    }


    val btAdapter by lazy { BluetoothAdapter.getDefaultAdapter()!! }
    var btHid       : BluetoothHidDevice?   = null
    var hostDevice  : BluetoothDevice?      = null
    var autoPairFlag                        = false
    var mpluggedDevice :BluetoothDevice?    = null

    private var deviceListener      : ((BluetoothHidDevice, BluetoothDevice)->Unit)? = null
    private var disconnectListener  : (()->Unit)? = null

    fun init(ctx: Context) {
        Log.i("BluetoothController", "init")
        if (btHid != null)
            return
        btAdapter.getProfileProxy(ctx, this, BluetoothProfile.HID_DEVICE)
    }

    fun getSender(callback: (BluetoothHidDevice, BluetoothDevice)->Unit) {
        Log.i("BluetoothController", "getSendor")
        btHid?.let { hidd ->
            hostDevice?.let { host ->
                callback(hidd, host)
                return
            }
        }
        deviceListener = callback
    }


    fun getDisconnector(callback: ()->Unit) {
        Log.i("BluetoothController", "getDisconnector")
        disconnectListener = callback
    }

    /*****************************************************/
    /** BluetoothProfile.ServiceListener implementation **/
    /*****************************************************/

    override fun onServiceDisconnected(profile: Int) {
        Log.e(TAG, "onServiceDisconnected::Service disconnected!")
        if (profile == BluetoothProfile.HID_DEVICE)
            btHid = null
    }

    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
        Log.i(TAG, "onSerivceConnected::Connected to service")
        if (profile != BluetoothProfile.HID_DEVICE) {
            Log.wtf(TAG, "WTF? $profile")
            return
        }

        val btHid = proxy as? BluetoothHidDevice
        if (btHid == null) {
            Log.wtf(TAG, "WTF? Proxy received but it's not BluetoothHidDevice")

            return
        }
        this.btHid = btHid
        btHid.registerApp(sdpRecord, null, qosOut, {it.run()}, this)//--
        btAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, 300000)
    }



    /************************************************/
    /** BluetoothHidDevice.Callback implementation **/
    /************************************************/



    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
        Log.i("BluetoothController", "onConnectionStateChanged")
        super.onConnectionStateChanged(device, state)
        Log.i(TAG, "Connection state ${when(state) {
            BluetoothProfile.STATE_CONNECTING       -> "CONNECTING"
            BluetoothProfile.STATE_CONNECTED        -> "CONNECTED"
            BluetoothProfile.STATE_DISCONNECTING    -> "DISCONNECTING"
            BluetoothProfile.STATE_DISCONNECTED     -> "DISCONNECTED"
            else -> state.toString()
        }}")
        if( state == BluetoothProfile.STATE_CONNECTED ) {
            if( device != null ) {
                hostDevice = device
                deviceListener?.invoke(btHid!!, device)
                //deviceListener = null
            } else {
                Log.e(TAG, "Device not connected")
            }
        } else {
            hostDevice = null
            if( state == BluetoothProfile.STATE_DISCONNECTED ) {
                disconnectListener?.invoke()
            }
        }
    }

    override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
        Log.i("BluetoothController", "onAppStatusChanged")
        super.onAppStatusChanged(pluggedDevice, registered)
        if(registered) {
            var pairedDevices = btHid?.getDevicesMatchingConnectionStates(intArrayOf(BluetoothProfile.STATE_CONNECTING,BluetoothProfile.STATE_CONNECTED,BluetoothProfile.STATE_DISCONNECTED,BluetoothProfile.STATE_DISCONNECTING))
            Log.d("paired d", "paired devices are : $pairedDevices")
            Log.d("paired d","${btHid?.getConnectionState(pairedDevices?.get(0))}")
            mpluggedDevice = pluggedDevice
            if( btHid?.getConnectionState(pluggedDevice) == 0 && pluggedDevice != null && autoPairFlag == true ) {
                btHid?.connect(pluggedDevice)
                //hostDevice.toString()
            } else if(btHid?.getConnectionState(pairedDevices?.get(0))==0 && autoPairFlag==true) {
                Log.i("ddaaqq","sssS" )
                btHid?.connect(pairedDevices?.get(0))
            }

//          val intent = Intent("CUSTOM_ACTION")
//          intent.putExtra("DATE", Date().toString())
//          Log.d("j", "sending broadcast")
//          // send local broadcast
//          LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }





    /*************/
    /** Garbage **/
    /*************/

    const val TAG = "BluetoothController"

    private val sdpRecord by lazy {
        BluetoothHidDeviceAppSdpSettings(
            "Pixel HID1",
            "Mobile BController",
            "bla",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            DescriptorCollection.MOUSE_KEYBOARD_COMBO
        )
    }

    private val qosOut by lazy {
        BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800,
            9,
            0,
            11250,
            BluetoothHidDeviceAppQosSettings.MAX
        )
    }

}