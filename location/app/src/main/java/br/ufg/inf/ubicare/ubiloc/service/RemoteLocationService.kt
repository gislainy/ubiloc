//package br.ufg.inf.ubicare.ubiloc.service
//
//import android.app.Activity
//import android.app.AlarmManager
//import android.app.Notification
//import android.app.PendingIntent
//import android.app.Service
//import android.bluetooth.BluetoothAdapter
//import android.bluetooth.BluetoothDevice
//import android.content.Context
//import android.content.Intent
//import android.os.Handler
//import android.os.HandlerThread
//import android.os.IBinder
//import android.os.Looper
//import android.os.Message
//import android.os.Process
//import android.support.v4.content.ContextCompat
//import android.support.v7.app.NotificationCompat
//import android.util.Log
//
//import com.lemmingapex.trilateration.NonLinearLeastSquaresSolver
//import com.lemmingapex.trilateration.TrilaterationFunction
//
//import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer
//import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer
//import org.webrtc.DataChannel
//
//import java.util.ArrayList
//import java.util.Arrays
//import java.util.Calendar
//import java.util.Date
//
//import br.ufg.inf.ubicare.ubiloc.R
//import br.ufg.inf.ubicare.ubiloc.activities.OptionsActivity
//import br.ufg.inf.ubicare.ubiloc.bluetooth.BluetoothDeviceStore
//import br.ufg.inf.ubicare.ubiloc.bluetooth.BluetoothServiceScanner
//import br.ufg.inf.ubicare.ubiloc.bluetooth.BluetoothServiceUtils
//import br.ufg.inf.ubicare.ubiloc.database.Database
//import br.ufg.inf.ubicare.ubiloc.domain.Beacon
//import br.ufg.inf.ubicare.ubiloc.domain.Room
//import br.ufg.inf.ubicare.ubiloc.domain.User
//import br.ufg.inf.ubicare.ubiloc.webrtc.CallStatus
//import br.ufg.inf.ubicare.ubiloc.webrtc.DataChannelSession
//import uk.co.alt236.bluetoothlelib.device.BluetoothLeDevice
//import uk.co.alt236.bluetoothlelib.device.beacon.BeaconType
//import uk.co.alt236.bluetoothlelib.device.beacon.BeaconUtils
//import uk.co.alt236.bluetoothlelib.device.beacon.ibeacon.IBeaconDevice
//import java.nio.ByteBuffer
//
//class RemoteLocationService : Service() {
//
//    private var beaconList: ArrayList<IBeaconDevice>? = null
//    private var mBluetoothUtils: BluetoothServiceUtils? = null
//    private var mScanner: BluetoothServiceScanner? = null
//    private var mDeviceStore: BluetoothDeviceStore? = null
//    private var mDatabase: Database? = null
//    private var localRooms: List<Room>? = null
//    private var currentRoom: Room? = null
//    private var mHandler: Handler? = null
//
//
//    private var dataChannelSession: DataChannelSession? = null
//    private var channel: DataChannel? = null
//
//
//    private val mLeScanCallback = BluetoothAdapter.LeScanCallback { device, rssi, scanRecord ->
//        val deviceLe = BluetoothLeDevice(device, rssi, scanRecord, System.currentTimeMillis())
//        if (BeaconUtils.getBeaconType(deviceLe) == BeaconType.IBEACON) {
//            mDeviceStore!!.addDevice(deviceLe)
//        }
//        beaconList = ArrayList()
//
//        for (dev in mDeviceStore!!.deviceList) {
//            val iBeacon = IBeaconDevice(dev)
//            beaconList!!.add(iBeacon)
//        }
//    }
//
//    private val closestBeacon: IBeaconDevice
//        get() {
//            var closestDevice = beaconList!![0]
//            for (i in 1 until beaconList!!.size) {
//                val device = beaconList!![i]
//                if (device.accuracy < closestDevice.accuracy) {
//                    closestDevice = device
//                }
//            }
//            return closestDevice
//        }
//
//    private val loop = object : Runnable {
//        override fun run() {
//            println("loop bom")
//            calculateAndSendLocation()
//            mHandler!!.postDelayed(this, 1500)
//        }
//    }
//
//    private fun getBeaconsRoom(device: IBeaconDevice): Room? {
//        for (room in localRooms!!) {
//            for (beacon in room.beacons) {
//                if (beacon.macAddress == device.address) {
//                    return room
//                }
//            }
//        }
//        return null
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//
//        beaconList = ArrayList()
//        localRooms = Room.listAll(Room::class.java)
//        mDeviceStore = BluetoothDeviceStore()
//        mBluetoothUtils = BluetoothServiceUtils(applicationContext)
//        mScanner = BluetoothServiceScanner(mLeScanCallback, mBluetoothUtils)
//        mDatabase = Database.with(User.listAll(User::class.java)[0].key)
//        val isBluetoothOn = mBluetoothUtils!!.isBluetoothOn
//        val isBluetoothLePresent = mBluetoothUtils!!.isBluetoothLeSupported
//        if (isBluetoothOn && isBluetoothLePresent) {
//            mScanner!!.scanLeDevice(-1, true)
//        }
//
//        val notificationIntent = Intent(this, OptionsActivity::class.java)
//        val pendingIntent = PendingIntent.getActivity(this, 0,
//                notificationIntent, 0)
//
//        val notification = NotificationCompat.Builder(this)
//                .setSmallIcon(R.mipmap.ic_launcher_round)
//                .setContentTitle("UbiCareLoc")
//                .setOngoing(true)
//                .setContentText("Enviando localização")
//                .setContentIntent(pendingIntent).build()
//
//        startForeground(1337, notification)
//
//        val mHandlerThread = HandlerThread("HandlerThread")
//        mHandlerThread.start()
//        mHandler = Handler(mHandlerThread.looper)
//        mHandler!!.post(loop)
//
//    }
//
//    override fun onBind(intent: Intent): IBinder? {
//        return null
//    }
//
//    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
//        calculateAndSendLocation()
//        startVideoSession();
//        return Service.START_STICKY
//    }
//
//    private fun calculateAndSendLocation() {
//        if (beaconList!!.size >= 3) {
//            currentRoom = getBeaconsRoom(closestBeacon)
//            if (currentRoom != null) {
//                var positions = arrayOf<DoubleArray>()
//                var distances = doubleArrayOf()
//                for (device in beaconList!!) {
//                    for (beacon in currentRoom!!.beacons) {
//                        if (device.address == beacon.macAddress) {
//                            positions = addFloatElement(positions, doubleArrayOf(beacon.coordinateX, beacon.coordinateY))
//                            distances = addElement(distances, device.accuracy)
//                        }
//                    }
//                }
//                if (positions.size > 2 && distances.size > 2) {
//                    val solver = NonLinearLeastSquaresSolver(TrilaterationFunction(positions, distances), LevenbergMarquardtOptimizer())
//                    val optimum = solver.solve()
//
//                    val centroid = optimum.point.toArray()
//                    println("COORDENADAS " + centroid[0] + ", " + centroid[1])
//                    mDatabase!!.updateUserLocationAtRoomOnServer(Room.listAll(Room::class.java)[0].name, centroid)
//                    sendMessage("COORDENADAS " + centroid[0] + ", " + centroid[1])
//                }
//            }
//        }
//
//    }
//    private fun startVideoSession() {
////        dataChannelSession = DataChannelSession.connect(this, BACKEND_URL, this::onMesasge, this::onSendCb, this::onStatusChanged)
//
//    }
//    private fun onStatusChanged(newStatus: CallStatus) {
////        Log.d(TAG,"New call status: $newStatus")
////        runOnUiThread {
////            when(newStatus) {
////                CallStatus.FINISHED -> finish()
////                else -> {
////                    statusConnection?.text = resources.getString(newStatus.label)
////                    statusConnection?.setTextColor(ContextCompat.getColor(this, newStatus.color))
////                }
////            }
////        }
//    }
//
//    private fun onMesasge(string: String) {
////        runOnUiThread {
////            val textRemote = remoteTextView?.text
////            remoteTextView?.text = string
////        }
//    }
//    private fun sendMessage() {
////        val textLocal = localTextView?.text;
////        if (channel?.state() == DataChannel.State.OPEN) {
////            val buffer = ByteBuffer.wrap(textLocal.toString().toByteArray())
////            channel?.send(DataChannel.Buffer(buffer, false))
////        }
//    }
//    private fun onSendCb(chan: DataChannel?) {
//        if(chan != null) channel = chan;
//    }
//    private fun sendMessage(text: String) {
//        if (channel?.state() == DataChannel.State.OPEN) {
//            val buffer = ByteBuffer.wrap(text.toByteArray())
//            channel?.send(DataChannel.Buffer(buffer, false))
//        }
//    }
//    companion object {
//
//        internal fun addElement(a: DoubleArray, e: Double): DoubleArray {
//            var a = a
//            a = Arrays.copyOf(a, a.size + 1)
//            a[a.size - 1] = e
//            return a
//        }
//
//        internal fun addFloatElement(a: Array<DoubleArray>, e: DoubleArray): Array<DoubleArray> {
//            var a = a
//            a = Arrays.copyOf(a, a.size + 1)
//            a[a.size - 1] = e
//            return a
//        }
//        private val BACKEND_URL = "ws://192.168.40.174:7000/"
//    }
//
//
//}
