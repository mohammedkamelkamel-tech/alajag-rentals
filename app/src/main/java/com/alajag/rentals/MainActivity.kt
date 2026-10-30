package com.alajag.rentals

import android.app.*
import android.os.Bundle
import android.content.*
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private fun stamp():String=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(Date())
data class Tenant(val id:Long,val name:String,val phone:String,val rent:Long,val notes:String,val active:Boolean,val created:String)
data class LedgerRow(val id:Long,val kind:String,val period:String?,val amount:Long,val receipt:Long?,val receiver:String?,val note:String?,val created:String)

class RentalDb(ctx:Context):SQLiteOpenHelper(ctx,"alajag_rentals.db",null,3){
 override fun onCreate(d:SQLiteDatabase){
  d.execSQL("CREATE TABLE tenants(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,phone TEXT NOT NULL DEFAULT '',rent INTEGER NOT NULL CHECK(rent>0),notes TEXT NOT NULL DEFAULT '',active INTEGER NOT NULL DEFAULT 1,created_at TEXT NOT NULL)")
  d.execSQL("CREATE TABLE ledger(id INTEGER PRIMARY KEY AUTOINCREMENT,tenant_id INTEGER NOT NULL,kind TEXT NOT NULL,period TEXT,amount INTEGER NOT NULL CHECK(amount>0),receipt_no INTEGER,receiver TEXT,note TEXT,created_at TEXT NOT NULL,FOREIGN KEY(tenant_id) REFERENCES tenants(id))")
  d.execSQL("CREATE UNIQUE INDEX one_rent_per_period ON ledger(tenant_id,period) WHERE kind='RENT'")
  d.execSQL("CREATE UNIQUE INDEX unique_receipt_no ON ledger(receipt_no) WHERE receipt_no IS NOT NULL")
  d.execSQL("CREATE INDEX ledger_tenant_date ON ledger(tenant_id,created_at)")
  d.execSQL("CREATE TABLE counters(name TEXT PRIMARY KEY,value INTEGER NOT NULL)")
  d.execSQL("INSERT INTO counters(name,value) VALUES('receipt',0)")
  d.execSQL("CREATE TABLE app_settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
 }
 override fun onUpgrade(d:SQLiteDatabase,old:Int,now:Int){
  if(old<2)d.execSQL("CREATE TABLE IF NOT EXISTS app_settings(key TEXT PRIMARY KEY,value TEXT NOT NULL DEFAULT '')")
  if(old<3){d.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS unique_receipt_no ON ledger(receipt_no) WHERE receipt_no IS NOT NULL");d.execSQL("CREATE INDEX IF NOT EXISTS ledger_tenant_date ON ledger(tenant_id,created_at)")}
 }
 private fun values(block:android.content.ContentValues.()->Unit)=android.content.ContentValues().apply(block)
 fun tenants(search:String="",all:Boolean=false):List<Tenant>{
  val out=mutableListOf<Tenant>();val active=if(all)"1=1" else "active=1"
  readableDatabase.rawQuery("SELECT id,name,phone,rent,notes,active,created_at FROM tenants WHERE "+active+" AND (name LIKE ? OR phone LIKE ?) ORDER BY active DESC,name",arrayOf("%"+search+"%","%"+search+"%")).use{c->while(c.moveToNext())out.add(Tenant(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),c.getString(4),c.getInt(5)==1,c.getString(6)))}
  return out
 }
 fun addTenant(name:String,phone:String,rent:Long,notes:String){
  require(name.isNotBlank()){"اسم المستأجر مطلوب"};require(rent>0){"الإيجار يجب أن يكون أكبر من صفر"}
  writableDatabase.insertOrThrow("tenants",null,values{put("name",name.trim());put("phone",phone.trim());put("rent",rent);put("notes",notes.trim());put("active",1);put("created_at",stamp())})
 }
 fun editTenant(t:Tenant,name:String,phone:String,rent:Long,notes:String,active:Boolean){
  require(name.isNotBlank()){"اسم المستأجر مطلوب"};require(rent>0){"الإيجار يجب أن يكون أكبر من صفر"}
  writableDatabase.update("tenants",values{put("name",name.trim());put("phone",phone.trim());put("rent",rent);put("notes",notes.trim());put("active",if(active)1 else 0)},"id=?",arrayOf(t.id.toString()))
 }
 fun payment(t:Tenant,amount:Long,receiver:String,note:String):Long{
  require(amount>0){"المبلغ يجب أن يكون أكبر من صفر"};val d=writableDatabase;d.beginTransaction()
  try{d.execSQL("UPDATE counters SET value=value+1 WHERE name='receipt'");val no=android.database.DatabaseUtils.longForQuery(d,"SELECT value FROM counters WHERE name='receipt'",null)
   d.insertOrThrow("ledger",null,values{put("tenant_id",t.id);put("kind","PAYMENT");put("amount",amount);put("receipt_no",no);put("receiver",receiver);put("note",note);put("created_at",stamp())})
   d.setTransactionSuccessful();return no
  }finally{d.endTransaction()}
 }
 fun adjustment(t:Tenant,kind:String,amount:Long,note:String){require(kind=="CHARGE"||kind=="CREDIT");require(amount>0);writableDatabase.insertOrThrow("ledger",null,values{put("tenant_id",t.id);put("kind",kind);put("amount",amount);put("note",note);put("created_at",stamp())})}
 fun balance(id:Long)=android.database.DatabaseUtils.longForQuery(readableDatabase,"SELECT COALESCE(SUM(CASE WHEN kind IN ('RENT','CHARGE') THEN amount ELSE -amount END),0) FROM ledger WHERE tenant_id=?",arrayOf(id.toString()))
 fun generateDuePeriods(today:Calendar=Calendar.getInstance()){
  val min=Calendar.getInstance().apply{set(2026,Calendar.NOVEMBER,1,0,0,0);set(Calendar.MILLISECOND,0)}
  val end=Calendar.getInstance().apply{timeInMillis=today.timeInMillis;set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}
  tenants().forEach{t->
   val created=Calendar.getInstance().apply{time=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).parse(t.created)?:Date()}
   val month=if(created.after(min))Calendar.getInstance().apply{timeInMillis=created.timeInMillis;set(Calendar.DAY_OF_MONTH,1);set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)} else min.clone() as Calendar
   while(!month.after(end)){
    val due=month.clone() as Calendar;due.set(Calendar.DAY_OF_MONTH,due.getActualMaximum(Calendar.DAY_OF_MONTH))
    if(!due.after(end)){val period=String.format(Locale.US,"%04d-%02d",month.get(Calendar.YEAR),month.get(Calendar.MONTH)+1);insertRent(t,period,due)}
    month.add(Calendar.MONTH,1)
   }
  }
 }
 private fun insertRent(t:Tenant,period:String,due:Calendar){
  val d=writableDatabase;d.beginTransaction()
  try{val n=android.database.DatabaseUtils.longForQuery(d,"SELECT COUNT(*) FROM ledger WHERE tenant_id=? AND kind='RENT' AND period=?",arrayOf(t.id.toString(),period))
   if(n==0L){val date=String.format(Locale.US,"%04d-%02d-%02d 23:59:00",due.get(Calendar.YEAR),due.get(Calendar.MONTH)+1,due.get(Calendar.DAY_OF_MONTH));d.insertOrThrow("ledger",null,values{put("tenant_id",t.id);put("kind","RENT");put("period",period);put("amount",t.rent);put("note","إيجار شهر "+period);put("created_at",date)})}
   d.setTransactionSuccessful()
  }finally{d.endTransaction()}
 }
 fun rows(id:Long,from:String="",to:String=""):List<LedgerRow>{
  val out=mutableListOf<LedgerRow>();readableDatabase.rawQuery("SELECT id,kind,period,amount,receipt_no,receiver,note,created_at FROM ledger WHERE tenant_id=? AND substr(created_at,1,10)>=? AND substr(created_at,1,10)<=? ORDER BY created_at,id",arrayOf(id.toString(),if(from.isBlank())"0000-00-00" else from,if(to.isBlank())"9999-99-99" else to)).use{c->while(c.moveToNext())out.add(LedgerRow(c.getLong(0),c.getString(1),if(c.isNull(2))null else c.getString(2),c.getLong(3),if(c.isNull(4))null else c.getLong(4),if(c.isNull(5))null else c.getString(5),if(c.isNull(6))null else c.getString(6),c.getString(7)))}
  return out
 }
 fun totals(from:String,to:String):LongArray{
  val a=LongArray(4);readableDatabase.rawQuery("SELECT kind,COALESCE(SUM(amount),0) FROM ledger WHERE substr(created_at,1,10)>=? AND substr(created_at,1,10)<=? GROUP BY kind",arrayOf(from,to)).use{c->while(c.moveToNext()){val i=when(c.getString(0)){"RENT"->0;"PAYMENT"->1;"CHARGE"->2;else->3};a[i]=c.getLong(1)}};return a
 }
 fun balances():Pair<Long,Long>{var due=0L;var credit=0L;tenants("",true).filter{it.active}.forEach{val b=balance(it.id);if(b>0)due+=b else credit-=b};return due to credit}
 fun backup():String{
  val root=JSONObject().put("format","alajag-rentals-v3").put("created_at",stamp())
  val ta=JSONArray();readableDatabase.rawQuery("SELECT id,name,phone,rent,notes,active,created_at FROM tenants ORDER BY id",null).use{c->while(c.moveToNext())ta.put(JSONObject().put("id",c.getLong(0)).put("name",c.getString(1)).put("phone",c.getString(2)).put("rent",c.getLong(3)).put("notes",c.getString(4)).put("active",c.getInt(5)).put("created_at",c.getString(6)))}
  val la=JSONArray();readableDatabase.rawQuery("SELECT id,tenant_id,kind,period,amount,receipt_no,receiver,note,created_at FROM ledger ORDER BY id",null).use{c->while(c.moveToNext())la.put(JSONObject().put("id",c.getLong(0)).put("tenant_id",c.getLong(1)).put("kind",c.getString(2)).put("period",if(c.isNull(3))JSONObject.NULL else c.getString(3)).put("amount",c.getLong(4)).put("receipt_no",if(c.isNull(5))JSONObject.NULL else c.getLong(5)).put("receiver",if(c.isNull(6))JSONObject.NULL else c.getString(6)).put("note",if(c.isNull(7))JSONObject.NULL else c.getString(7)).put("created_at",c.getString(8)))}
  val ca=JSONArray();readableDatabase.rawQuery("SELECT name,value FROM counters",null).use{c->while(c.moveToNext())ca.put(JSONObject().put("name",c.getString(0)).put("value",c.getLong(1)))}
  val sa=JSONArray();readableDatabase.rawQuery("SELECT key,value FROM app_settings",null).use{c->while(c.moveToNext())sa.put(JSONObject().put("key",c.getString(0)).put("value",c.getString(1)))}
  return root.put("tenants",ta).put("ledger",la).put("counters",ca).put("settings",sa).toString(2)
 }
 fun restore(raw:String){
  val root=JSONObject(raw);require(root.optString("format").startsWith("alajag-rentals")){"ملف النسخة غير صالح"}
  val ta=root.optJSONArray("tenants")?:JSONArray();val la=root.optJSONArray("ledger")?:JSONArray();require(ta.length()<100000&&la.length()<1000000){"حجم النسخة غير مقبول"}
  val d=writableDatabase;d.beginTransaction()
  try{
   d.delete("ledger",null,null);d.delete("tenants",null,null);d.delete("counters",null,null);d.delete("app_settings",null,null)
   for(i in 0 until ta.length()){val o=ta.getJSONObject(i);require(o.getLong("rent")>0&&o.getString("name").isNotBlank());d.insertOrThrow("tenants",null,values{put("id",o.getLong("id"));put("name",o.getString("name"));put("phone",o.optString("phone"));put("rent",o.getLong("rent"));put("notes",o.optString("notes"));put("active",o.optInt("active",1));put("created_at",o.optString("created_at",stamp()))})}
   for(i in 0 until la.length()){val o=la.getJSONObject(i);val kind=o.getString("kind");require(kind in listOf("RENT","PAYMENT","CHARGE","CREDIT")&&o.getLong("amount")>0);d.insertOrThrow("ledger",null,values{put("id",o.getLong("id"));put("tenant_id",o.getLong("tenant_id"));put("kind",kind);if(!o.isNull("period"))put("period",o.getString("period"));put("amount",o.getLong("amount"));if(!o.isNull("receipt_no"))put("receipt_no",o.getLong("receipt_no"));if(!o.isNull("receiver"))put("receiver",o.getString("receiver"));if(!o.isNull("note"))put("note",o.getString("note"));put("created_at",o.getString("created_at"))})}
   val ca=root.optJSONArray("counters")?:JSONArray();for(i in 0 until ca.length()){val o=ca.getJSONObject(i);d.insertOrThrow("counters",null,values{put("name",o.getString("name"));put("value",o.getLong("value"))})}
   val sa=root.optJSONArray("settings")?:JSONArray();for(i in 0 until sa.length()){val o=sa.getJSONObject(i);d.insertOrThrow("app_settings",null,values{put("key",o.getString("key"));put("value",o.getString("value"))})}
   d.execSQL("INSERT OR IGNORE INTO counters(name,value) VALUES('receipt',0)");d.setTransactionSuccessful()
  }finally{d.endTransaction()}
 }
}

