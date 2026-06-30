package com.example.studntapp

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class StatementActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // نستخدم واجهة قائمة المواد لأنها تحتوي على RecyclerView جاهز
        setContentView(R.layout.activity_subjects)
        supportActionBar?.title = "كشف الحساب المالي"

        // كشف الحساب لا يحتاج شريط فرز المواد (نشطة/مكتملة) — نخفيه.
        findViewById<android.view.View?>(R.id.filterBar)?.visibility = android.view.View.GONE

        val rv = findViewById<RecyclerView>(R.id.rvSubjects)
        rv.layoutManager = LinearLayoutManager(this)

        // جلب معرف المستخدم (والذي يمثل معرف الطالب سواء كان الدخول كطالب أو كولي أمر)
        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        val studentId = prefs.getInt("USER_ID", 0)

        loadStatement(studentId, rv)
        
        val swipeRefresh = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)
        swipeRefresh?.setOnRefreshListener { loadStatement(studentId, rv) }
    }

    private fun loadStatement(studentId: Int, rv: RecyclerView) {
        RetrofitClient.instance.getStatement(studentId = studentId).enqueue(object : Callback<StatementResponse> {
            override fun onResponse(call: Call<StatementResponse>, response: Response<StatementResponse>) {
                findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                if (response.isSuccessful && response.body()?.status == "success") {
                    val transactions = response.body()?.data?.reversed() ?: emptyList()

                    if (transactions.isEmpty()) {
                        Toast.makeText(this@StatementActivity, "لا توجد حركات مالية مسجلة", Toast.LENGTH_LONG).show()
                    } else {
                        rv.adapter = StatementAdapter(transactions, this@StatementActivity)
                    }
                } else {
                    Toast.makeText(this@StatementActivity, response.body()?.status ?: "حدث خطأ في جلب البيانات", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<StatementResponse>, t: Throwable) {
                findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                Toast.makeText(this@StatementActivity, "تأكد من اتصالك بالإنترنت", Toast.LENGTH_SHORT).show()
            }
        })
    }
}

// ==========================================
// محول القائمة (تصميم بطاقة كشف الحساب البنكية)
// ==========================================
class StatementAdapter(private val list: List<TransactionData>, private val context: Context) : RecyclerView.Adapter<StatementAdapter.VH>() {
    class VH(val card: CardView, val tvDate: TextView, val tvDesc: TextView, val tvAmount: TextView, val tvBalance: TextView, val leftPanel: LinearLayout) : RecyclerView.ViewHolder(card)

    // لون الثيم الأساسي (يتغيّر حسب الباقة المختارة).
    private fun primary(ctx: Context): Int {
        val tv = android.util.TypedValue()
        ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
        return tv.data
    }
    private fun col(id: Int) = ContextCompat.getColor(context, id)

    /** تنسيق رقمي: نقطة كل 3 مراتب وفاصلة للكسور، مثل 10.000.000,00 */
    private fun fmt(v: Double): String {
        val sym = java.text.DecimalFormatSymbols(java.util.Locale.US).apply {
            groupingSeparator = '.'; decimalSeparator = ','
        }
        return java.text.DecimalFormat("#,##0.00", sym).format(v)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        // البطاقة الأساسية
        val card = CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(16, 12, 16, 12) }
            radius = 20f
            cardElevation = 4f
            setCardBackgroundColor(col(R.color.surface))
        }

        // الحاوية الرئيسية (أفقية لتقسيم الكرت إلى يمين ويسار)
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 10f
        }

        // ---------------- القسم الأيمن (تفاصيل العملية - 70%) ----------------
        val rightPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 7f)
            setPadding(30, 30, 20, 30)
        }

        val tvDate = TextView(context).apply { textSize = 12f; setTextColor(col(R.color.ink_muted)) }
        val tvDesc = TextView(context).apply { textSize = 15f; setTextColor(col(R.color.ink)); setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 10, 0, 15) }
        val tvAmount = TextView(context).apply { textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD) }

        rightPanel.addView(tvDate)
        rightPanel.addView(tvDesc)
        rightPanel.addView(tvAmount)

        // ---------------- القسم الأيسر (الرصيد المعزول - 30%) ----------------
        val leftPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3f)
            gravity = Gravity.CENTER
            setBackgroundColor(col(R.color.surface_alt))
        }

        val lblBalance = TextView(context).apply { text = "الرصيد"; textSize = 12f; setTextColor(col(R.color.ink_muted)); gravity = Gravity.CENTER; setPadding(0,0,0,5) }
        val tvBalance = TextView(context).apply { textSize = 16f; setTextColor(primary(context)); setTypeface(null, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER }

        leftPanel.addView(lblBalance)
        leftPanel.addView(tvBalance)

        // تجميع الأقسام
        mainLayout.addView(rightPanel)
        mainLayout.addView(leftPanel)
        card.addView(mainLayout)

        return VH(card, tvDate, tvDesc, tvAmount, tvBalance, leftPanel)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]

        // استخدام عامل الحماية (?.) تحسباً للقيم الفارغة من السيرفر
        holder.tvDate.text = item.date ?: "تاريخ غير محدد"
        holder.tvDesc.text = item.description ?: "عملية مالية"

        // تحديد نوع الحركة (دين أم دفعة) لتلوين البطاقة
        if (item.debit > 0) {
            holder.tvAmount.text = "مطلوب دفع: ${fmt(item.debit)}"
            holder.tvAmount.setTextColor(col(R.color.error_red)) // أحمر للمطلوب (متوافق مع الليلي)
            holder.leftPanel.setBackgroundColor(col(R.color.panel_debit)) // خلفية حمراء باهتة
        } else if (item.credit > 0) {
            holder.tvAmount.text = "تم دفع: ${fmt(item.credit)}"
            holder.tvAmount.setTextColor(col(R.color.success_green)) // أخضر للمدفوع (متوافق مع الليلي)
            holder.leftPanel.setBackgroundColor(col(R.color.panel_credit)) // خلفية خضراء باهتة
        } else {
            holder.tvAmount.text = "قيمة العملية: ${fmt(0.0)}"
            holder.tvAmount.setTextColor(col(R.color.ink_muted))
            holder.leftPanel.setBackgroundColor(col(R.color.surface_alt))
        }

        // عرض الرصيد الإجمالي التراكمي
        holder.tvBalance.text = fmt(item.balance ?: 0.0)

        // الضغط على الإيصال يفتح بطاقة بكل تفاصيله.
        holder.card.setOnClickListener { showReceiptDialog(item) }
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    /** بطاقة منبثقة أنيقة بكل معلومات الإيصال، متكيّفة مع الثيم. */
    private fun showReceiptDialog(item: TransactionData) {
        val isDebit = item.debit > 0
        val amountColor = if (isDebit) col(R.color.error_red) else col(R.color.success_green)
        val amountLabel = if (isDebit) "مطلوب دفع" else "تم دفع"
        val amountValue = if (isDebit) item.debit else item.credit

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(col(R.color.surface))
            setPadding(dp(22), dp(20), dp(22), dp(8))
        }
        layout.addView(TextView(context).apply {
            text = "تفاصيل الإيصال"; textSize = 19f
            setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(col(R.color.ink))
            setPadding(0, 0, 0, dp(14))
        })
        layout.addView(receiptRow("التاريخ", item.date ?: "—", col(R.color.ink)))
        layout.addView(receiptRow("البيان", item.description ?: "عملية مالية", col(R.color.ink)))
        layout.addView(receiptRow(amountLabel, fmt(amountValue), amountColor))
        layout.addView(receiptRow("الرصيد بعد العملية", fmt(item.balance ?: 0.0), primary(context)))

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setView(layout)
            .setPositiveButton("إغلاق", null)
            .create().apply {
                window?.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(22).toFloat(); setColor(col(R.color.surface))
                })
                show()
                getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(primary(context))
            }
    }

    /** صف: عنوان رمادي + قيمة، داخل خلفية ناعمة دائرية. */
    private fun receiptRow(label: String, value: String, valueColor: Int): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat(); setColor(col(R.color.surface_alt)); setStroke(dp(1), col(R.color.line))
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            addView(TextView(context).apply {
                text = label; textSize = 12f; setTextColor(col(R.color.ink_muted))
            })
            addView(TextView(context).apply {
                text = value; textSize = 16f; setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(valueColor); setPadding(0, dp(3), 0, 0)
            })
        }
    }

    override fun getItemCount() = list.size
}
