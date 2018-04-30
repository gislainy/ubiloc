package br.ufg.inf.ubicare.ubiloc.webrtc


import android.content.Context
import android.os.Build
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.webrtc.*
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import br.ufg.inf.ubicare.ubiloc.R
enum class CallStatus(val label: Int, val color: Int) {
    UNKNOWN(0x7f0b0028, 0x7f04002d),
    CONNECTING(0x7f0b0024,0x7f040028),
    MATCHING(0x7f0b0027, 0x7f04002a),
    FAILED(0x7f0b0025, 0x7f040029),
    CONNECTED(0x7f0b0023, 0x7f040027),
    FINISHED(0x7f0b0026, 0x7f040027);
}


class DataChannelSession (
        private val context: Context,
        private val onMessageCb: (String) -> Unit,
        private val onSendCb: (DataChannel?) -> Unit,
        private val onStatusChangedListener: (CallStatus) -> Unit,
        private val signaler: SignalingWebSocket)  {

    private var peerConnection : PeerConnection? = null
    private var factory : PeerConnectionFactory? = null
    private var isOfferingPeer = false
    private val eglBase = EglBase.create()
    private var sendChannel: DataChannel? = null
    private var receiveChannel: DataChannel? = null
    private var localMessageReceived = " "
    private var remoteMessageReceived = " "

    private var localLastMessageReceived = " "
    private var remoteLastMessageReceived = " "



    val renderContext: EglBase.Context
        get() = eglBase.eglBaseContext

    class SimpleRTCEventHandler (
            private val onIceCandidateCb: (IceCandidate) -> Unit,
            private val onAddStreamCb: (MediaStream) -> Unit,
            private val onRemoveStreamCb: (MediaStream) -> Unit,
            private val onDataChannelCb: (DataChannel) -> Unit) : PeerConnection.Observer {

        override fun onIceCandidate(candidate: IceCandidate?) {
            if(candidate != null) onIceCandidateCb(candidate)
        }

        override fun onAddStream(stream: MediaStream?) {
            if (stream != null) onAddStreamCb(stream)
        }

        override fun onRemoveStream(stream: MediaStream?) {
            if(stream != null) onRemoveStreamCb(stream)
        }

        override fun onDataChannel(channel: DataChannel?) {
            Log.w(TAG, "onDataChannel: $channel")
            if(channel != null) {
                onDataChannelCb(channel)
            };

        }

        override fun onIceConnectionReceivingChange(p0: Boolean) { Log.w(TAG, "onIceConnectionReceivingChange: $p0") }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) { Log.w(TAG, "onIceConnectionChange: $newState") }

        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) { Log.w(TAG, "onIceGatheringChange: $newState") }

        override fun onSignalingChange(newState: PeerConnection.SignalingState?) { Log.w(TAG, "onSignalingChange: $newState") }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) { Log.w(TAG, "onIceCandidatesRemoved: $candidates") }

        override fun onRenegotiationNeeded() { Log.w(TAG, "onRenegotiationNeeded") }

        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) { }
    }


    init {
        signaler.messageHandler = this::onMessage
        this.onStatusChangedListener(CallStatus.MATCHING)
        executor.execute(this::init)
    }

    private fun init() {
        PeerConnectionFactory.initializeAndroidGlobals(context, true)
        val opts = PeerConnectionFactory.Options()
        opts.networkIgnoreMask = 0

        factory = PeerConnectionFactory(opts)
        factory?.setVideoHwAccelerationOptions(eglBase.eglBaseContext, eglBase.eglBaseContext)

        val iceServers = arrayListOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        val rtcCfg = PeerConnection.RTCConfiguration(iceServers)
        rtcCfg.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        val rtcEvents = SimpleRTCEventHandler(this::handleLocalIceCandidate, this::addRemoteStream, this::removeRemoteStream, this::handleDataChannel)
        peerConnection = factory?.createPeerConnection(rtcCfg, constraints, rtcEvents)
        sendChannel = peerConnection?.createDataChannel("createDataChannel", DataChannel.Init());
        sendChannel?.registerObserver(localDataChannelObserver);
        onSendCb(sendChannel);
//        sendMessage();

    }
    private fun start() {
        executor.execute(this::maybeCreateOffer)
    }

    private fun maybeCreateOffer() {
        if(isOfferingPeer) {
            peerConnection?.createOffer(SDPCreateCallback(this::createDescriptorCallback), MediaConstraints())
        }
    }

    private fun handleLocalIceCandidate(candidate: IceCandidate) {
        Log.w(TAG, "Local ICE candidate: $candidate")
        signaler.sendCandidate(candidate.sdpMLineIndex, candidate.sdpMid, candidate.sdp)
    }

    private fun addRemoteStream(stream: MediaStream) {

    }

    private fun removeRemoteStream(@Suppress("UNUSED_PARAMETER") _stream: MediaStream) {
        // We lost the stream, lets finish
        Log.i(TAG, "Bye")
        onStatusChangedListener(CallStatus.FINISHED)
    }

    private fun handleRemoteCandidate(label: Int, id: String, strCandidate: String) {
        Log.i(TAG, "Got remote ICE candidate $strCandidate")
        executor.execute {
            val candidate = IceCandidate(id, label, strCandidate)
            peerConnection?.addIceCandidate(candidate)
        }
    }


    private fun createAudioConstraints(): MediaConstraints {
        val audioConstraints = MediaConstraints()
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "false"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "false"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "false"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "false"))
        return audioConstraints
    }

    private fun handleRemoteDescriptor(sdp: String) {
        if(isOfferingPeer) {
            peerConnection?.setRemoteDescription(SDPSetCallback({ setError ->
                if(setError != null) {
                    Log.e(TAG, "setRemoteDescription failed: $setError")
                }
            }), SessionDescription(SessionDescription.Type.ANSWER, sdp))
        } else {
            peerConnection?.setRemoteDescription(SDPSetCallback({ setError ->
                if(setError != null) {
                    Log.e(TAG, "setRemoteDescription failed: $setError")
                } else {
                    peerConnection?.createAnswer(SDPCreateCallback(this::createDescriptorCallback), MediaConstraints())
                }
            }), SessionDescription(SessionDescription.Type.OFFER, sdp))
        }
    }

    private fun createDescriptorCallback(result: SDPCreateResult) {
        when(result) {
            is SDPCreateSuccess -> {
                peerConnection?.setLocalDescription(SDPSetCallback({ setResult ->
                    Log.i(TAG, "SetLocalDescription: $setResult")
                }), result.descriptor)
                signaler.sendSDP(result.descriptor.description)
            }
            is SDPCreateFailure -> Log.e(TAG, "Error creating offer: ${result.reason}")
        }
    }

    private fun onMessage(message: ClientMessage) {
        when(message) {
            is MatchMessage -> {
                onStatusChangedListener(CallStatus.CONNECTING)
                isOfferingPeer = message.offer
                start()
            }
            is SDPMessage -> {
                handleRemoteDescriptor(message.sdp)
            }
            is ICEMessage -> {
                handleRemoteCandidate(message.label, message.id, message.candidate)
            }
            is PeerLeft -> {
                onStatusChangedListener(CallStatus.FINISHED)
            }
        }
    }

    private fun handleDataChannel(dataChannel: DataChannel) {
        receiveChannel = dataChannel;
        receiveChannel?.registerObserver(DataChannelObserver);
//        sendMessage();
    }
    internal var DataChannelObserver: DataChannel.Observer = object : DataChannel.Observer{
        override fun onBufferedAmountChange(l: Long) {


        }
        override fun onStateChange() {
            Log.d(TAG, "remoteDataChannel onStateChange() " + receiveChannel!!.state().name)

        }

        override fun onMessage(buffer: DataChannel.Buffer) {
            Log.d(TAG, "remoteDataChannel onMessage()")

            if (!buffer.binary) {
                val limit = buffer.data.limit()
                val datas = ByteArray(limit)
                buffer.data.get(datas);
                onMessageCb(String(datas))
                Log.d(TAG, "remoteMessageReceived" + String(datas))
            }
        }
    }
    internal var localDataChannelObserver: DataChannel.Observer = object : DataChannel.Observer {

        override fun onBufferedAmountChange(l: Long) {

        }

        override fun onStateChange() {
            Log.d(TAG, "localDataChannelObserver onStateChange() " + sendChannel!!.state().name)
        }

        override fun onMessage(buffer: DataChannel.Buffer) {
            Log.d(TAG, "localDataChannelObserver onMessage()")

            if (!buffer.binary) {
                val limit = buffer.data.limit()
                val datas = ByteArray(limit)
                buffer.data.get(datas)

                Log.d(TAG, "localMessageReceived" + String(datas))
//                localMessageReceived = String(datas)

            }

        }
    }

    public fun sendMessage(message: String) {
        if (sendChannel?.state() == DataChannel.State.OPEN) {
            val buffer = ByteBuffer.wrap(message.toByteArray())
            sendChannel?.send(DataChannel.Buffer(buffer, false))
        }
    }
    fun terminate() {
        signaler.close()


        peerConnection?.dispose()

        factory?.dispose()

        eglBase.release()
    }


    companion object {

        fun connect(context: Context, url: String, onMessageCb: (String) -> Unit, onSendCb: (DataChannel?) -> Unit, callback: (CallStatus) -> Unit) : DataChannelSession {
            val websocketHandler = SignalingWebSocket()
            val session = DataChannelSession(context, onMessageCb, onSendCb, callback, websocketHandler)
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            Log.i(TAG, "Connecting to $url")
            client.newWebSocket(request, websocketHandler)
            client.dispatcher().executorService().shutdown()
            return session
        }

        private val TAG = "DataChannelSession"
        private val executor = Executors.newSingleThreadExecutor()
    }
}