package com.example.studntapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

class MessagesActivity : BaseActivity() {

    private lateinit var layoutChatList: LinearLayout
    private lateinit var rvChatList: RecyclerView
    private lateinit var etSearchChats: EditText

    private lateinit var layoutChatRoom: LinearLayout
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnRecord: Button
    private lateinit var btnAttachFile: ImageButton
    private lateinit var btnClearText: ImageButton
    private lateinit var btnBackToList: ImageButton
    private lateinit var tvRoomName: TextView
    private lateinit var btnToggleSearchMessages: ImageButton
    private lateinit var etSearchMessages: EditText
    private lateinit var btnActionContainer: FrameLayout

    private var userId = 0
    private var role = ""
    private var currentTargetType = "user"
    private var currentTargetId = 1
    private var isGroupChat = false

    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath = ""
    private var isRecording = false
    private var recordingStartTime = 0L

    private var allChatsList = listOf<ChatEntity>()
    private var allMessagesList = listOf<ChatMessageData>()
    private var chatAdapter: AdvancedChatAdapter? = null

    // محرك WebSockets اللحظي
    private var pusher: Pusher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)
        // قائمة المحادثات تستخدم شريط الهيكل الموحّد (كباقي الشاشات). وعند فتح
        // غرفة المحادثة نُخفي شريط الهيكل ليظهر شريط الغرفة الخاص (رجوع + اسم).
        supportActionBar?.title = "الرسائل والدردشة"

        // مراعاة النوتش لرأس غرفة المحادثة (يظهر عند إخفاء شريط الهيكل).
        findViewById<View?>(R.id.chatRoomHeader)?.let { header ->
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
                val top = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                        androidx.core.view.WindowInsetsCompat.Type.displayCutout()
                ).top
                v.setPadding(v.paddingLeft, top, v.paddingRight, v.paddingBottom)
                insets
            }
        }

        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        userId = prefs.getInt("USER_ID", 0)
        role = prefs.getString("USER_ROLE", "student") ?: "student"

        layoutChatList = findViewById(R.id.layoutChatList)
        rvChatList = findViewById(R.id.rvChatList)
        etSearchChats = findViewById(R.id.etSearchChats)

        layoutChatRoom = findViewById(R.id.layoutChatRoom)
        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSendMessage)
        btnRecord = findViewById(R.id.btnRecordAudio)
        btnAttachFile = findViewById(R.id.btnAttachFile)
        btnClearText = findViewById(R.id.btnClearText)
        btnBackToList = findViewById(R.id.btnBackToList)
        tvRoomName = findViewById(R.id.tvRoomName)
        btnToggleSearchMessages = findViewById(R.id.btnToggleSearchMessages)
        etSearchMessages = findViewById(R.id.etSearchMessages)
        btnActionContainer = findViewById(R.id.btnActionContainer)

        rvChatList.layoutManager = LinearLayoutManager(this)
        rvMessages.layoutManager = LinearLayoutManager(this)

        requestAudioPermission()

        etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrEmpty()) {
                    btnSend.visibility = View.GONE
                    btnRecord.visibility = View.VISIBLE
                    btnClearText.visibility = View.GONE
                } else {
                    btnSend.visibility = View.VISIBLE
                    btnRecord.visibility = View.GONE
                    btnClearText.visibility = View.VISIBLE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearText.setOnClickListener { etMessage.text.clear() }

        etSearchChats.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filterChatList(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })

        etSearchMessages.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filterMessages(s.toString(), false) }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnToggleSearchMessages.setOnClickListener {
            if (etSearchMessages.visibility == View.VISIBLE) {
                etSearchMessages.visibility = View.GONE
                etSearchMessages.text.clear()
            } else {
                etSearchMessages.visibility = View.VISIBLE
                etSearchMessages.requestFocus()
            }
        }

        btnSend.setOnClickListener { sendMsg(etMessage.text.toString(), null) }
        btnRecord.setOnClickListener {
            if (checkAudioPermission()) { if (!isRecording) startRecording() else stopAndSendRecording() }
            else { requestAudioPermission() }
        }
        btnBackToList.setOnClickListener { closeChatRoom() }

        btnAttachFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
            startActivityForResult(intent, 1001)
        }

        loadChatList()
        openChatFromIntentIfNeeded()
    }

    private fun checkAudioPermission(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun requestAudioPermission() { if (!checkAudioPermission()) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100) }

    private fun loadChatList() {
        RetrofitClient.instance.getChatList(userId = userId, role = role).enqueue(object : Callback<ChatListResponse> {
            override fun onResponse(call: Call<ChatListResponse>, response: Response<ChatListResponse>) {
                if (response.isSuccessful) {
                    val data = response.body()?.data
                    val tempChats = mutableListOf<ChatEntity>()
                    data?.groups?.let { tempChats.addAll(it) }
                    data?.contacts?.let { tempChats.addAll(it) }
                    allChatsList = tempChats.sortedByDescending { it.lastMsgTime ?: "1970-01-01" }
                    Log.d("CHAT_DEBUG", "Loaded ${allChatsList.size} chats")
                    filterChatList(etSearchChats.text.toString())
                } else {
                    Log.e("CHAT_DEBUG", "Failed to load chats: ${response.code()}")
                    Toast.makeText(this@MessagesActivity, "فشل تحميل المحادثات", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<ChatListResponse>, t: Throwable) {
                Log.e("CHAT_DEBUG", "Error loading chats", t)
                Toast.makeText(this@MessagesActivity, "خطأ في الاتصال", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun filterChatList(query: String) {
        val filtered = if (query.isEmpty()) allChatsList else allChatsList.filter { it.name?.lowercase()?.contains(query.lowercase()) == true }
        rvChatList.adapter = ChatListAdapter(filtered) { chat -> openChatRoom(chat) }
    }

    private fun openChatRoom(chat: ChatEntity) {
        openChatDirect(chat.id, chat.type ?: "user", chat.name)
    }

    /** فتح محادثة مباشرةً بمعرّف ونوع واسم — يُستخدم من القائمة ومن الإشعارات. */
    private fun openChatDirect(targetId: Int, targetType: String, targetName: String?) {
        currentTargetType = targetType
        currentTargetId = targetId
        isGroupChat = (currentTargetType == "group")
        tvRoomName.text = targetName

        // داخل الغرفة نُخفي شريط الهيكل ليظهر شريط الغرفة الخاص (رجوع + اسم المحادثة).
        supportActionBar?.hide()
        layoutChatList.visibility = View.GONE
        layoutChatRoom.visibility = View.VISIBLE

        chatAdapter = AdvancedChatAdapter(emptyList(), userId, role, isGroupChat)
        rvMessages.adapter = chatAdapter

        loadMessages(scrollToBottom = true)
        connectToSoketiChannel()
    }

    /** إن وصلنا من إشعار رسالة، نفتح المحادثة المطلوبة مباشرةً. */
    private fun openChatFromIntentIfNeeded() {
        val id = intent.getIntExtra("OPEN_CHAT_ID", 0)
        if (id != 0) {
            val type = intent.getStringExtra("OPEN_CHAT_TYPE") ?: "user"
            val name = intent.getStringExtra("OPEN_CHAT_NAME")
            rvChatList.post { openChatDirect(id, type, name) }
        }
    }

    private fun connectToSoketiChannel() {
        val options = PusherOptions().apply {
            setHost("olms.inspirers-ngo.org")
            setUseTLS(true)
        }
        pusher = Pusher("app-key", options)
        pusher?.connect()

        val channelName = if (isGroupChat) "group_$currentTargetId" else "private_${min(userId, currentTargetId)}_${max(userId, currentTargetId)}"
        val channel = pusher?.subscribe(channelName)

        channel?.bind("new_message") { _ ->
            runOnUiThread {
                loadMessages(scrollToBottom = true)
                loadChatList()
            }
        }
    }

    private fun closeChatRoom() {
        pusher?.disconnect()
        pusher = null
        etSearchMessages.text.clear()
        etSearchMessages.visibility = View.GONE
        layoutChatRoom.visibility = View.GONE
        layoutChatList.visibility = View.VISIBLE
        // العودة للقائمة: نُظهر شريط الهيكل الموحّد مجدداً.
        supportActionBar?.show()
        chatAdapter = null
        loadChatList()
    }

    override fun onBackPressed() {
        if (layoutChatRoom.visibility == View.VISIBLE) closeChatRoom() else super.onBackPressed()
    }

    private fun loadMessages(scrollToBottom: Boolean = true) {
        val targetTypeReq = if (isGroupChat) "group" else "user"
        RetrofitClient.instance.getChatMessages(userId = userId, role = role, targetType = targetTypeReq, targetId = currentTargetId)
            .enqueue(object : Callback<ChatMessageResponse> {
                override fun onResponse(call: Call<ChatMessageResponse>, response: Response<ChatMessageResponse>) {
                    if (response.isSuccessful) {
                        allMessagesList = response.body()?.data ?: emptyList()
                        filterMessages(etSearchMessages.text.toString(), scrollToBottom)
                    }
                }
                override fun onFailure(call: Call<ChatMessageResponse>, t: Throwable) {}
            })
    }

    private fun filterMessages(query: String, scrollToBottom: Boolean) {
        val filtered = if (query.isEmpty()) allMessagesList else allMessagesList.filter {
            it.body?.lowercase()?.contains(query.lowercase()) == true || it.senderName?.lowercase()?.contains(query.lowercase()) == true
        }
        if (filtered.isEmpty() && query.isEmpty()) {
            chatAdapter?.updateList(emptyList())
            return
        }
        val isAtBottom = !rvMessages.canScrollVertically(1)
        val isFirstLoad = chatAdapter?.itemCount == 0
        chatAdapter?.updateList(filtered)
        if (scrollToBottom || isAtBottom || isFirstLoad) {
            if (filtered.isNotEmpty()) rvMessages.scrollToPosition(filtered.size - 1)
        }
    }

    private fun startRecording() {
        if (!checkAudioPermission()) { requestAudioPermission(); return }
        try {
            val file = File(externalCacheDir ?: cacheDir, "audio_msg.m4a")
            audioFilePath = file.absolutePath
            mediaRecorder?.release()
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
            mediaRecorder = recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFilePath)
                prepare()
                start()
            }
            recordingStartTime = System.currentTimeMillis()
            isRecording = true
            btnRecord.text = "⏹️"
        } catch (e: Exception) {
            Toast.makeText(this, "الميكروفون مشغول", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopAndSendRecording() {
        if (System.currentTimeMillis() - recordingStartTime < 1000) {
            Toast.makeText(this, "التسجيل قصير جداً", Toast.LENGTH_SHORT).show()
            try { mediaRecorder?.stop() } catch (e: Exception) {}
            mediaRecorder?.release(); mediaRecorder = null; isRecording = false; btnRecord.text = "🎤"
            return
        }
        try {
            mediaRecorder?.stop(); mediaRecorder?.release(); mediaRecorder = null; isRecording = false; btnRecord.text = "🎤"
            val file = File(audioFilePath)
            if (file.exists()) sendMsg("رسالة صوتية 🎤", file)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun sendMsg(text: String, file: File?) {
        if (text.isEmpty() && file == null) return
        val actReq = "send_chat_message".toRequestBody(MultipartBody.FORM)
        val sIdReq = userId.toString().toRequestBody(MultipartBody.FORM)
        val tTypeReq = (if(isGroupChat) "group" else "user").toRequestBody(MultipartBody.FORM)
        val tIdReq = currentTargetId.toString().toRequestBody(MultipartBody.FORM)
        val bodyReq = text.toRequestBody(MultipartBody.FORM)
        val roleReq = role.toRequestBody(MultipartBody.FORM) // إرسال الدور للسيرفر

        var filePart: MultipartBody.Part? = null
        if (file != null) {
            val extension = file.extension.lowercase()
            val mimeType = when (extension) {
                "jpg", "jpeg", "png" -> "image/*"
                "mp3", "m4a", "wav" -> "audio/*"
                "mp4", "3gp" -> "video/*"
                "pdf" -> "application/pdf"
                else -> "application/octet-stream"
            }
            filePart = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody(mimeType.toMediaTypeOrNull()))
        }

        RetrofitClient.instance.sendChatMessage(actReq, sIdReq, tTypeReq, tIdReq, bodyReq, roleReq, filePart)
            .enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    if (response.isSuccessful && response.body()?.status == "success") {
                        etMessage.text.clear()
                        // التحديث الفوري للشاشة
                        loadMessages(scrollToBottom = true)
                        loadChatList()
                    } else {
                        Toast.makeText(this@MessagesActivity, "فشل الإرسال", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    Toast.makeText(this@MessagesActivity, "خطأ بالاتصال بالسيرفر", Toast.LENGTH_SHORT).show()
                }
            })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val file = File(cacheDir, "attachment_${System.currentTimeMillis()}")
                        file.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                            sendMsg("ملف مرفق 📁", file)
                        }
                    }
                } catch (e: Exception) { Toast.makeText(this, "فشل معالجة الملف", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        chatAdapter?.releasePlayer()
        pusher?.disconnect()
        mediaRecorder?.release()
    }
}

class ChatListAdapter(private var chats: List<ChatEntity>, private val onChatClick: (ChatEntity) -> Unit) : RecyclerView.Adapter<ChatListAdapter.ChatVH>() {
    class ChatVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvIcon: TextView = v.findViewById(R.id.tvChatIcon)
        val cvIcon: CardView = v.findViewById(R.id.cvIcon)
        val tvName: TextView = v.findViewById(R.id.tvChatName)
        val tvLastMessage: TextView = v.findViewById(R.id.tvLastMessage)
        val tvLastMsgTime: TextView = v.findViewById(R.id.tvLastMsgTime)
        val tvUnreadBadge: TextView = v.findViewById(R.id.tvUnreadBadge)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatVH = ChatVH(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_list, parent, false))
    override fun onBindViewHolder(holder: ChatVH, position: Int) {
        val chat = chats[position]
        holder.tvName.text = chat.name
        holder.tvLastMessage.text = chat.lastMessage ?: "انقر للبدء..."

        val timeFormat = chat.lastMsgTime?.let {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
                sdf.parse(it)?.let { date -> SimpleDateFormat("hh:mm a", Locale("ar")).format(date) }
            } catch (e: Exception) { "" }
        } ?: ""
        holder.tvLastMsgTime.text = timeFormat

        if (chat.unreadCount > 0) {
            holder.tvUnreadBadge.visibility = View.VISIBLE
            holder.tvUnreadBadge.text = chat.unreadCount.toString()
            holder.tvLastMsgTime.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.error_red))
        } else {
            holder.tvUnreadBadge.visibility = View.GONE
            holder.tvLastMsgTime.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.ink_muted))
        }

        holder.cvIcon.setCardBackgroundColor(if (chat.type == "group") Color.parseColor("#2F358F") else Color.parseColor("#F7A61B"))
        holder.tvIcon.text = if (chat.type == "group") "📚" else (chat.name?.firstOrNull()?.toString()?.uppercase() ?: "👤")

        holder.itemView.setOnClickListener { onChatClick(chat) }
    }
    override fun getItemCount() = chats.size
}

