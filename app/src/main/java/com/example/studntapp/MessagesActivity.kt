package com.example.studntapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
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

    private lateinit var chatListAdapter: ChatListAdapter
    private var showingArchived = false
    private var currentFilter = "all" // all | unread | groups | list:<name>
    private lateinit var llChatFilters: LinearLayout

    // محرك WebSockets اللحظي
    private var pusher: Pusher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)
        // قائمة المحادثات تستخدم شريط الهيكل الموحّد (كباقي الشاشات). وعند فتح
        // غرفة المحادثة نُخفي شريط الهيكل ليظهر شريط الغرفة الخاص (رجوع + اسم).
        supportActionBar?.title = "الرسائل والدردشة"

        // إخفاء الشريط السفلي للتنقّل داخل شاشة المحادثات (مثل تيليجرام/واتساب)،
        // وإلغاء الحشوة السفلية المخصّصة له حتى لا يرتفع شريط الكتابة بعيداً عن أسفل الشاشة.
        findViewById<View?>(R.id.barBackground)?.visibility = View.GONE
        findViewById<View?>(R.id.activity_content)?.let { c ->
            c.setPadding(c.paddingLeft, c.paddingTop, c.paddingRight, 0)
        }

        // رفع شريط الكتابة فوق لوحة المفاتيح (مع وضع Edge-to-edge): نضيف هامشاً سفلياً
        // بمقدار ارتفاع الكيبورد أو شريط التنقّل أيهما أكبر.
        findViewById<View?>(R.id.layoutChatRoom)?.let { room ->
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(room) { v, insets ->
                val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
                val navBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, maxOf(ime, navBar))
                insets
            }
        }

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

        chatListAdapter = ChatListAdapter(
            ctx = this,
            onChatClick = { chat -> openChatRoom(chat) },
            onHeaderClick = { toggleArchiveView() },
            onAddToList = { name -> showBulkAddDialog(name) },
            onAction = { chat, action -> handleChatAction(chat, action) }
        )
        rvChatList.adapter = chatListAdapter
        attachChatSwipe()

        llChatFilters = findViewById(R.id.llChatFilters)
        buildFilterChips()

        requestAudioPermission()

        etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // فارغ: بار الكتابة ممتد بلا زر إرسال. عند الكتابة: يظهر زر الإرسال بشكل ناعم.
                val hasText = !s.isNullOrBlank()
                animateSendButton(hasText)
                btnSend.visibility = View.VISIBLE
                btnRecord.visibility = View.GONE
                btnClearText.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // الحالة الابتدائية لزر الإرسال (مخفي وناعم)
        btnActionContainer.alpha = 0f
        btnActionContainer.scaleX = 0.6f
        btnActionContainer.scaleY = 0.6f
        btnActionContainer.visibility = View.GONE

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

        btnToggleSearchMessages.setOnClickListener { toggleMessageSearch() }

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

    private fun filterChatList(query: String) { refreshChatRows() }

    /** بناء صفوف القائمة: رأس الأرشيف + المحادثات (المثبتة بالأعلى) مع احترام البحث والأرشفة. */
    private fun refreshChatRows() {
        val q = etSearchChats.text.toString().lowercase()
        val visible = allChatsList.filter { c ->
            val key = ChatPrefs.keyOf(c)
            val arch = ChatPrefs.isArchived(this, key)
            (if (showingArchived) arch else !arch) &&
                (q.isEmpty() || (c.name?.lowercase()?.contains(q) == true)) &&
                matchesFilter(c, key)
        }
        val sorted = if (showingArchived) visible
            else visible.sortedWith(compareByDescending<ChatEntity> { ChatPrefs.isPinned(this, ChatPrefs.keyOf(it)) })

        val archivedCount = allChatsList.count { ChatPrefs.isArchived(this, ChatPrefs.keyOf(it)) }
        val rows = mutableListOf<ChatRow>()
        if (showingArchived) rows.add(ChatRow.Header(archivedCount, true))
        else if (archivedCount > 0) rows.add(ChatRow.Header(archivedCount, false))
        // داخل قائمة مخصّصة: صف لإضافة محادثات للقائمة
        if (!showingArchived && currentFilter.startsWith("list:"))
            rows.add(ChatRow.AddToList(currentFilter.removePrefix("list:")))
        sorted.forEach { rows.add(ChatRow.Item(it)) }
        chatListAdapter.submit(rows)
    }

    private fun toggleArchiveView() {
        showingArchived = !showingArchived
        etSearchChats.setText("")
        refreshChatRows()
        // أنميشن دخول/خروج من الأرشيف (انزلاق + تلاشٍ باتجاه مختلف لكل حالة).
        val dir = if (showingArchived) 1f else -1f
        val dx = rvChatList.width.toFloat().coerceAtLeast(220f) * 0.28f * dir
        rvChatList.alpha = 0f
        rvChatList.translationX = dx
        rvChatList.animate().alpha(1f).translationX(0f).setDuration(260)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    /** أرشفة مع انبثاق سفلي للتراجع لمدة 5 ثوانٍ. */
    private fun archiveWithUndo(chat: ChatEntity) {
        val key = ChatPrefs.keyOf(chat)
        ChatPrefs.setArchived(this, key, true)
        refreshChatRows()
        Snackbar.make(findViewById(R.id.layoutChatList), "تم نقل \"${chat.name ?: ""}\" للأرشيف", 5000)
            .setAction("تراجع") {
                ChatPrefs.setArchived(this, key, false)
                refreshChatRows()
            }
            .setActionTextColor(Color.parseColor("#F7A61B"))
            .show()
    }

    private fun matchesFilter(c: ChatEntity, key: String): Boolean = when {
        currentFilter == "all" -> true
        currentFilter == "unread" -> ChatPrefs.effectiveUnread(this, c) > 0
        currentFilter == "groups" -> c.type == "group"
        currentFilter == "chats" -> c.type != "group"
        currentFilter.startsWith("list:") -> ChatPrefs.isInList(this, currentFilter.removePrefix("list:"), key)
        else -> true
    }

    /** بناء شريط الفلاتر: الكل / غير مقروءة / المجموعات / القوائم المخصّصة + زر إنشاء. */
    private fun buildFilterChips() {
        llChatFilters.removeAllViews()
        val chips = mutableListOf<Pair<String, String>>(
            "all" to "الكل",
            "unread" to "غير مقروءة",
            "chats" to "المحادثات",
            "groups" to "المجموعات"
        )
        ChatPrefs.listNames(this).forEach { chips.add("list:$it" to it) }

        for ((value, label) in chips) {
            llChatFilters.addView(makeChip(label, value == currentFilter, onClick = {
                currentFilter = value
                buildFilterChips()
                refreshChatRows()
            }, onLong = if (value.startsWith("list:")) ({ confirmDeleteList(value.removePrefix("list:")) }) else null))
        }
        // زر إنشاء قائمة
        llChatFilters.addView(makeChip("＋ قائمة", false, onClick = { showCreateListDialog() }, onLong = null))
    }

    private fun makeChip(label: String, selected: Boolean, onClick: () -> Unit, onLong: (() -> Unit)?): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.textSize = 13f
        tv.setPadding(40, 18, 40, 18)
        tv.setBackgroundResource(if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
        tv.setTextColor(if (selected) Color.WHITE else Color.parseColor("#2F358F"))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.marginEnd = 16
        tv.layoutParams = lp
        tv.setOnClickListener { onClick() }
        if (onLong != null) tv.setOnLongClickListener { onLong(); true }
        return tv
    }

    private fun showCreateListDialog() {
        val input = EditText(this)
        input.hint = "اسم القائمة"
        AlertDialog.Builder(this)
            .setTitle("إنشاء قائمة جديدة")
            .setView(input)
            .setPositiveButton("إنشاء") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    ChatPrefs.createList(this, name)
                    currentFilter = "list:$name"
                    buildFilterChips()
                    refreshChatRows()
                    Toast.makeText(this, "أضف محادثات للقائمة بالضغط المطوّل عليها", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun confirmDeleteList(name: String) {
        AlertDialog.Builder(this)
            .setTitle("حذف القائمة \"$name\"؟")
            .setPositiveButton("حذف") { _, _ ->
                ChatPrefs.deleteList(this, name)
                if (currentFilter == "list:$name") currentFilter = "all"
                buildFilterChips()
                refreshChatRows()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    /** اختيار متعدّد لكل المحادثات لإضافتها/إزالتها من قائمة محدّدة. */
    private fun showBulkAddDialog(name: String) {
        val chats = allChatsList.filter { !ChatPrefs.isArchived(this, ChatPrefs.keyOf(it)) }
        if (chats.isEmpty()) { Toast.makeText(this, "لا توجد محادثات", Toast.LENGTH_SHORT).show(); return }
        val labels = chats.map { it.name ?: "—" }.toTypedArray()
        val checked = BooleanArray(chats.size) { ChatPrefs.isInList(this, name, ChatPrefs.keyOf(chats[it])) }
        AlertDialog.Builder(this)
            .setTitle("إضافة محادثات إلى \"$name\"")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val key = ChatPrefs.keyOf(chats[which])
                if (ChatPrefs.isInList(this, name, key) != isChecked)
                    ChatPrefs.toggleListMember(this, name, key)
            }
            .setPositiveButton("تم") { _, _ -> refreshChatRows() }
            .show()
    }

    private fun showListPicker(chat: ChatEntity) {
        val names = ChatPrefs.listNames(this)
        if (names.isEmpty()) { showCreateListDialog(); return }
        val key = ChatPrefs.keyOf(chat)
        val checked = BooleanArray(names.size) { ChatPrefs.isInList(this, names[it], key) }
        AlertDialog.Builder(this)
            .setTitle("إضافة إلى قائمة")
            .setMultiChoiceItems(names.toTypedArray(), checked) { _, which, isChecked ->
                if (ChatPrefs.isInList(this, names[which], key) != isChecked)
                    ChatPrefs.toggleListMember(this, names[which], key)
            }
            .setPositiveButton("تم") { _, _ -> refreshChatRows() }
            .show()
    }

    private fun handleChatAction(chat: ChatEntity, action: String) {
        val key = ChatPrefs.keyOf(chat)
        when (action) {
            "archive" -> { archiveWithUndo(chat); return }
            "unarchive" -> { ChatPrefs.setArchived(this, key, false); Toast.makeText(this, "تم الإخراج من الأرشيف", Toast.LENGTH_SHORT).show() }
            "star" -> ChatPrefs.toggleStar(this, key)
            "pin" -> ChatPrefs.togglePin(this, key)
            "read" -> ChatPrefs.setRead(this, key)
            "unread" -> ChatPrefs.setUnread(this, key)
            "lists" -> { showListPicker(chat); return }
        }
        refreshChatRows()
    }

    private fun attachChatSwipe() {
        val cb = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int =
                if (chatListAdapter.isItem(vh.bindingAdapterPosition))
                    makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
                else 0

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            // إبطاء الإيماءة: عتبة أكبر وسرعة إفلات أعلى = إحساس أنعم وأقل حدّة.
            override fun getSwipeThreshold(vh: RecyclerView.ViewHolder) = 0.45f
            override fun getSwipeEscapeVelocity(defaultValue: Float) = defaultValue * 4f
            override fun getSwipeVelocityThreshold(defaultValue: Float) = defaultValue * 0.6f
            // مدّة حركة العودة/الإزاحة (أنعم بدل القفز الفوري).
            override fun getAnimationDuration(rv: RecyclerView, animType: Int, animateDx: Float, animateDy: Float) = 320L

            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val pos = vh.bindingAdapterPosition
                val chat = chatListAdapter.chatAt(pos)
                if (chat == null) { refreshChatRows(); return }
                val key = ChatPrefs.keyOf(chat)
                if (dir == ItemTouchHelper.LEFT) {
                    // سحب لليسار: تبديل حالة القراءة
                    if (ChatPrefs.effectiveUnread(this@MessagesActivity, chat) > 0) ChatPrefs.setRead(this@MessagesActivity, key)
                    else ChatPrefs.setUnread(this@MessagesActivity, key)
                    // إن كان فلتر "غير مقروءة" قد يختفي العنصر → أعِد البناء، وإلا حرّكه للخلف بنعومة
                    if (currentFilter == "unread") refreshChatRows()
                    else chatListAdapter.notifyItemChanged(pos)
                } else {
                    // سحب لليمين: أرشفة (مع تراجع)، أو إخراج من الأرشيف داخل وضع الأرشيف
                    if (showingArchived) { ChatPrefs.setArchived(this@MessagesActivity, key, false); refreshChatRows() }
                    else archiveWithUndo(chat)
                }
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                     dX: Float, dY: Float, state: Int, active: Boolean) {
                val item = vh.itemView
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                val tp = Paint(Paint.ANTI_ALIAS_FLAG)
                tp.color = Color.WHITE
                tp.textSize = 42f
                tp.isFakeBoldText = true
                val cy = (item.top + item.bottom) / 2f + 15f

                if (dX > 0) {
                    // أرشفة
                    p.color = Color.parseColor("#2F358F")
                    c.drawRect(item.left.toFloat(), item.top.toFloat(), item.left + dX, item.bottom.toFloat(), p)
                    val label = if (showingArchived) "↩  إخراج" else "🗄  أرشفة"
                    tp.textAlign = Paint.Align.LEFT
                    c.drawText(label, item.left + 48f, cy, tp)
                } else if (dX < 0) {
                    // تبديل القراءة
                    val chat = chatListAdapter.chatAt(vh.bindingAdapterPosition)
                    val unread = chat != null && ChatPrefs.effectiveUnread(this@MessagesActivity, chat) > 0
                    p.color = Color.parseColor("#2E7D32")
                    c.drawRect(item.right + dX, item.top.toFloat(), item.right.toFloat(), item.bottom.toFloat(), p)
                    val label = if (unread) "تعليم كمقروء  ✓" else "تعليم كغير مقروء  ●"
                    tp.textAlign = Paint.Align.RIGHT
                    c.drawText(label, item.right - 48f, cy, tp)
                }
                super.onChildDraw(c, rv, vh, dX, dY, state, active)
            }
        }
        ItemTouchHelper(cb).attachToRecyclerView(rvChatList)
    }

    private fun openChatRoom(chat: ChatEntity) {
        ChatPrefs.clearRead(this, ChatPrefs.keyOf(chat))
        openChatDirect(chat.id, chat.type ?: "user", chat.name)
    }

    /** إظهار/إخفاء زر الإرسال بحركة ناعمة (تلاشٍ + تكبير) بدل الظهور المفاجئ. */
    private fun animateSendButton(show: Boolean) {
        // تجنّب إعادة تشغيل الحركة على كل ضغطة حرف
        if (show && btnActionContainer.visibility == View.VISIBLE && btnActionContainer.alpha == 1f) return
        if (!show && btnActionContainer.visibility == View.GONE) return

        btnActionContainer.animate().cancel()
        if (show) {
            btnActionContainer.visibility = View.VISIBLE
            btnActionContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction(null)
                .start()
        } else {
            btnActionContainer.animate()
                .alpha(0f)
                .scaleX(0.6f)
                .scaleY(0.6f)
                .setDuration(150L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { btnActionContainer.visibility = View.GONE }
                .start()
        }
    }

    /** فتح محادثة مباشرةً بمعرّف ونوع واسم — يُستخدم من القائمة ومن الإشعارات. */
    private fun openChatDirect(targetId: Int, targetType: String, targetName: String?) {
        currentTargetType = targetType
        currentTargetId = targetId
        isGroupChat = (currentTargetType == "group")
        tvRoomName.text = targetName

        // داخل الغرفة نُخفي حاوية شريط الهيكل كاملةً (وليس الـToolbar فقط)، حتى لا تبقى
        // حشوة شريط الحالة كمساحة فارغة فوق رأس المحادثة.
        supportActionBar?.hide()
        findViewById<View?>(R.id.appbar)?.visibility = View.GONE
        layoutChatList.visibility = View.GONE
        layoutChatRoom.visibility = View.VISIBLE

        // أيقونة جهة الاتصال (حرف أول) + حالة + إظهار زر الإرسال.
        findViewById<TextView?>(R.id.tvRoomAvatar)?.text =
            if (isGroupChat) "👥" else (targetName?.trim()?.firstOrNull()?.toString()?.uppercase() ?: "👤")
        findViewById<TextView?>(R.id.tvRoomStatus)?.text = if (isGroupChat) "مجموعة" else "محادثة خاصة"
        // الحالة الابتدائية: لا زر إرسال (الحقل فارغ) ليمتدّ بار الكتابة.
        btnActionContainer.animate().cancel()
        btnActionContainer.alpha = 0f
        btnActionContainer.scaleX = 0.6f
        btnActionContainer.scaleY = 0.6f
        btnActionContainer.visibility = View.GONE
        btnSend.visibility = View.GONE
        btnRecord.visibility = View.GONE

        // تأثير حركي لطيف عند الدخول للمحادثة.
        layoutChatRoom.alpha = 0f
        layoutChatRoom.translationX = layoutChatRoom.width.toFloat().coerceAtLeast(200f)
        layoutChatRoom.animate().alpha(1f).translationX(0f).setDuration(260).start()

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

    /** إظهار/إخفاء شريط البحث داخل المحادثة بحركة لطيفة. */
    private fun toggleMessageSearch() {
        val card = findViewById<View>(R.id.cardSearchMessages)
        if (card.visibility == View.VISIBLE) {
            card.animate().alpha(0f).setDuration(160).withEndAction {
                card.visibility = View.GONE
                etSearchMessages.setText("")
            }.start()
        } else {
            card.alpha = 0f
            card.visibility = View.VISIBLE
            card.animate().alpha(1f).setDuration(220).start()
            etSearchMessages.requestFocus()
        }
    }

    private fun closeChatRoom() {
        val room = layoutChatRoom
        // تأثير خروج انزلاقي ناعم ثم العودة للقائمة.
        room.animate()
            .alpha(0f)
            .translationX(room.width.toFloat().coerceAtLeast(200f))
            .setDuration(220)
            .withEndAction {
                pusher?.disconnect()
                pusher = null
                etSearchMessages.setText("")
                findViewById<View?>(R.id.cardSearchMessages)?.visibility = View.GONE
                room.visibility = View.GONE
                room.translationX = 0f
                room.alpha = 1f
                layoutChatList.visibility = View.VISIBLE
                // العودة للقائمة: نُظهر شريط الهيكل الموحّد مجدداً.
                findViewById<View?>(R.id.appbar)?.visibility = View.VISIBLE
                supportActionBar?.show()
                chatAdapter = null
                loadChatList()
            }
            .start()
    }

    override fun onBackPressed() {
        val searchCard = findViewById<View?>(R.id.cardSearchMessages)
        when {
            searchCard?.visibility == View.VISIBLE -> toggleMessageSearch() // إيقاف وضع البحث أولاً
            layoutChatRoom.visibility == View.VISIBLE -> closeChatRoom()
            showingArchived -> toggleArchiveView() // الخروج من وضع الأرشيف أولاً
            else -> super.onBackPressed()
        }
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

private fun themePrimary(ctx: Context): Int {
    val tv = android.util.TypedValue()
    ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
    return tv.data
}

sealed class ChatRow {
    data class Header(val archivedCount: Int, val archiveMode: Boolean) : ChatRow()
    data class AddToList(val listName: String) : ChatRow()
    data class Item(val chat: ChatEntity) : ChatRow()
}

class ChatListAdapter(
    private val ctx: Context,
    private val onChatClick: (ChatEntity) -> Unit,
    private val onHeaderClick: () -> Unit,
    private val onAddToList: (String) -> Unit,
    private val onAction: (ChatEntity, String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<ChatRow> = emptyList()

    fun submit(newRows: List<ChatRow>) { rows = newRows; notifyDataSetChanged() }
    fun isItem(pos: Int) = pos in rows.indices && rows[pos] is ChatRow.Item
    fun chatAt(pos: Int): ChatEntity? = (rows.getOrNull(pos) as? ChatRow.Item)?.chat

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is ChatRow.Item -> 1
        else -> 0 // Header + AddToList يستخدمان نفس تخطيط الرأس
    }
    override fun getItemCount() = rows.size

    class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v.findViewById(R.id.tvArchiveHeader)
    }
    class ChatVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvIcon: TextView = v.findViewById(R.id.tvChatIcon)
        val cvIcon: CardView = v.findViewById(R.id.cvIcon)
        val tvName: TextView = v.findViewById(R.id.tvChatName)
        val tvLastMessage: TextView = v.findViewById(R.id.tvLastMessage)
        val tvLastMsgTime: TextView = v.findViewById(R.id.tvLastMsgTime)
        val tvUnreadBadge: TextView = v.findViewById(R.id.tvUnreadBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == 0) HeaderVH(inf.inflate(R.layout.item_chat_header, parent, false))
        else ChatVH(inf.inflate(R.layout.item_chat_list, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is ChatRow.Header -> {
                val h = holder as HeaderVH
                val pc = themePrimary(h.tv.context)
                if (row.archiveMode) {
                    h.tv.text = "رجوع للمحادثات"
                    h.tv.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(h.tv.context, R.color.ink_muted))
                    h.tv.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_chevron_right, 0, 0, 0)
                } else {
                    h.tv.text = "الأرشيف (${row.archivedCount})"
                    h.tv.backgroundTintList = android.content.res.ColorStateList.valueOf(pc)
                    h.tv.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_chevron_left, 0, 0, 0)
                }
                h.tv.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
                h.itemView.setOnClickListener { onHeaderClick() }
            }
            is ChatRow.AddToList -> {
                val h = holder as HeaderVH
                h.tv.text = "إضافة محادثات إلى \"${row.listName}\""
                h.tv.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#F7A61B"))
                h.tv.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
                h.itemView.setOnClickListener { onAddToList(row.listName) }
            }
            is ChatRow.Item -> bindChat(holder as ChatVH, row.chat)
        }
    }

    private fun bindChat(holder: ChatVH, chat: ChatEntity) {
        val key = ChatPrefs.keyOf(chat)
        val pinned = ChatPrefs.isPinned(ctx, key)
        val starred = ChatPrefs.isStarred(ctx, key)
        val archived = ChatPrefs.isArchived(ctx, key)
        val unread = ChatPrefs.effectiveUnread(ctx, chat)

        val pin = if (pinned) "📌 " else ""
        val star = if (starred) "  ⭐" else ""
        holder.tvName.text = "$pin${chat.name ?: ""}$star"
        holder.tvLastMessage.text = chat.lastMessage ?: "انقر للبدء..."

        val timeFormat = chat.lastMsgTime?.let {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
                sdf.parse(it)?.let { date -> SimpleDateFormat("hh:mm a", Locale("ar")).format(date) }
            } catch (e: Exception) { "" }
        } ?: ""
        holder.tvLastMsgTime.text = timeFormat

        if (unread > 0) {
            holder.tvUnreadBadge.visibility = View.VISIBLE
            holder.tvUnreadBadge.text = unread.toString()
            holder.tvLastMsgTime.setTextColor(ContextCompat.getColor(ctx, R.color.error_red))
            holder.tvName.setTypeface(null, Typeface.BOLD)
        } else {
            holder.tvUnreadBadge.visibility = View.GONE
            holder.tvLastMsgTime.setTextColor(ContextCompat.getColor(ctx, R.color.ink_muted))
            holder.tvName.setTypeface(null, Typeface.BOLD)
        }

        holder.cvIcon.setCardBackgroundColor(if (chat.type == "group") Color.parseColor("#2F358F") else Color.parseColor("#F7A61B"))
        holder.tvIcon.text = if (chat.type == "group") "📚" else (chat.name?.firstOrNull()?.toString()?.uppercase() ?: "👤")

        holder.itemView.setOnClickListener { onChatClick(chat) }
        holder.itemView.setOnLongClickListener {
            showMenu(holder.itemView, chat, pinned, starred, archived, unread)
            true
        }
    }

    private fun showMenu(anchor: View, chat: ChatEntity, pinned: Boolean, starred: Boolean, archived: Boolean, unread: Int) {
        val pm = PopupMenu(anchor.context, anchor)
        val m = pm.menu
        m.add(0, 1, 0, if (archived) "إلغاء الأرشفة" else "أرشفة")
        m.add(0, 2, 1, if (starred) "إزالة النجمة" else "تمييز بنجمة")
        m.add(0, 3, 2, if (pinned) "إلغاء التثبيت" else "تثبيت المحادثة")
        if (unread > 0) m.add(0, 4, 3, "تعليم كمقروءة")
        else m.add(0, 5, 3, "تعليم كغير مقروءة")
        m.add(0, 6, 4, "إضافة إلى قائمة")
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> onAction(chat, if (archived) "unarchive" else "archive")
                2 -> onAction(chat, "star")
                3 -> onAction(chat, "pin")
                4 -> onAction(chat, "read")
                5 -> onAction(chat, "unread")
                6 -> onAction(chat, "lists")
            }
            true
        }
        pm.show()
    }
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
        val dateSep: TextView? = v.findViewById(R.id.tvDateSeparator)
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

    /** مفتاح اليوم (yyyy-MM-dd) من طابع الوقت. */
    private fun dayKey(createdAt: String?): String? {
        if (createdAt.isNullOrBlank()) return null
        return try {
            val d = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).parse(createdAt)
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(d!!)
        } catch (e: Exception) {
            if (createdAt.length >= 10) createdAt.substring(0, 10) else null
        }
    }

    /** تسمية ودّية لليوم: اليوم / أمس / dd MMMM yyyy. */
    private fun dayLabel(dayKey: String): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(java.util.Date())
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(
            java.util.Date(System.currentTimeMillis() - 86_400_000L)
        )
        return when (dayKey) {
            today -> "اليوم"
            yesterday -> "أمس"
            else -> try {
                val d = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(dayKey)
                SimpleDateFormat("dd MMMM yyyy", Locale("ar")).format(d!!)
            } catch (e: Exception) { dayKey }
        }
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

        // فاصل التاريخ: يظهر عند أول رسالة أو عند تغيّر اليوم عن الرسالة السابقة.
        val thisDay = dayKey(msg.createdAt)
        val prevDay = if (position > 0) dayKey(list[position - 1].createdAt) else null
        holder.dateSep?.let { sep ->
            if (thisDay != null && thisDay != prevDay) {
                sep.visibility = View.VISIBLE
                sep.text = dayLabel(thisDay)
            } else sep.visibility = View.GONE
        }

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