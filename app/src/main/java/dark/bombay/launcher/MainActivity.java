package dark.bombay.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
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
    private LinearLayout home, drawer, controls, dialer, calculator;
    private TextView dialNumber, calcDisplay;
    private String enteredNumber = "";
    private String calcInput = "";
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
        dialer=createDialer();
        calculator=createCalculator();
        base.addView(home,new FrameLayout.LayoutParams(-1,-1));
        base.addView(drawer,new FrameLayout.LayoutParams(-1,-1));
        base.addView(controls,new FrameLayout.LayoutParams(-1,-1));
        base.addView(dialer,new FrameLayout.LayoutParams(-1,-1));
        base.addView(calculator,new FrameLayout.LayoutParams(-1,-1));
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
        dock.addView(dockButton("☎",()->show(3)));
        dock.addView(dockButton("✉",()->safeLaunch(new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:")))));
        dock.addView(dockButton("▦",()->show(1)));
        dock.addView(dockButton("⚙",()->show(2)));
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
            android.graphics.ColorMatrix mx=new android.graphics.ColorMatrix();mx.setSaturation(0);
            img.setColorFilter(new android.graphics.ColorMatrixColorFilter(mx));
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
            if(fallback.equals("Настройки"))show(2);
            else if(target!=null)safeLaunch(target.launch);
            else if(fallback.equals("Музыка"))openMusic();
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
        LinearLayout page=column();page.setBackgroundColor(0xED0B0B0E);
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
                    ImageView img=new ImageView(this);img.setImageDrawable(item.icon);img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    img.setPadding(dp(11),dp(9),dp(11),dp(9));
                    img.setBackground(surface(0xF0151518,LINE,16));
                    android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
                    matrix.setSaturation(0);
                    img.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
                    FrameLayout framed = new FrameLayout(this);
                    framed.addView(img,new FrameLayout.LayoutParams(-1,-1));
                    TextView corner = text("•",17,RED,true);corner.setGravity(Gravity.RIGHT|Gravity.TOP);
                    FrameLayout.LayoutParams cfp=new FrameLayout.LayoutParams(-1,-1);cfp.setMargins(0,dp(1),dp(6),0);
                    framed.addView(corner,cfp);
                    cell.addView(framed,lp(62,62));
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
        TextView title=text("НАСТРОЙКИ // BOMBAY:DARK",20,RED,true);
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
        inner.addView(action("КАЛЬКУЛЯТОР BOMBAY:DARK",()->show(4)));
        inner.addView(action("ТЕЛЕФОН BOMBAY:DARK",()->show(3)));
        inner.addView(action("ПРОВЕРИТЬ ДИАГНОСТИКУ",this::diagnostics));
        TextView section=text("СИСТЕМА ANDROID",14,RED,true);
        section.setPadding(0,dp(20),0,dp(8));inner.addView(section);
        inner.addView(action("⌕   СЕТЬ И ИНТЕРНЕТ",()->openSettings(Settings.ACTION_WIRELESS_SETTINGS)));
        inner.addView(action("◈   ПРИЛОЖЕНИЯ",()->openSettings(Settings.ACTION_APPLICATION_SETTINGS)));
        inner.addView(action("▣   УВЕДОМЛЕНИЯ",()->openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS)));
        inner.addView(action("♫   ЗВУК И ВИБРАЦИЯ",()->openSettings(Settings.ACTION_SOUND_SETTINGS)));
        inner.addView(action("☼   ЭКРАН И ЯРКОСТЬ",()->openSettings(Settings.ACTION_DISPLAY_SETTINGS)));
        inner.addView(action("⚙   ОТКРЫТЬ НАСТРОЙКИ ТЕЛЕФОНА",()->openSettings(Settings.ACTION_SETTINGS)));
        return page;
    }
    private TextView tile(String name,String detail,Runnable fn){
        TextView t=text(name+"\n"+detail,14,WHITE,true);t.setPadding(dp(14),dp(11),dp(7),dp(11));
        t.setBackground(surface(PANEL,LINE,16));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(90),1);
        p.setMargins(dp(3),dp(6),dp(3),dp(6));t.setLayoutParams(p);
        t.setOnClickListener(v->fn.run());return t;
    }

    private LinearLayout createDialer(){
        LinearLayout page=column();page.setBackgroundColor(0xFF070709);
        page.setPadding(dp(18),dp(12),dp(18),dp(20));page.setVisibility(View.GONE);
        LinearLayout header=row();
        TextView title=text("ТЕЛЕФОН  //  BOMBAY:DARK",20,WHITE,true);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(55),1));
        TextView exit=text("×",30,RED,true);exit.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        exit.setOnClickListener(v->show(0));header.addView(exit,lp(44,50));page.addView(header);
        TextView over=text("НАБОР НОМЕРА",12,RED,true);over.setLetterSpacing(.1f);page.addView(over);
        dialNumber=text("",32,WHITE,true);dialNumber.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        dialNumber.setSingleLine(true);dialNumber.setEllipsize(android.text.TextUtils.TruncateAt.START);
        dialNumber.setBackground(surface(0xFF151517,LINE,14));
        dialNumber.setPadding(dp(14),0,dp(12),0);
        LinearLayout.LayoutParams display=lp(-1,66);display.setMargins(0,dp(12),0,dp(12));
        page.addView(dialNumber,display);
        TextView delete=action("⌫  УДАЛИТЬ ПОСЛЕДНЮЮ ЦИФРУ",()->{
            if(!enteredNumber.isEmpty())enteredNumber=enteredNumber.substring(0,enteredNumber.length()-1);
            dialNumber.setText(enteredNumber);
        });
        delete.setTextColor(GREY);page.addView(delete);
        View spacer=new View(this);page.addView(spacer,new LinearLayout.LayoutParams(1,0,1));
        String[][] numbers={{"1","2","3"},{"4","5","6"},{"7","8","9"},{"*","0","#"}};
        for(String[] line:numbers){
            LinearLayout row=row();
            for(String number:line){
                TextView digit=text(number,33,WHITE,true);digit.setGravity(Gravity.CENTER);
                digit.setBackground(surface(PANEL,LINE,17));
                LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(0,dp(81),1);
                item.setMargins(dp(5),dp(5),dp(5),dp(5));row.addView(digit,item);
                digit.setOnClickListener(v->{
                    if(enteredNumber.length()<28){enteredNumber+=number;dialNumber.setText(enteredNumber);}
                });
            }
            page.addView(row);
        }
        TextView call=action("☎   ОТКРЫТЬ ВЫЗОВ",()->{
            if(enteredNumber.isEmpty()){toast("Введите номер");return;}
            Intent i=new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",enteredNumber,null));
            safeLaunch(i);
        });
        call.setTextColor(WHITE);call.setTextSize(17);
        call.setBackground(surface(0xFFB20724,RED,18));
        LinearLayout.LayoutParams callParams=lp(-1,60);callParams.setMargins(0,dp(13),0,0);
        page.addView(call,callParams);
        TextView safety=text("Звонок подтверждается в системном приложении телефона.",11,GREY,false);
        safety.setGravity(Gravity.CENTER);safety.setPadding(0,dp(8),0,0);page.addView(safety);
        return page;
    }
    private LinearLayout createCalculator(){
        LinearLayout page=column();page.setBackgroundColor(0xFF09090C);
        page.setPadding(dp(18),dp(12),dp(18),dp(24));page.setVisibility(View.GONE);
        LinearLayout header=row();
        TextView title=text("КАЛЬКУЛЯТОР  //  BOMBAY:DARK",18,WHITE,true);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(55),1));
        TextView exit=text("×",30,RED,true);exit.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        header.addView(exit,lp(44,48));exit.setOnClickListener(v->show(0));page.addView(header);
        View spacer=new View(this);page.addView(spacer,new LinearLayout.LayoutParams(1,0,1));
        calcDisplay=text("0",53,WHITE,true);calcDisplay.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams dp1=lp(-1,120);dp1.setMargins(0,dp(14),0,dp(14));
        page.addView(calcDisplay,dp1);
        String[][] keys={{"C","(",")","÷"},{"7","8","9","×"},{"4","5","6","−"},{"1","2","3","+"},{"±","0",",","="}};
        for(String[] items:keys){
            LinearLayout row=row();
            for(String symbol:items){
                TextView key=text(symbol,25,WHITE,true);key.setGravity(Gravity.CENTER);
                boolean operator="÷×−+=".contains(symbol);
                key.setBackground(surface(symbol.equals("=")?0xFFE71537:PANEL,symbol.equals("=")?RED:LINE,14));
                key.setTextColor(operator?symbol.equals("=")?WHITE:RED:WHITE);
                LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(67),1);
                cell.setMargins(dp(3),dp(4),dp(3),dp(4));row.addView(key,cell);
                key.setOnClickListener(v->calculate(symbol));
            }
            page.addView(row);
        }
        return page;
    }
    private void calculate(String key){
        if(key.equals("C"))calcInput="";
        else if(key.equals("±")){
            if(calcInput.startsWith("-"))calcInput=calcInput.substring(1);
            else if(!calcInput.isEmpty())calcInput="-"+calcInput;
        }
        else if(key.equals("=")){
            try{
                String expression=calcInput.replace("×","*").replace("÷","/").replace("−","-").replace(",",".");
                java.util.regex.Matcher m=java.util.regex.Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*([+*/-])\\s*(-?\\d+(?:\\.\\d+)?)").matcher(expression);
                if(m.matches()){
                    double a=Double.parseDouble(m.group(1)),b=Double.parseDouble(m.group(3)),out=0;
                    switch(m.group(2)){
                        case "+":out=a+b;break;
                        case "-":out=a-b;break;
                        case "*":out=a*b;break;
                        case "/":out=a/b;break;
                    }
                    if(!Double.isFinite(out))throw new ArithmeticException();
                    calcInput=out==(long)out?Long.toString((long)out):Double.toString(out);
                }
            }catch(Throwable e){toast("Ошибка вычисления");calcInput="";}
        }else if(key.equals("(")||key.equals(")")){
            toast("Скобки пока не поддерживаются");
        }
        else if(calcInput.length()<40)calcInput+=key;
        if(calcDisplay!=null)calcDisplay.setText(calcInput.isEmpty()?"0":calcInput);
    }
    private void show(int page){
        if(home!=null)home.setVisibility(page==0?View.VISIBLE:View.GONE);
        if(drawer!=null)drawer.setVisibility(page==1?View.VISIBLE:View.GONE);
        if(controls!=null)controls.setVisibility(page==2?View.VISIBLE:View.GONE);
        if(dialer!=null)dialer.setVisibility(page==3?View.VISIBLE:View.GONE);
        if(calculator!=null)calculator.setVisibility(page==4?View.VISIBLE:View.GONE);
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
        if(dialer!=null&&dialer.getVisibility()==View.VISIBLE){show(0);return;}
        if(calculator!=null&&calculator.getVisibility()==View.VISIBLE){show(0);return;}
        super.onBackPressed();
    }
    private static class Wallpaper extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private Bitmap background;
        Wallpaper(Context c){
            super(c);
            try { background=BitmapFactory.decodeResource(c.getResources(),R.drawable.bombay_wallpaper); }
            catch(Throwable ignored) { background=null; }
        }
        @Override protected void onDraw(Canvas canvas){
            int w=getWidth(),h=getHeight();
            canvas.drawColor(0xFF050507);
            if(background!=null && !background.isRecycled()){
                Rect src=new Rect(0,0,background.getWidth(),background.getHeight());
                Rect dst=new Rect(0,0,w,h);
                canvas.drawBitmap(background,src,dst,paint);
            }else{
                paint.setShader(new LinearGradient(0,0,w,h,0xFF09090A,0xFF4D081A,Shader.TileMode.CLAMP));
                canvas.drawRect(0,0,w,h,paint);
                paint.setShader(null);
            }
            paint.setShader(new LinearGradient(0,0,0,h,
                new int[]{0x35000000,0x08000000,0x16000000,0x77000000},
                new float[]{0f,.20f,.68f,1f},Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,w,h,paint);
            paint.setShader(null);
        }
    }
}
