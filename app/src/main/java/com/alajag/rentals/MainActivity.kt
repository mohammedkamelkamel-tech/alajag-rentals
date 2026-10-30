package com.alajag.rentals

import android.app.*
import android.os.Bundle
import android.content.*
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.*

data class Tenant(val id: Long, val name: String, val phone: String, val type: String, val rent: Long, val notes: String, val active: Boolean)

class Db(ctx: Context) : SQLiteOpenHelper(ctx, "alajag_rentals.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE tenants(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,phone TEXT,type TEXT,rent INTEGER NOT NULL,notes TEXT,active INTEGER NOT NULL DEFAULT 1,created_at TEXT)")
        db.execSQL("CREATE TABLE ledger(id INTEGER PRIMARY KEY AUTOINCREMENT,tenant_id INTEGER NOT NULL,kind TEXT NOT NULL,period TEXT,amount INTEGER NOT NULL,receipt_no INTEGER,receiver TEXT,note TEXT,created_at TEXT NOT NULL,FOREIGN KEY(tenant_id) REFERENCES tenants(id))")
        db.execSQL("CREATE UNIQUE INDEX one_rent_per_period ON ledger(tenant_id,period) WHERE kind='RENT'")
        db.execSQL("CREATE TABLE counters(name TEXT PRIMARY KEY,value INTEGER NOT NULL)")
        db.execSQL("INSERT INTO counters(name,value) VALUES('receipt',0)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun tenants(): List<Tenant> {
        val out= mutableListOf<Tenant>()
        readableDatabase.rawQuery("SELECT * FROM tenants ORDER BY name",null).use { c ->
            while(c.moveToNext()) out.add(Tenant(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getString(5),c.getInt(6)==1))
        }; return out
    }
    fun addTenant(name:String,phone:String,type:String,rent:Long,notes:String) {
        val v=android.content.ContentValues().apply { put("name",name);put("phone",phone);put("type",type);put("rent",rent);put("notes",notes);put("active",1);put("created_at",now()) }
        writableDatabase.insertOrThrow("tenants",null,v)
    }
    fun updateTenant(t:Tenant,name:String,phone:String,type:String,rent:Long,notes:String) {
        val v=android.content.ContentValues().apply { put("name",name);put("phone",phone);put("type",type);put("rent",rent);put("notes",notes) }
        writableDatabase.update("tenants",v,"id=?", arrayOf(t.id.toString()))
    }
    fun collect(t:Tenant,amount:Long,receiver:String,note:String):Long {
        val db=writableDatabase; db.beginTransaction()
        try {
            db.execSQL("UPDATE counters SET value=value+1 WHERE name='receipt'")
            val receipt=android.database.DatabaseUtils.longForQuery(db,"SELECT value FROM counters WHERE name='receipt'",null)
            val v=android.content.ContentValues().apply { put("tenant_id",t.id);put("kind","PAYMENT");put("amount",amount);put("receipt_no",receipt);put("receiver",receiver);put("note",note);put("created_at",now()) }
            db.insertOrThrow("ledger",null,v); db.setTransactionSuccessful(); return receipt
        } finally { db.endTransaction() }
    }
    fun balance(id:Long):Long = android.database.DatabaseUtils.longForQuery(readableDatabase,"SELECT COALESCE(SUM(CASE WHEN kind IN ('RENT','WITHDRAWAL') THEN amount ELSE -amount END),0) FROM ledger WHERE tenant_id=?", arrayOf(id.toString()))
    fun due(period:String) {
        val db=writableDatabase
        for(t in tenants().filter{it.active}) {
            val exists=android.database.DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM ledger WHERE tenant_id=? AND kind='RENT' AND period=?", arrayOf(t.id.toString(),period))
            if(exists==0L) {
                val v=android.content.ContentValues().apply { put("tenant_id",t.id);put("kind","RENT");put("period",period);put("amount",t.rent);put("note","إيجار شهر $period");put("created_at",now()) }
                db.insertOrThrow("ledger",null,v)
            }
        }
    }
    fun statement(id:Long):String {
        val b=StringBuilder()
        readableDatabase.rawQuery("SELECT kind,period,amount,receipt_no,receiver,created_at,note FROM ledger WHERE tenant_id=? ORDER BY id",arrayOf(id.toString())).use { c ->
            while(c.moveToNext()) b.append(c.getString(5)).append(" | ").append(c.getString(0)).append(if(c.isNull(1)) "" else " "+c.getString(1)).append(" | ").append(c.getLong(2)).append(" ريال").append(if(c.isNull(3)) "" else " | إيصال "+c.getLong(3)).append(if(c.isNull(4)) "" else " | المستلم "+c.getString(4)).append("\\n")
        }
        b.append("\\nالرصيد المتبقي: ").append(balance(id)).append(" ريال")
        return b.toString()
    }
    companion object { fun now()=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(Date()) }
}

