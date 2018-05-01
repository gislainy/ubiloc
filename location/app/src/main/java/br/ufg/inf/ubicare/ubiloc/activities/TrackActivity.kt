package br.ufg.inf.ubicare.ubiloc.activities

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.TextView
import br.ufg.inf.ubicare.ubiloc.R
import br.ufg.inf.ubicare.ubiloc.data.PositionPojo
import br.ufg.inf.ubicare.ubiloc.database.Database
import br.ufg.inf.ubicare.ubiloc.domain.User
import br.ufg.inf.ubicare.ubiloc.service.RemoteLocationService
import br.ufg.inf.ubicare.ubiloc.webrtc.CallStatus
import br.ufg.inf.ubicare.ubiloc.webrtc.DataChannelSession
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import org.webrtc.DataChannel
import java.nio.ByteBuffer
import java.util.*

class TrackActivity : AppCompatActivity() {

    private var mHolder: ViewHolder? = null

    private var dataChannelSession: DataChannelSession? = null
    private var channel: DataChannel? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startVideoSession()
        initViews()
    }

    private fun initViews() {
        mHolder = ViewHolder()
        setContentView(R.layout.activity_track)

        mHolder!!.coordinates = findViewById(R.id.coordinates)
        if (User.listAll(User::class.java).size > 0) {
            val user = User.listAll(User::class.java)[0]
            Database.with(user.key).getPositionFromServer(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val position = dataSnapshot.getValue(PositionPojo::class.java)

                    val str =       "Localização atual é: Cômodo - " + position!!.roomName + " X: " + String.format("%.2f", position.x) + " , Y: " + String.format("%.2f", position.y)
                    mHolder!!.coordinates!!.text = str
                    sendMessage(str);
                }

                override fun onCancelled(databaseError: DatabaseError) {

                }
            })
        }
    }

    private fun startVideoSession() {
        dataChannelSession = DataChannelSession.connect(this, BACKEND_URL, this::onMesasge, this::onSendCb, this::onStatusChanged)

    }
    private fun onStatusChanged(newStatus: CallStatus) {
//        Log.d(TAG,"New call status: $newStatus")
//        runOnUiThread {
//            when(newStatus) {
//                CallStatus.FINISHED -> finish()
//                else -> {
//                    statusConnection?.text = resources.getString(newStatus.label)
//                    statusConnection?.setTextColor(ContextCompat.getColor(this, newStatus.color))
//                }
//            }
//        }
    }

    private fun onMesasge(string: String) {
//        runOnUiThread {
//            val textRemote = remoteTextView?.text
//            remoteTextView?.text = string
//        }
    }
    private fun sendMessage() {
//        val textLocal = localTextView?.text;
//        if (channel?.state() == DataChannel.State.OPEN) {
//            val buffer = ByteBuffer.wrap(textLocal.toString().toByteArray())
//            channel?.send(DataChannel.Buffer(buffer, false))
//        }
    }
    private fun onSendCb(chan: DataChannel?) {
        if(chan != null) channel = chan;
    }
    private fun sendMessage(text: String) {
        if (channel?.state() == DataChannel.State.OPEN) {
            val buffer = ByteBuffer.wrap(text.toByteArray())
            channel?.send(DataChannel.Buffer(buffer, false))
        }
    }
    private inner class ViewHolder {
        internal var coordinates: TextView? = null
    }
    companion object {

        private val BACKEND_URL = "ws://192.168.15.8:4433/"
    }

}
