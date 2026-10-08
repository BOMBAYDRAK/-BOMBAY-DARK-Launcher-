package dark.bombay.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(7,7,9);
    private static final int PANEL = Color.rgb(21,21,24);
    private static final int LINE = Color.rgb(62,25,32);
    private static final int RED = Color.rgb(238,20,52);
    private static final int WHITE = Color.rgb(249,249,250);
    private static final int GREY = Color.rgb(167,167,176);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout base;
    private LinearLayout home, drawer, controls;
    private LinearLayout appsGrid;
    private TextView clockBig, clockSmall, dateSmall;
    private EditText search;
    private final List<AppItem> applications = new ArrayList<>();
    private String query = "";
    private String category = "Все";
    private boolean loading = false;
    private final Runnable clockUpdate = new Runnable() {
        @Override public void run() {
            try {
                if (clockBig != null) clockBig.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
                if (clockSmall != null) clockSmall.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
                if (dateSmall != null) dateSmall.setText(new SimpleDateFormat("EEEE, d MMMM", new Locale("ru")).format(new Date()).toUpperCase(new Locale("ru")));
            } catch (Throwable ignored) { }
            handler.postDelayed(this, 30000);
        }
    };
    private static final class AppItem {
        String title;
        String pkg;
        Intent launch;
        Drawable icon;
        AppItem(String title, String pkg, Intent launch, Drawable icon) {
            this.title=title;this.pkg=pkg;this.launch=launch;this.icon=icon;
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t,e)->{
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                try(FileOutputStream fos=openFileOutput("bombay_crash.txt",MODE_PRIVATE)){
                    fos.write(("Android "+Build.VERSION.RELEASE+"\n"+Build.MODEL+"\n"+sw).getBytes("UTF-8"));
                }
            } catch(Throwable ignored) { }
            if(old!=null)old.uncaughtException(t,e);
            else android.os.Process.killProcess(android.os.Process.myPid());
        });
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        try {
            loadApps();
            buildUi();
        } catch(Throwable e) {
            showRecovery(e);
        }
    }
    private void showRecovery(Throwable e) {
        LinearLayout fallback = column();
        fallback.setPadding(dp(24),dp(40),dp(24),dp(24));
        fallback.setBackgroundColor(BG);
        fallback.addView(text("BOMBAY:DARK",30,RED,true));
        fallback.addView(text("Режим восстановления",20,WHITE,true));
        fallback.addView(text("Ошибка интерфейса. Можно открыть настройки телефона или скопировать сведения об ошибке.",15,GREY,false));
        fallback.addView(action("ОТКРЫТЬ НАСТРОЙКИ",()->openSettings(Settings.ACTION_SETTINGS)));
        fallback.addView(action("ПЕРЕЗАПУСТИТЬ ЛАУНЧЕР",()->recreate()));
        setContentView(fallback);
    }
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout column() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v; }
    private LinearLayout row() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v; }
    private TextView text(String s,int size,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.create(bold?"sans-serif-condensed-medium":"sans-serif",Typeface.NORMAL));
        t.setGravity(Gravity.CENTER_VERTICAL);t.setIncludeFontPadding(true);return t;
    }
    private LinearLayout.LayoutParams lp(int w,int h){
        return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));
    }
    private GradientDrawable surface(int fill,int stroke,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));
        if(stroke!=0)d.setStroke(dp(1),stroke);return d;
    }
    private TextView action(String s,Runnable run){
        TextView t=text(s,14,WHITE,true);t.setGravity(Gravity.CENTER);t.setAllCaps(false);
        t.setBackground(surface(PANEL,LINE,15));t.setPadding(dp(12),dp(14),dp(12),dp(14));
        LinearLayout.LayoutParams p=lp(-1,-2);p.setMargins(0,dp(7),0,dp(7));t.setLayoutParams(p);
        t.setOnClickListener(v->run.run());return t;
    }
    private void loadApps(){
        applications.clear();
        PackageManager pm=getPackageManager();
        Intent request=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> installed=pm.queryIntentActivities(request,0);
        Set<String> components=new HashSet<>();
        for(ResolveInfo ri:installed){
            try{
                if(ri.activityInfo==null)continue;
                String pkg=ri.activityInfo.packageName;
                String cls=ri.activityInfo.name;
                if(pkg.equals(getPackageName()) || !components.add(pkg+"/"+cls))continue;
                Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                launch.setClassName(pkg,cls);
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                String label=ri.loadLabel(pm).toString();
                applications.add(new AppItem(label,pkg,launch,ri.loadIcon(pm)));
            }catch(Throwable ignored){}
        }
        Collections.sort(applications,Comparator.comparing(a->a.title.toLowerCase(new Locale("ru"))));
    }
    private void buildUi(){
        base=new FrameLayout(this);
        base.setBackgroundColor(BG);
        base.addView(new Wallpaper(this),new FrameLayout.LayoutParams(-1,-1));
        home=createHome();
        drawer=createDrawer();
        controls=createControls();
        base.addView(home,new FrameLayout.LayoutParams(-1,-1));
        base.addView(drawer,new FrameLayout.LayoutParams(-1,-1));
        base.addView(controls,new FrameLayout.LayoutParams(-1,-1));
        setContentView(base);
        show(0);
    }
    private LinearLayout createHome(){
        LinearLayout page=column();
        page.setPadding(dp(14),dp(9),dp(14),dp(5));
        LinearLayout top=row();
        TextView brand=text("BOMBAY:DARK",19,RED,true);
        brand.setLetterSpacing(.08f);
        top.addView(brand,new LinearLayout.LayoutParams(0,dp(47),1));
        TextView menu=text("☷",31,WHITE,true);
        menu.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        top.addView(menu,lp(46,45));menu.setOnClickListener(v->show(2));
        page.addView(top);

        LinearLayout clockRow=row();
        clockBig=text("",56,WHITE,true);
        clockBig.setShadowLayer(dp(5),0,dp(2),0xFF000000);
        clockRow.addView(clockBig,new LinearLayout.LayoutParams(0,dp(76),1));
        TextView mark=text("●  DARK",13,RED,true); mark.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        clockRow.addView(mark,lp(94,65));page.addView(clockRow);
        dateSmall=text("",14,WHITE,true);dateSmall.setLetterSpacing(.05f);page.addView(dateSmall);
        TextView caption=text("ТЁМНАЯ СТОРОНА ТВОЕГО ANDROID",11,0xFFE5ADB7,true);
        caption.setLetterSpacing(.1f);page.addView(caption);

        View empty=new View(this);
        page.addView(empty,new LinearLayout.LayoutParams(1,0,1));

        LinearLayout music=column();
        music.setPadding(dp(16),dp(9),dp(16),dp(10));
        music.setBackground(surface(0xE9151015,RED,16));
        music.addView(text("♫  BOMBAY:DARK  /  МУЗЫКА",16,WHITE,true));
        music.addView(text("Открыть музыкальный плеер",12,0xFFE0ABB3,false));
        TextView play=text("◀◀     ▶     ▶▶",23,RED,true);
        play.setGravity(Gravity.CENTER);play.setPadding(0,dp(3),0,0);
        music.addView(play,lp(-1,34));music.setOnClickListener(v->openMusic());
        LinearLayout.LayoutParams mp=lp(-1,-2);mp.setMargins(0,dp(3),0,dp(6));page.addView(music,mp);

        LinearLayout row1=row();row1.setGravity(Gravity.TOP);
        addHomeTile(row1,new String[]{"telegram"},"Telegram","✈");
        addHomeTile(row1,new String[]{"vk","вконтакте"},"VK","ВК");
        addHomeTile(row1,new String[]{"youtube"},"YouTube","▶");
        addHomeTile(row1,new String[]{"музыка","music","yandex.music","zvuk"},"Музыка","♫");
        page.addView(row1,lp(-1,87));

        LinearLayout row2=row();row2.setGravity(Gravity.TOP);
        addHomeTile(row2,new String[]{"камера","camera"},"Камера","◎");
        addHomeTile(row2,new String[]{"галерея","gallery","фото","photos"},"Галерея","▣");
        addHomeTile(row2,new String[]{"настройки","settings"},"Настройки","⚙");
        addHomeTile(row2,new String[]{"файлы","files","проводник"},"Файлы","▤");
        page.addView(row2,lp(-1,87));

        LinearLayout dock=row();dock.setGravity(Gravity.CENTER);dock.setPadding(0,dp(6),0,dp(2));
        dock.addView(dockButton("☎",()->safeLaunch(new Intent(Intent.ACTION_DIAL))));
        dock.addView(dockButton("✉",()->safeLaunch(new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:")))));
        dock.addView(dockButton("▦",()->show(1)));
        dock.addView(dockButton("◎",()->safeLaunch(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))));
        page.addView(dock);
        return page;
    }
    private void addHomeTile(LinearLayout row,String[] keys,String fallback,String symbol){
        AppItem found=null;
        for(String key:keys){
            for(AppItem app:applications){
                if(app.title.toLowerCase(new Locale("ru")).contains(key) || app.pkg.toLowerCase(Locale.ROOT).contains(key)){
                    found=app;break;
                }
            }
            if(found!=null)break;
        }
        final AppItem target=found;
        LinearLayout cell=column();cell.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(0,dp(86),1);
        item.setMargins(dp(3),dp(2),dp(3),0);row.addView(cell,item);
        FrameLayout tile=new FrameLayout(this);
        tile.setBackground(surface(0xF0161418,LINE,13));
        if(found!=null){
            ImageView img=new ImageView(this);
            img.setImageDrawable(found.icon);
            img.setColorFilter(RED,android.graphics.PorterDuff.Mode.SRC_IN);
            img.setPadding(dp(13),dp(13),dp(13),dp(13));
            tile.addView(img,new FrameLayout.LayoutParams(-1,-1));
        }else{
            TextView glyph=text(symbol,26,RED,true);glyph.setGravity(Gravity.CENTER);
            tile.addView(glyph,new FrameLayout.LayoutParams(-1,-1));
        }
        cell.addView(tile,lp(57,57));
        TextView label=text(fallback,11,WHITE,false);
        label.setSingleLine(true);label.setGravity(Gravity.CENTER);
        cell.addView(label,lp(-1,23));
        cell.setOnClickListener(v->{
            if(target!=null)safeLaunch(target.launch);
            else if(fallback.equals("Музыка"))openMusic();
            else if(fallback.equals("Настройки"))openSettings(Settings.ACTION_SETTINGS);
            else if(fallback.equals("Камера"))safeLaunch(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
            else show(1);
        });
    }
        private TextView dockButton(String label,Runnable click){
        TextView b=text(label,27,RED,true);b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(61),1);
        p.setMargins(dp(4),0,dp(4),0);b.setBackground(surface(0xF0141416,RED,15));
        dockAddPlaceholder(b,click);b.setLayoutParams(p);return b;
    }
    private void dockAddPlaceholder(View b,Runnable r){b.setOnClickListener(v->r.run());}
    private LinearLayout createDrawer(){
        LinearLayout page=column();page.setBackgroundColor(0xFA0B0B0E);
        page.setPadding(dp(16),dp(12),dp(16),dp(5));page.setVisibility(View.GONE);
        LinearLayout head=row();
        TextView title=text("ПРИЛОЖЕНИЯ",26,WHITE,true);
        head.addView(title,new LinearLayout.LayoutParams(0,dp(60),1));
        TextView close=text("×",31,RED,true);close.setGravity(Gravity.CENTER);
        head.addView(close,lp(42,52));close.setOnClickListener(v->show(0));
        page.addView(head);
        search=new EditText(this);
        search.setSingleLine(true);search.setTextColor(WHITE);search.setHintTextColor(GREY);
        search.setTextSize(15);search.setHint("⌕  Поиск приложений");
        search.setPadding(dp(15),0,dp(12),0);search.setBackground(surface(PANEL,LINE,15));
        page.addView(search,lp(-1,50));
        LinearLayout tabs=row();tabs.setPadding(0,dp(13),0,dp(9));
        for(String tab:new String[]{"Все","Соцсети","Медиа","Система"}){
            TextView chip=text(tab,12,tab.equals(category)?WHITE:GREY,true);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(surface(tab.equals(category)?0xFF8E1024:0xFF19191C,tab.equals(category)?RED:LINE,11));
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(39),1);
            tp.setMargins(dp(2),0,dp(2),0);tabs.addView(chip,tp);
            chip.setOnClickListener(v->{category=tab; for(int z=0;z<tabs.getChildCount();z++){
                TextView t=(TextView)tabs.getChildAt(z);boolean sel=t.getText().toString().equals(category);
                t.setTextColor(sel?WHITE:GREY);
                t.setBackground(surface(sel?0xFF8E1024:0xFF19191C,sel?RED:LINE,11));
            }renderApps();});
        }
        page.addView(tabs);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(false);
        appsGrid=column();appsGrid.setPadding(0,dp(4),0,dp(28));
        sc.addView(appsGrid);page.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString().toLowerCase(new Locale("ru"));renderApps();}
            public void afterTextChanged(Editable e){}
        });
        renderApps();return page;
    }
    private void renderApps(){
        if(appsGrid==null || loading)return;loading=true;
        appsGrid.removeAllViews();
        List<AppItem> filtered=new ArrayList<>();
        for(AppItem item:applications){
            if(!query.isEmpty() && !item.title.toLowerCase(new Locale("ru")).contains(query))continue;
            String name=(item.title+" "+item.pkg).toLowerCase(new Locale("ru"));
            boolean social=name.matches(".*(telegram|whats|вк|vk|messenger|discord|signal|viber|сообщения).*");
            boolean media=name.matches(".*(music|музык|youtube|видео|video|gallery|галерея|фото|camera|камера|suno|зву).*");
            boolean system=name.matches(".*(setting|настрой|калькулятор|calc|проводник|files|файл|час|clock|calendar|календар).*");
            if(category.equals("Все") || category.equals("Соцсети")&&social || category.equals("Медиа")&&media || category.equals("Система")&&system)filtered.add(item);
        }
        if(filtered.isEmpty())appsGrid.addView(text("Приложения не найдены",16,GREY,false));
        int size=4;
        for(int i=0;i<filtered.size();i+=size){
            LinearLayout gridRow=row();gridRow.setGravity(Gravity.TOP);
            for(int j=0;j<size;j++){
                if(i+j<filtered.size()){
                    AppItem item=filtered.get(i+j);
                    LinearLayout cell=column();cell.setGravity(Gravity.CENTER_HORIZONTAL);
                    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(105),1);
                    cp.setMargins(dp(2),dp(2),dp(2),dp(3));gridRow.addView(cell,cp);
                    ImageView img=new ImageView(this);img.setImageDrawable(item.icon);img.setColorFilter(RED,android.graphics.PorterDuff.Mode.SRC_IN);img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    img.setPadding(dp(14),dp(12),dp(14),dp(12));
                    img.setBackground(surface(0xF0202023,LINE,16));
                    cell.addView(img,lp(62,62));
                    TextView label=text(item.title,11,WHITE,false);label.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
                    label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    label.setPadding(dp(1),dp(5),dp(1),0);cell.addView(label,lp(-1,34));
                    cell.setOnClickListener(v->safeLaunch(item.launch));
                }else{
                    gridRow.addView(new View(this),new LinearLayout.LayoutParams(0,dp(104),1));
                }
            }
            appsGrid.addView(gridRow,lp(-1,105));
        }
        loading=false;
    }
    private LinearLayout createControls(){
        LinearLayout page=column();page.setBackgroundColor(0xFF0D0D10);
        page.setPadding(dp(16),dp(12),dp(16),dp(12));page.setVisibility(View.GONE);
        LinearLayout head=row();
        TextView title=text("BOMBAY:DARK // ПАНЕЛЬ",19,RED,true);
        head.addView(title,new LinearLayout.LayoutParams(0,dp(60),1));
        TextView close=text("×",31,WHITE,true);close.setGravity(Gravity.CENTER);
        head.addView(close,lp(42,50));close.setOnClickListener(v->show(0));page.addView(head);
        ScrollView sc=new ScrollView(this);LinearLayout inner=column();
        sc.addView(inner);page.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        clockSmall=text("",38,WHITE,true);inner.addView(clockSmall);
        inner.addView(text("БЫСТРЫЕ ДЕЙСТВИЯ",13,GREY,true));
        LinearLayout r1=row();
        r1.addView(tile("◉  WI-FI","Настройки сети",()->openSettings(Settings.ACTION_WIFI_SETTINGS)));
        r1.addView(tile("ᛒ  BLUETOOTH","Подключения",()->openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)));
        inner.addView(r1);
        LinearLayout r2=row();
        r2.addView(tile("☼  ЭКРАН","Яркость",()->openSettings(Settings.ACTION_DISPLAY_SETTINGS)));
        r2.addView(tile("◖  ЗВУК","Громкость",()->openSettings(Settings.ACTION_SOUND_SETTINGS)));
        inner.addView(r2);
        LinearLayout r3=row();
        r3.addView(tile("▣  БАТАРЕЯ","Экономия",()->openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS)));
        r3.addView(tile("⚙  ANDROID","Система",()->openSettings(Settings.ACTION_SETTINGS)));
        inner.addView(r3);
        TextView note=text("Управление системными переключателями выполняется через настройки Android. Эта панель не заменяет системную шторку.",12,GREY,false);
        note.setPadding(0,dp(15),0,dp(14));inner.addView(note);
        inner.addView(action("СДЕЛАТЬ ГЛАВНЫМ ЭКРАНОМ",this::chooseHome));
        inner.addView(action("ОБНОВИТЬ СПИСОК ПРИЛОЖЕНИЙ",()->{loadApps();renderApps();toast("Список обновлён");}));
        inner.addView(action("ПРОВЕРИТЬ ДИАГНОСТИКУ",this::diagnostics));
        inner.addView(action("ОТКРЫТЬ НАСТРОЙКИ ТЕЛЕФОНА",()->openSettings(Settings.ACTION_SETTINGS)));
        return page;
    }
    private TextView tile(String name,String detail,Runnable fn){
        TextView t=text(name+"\n"+detail,14,WHITE,true);t.setPadding(dp(14),dp(11),dp(7),dp(11));
        t.setBackground(surface(PANEL,LINE,16));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(90),1);
        p.setMargins(dp(3),dp(6),dp(3),dp(6));t.setLayoutParams(p);
        t.setOnClickListener(v->fn.run());return t;
    }
    private void show(int page){
        if(home!=null)home.setVisibility(page==0?View.VISIBLE:View.GONE);
        if(drawer!=null)drawer.setVisibility(page==1?View.VISIBLE:View.GONE);
        if(controls!=null)controls.setVisibility(page==2?View.VISIBLE:View.GONE);
    }
    private void safeLaunch(Intent intent){
        try{startActivity(intent);}catch(Throwable e){toast("Не удалось открыть приложение");}
    }
    private void openSettings(String action){
        try{startActivity(new Intent(action));}
        catch(Throwable e){safeLaunch(new Intent(Settings.ACTION_SETTINGS));}
    }
    private void openMusic(){
        try{
            Intent i=new Intent(Intent.ACTION_MAIN);i.addCategory(Intent.CATEGORY_APP_MUSIC);
            startActivity(i);
        }catch(Throwable e){
            show(1);toast("Выберите музыкальное приложение");
        }
    }
    private void chooseHome(){
        try{
            if(Build.VERSION.SDK_INT>=29){
                RoleManager rm=(RoleManager)getSystemService(Context.ROLE_SERVICE);
                if(rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_HOME)){
                    startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_HOME));return;
                }
            }
            openSettings(Settings.ACTION_HOME_SETTINGS);
        }catch(Throwable e){openSettings(Settings.ACTION_HOME_SETTINGS);}
    }
    private void diagnostics(){
        String report;
        try{
            java.io.File f=new java.io.File(getFilesDir(),"bombay_crash.txt");
            if(!f.exists())report="Ошибок не записано.";
            else{
                byte[] bytes=new byte[(int)Math.min(f.length(),12000)];
                try(java.io.FileInputStream in=new java.io.FileInputStream(f)){int n=in.read(bytes);report=n>0?new String(bytes,0,n,"UTF-8"):"Отчёт пуст";}
            }
        }catch(Exception e){report="Не удалось прочитать отчёт";}
        final String content=report;
        new AlertDialog.Builder(this).setTitle("Диагностика BOMBAY:DARK").setMessage(report)
            .setPositiveButton("КОПИРОВАТЬ",(d,w)->{
                android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if(cm!=null)cm.setPrimaryClip(android.content.ClipData.newPlainText("Launcher crash",content));
            }).setNegativeButton("ЗАКРЫТЬ",null).show();
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(clockUpdate);handler.post(clockUpdate);}
    @Override protected void onPause(){handler.removeCallbacks(clockUpdate);super.onPause();}
    @Override public void onBackPressed(){
        if(drawer!=null&&drawer.getVisibility()==View.VISIBLE){show(0);return;}
        if(controls!=null&&controls.getVisibility()==View.VISIBLE){show(0);return;}
        super.onBackPressed();
    }
    private static class Wallpaper extends View{
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Wallpaper(Context c){super(c);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float w=getWidth(),h=getHeight();
            p.setShader(new LinearGradient(0,0,w,h,new int[]{0xFF070709,0xFF21070E,0xFF070709},null,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);p.setShader(null);
            float x=w*.66f,y=h*.39f;
            p.setColor(0x44FF1138);c.drawCircle(x,y,w*.38f,p);
            p.setColor(0x66B60D2D);c.drawCircle(x,y,w*.31f,p);
            p.setColor(0x99260710);c.drawCircle(x,y,w*.29f,p);
            p.setColor(0xD6000000);
            for(int i=0;i<17;i++){
                float left=i*w/16f;float bw=w/12f;float tower=(float)(h*(.32+.20*Math.abs(Math.sin(i*1.7))));
                c.drawRect(left,h-tower,left+bw,h,p);
            }
            p.setStrokeWidth(2);p.setColor(0x44FF2040);
            for(int i=0;i<12;i++)c.drawLine(0,h*(.5f+i*.04f),w,h*(.35f+i*.04f),p);
        }
    }
}