class MainActivity : Activity() {
    private lateinit var db:Db
    private lateinit var list:LinearLayout
    private lateinit var summary:TextView
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); db=Db(this)
        val prefs=getSharedPreferences("schedule",0)
        val cal=Calendar.getInstance()
        val month=String.format(Locale.US,"%04d-%02d",cal.get(Calendar.YEAR),cal.get(Calendar.MONTH)+1)
        val first=cal.get(Calendar.YEAR)*100+cal.get(Calendar.MONTH)+1
        val start=202611
        val last=prefs.getInt("last_due",0)
        if(first>=start && first>last) { if(cal.get(Calendar.DAY_OF_MONTH)==cal.getActualMaximum(Calendar.DAY_OF_MONTH)) { db.due(month); prefs.edit().putInt("last_due",first).apply() } }
        render()
    }
    private fun render() {
        val root=LinearLayout(this).apply { orientation=1; setPadding(24,24,24,24); layoutDirection=View.LAYOUT_DIRECTION_RTL; setBackgroundColor(Color.rgb(246,248,250)) }
        val title=TextView(this).apply { text="العجاج - الإيجارات";textSize=25f;setTextColor(Color.rgb(23,52,76));gravity=Gravity.CENTER;setPadding(4,16,4,20) }
        root.addView(title)
        summary=TextView(this).apply { textSize=16f;setPadding(12,12,12,12);setBackgroundColor(Color.WHITE) }; root.addView(summary)
        val buttons=LinearLayout(this).apply { orientation=0;gravity=Gravity.CENTER }
        buttons.addView(btn("إضافة مستأجر"){ tenantDialog(null) },LinearLayout.LayoutParams(0,-2,1f))
        buttons.addView(btn("نسخة احتياطية"){ backup() },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(buttons)
        val sc=ScrollView(this); list=LinearLayout(this).apply { orientation=1 };sc.addView(list);root.addView(sc,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root); refresh()
    }
    private fun btn(label:String,action:()->Unit)=Button(this).apply { text=label;setOnClickListener{action()} }
    private fun refresh() {
        list.removeAllViews(); val tenants=db.tenants()
        summary.text="عدد المستأجرين: ${tenants.size}    |    إجمالي المتبقي: ${tenants.sumOf{db.balance(it.id)}} ريال"
        tenants.forEach { t ->
            val card=LinearLayout(this).apply { orientation=1;setPadding(14,12,14,12);setBackgroundColor(Color.WHITE) }
            card.addView(TextView(this).apply { text="${t.name}   •   ${t.rent} ريال / ${t.type}";textSize=18f;setTextColor(Color.BLACK) })
            card.addView(TextView(this).apply { text="الهاتف: ${t.phone}    |    المتبقي: ${db.balance(t.id)} ريال";textSize=14f })
            val row=LinearLayout(this)
            row.addView(btn("تحصيل دفعة"){ paymentDialog(t) },LinearLayout.LayoutParams(0,-2,1f))
            row.addView(btn("كشف الحساب"){ share("كشف حساب ${t.name}",db.statement(t.id)) },LinearLayout.LayoutParams(0,-2,1f))
            row.addView(btn("تعديل"){ tenantDialog(t) },LinearLayout.LayoutParams(0,-2,1f))
            card.addView(row);list.addView(card)
            list.addView(View(this).apply { setBackgroundColor(Color.LTGRAY) },LinearLayout.LayoutParams(-1,1))
        }
    }
    private fun field(hint:String)=EditText(this).apply { this.hint=hint; textSize=16f }
    private fun tenantDialog(old:Tenant?) {
        val box=LinearLayout(this).apply { orientation=1;setPadding(20,4,20,4) }
        val name=field("اسم المستأجر").also{it.setText(old?.name ?: "")}
        val phone=field("رقم الهاتف").also{it.setText(old?.phone ?: "")}
        val type=field("نوع الإيجار: شهري / سنوي").also{it.setText(old?.type ?: "شهري")}
        val rent=field("مبلغ الإيجار بالأرقام").also{it.inputType=2;it.setText(old?.rent?.toString() ?: "")}
        val notes=field("ملاحظات").also{it.setText(old?.notes ?: "")}
        listOf(name,phone,type,rent,notes).forEach{box.addView(it)}
        AlertDialog.Builder(this).setTitle(if(old==null)"إضافة مستأجر" else "تعديل بيانات المستأجر").setView(box).setNegativeButton("إلغاء",null).setPositiveButton("حفظ"){_,_->
            val n=name.text.toString().trim();val amount=rent.text.toString().toLongOrNull()
            if(n.isEmpty()||amount==null||amount<0) { toast("أدخل الاسم ومبلغ إيجار صحيح");return@setPositiveButton }
            if(old==null) db.addTenant(n,phone.text.toString(),type.text.toString(),amount,notes.text.toString()) else db.updateTenant(old,n,phone.text.toString(),type.text.toString(),amount,notes.text.toString())
            refresh()
        }.show()
    }
    private fun paymentDialog(t:Tenant) {
        val box=LinearLayout(this).apply { orientation=1;setPadding(20,4,20,4) }
        val amount=field("المبلغ المستلم").also{it.inputType=2}
        val receiver=field("اسم المستلم")
        val note=field("ملاحظات")
        listOf(amount,receiver,note).forEach{box.addView(it)}
        AlertDialog.Builder(this).setTitle("تحصيل من ${t.name}").setView(box).setNegativeButton("إلغاء",null).setPositiveButton("تسجيل"){_,_->
            val a=amount.text.toString().toLongOrNull()
            if(a==null||a<=0){toast("أدخل مبلغاً صحيحاً");return@setPositiveButton}
            val no=db.collect(t,a,receiver.text.toString(),note.text.toString())
            share("إيصال تحصيل رقم $no","العجاج - الإيجارات\\nالمستأجر: ${t.name}\\nالمبلغ: $a ريال\\nالتاريخ: ${Db.now()}\\nالمستلم: ${receiver.text}\\nرقم الإيصال: $no")
            refresh()
        }.show()
    }
    private fun share(subject:String,body:String) {
        val i=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_SUBJECT,subject);putExtra(Intent.EXTRA_TEXT,body)}
        startActivity(Intent.createChooser(i,"إرسال عبر واتساب أو تطبيق آخر"))
    }
    private fun backup() {
        val i=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,"alajag-backup-${Db.now().replace(':','-')}.json")}
        startActivityForResult(i,81)
        toast("اختر مكان حفظ النسخة الاحتياطية")
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==81&&resultCode==RESULT_OK&&data?.data!=null) {
            try {
                val json=org.json.JSONObject().put("format","alajag-rentals-v1").put("exported_at",Db.now())
                val tenants=org.json.JSONArray()
                db.tenants().forEach{t->tenants.put(org.json.JSONObject().put("id",t.id).put("name",t.name).put("phone",t.phone).put("type",t.type).put("rent",t.rent).put("notes",t.notes).put("active",t.active))}
                json.put("tenants",tenants)
                contentResolver.openOutputStream(data.data!!)?.use{it.write(json.toString(2).toByteArray(Charsets.UTF_8))}
                toast("تم حفظ بيانات المستأجرين. استكمال النسخ الكامل قيد التطوير.")
            } catch(e:Exception){toast("تعذر إنشاء النسخة: ${e.message}")}
        }
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