class AdvancedChatAdapter(
    private var list: List<ChatMessageData>,
    private val currentUserId: Int,
    private val currentUserRole: String,
    private val isGroup: Boolean
) : RecyclerView.Adapter<AdvancedChatAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val text: TextView = v.findViewById(R.id.tvMessageText)
        val time: TextView = v.findViewById(R.id.tvTime)
        val senderNameRole: TextView? = v.findViewById(R.id.tvSenderNameRole)
        val audioPlayer: View? = v.findViewById(R.id.layoutAudioPlayer)
        val btnPlayPause: ImageButton? = v.findViewById(R.id.btnPlayPause)
        val audioSeekBar: SeekBar? = v.findViewById(R.id.audioSeekBar)
        val tvAudioTime: TextView? = v.findViewById(R.id.tvAudioTime)
        val btnPlaybackSpeed: TextView? = v.findViewById(R.id.btnPlaybackSpeed)
    }

    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPosition: Int = -1
    private var currentPlaybackSpeed: Float = 1.0f
    private val handler = Handler(Looper.getMainLooper())
    private var updateSeekBarTask: Runnable? = null

    fun updateList(newList: List<ChatMessageData>) {
        this.list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(if (viewType == 1) R.layout.item_message_sent else R.layout.item_message_received, parent, false))

    override fun getItemViewType(position: Int): Int {
        val msg = list[position]
        // التحقق من المعرف (ID) بالإضافة إلى دور المستخدم (Role)
        return if (msg.senderId == currentUserId && msg.senderRole == currentUserRole) 1 else 0
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = list[position]
        val isMine = (msg.senderId == currentUserId && msg.senderRole == currentUserRole)

        val timeFormat = msg.createdAt?.let {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
                sdf.parse(it)?.let { date -> SimpleDateFormat("hh:mm a", Locale("ar")).format(date) }
            } catch (e: Exception) { it }
        } ?: ""
        holder.time.text = timeFormat

        if (!isMine && isGroup) {
            holder.senderNameRole?.visibility = View.VISIBLE
            val roleLabel = when (msg.senderRole) { "teacher" -> "أستاذ"; "student" -> "طالب"; else -> "إدارة" }
            val nm = msg.senderName?.takeIf { it.isNotBlank() && it != "null" }
            holder.senderNameRole?.text = if (nm != null) "$nm ($roleLabel)" else roleLabel
        } else holder.senderNameRole?.visibility = View.GONE

        if (!msg.attachmentPath.isNullOrEmpty()) {
            val path = msg.attachmentPath!!
            if (msg.attachmentType == "audio") {
                holder.audioPlayer?.visibility = View.VISIBLE
                holder.text.visibility = if (msg.body.isNullOrEmpty()) View.GONE else View.VISIBLE
                holder.text.text = msg.body
                holder.text.setTextColor(if (isMine) Color.WHITE else ContextCompat.getColor(holder.itemView.context, R.color.ink))
                holder.text.setOnClickListener(null)

                setupAudioPlayer(holder, path, position)
            } else {
                holder.audioPlayer?.visibility = View.GONE
                holder.text.visibility = View.VISIBLE
                val icon = when (msg.attachmentType) {
                    "image" -> "🖼️ صورة"
                    "video" -> "🎬 فيديو"
                    else -> "📁 ملف مرفق"
                }
                holder.text.text = "$icon\n${msg.body ?: ""}"
                holder.text.setTextColor(if (isMine) Color.WHITE else ContextCompat.getColor(holder.itemView.context, R.color.ink))
                holder.text.setOnClickListener {
                    val fullUrl = if (path.startsWith("http")) path else RetrofitClient.BASE_URL + path
                    holder.itemView.context.startActivity(Intent(holder.itemView.context, MediaViewerActivity::class.java).apply {
                        putExtra("FILE_URL", fullUrl)
                        putExtra("FILE_TYPE", msg.attachmentType ?: "document")
                    })
                }
            }
        } else {
            holder.audioPlayer?.visibility = View.GONE
            holder.text.visibility = View.VISIBLE
            holder.text.text = msg.body
            holder.text.setTextColor(if (isMine) Color.WHITE else ContextCompat.getColor(holder.itemView.context, R.color.ink))
            holder.text.setOnClickListener(null)
        }
    }

    private fun setupAudioPlayer(holder: VH, path: String, position: Int) {
        val fullUrl = if (path.startsWith("http")) path else RetrofitClient.BASE_URL + path
        
        // Reset UI if not playing
        if (currentlyPlayingPosition != position) {
            holder.btnPlayPause?.setImageResource(android.R.drawable.ic_media_play)
            holder.audioSeekBar?.progress = 0
            holder.tvAudioTime?.text = "00:00"
            holder.btnPlaybackSpeed?.text = "1x"
        } else {
            holder.btnPlayPause?.setImageResource(if (mediaPlayer?.isPlaying == true) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            holder.btnPlaybackSpeed?.text = "${currentPlaybackSpeed.toString().replace(".0", "")}x"
            startSeekBarUpdate(holder)
        }

        holder.btnPlayPause?.setOnClickListener {
            if (currentlyPlayingPosition == position) {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                    holder.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                    stopSeekBarUpdate()
                } else {
                    mediaPlayer?.start()
                    holder.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                    startSeekBarUpdate(holder)
                }
            } else {
                playAudio(holder, fullUrl, position)
            }
        }

        holder.audioSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && currentlyPlayingPosition == position) {
                    mediaPlayer?.seekTo(progress)
                    holder.tvAudioTime?.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        holder.btnPlaybackSpeed?.setOnClickListener {
            currentPlaybackSpeed = when (currentPlaybackSpeed) {
                1.0f -> 1.5f
                1.5f -> 2.0f
                else -> 1.0f
            }
            holder.btnPlaybackSpeed.text = "${currentPlaybackSpeed.toString().replace(".0", "")}x"
            if (currentlyPlayingPosition == position && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        val params = it.playbackParams
                        params.speed = currentPlaybackSpeed
                        it.playbackParams = params
                    }
                }
            }
        }
    }

    private fun playAudio(holder: VH, url: String, position: Int) {
        stopAudio()
        
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(url)
            prepareAsync()
            setOnPreparedListener {
                currentlyPlayingPosition = position
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val params = playbackParams
                    params.speed = currentPlaybackSpeed
                    playbackParams = params
                }
                start()
                holder.audioSeekBar?.max = duration
                holder.btnPlayPause?.setImageResource(android.R.drawable.ic_media_pause)
                startSeekBarUpdate(holder)
            }
            setOnCompletionListener {
                stopAudio()
                notifyItemChanged(position)
            }
            setOnErrorListener { _, _, _ ->
                stopAudio()
                false
            }
        }
    }

    private fun stopAudio() {
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.release()
        mediaPlayer = null
        val oldPos = currentlyPlayingPosition
        currentlyPlayingPosition = -1
        if (oldPos != -1) notifyItemChanged(oldPos)
    }

    private fun startSeekBarUpdate(holder: VH) {
        stopSeekBarUpdate()
        updateSeekBarTask = object : Runnable {
            override fun run() {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        val currentPos = it.currentPosition
                        holder.audioSeekBar?.progress = currentPos
                        holder.tvAudioTime?.text = formatTime(currentPos)
                        handler.postDelayed(this, 100)
                    }
                }
            }
        }
        handler.post(updateSeekBarTask!!)
    }

    private fun stopSeekBarUpdate() {
        updateSeekBarTask?.let { handler.removeCallbacks(it) }
    }

    private fun formatTime(ms: Int): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun releasePlayer() {
        stopAudio()
    }
    override fun getItemCount() = list.size
}