class MainActivity:Activity(){
 private lateinit var db:RentalDb;private lateinit var list:LinearLayout;private lateinit var summary:TextView;private var query="";private var includeInactive=false;private var pdf:PdfDocument?=null
 override fun onCreate(state:Bundle?){super.onCreate(state);db=RentalDb(this);db.generateDuePeriods();createChannel();Schedule.install(this);home()}
 private fun createChannel(){if(android.os.Build.VERSION.SDK_INT>=26){val m=getSystemService(NotificationManager::class.java);if(m.getNotificationChannel("rent_due")==null)m.createNotificationChannel(NotificationChannel("rent_due","تنبيهات الإيجارات",NotificationManager.IMPORTANCE_DEFAULT))}}
 private fun home(){
  val root=LinearLayout(this).apply{orientation=1;setPadding(14,8,14,8);layoutDirection=View.LAYOUT_DIRECTION_RTL;setBackgroundColor(Color.rgb(246,248,250))}
  root.addView(TextView(this).apply{text="العجاج - الإيجارات";textSize=25f;gravity=Gravity.CENTER;setTextColor(Color.rgb(23,52,76));setPadding(4,12,4,14)})
  summary=TextView(this).apply{textSize=15f;setPadding(12,12,12,12);setBackgroundColor(Color.WHITE)};root.addView(summary)
  root.addView(EditText(this).apply{hint="بحث بالاسم أو الهاتف";addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){query=s.toString();refresh()};override fun afterTextChanged(e:android.text.Editable?){}})})
  val a=LinearLayout(this);a.addView(btn("إضافة مستأجر"){tenantDialog(null)},LinearLayout.LayoutParams(0,-2,1f));a.addView(btn("التقارير"){reportDialog()},LinearLayout.LayoutParams(0,-2,1f));root.addView(a)
  val b=LinearLayout(this);b.addView(btn("نسخ احتياطي كامل"){backup()},LinearLayout.LayoutParams(0,-2,1f));b.addView(btn("استعادة نسخة"){restore()},LinearLayout.LayoutParams(0,-2,1f));root.addView(b)
  root.addView(btn("عرض النشطين / الموقوفين"){includeInactive=!includeInactive;refresh()})
  list=LinearLayout(this).apply{orientation=1};val sc=ScrollView(this);sc.addView(list);root.addView(sc,LinearLayout.LayoutParams(-1,0,1f));setContentView(root);refresh()
 }
 private fun btn(s:String,a:()->Unit)=Button(this).apply{text=s;setOnClickListener{a()}}
 private fun field(s:String)=EditText(this).apply{hint=s;textSize=16f}
 private fun refresh(){
  if(!::list.isInitialized)return;list.removeAllViews();val totals=db.balances();summary.text="المستأجرون: "+db.tenants("",true).size+" | المستحق: "+totals.first+" ريال | الدائن: "+totals.second+" ريال"
  db.tenants(query,includeInactive).forEach{t->
   val card=LinearLayout(this).apply{orientation=1;setPadding(12,10,12,10);setBackgroundColor(Color.WHITE)}
   card.addView(TextView(this).apply{text=t.name+" • "+t.rent+" ريال / شهري";textSize=18f;setTextColor(Color.BLACK)})
   val balance=db.balance(t.id);card.addView(TextView(this).apply{text="الهاتف: "+t.phone+" | "+if(balance>0)"المتبقي: "+balance+" ريال" else "رصيد دائن: "+(-balance)+" ريال";setTextColor(if(balance<0)Color.rgb(0,130,60) else Color.DKGRAY)})
   val row=LinearLayout(this);row.addView(btn("تحصيل"){paymentDialog(t)},LinearLayout.LayoutParams(0,-2,1f));row.addView(btn("كشف الحساب"){statementDialog(t)},LinearLayout.LayoutParams(0,-2,1f));row.addView(btn("تعديل"){tenantDialog(t)},LinearLayout.LayoutParams(0,-2,1f));card.addView(row)
   val row2=LinearLayout(this);row2.addView(btn("تسوية"){adjustmentDialog(t)},LinearLayout.LayoutParams(0,-2,1f));row2.addView(btn(if(t.active)"إيقاف" else "تفعيل"){db.editTenant(t,t.name,t.phone,t.rent,t.notes,!t.active);refresh()},LinearLayout.LayoutParams(0,-2,1f));card.addView(row2)
   list.addView(card);list.addView(View(this).apply{setBackgroundColor(Color.LTGRAY)},LinearLayout.LayoutParams(-1,1))
  }
 }
 private fun tenantDialog(old:Tenant?){
  val box=LinearLayout(this).apply{orientation=1;setPadding(14,4,14,4)};val n=field("اسم المستأجر").also{it.setText(old?.name?:"")};val p=field("رقم الهاتف").also{it.setText(old?.phone?:"")};val r=field("الإيجار الشهري").also{it.inputType=2;it.setText(old?.rent?.toString()?:"")};val note=field("ملاحظات").also{it.setText(old?.notes?:"")};val active=CheckBox(this).apply{text="مستأجر نشط";isChecked=old?.active?:true};listOf(n,p,r,note,active).forEach{box.addView(it)}
  AlertDialog.Builder(this).setTitle(if(old==null)"إضافة مستأجر" else "تعديل مستأجر").setView(box).setNegativeButton("إلغاء",null).setPositiveButton("حفظ"){_,_->try{val amount=r.text.toString().toLong();if(old==null)db.addTenant(n.text.toString(),p.text.toString(),amount,note.text.toString())else db.editTenant(old,n.text.toString(),p.text.toString(),amount,note.text.toString(),active.isChecked);refresh()}catch(e:Exception){toast(e.message?:"تحقق من البيانات")}}.show()
 }
 private fun paymentDialog(t:Tenant){
  val box=LinearLayout(this).apply{orientation=1;setPadding(14,4,14,4)};val amount=field("المبلغ المستلم").also{it.inputType=2};val receiver=field("اسم المستلم");val note=field("ملاحظات");listOf(amount,receiver,note).forEach{box.addView(it)}
  AlertDialog.Builder(this).setTitle("تحصيل من "+t.name).setView(box).setNegativeButton("إلغاء",null).setPositiveButton("تسجيل"){_,_->try{val a=amount.text.toString().toLong();val no=db.payment(t,a,receiver.text.toString(),note.text.toString());share("إيصال رقم "+no,"العجاج - الإيجارات\\nالمستأجر: "+t.name+"\\nالمبلغ: "+a+" ريال\\nالتاريخ: "+stamp()+"\\nالمستلم: "+receiver.text+"\\nرقم الإيصال: "+no);refresh()}catch(e:Exception){toast(e.message?:"مبلغ غير صحيح")}}.show()
 }
 private fun adjustmentDialog(t:Tenant){
  val amount=field("المبلغ").also{it.inputType=2};val note=field("سبب التسوية");val kind=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("إضافة مستحق","إضافة رصيد دائن"))};val box=LinearLayout(this).apply{orientation=1;addView(kind);addView(amount);addView(note)}
  AlertDialog.Builder(this).setTitle("تسوية حساب "+t.name).setView(box).setNegativeButton("إلغاء",null).setPositiveButton("حفظ"){_,_->try{db.adjustment(t,if(kind.selectedItemPosition==0)"CHARGE" else "CREDIT",amount.text.toString().toLong(),note.text.toString());refresh()}catch(e:Exception){toast(e.message?:"بيانات غير صحيحة")}}.show()
 }
 private fun statementDialog(t:Tenant){
  val from=field("من تاريخ YYYY-MM-DD (اختياري)");val to=field("إلى تاريخ YYYY-MM-DD (اختياري)");val box=LinearLayout(this).apply{orientation=1;addView(from);addView(to)}
  AlertDialog.Builder(this).setTitle("كشف حساب "+t.name).setView(box).setNegativeButton("إلغاء",null).setNeutralButton("مشاركة نصية"){_,_->share("كشف حساب "+t.name,statementText(t,from.text.toString(),to.text.toString()))}.setPositiveButton("PDF"){_,_->makePdf("كشف حساب "+t.name,statementText(t,from.text.toString(),to.text.toString()),"statement-"+t.id+".pdf")}.show()
 }
 private fun statementText(t:Tenant,from:String,to:String):String{
  val out=StringBuilder("العجاج - الإيجارات\\nكشف حساب: "+t.name+"\\nالهاتف: "+t.phone+"\\n\\n")
  db.rows(t.id,from,to).forEach{e->val label=when(e.kind){"RENT"->"إيجار "+e.period;"PAYMENT"->"تحصيل";"CHARGE"->"مبلغ مستحق";else->"رصيد دائن"};out.append(e.created+" | "+label+" | "+e.amount+" ريال");if(e.receipt!=null)out.append(" | إيصال "+e.receipt);if(!e.receiver.isNullOrBlank())out.append(" | المستلم "+e.receiver);if(!e.note.isNullOrBlank())out.append(" | "+e.note);out.append("\\n")}
  out.append("\\nالرصيد الحالي: "+db.balance(t.id)+" ريال");return out.toString()
 }
 private fun reportDialog(){
  val from=field("من تاريخ YYYY-MM-DD");val to=field("إلى تاريخ YYYY-MM-DD");val box=LinearLayout(this).apply{orientation=1;addView(from);addView(to)}
  AlertDialog.Builder(this).setTitle("تقرير الفترة").setView(box).setNegativeButton("إلغاء",null).setNeutralButton("عرض"){_,_->val a=db.totals(from.text.toString(),to.text.toString());toast("الإيجارات: "+a[0]+" ريال\\nالتحصيلات: "+a[1]+" ريال\\nإضافات: "+a[2]+" ريال\\nأرصدة دائنة: "+a[3]+" ريال")}.setPositiveButton("PDF"){_,_->val a=db.totals(from.text.toString(),to.text.toString());val body="الفترة: "+from.text+" إلى "+to.text+"\\nالإيجارات: "+a[0]+" ريال\\nالتحصيلات: "+a[1]+" ريال\\nالمستحقات الإضافية: "+a[2]+" ريال\\nالأرصدة الدائنة: "+a[3]+" ريال\\n\\n"+db.tenants("",true).joinToString("\\n"){it.name+": "+db.balance(it.id)+" ريال"};makePdf("تقرير الإيجارات والتحصيل",body,"rent-report.pdf")}.show()
 }
 private fun makePdf(title:String,body:String,file:String){
  val doc=PdfDocument();val paint=android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply{textSize=13f;color=Color.BLACK};val text="العجاج - الإيجارات\\n"+title+"\\nالتاريخ: "+stamp()+"\\n\\n"+body;val layout=android.text.StaticLayout.Builder.obtain(text,0,text.length,paint,520).setAlignment(android.text.Layout.Alignment.ALIGN_OPPOSITE).setTextDirection(android.text.TextDirectionHeuristics.RTL).setLineSpacing(3f,1f).build();var start=0;var pageNo=1
  while(start<layout.lineCount){val page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,pageNo++).create());val canvas=page.canvas;val end=minOf(start+42,layout.lineCount);val top=layout.getLineTop(start);val bottom=layout.getLineBottom(end-1);canvas.save();canvas.clipRect(0f,0f,525f,(bottom-top).toFloat()+4f);canvas.translate(35f,40f-top.toFloat());layout.draw(canvas);canvas.restore();doc.finishPage(page);start=end}
  pdf=doc;val i=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/pdf";putExtra(Intent.EXTRA_TITLE,file)};startActivityForResult(i,83)
 }
 private fun backup(){val i=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,"alajag-backup-"+stamp().replace(':','-')+".json")};startActivityForResult(i,81)}
 private fun restore(){AlertDialog.Builder(this).setTitle("استعادة نسخة").setMessage("سيتم استبدال البيانات الحالية. احفظ نسخة احتياطية منها أولاً.").setNegativeButton("إلغاء",null).setPositiveButton("اختيار ملف"){_,_->val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json"};startActivityForResult(i,82)}.show()}
 override fun onActivityResult(req:Int,res:Int,data:Intent?){super.onActivityResult(req,res,data);if(res!=RESULT_OK||data?.data==null){pdf?.close();pdf=null;return};try{when(req){81->contentResolver.openOutputStream(data.data!!)?.use{it.write(db.backup().toByteArray(Charsets.UTF_8))}.also{toast("تم حفظ النسخة الكاملة")};82->{val raw=contentResolver.openInputStream(data.data!!)!!.bufferedReader().use{it.readText()};db.restore(raw);db.generateDuePeriods();refresh();toast("تمت استعادة النسخة")};83->{pdf?.let{p->contentResolver.openOutputStream(data.data!!)?.use{p.writeTo(it)};p.close();pdf=null;toast("تم حفظ PDF")}}}}catch(e:Exception){toast("تعذرت العملية: "+e.message)}}
 private fun share(subject:String,body:String){startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_SUBJECT,subject);putExtra(Intent.EXTRA_TEXT,body)},"مشاركة"))}
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
