package io.freemytv;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.net.Uri;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** The same dark rounded popup family as showtimes; category edits are applied transactionally. */
final class CulturalDialogs {
    private static final java.util.concurrent.ExecutorService QR_WORKER=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Activity activity;
    private final Dialog dialog;
    private final float scale;
    private final LinearLayout root;
    private CulturalDialogs(Activity activity,String title) {
        this.activity=activity;
        scale=Math.min(activity.getResources().getDisplayMetrics().widthPixels/1920f,activity.getResources().getDisplayMetrics().heightPixels/1080f);
        dialog=new Dialog(activity); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        root=new LinearLayout(activity); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(px(40),px(30),px(40),px(30));
        root.setBackground(shape(0xFA171B23,0xFF414956,30));
        TextView heading=text(title,38,true); heading.setSingleLine(true); heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(heading,new LinearLayout.LayoutParams(-1,px(62)));
        dialog.setContentView(root);
    }
    /** Two compact columns: all parent categories remain visible; only one child group is open. */
    static void settings(Activity activity,CulturalEvents model) {
        CulturalDialogs ui=new CulturalDialogs(activity,Ui.tr("Event settings"));
        Set<String> draft=CulturalData.expandSelection(model.catalog,model.selection);
        Set<String> starred=new HashSet<>(model.likes.tags.keySet()),changedStars=new HashSet<>();
        List<CulturalData.Category> parents=new ArrayList<>();
        for(CulturalData.Category c:model.catalog.categories)if(c.parent.isEmpty())parents.add(c);
        Map<String,CheckBox> boxes=new HashMap<>();
        LinearLayout toolbar=new LinearLayout(activity);
        Runnable[] repaint={null};
        ui.button(toolbar,Ui.tr("Select all"),()->{draft.clear();for(CulturalData.Category c:parents)draft.add(c.key);repaint[0].run();});
        ui.button(toolbar,Ui.tr("Clear selection"),()->{draft.clear();repaint[0].run();});
        ui.button(toolbar,Ui.tr("Your interests"),()->favourites(activity,model));
        LinearLayout columns=new LinearLayout(activity);
        ui.root.addView(columns,new LinearLayout.LayoutParams(-1,0,1));
        ScrollView left=new ScrollView(activity);left.setVerticalScrollBarEnabled(false);
        LinearLayout categoryList=new LinearLayout(activity);categoryList.setOrientation(LinearLayout.VERTICAL);left.addView(categoryList);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,0.5f);lp.setMargins(0,0,ui.px(22),0);columns.addView(left,lp);
        LinearLayout right=new LinearLayout(activity);right.setOrientation(LinearLayout.VERTICAL);right.setPadding(ui.px(18),ui.px(6),0,0);
        right.setBackground(ui.shape(0x8011151D,0,18));columns.addView(right,new LinearLayout.LayoutParams(0,-1,0.5f));
        TextView groupName=ui.text(Ui.tr("Subcategories"),25,true);right.addView(groupName,new LinearLayout.LayoutParams(-1,ui.px(42)));
        ScrollView childrenScroll=new ScrollView(activity);childrenScroll.setVerticalScrollBarEnabled(false);
        LinearLayout children=new LinearLayout(activity);children.setOrientation(LinearLayout.VERTICAL);childrenScroll.addView(children);
        right.addView(childrenScroll,new LinearLayout.LayoutParams(-1,0,1));
        String[] active={""};Map<String,CheckBox> parentBoxes=new LinkedHashMap<>(),parentStars=new HashMap<>();
        repaint[0]=()->{
            for(CulturalData.Category c:model.catalog.categories) {
                boolean checked=draft.contains(c.key) || !c.parent.isEmpty() && draft.contains(c.parent);
                CheckBox box=boxes.get(c.key);if(box!=null)box.setChecked(checked);
                if(c.parent.isEmpty()) {
                    int selected=0,total=0;
                    for(CulturalData.Category sub:model.catalog.categories)if(c.key.equals(sub.parent)){total++;if(draft.contains(c.key)||draft.contains(sub.key))selected++;}
                    CheckBox parent=parentBoxes.get(c.key);
                    if(parent!=null)parent.setText(c.name+(selected>0 && selected<total?" · "+selected+"/"+total:""));
                }
            }
        };
        java.util.function.Consumer<CulturalData.Category> showChildren=c->{
            if(c.key.equals(active[0]))return;
            active[0]=c.key;groupName.setText(c.name);children.removeAllViews();
            boxes.keySet().removeIf(key->!parentBoxes.containsKey(key));
            List<CulturalData.Category> subs=new ArrayList<>();
            for(CulturalData.Category sub:model.catalog.categories)if(c.key.equals(sub.parent))subs.add(sub);
            CheckBox first=null;
            for(int i=0;i<subs.size();i++) {
                LinearLayout row=new LinearLayout(activity);children.addView(row,new LinearLayout.LayoutParams(-1,ui.px(57)));
                    CulturalData.Category sub=subs.get(i);
                    CheckBox box=ui.categoryBox(sub.name,21,()->{Set<String> next=CulturalData.toggle(model.catalog,draft,sub);draft.clear();draft.addAll(next);repaint[0].run();});
                    if(first==null)first=box;boxes.put(sub.key,box);
                    row.addView(box,new LinearLayout.LayoutParams(0,-1,1));
                    CheckBox star=ui.favouriteBox(sub,starred,changedStars);row.addView(star,new LinearLayout.LayoutParams(ui.px(60),-1));
                    box.setNextFocusRightId(star.getId());star.setNextFocusLeftId(box.getId());
            }
            if(subs.isEmpty())children.addView(ui.text(Ui.tr("No subcategories"),21,false));
            CheckBox parent=parentBoxes.get(c.key);
            CheckBox parentStar=parentStars.get(c.key);
            if(first!=null && parentStar!=null){parentStar.setNextFocusRightId(first.getId());first.setNextFocusLeftId(parentStar.getId());}
            childrenScroll.scrollTo(0,0);repaint[0].run();
        };
        for(CulturalData.Category c:parents) {
            CheckBox box=ui.categoryBox(c.name,24,()->{Set<String> next=CulturalData.toggle(model.catalog,draft,c);draft.clear();draft.addAll(next);repaint[0].run();});
            parentBoxes.put(c.key,box);boxes.put(c.key,box);
            box.setOnFocusChangeListener((v,focused)->{ui.focusBox(box,focused);if(focused)showChildren.accept(c);});
            LinearLayout row=new LinearLayout(activity);LinearLayout.LayoutParams rowSize=new LinearLayout.LayoutParams(-1,ui.px(57));rowSize.setMargins(0,0,0,ui.px(3));categoryList.addView(row,rowSize);
            row.addView(box,new LinearLayout.LayoutParams(0,-1,1));
            CheckBox star=ui.favouriteBox(c,starred,changedStars);
            parentStars.put(c.key,star);box.setNextFocusRightId(star.getId());star.setNextFocusLeftId(box.getId());
            star.setOnFocusChangeListener((v,focused)->{ui.focusBox(star,focused);if(focused)showChildren.accept(c);});
            row.addView(star,new LinearLayout.LayoutParams(ui.px(60),-1));
        }
        TextView placeholder=ui.text(model.fetching?Ui.tr("Loading events…"):Ui.tr("Events could not be loaded."),23,false);
        if(parents.isEmpty())categoryList.addView(placeholder);
        if(!parents.isEmpty())showChildren.accept(parents.get(0));
        repaint[0].run();
        ui.root.addView(toolbar,new LinearLayout.LayoutParams(-1,ui.px(66)));
        LinearLayout footer=new LinearLayout(activity);
        TextView save=ui.button(footer,Ui.tr("Save"),()->{
            boolean all=!parents.isEmpty();for(CulturalData.Category c:parents)if(!draft.contains(c.key))all=false;
            for(CulturalData.Category c:model.catalog.categories)if(changedStars.contains(c.key))model.likes.set(model.catalog,c,starred.contains(c.key));
            model.saveLikes();model.select(all?Collections.singleton(CulturalData.ALL):draft);ui.dialog.dismiss();
        });save.setEnabled(!parents.isEmpty());
        ui.button(footer,Ui.tr("Cancel"),ui.dialog::dismiss);ui.root.addView(footer,new LinearLayout.LayoutParams(-1,ui.px(70)));
        ui.show(1220,790);if(!parentBoxes.isEmpty())parentBoxes.values().iterator().next().requestFocus();
        else {
            Runnable watch=()->{if(!ui.dialog.isShowing())return;if(!model.catalog.categories.isEmpty()){ui.dialog.dismiss();settings(activity,model);}
                else placeholder.setText(model.fetching?Ui.tr("Loading events…"):Ui.tr("Events could not be loaded."));};
            ui.dialog.setOnDismissListener(d->model.removeListener(watch));model.addListener(watch);
        }
    }
    private CheckBox favouriteBox(CulturalData.Category category,Set<String> starred,Set<String> changed) {
        CheckBox star=categoryBox("",30,()->{});
        StateListDrawable icon=new StateListDrawable();
        icon.addState(new int[]{android.R.attr.state_checked},roundedStar(CalendarAgenda.ATTENTION));
        icon.addState(new int[]{},roundedStar(0x66FFFFFF));
        star.setButtonTintList(null);star.setButtonDrawable(icon);
        star.setGravity(Gravity.CENTER_VERTICAL);star.setPadding(px(15),0,px(15),0);
        star.setChecked(starred.contains(category.key));star.setContentDescription(Ui.tr("Favourite")+": "+category.name);
        star.setOnClickListener(v->{if(!starred.add(category.key))starred.remove(category.key);changed.add(category.key);
            star.setChecked(starred.contains(category.key));});
        return star;
    }
    private Drawable roundedStar(int color) {
        return new Drawable() {
            final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            final android.graphics.Path path=new android.graphics.Path();
            {
                paint.setColor(color);paint.setPathEffect(new android.graphics.CornerPathEffect(2));
                for(int i=0;i<10;i++) {
                    double angle=-Math.PI/2+i*Math.PI/5;float radius=i%2==0?14:6.7f;
                    float x=16+(float)Math.cos(angle)*radius,y=16+(float)Math.sin(angle)*radius;
                    if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
                }
                path.close();
            }
            @Override public void draw(android.graphics.Canvas canvas) {
                android.graphics.Rect bounds=getBounds();canvas.save();canvas.translate(bounds.left,bounds.top);
                canvas.scale(bounds.width()/32f,bounds.height()/32f);canvas.drawPath(path,paint);canvas.restore();
            }
            @Override public int getIntrinsicWidth(){return px(30);}
            @Override public int getIntrinsicHeight(){return px(30);}
            @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
            @Override public void setColorFilter(android.graphics.ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
            @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
        };
    }
    private CheckBox categoryBox(String name,int size,Runnable action) {
        CheckBox box=new CheckBox(activity);box.setId(View.generateViewId());box.setText(name);box.setTextColor(Color.WHITE);
        box.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));box.setTypeface(Ui.medium);box.setPadding(px(8),0,px(10),0);
        box.setMaxLines(2);box.setEllipsize(android.text.TextUtils.TruncateAt.END);box.setFocusable(true);box.setFocusableInTouchMode(true);
        box.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFFA5D7FF));
        box.setOnFocusChangeListener((v,focused)->focusBox(box,focused));box.setOnClickListener(v->action.run());return box;
    }
    private void focusBox(CheckBox box,boolean focused) {box.setBackground(shape(focused?0xFF303B4C:0x00000000,focused?0xFFFFFFFF:0,13));}
    static void favourites(Activity activity,CulturalEvents model) {
        CulturalDialogs ui=new CulturalDialogs(activity,Ui.tr("Your interests"));
        ui.root.addView(ui.text(Ui.tr("Select a preference to remove it."),22,false),new LinearLayout.LayoutParams(-1,ui.px(44)));
        ScrollView scroll=new ScrollView(activity);LinearLayout list=new LinearLayout(activity);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        ui.root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(model.likes.tags.isEmpty() && model.likes.blocked.isEmpty() && model.likes.less.isEmpty() && model.likes.hiddenEvents.isEmpty() && model.likes.venues.isEmpty() && model.likes.blockedVenues.isEmpty() && model.likes.artists.isEmpty())list.addView(ui.text(Ui.tr("Choose interests from an event's details."),24,false));
        for(boolean excluded:new boolean[]{false,true}) {
            Map<String,String> values=excluded?model.likes.blocked:model.likes.tags;
            if(values.isEmpty())continue;
            TextView label=ui.text(Ui.tr(excluded?"Disliked interests":"Liked interests"),23,true);label.setPadding(ui.px(8),ui.px(12),0,ui.px(6));list.addView(label);
            List<String> keys=new ArrayList<>(values.keySet());keys.sort(Comparator.comparing(key->model.likes.label(model.catalog,key),String.CASE_INSENSITIVE_ORDER));
            for(String key:keys) {
                LinearLayout row=new LinearLayout(activity);list.addView(row);
                ui.button(row,model.likes.label(model.catalog,key),()->{values.remove(key);model.saveLikes();ui.dialog.dismiss();favourites(activity,model);});
            }
        }
        ui.preferenceRows(list,Ui.tr("Favourite venues"),model.likes.venues,model);
        ui.preferenceRows(list,Ui.tr("Excluded venues"),model.likes.blockedVenues,model);
        ui.preferenceRows(list,Ui.tr("Favourite artists"),model.likes.artists,model);
        ui.preferenceRows(list,Ui.tr("Styles shown less often"),model.likes.less,model);
        ui.preferenceRows(list,Ui.tr("Hidden events"),model.likes.hiddenEvents,model);
        LinearLayout footer=new LinearLayout(activity);ui.button(footer,Ui.tr("Close"),ui.dialog::dismiss);ui.root.addView(footer);ui.show(1120,700);
    }
    /** Each thumb first asks for the scope; no ambiguous dislike that silently excludes a whole genre. */
    static void actions(Activity activity,CulturalEvents model,CulturalData.Event event,boolean negative,Runnable changed,Runnable hidden) {
        CulturalDialogs ui=new CulturalDialogs(activity,Ui.tr(negative?"Adjust recommendations":"Choose your interests"));
        TextView hint=ui.text(negative?Ui.tr("Hide this event")+" · "+Ui.tr("Show less of this style")+" · "+Ui.tr("Exclude this venue"):
                Ui.tr("Prioritise a style, follow an artist, or favour this venue."),22,false);
        ui.root.addView(hint,new LinearLayout.LayoutParams(-1,ui.px(60)));
        List<CulturalData.Category> styles=CulturalLikes.choices(model.catalog,event);
        boolean hasStyles=!styles.isEmpty(),hasVenue=!event.venueId.isEmpty() && !event.venueName.isEmpty();
        String style=styles.isEmpty()?"—":styles.get(0).name;
        LinearLayout choices=new LinearLayout(activity);
        ui.root.addView(choices,new LinearLayout.LayoutParams(-1,0,1));
        if(negative) {
            ui.interestCard(choices,"hide",Ui.tr("Hide this event"),event.title,()->{model.likes.hide(event,true);model.saveLikes();ui.dialog.dismiss();hidden.run();}).setEnabled(!event.id.isEmpty());
            ui.interestCard(choices,"reduce",Ui.tr("Show less of this style"),style,()->{ui.dialog.dismiss();stylePicker(activity,model,event,1,changed);}).setEnabled(hasStyles);
            ui.interestCard(choices,"venue_block",Ui.tr("Exclude this venue"),hasVenue?event.venueName:"—",()->{ui.dialog.dismiss();venuePicker(activity,model,event,true,changed);}).setEnabled(hasVenue);
        } else {
            List<String> candidates=CulturalArtists.candidates(event);
            String artist=candidates.isEmpty()?"—":CulturalPresentation.capital(candidates.get(0),Ui.locale());
            if(!candidates.isEmpty() && CulturalData.normalise(event.title).equals(candidates.get(0)))artist=event.title;
            ui.interestCard(choices,"style",Ui.tr("I'm interested in this style"),styles.isEmpty()?"—":styles.get(0).name,()->{ui.dialog.dismiss();stylePicker(activity,model,event,0,changed);}).setEnabled(hasStyles);
            ui.interestCard(choices,"music",Ui.tr("Follow artist"),artist,()->{ui.dialog.dismiss();artistPicker(activity,model,event,changed);});
            ui.interestCard(choices,"venue",Ui.tr("Prioritise this venue"),hasVenue?event.venueName:"—",()->{ui.dialog.dismiss();venuePicker(activity,model,event,false,changed);}).setEnabled(hasVenue);
        }
        ui.show(1120,430);
    }
    private View interestCard(LinearLayout row,String icon,String label,String target,Runnable action) {
        LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);
        card.setPadding(px(18),px(20),px(18),px(20));card.setId(View.generateViewId());card.setFocusable(true);card.setFocusableInTouchMode(true);
        ImageView symbol=new ImageView(activity);symbol.setImageDrawable(detailIcon(icon,Color.WHITE,56));
        card.addView(symbol,new LinearLayout.LayoutParams(px(64),px(64)));
        TextView title=text(label,24,true);title.setGravity(Gravity.CENTER);title.setMaxLines(2);
        LinearLayout.LayoutParams titleSize=new LinearLayout.LayoutParams(-1,px(68));titleSize.setMargins(0,px(12),0,0);card.addView(title,titleSize);
        TextView name=text(CulturalPresentation.line(target),20,false);name.setGravity(Gravity.CENTER);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(name,new LinearLayout.LayoutParams(-1,px(52)));
        card.setContentDescription(label+": "+target);card.setBackground(shape(0xFF242B35,0,18));
        card.setOnFocusChangeListener((v,focused)->{card.setBackground(shape(focused?0xFFA5D7FF:0xFF242B35,focused?Color.WHITE:0,18));
            int color=focused?0xFF102234:Color.WHITE;title.setTextColor(color);name.setTextColor(color);symbol.setImageDrawable(detailIcon(icon,color,56));});
        card.setOnClickListener(v->action.run());LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-1,1);size.setMargins(px(5),px(8),px(5),0);row.addView(card,size);
        return card;
    }
    private static void stylePicker(Activity activity,CulturalEvents model,CulturalData.Event event,int mode,Runnable changed) {
        String title=mode==0?"I'm interested in this style":mode==1?"Show less of this style":"Exclude this style";
        CulturalDialogs ui=new CulturalDialogs(activity,Ui.tr(title));
        ui.root.addView(ui.text(Ui.tr(mode==0?"Matching events move ahead of neutral suggestions.":mode==1?"Matching events stay available, with lower priority. Followed artists can still take priority.":"Matching events will be hidden, even for followed artists."),22,false),new LinearLayout.LayoutParams(-1,ui.px(76)));
        Map<String,String> current=mode==0?model.likes.tags:mode==1?model.likes.less:model.likes.blocked;
        Set<String> draft=new HashSet<>(current.keySet());List<CulturalData.Category> choices=new ArrayList<>(CulturalLikes.choices(model.catalog,event));
        for(CulturalData.Category c:model.catalog.categories)if(c.parent.isEmpty() && current.containsKey(c.key) && event.categories.contains(c.key) && !choices.contains(c))choices.add(0,c);
        ScrollView scroll=new ScrollView(activity);LinearLayout rows=new LinearLayout(activity);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);ui.root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        CheckBox first=null;
        for(CulturalData.Category c:choices) {CheckBox box=ui.categoryBox(CulturalLikes.label(model.catalog,c),25,()->{if(!draft.add(c.key))draft.remove(c.key);});
            box.setChecked(draft.contains(c.key));rows.addView(box,new LinearLayout.LayoutParams(-1,ui.px(68)));if(first==null)first=box;}
        if(choices.isEmpty())rows.addView(ui.text(Ui.tr("No standard preference tags available."),24,false));
        LinearLayout footer=new LinearLayout(activity);
        ui.button(footer,Ui.tr("Save"),()->{for(CulturalData.Category c:choices) {
            if(mode==0)model.likes.set(model.catalog,c,draft.contains(c.key));else if(mode==1)model.likes.setLess(model.catalog,c,draft.contains(c.key));else model.likes.setBlocked(model.catalog,c,draft.contains(c.key));
        }model.saveLikes();ui.dialog.dismiss();}).setEnabled(!choices.isEmpty());
        ui.button(footer,Ui.tr("Cancel"),ui.dialog::dismiss);ui.root.addView(footer);ui.dialog.setOnDismissListener(d->changed.run());
        ui.show(1120,Math.min(760,360+Math.max(1,choices.size())*68));if(first!=null)first.requestFocus();
    }
    private static void venuePicker(Activity activity,CulturalEvents model,CulturalData.Event event,boolean exclude,Runnable changed) {
        CulturalDialogs ui=new CulturalDialogs(activity,Ui.tr(exclude?"Exclude this venue":"Prioritise this venue"));
        CheckBox box=ui.categoryBox(event.venueName,26,()->{});box.setChecked((exclude?model.likes.blockedVenues:model.likes.venues).containsKey(event.venueId));
        ui.root.addView(box,new LinearLayout.LayoutParams(-1,ui.px(90)));
        LinearLayout footer=new LinearLayout(activity);ui.button(footer,Ui.tr("Save"),()->{model.likes.venue(event,exclude,box.isChecked());model.saveLikes();ui.dialog.dismiss();});
        ui.button(footer,Ui.tr("Cancel"),ui.dialog::dismiss);ui.root.addView(footer);ui.dialog.setOnDismissListener(d->changed.run());ui.show(1000,330);box.requestFocus();
    }
    private void preferenceRows(LinearLayout list,String heading,Map<String,String> values,CulturalEvents model) {
        if(values.isEmpty())return;TextView title=text(heading,23,true);title.setPadding(px(8),px(12),0,px(6));list.addView(title);
        List<String> keys=new ArrayList<>(values.keySet());keys.sort(Comparator.comparing(values::get,String.CASE_INSENSITIVE_ORDER));
        for(String key:keys){LinearLayout row=new LinearLayout(activity);list.addView(row);
            button(row,values.get(key),()->{if(values==model.likes.artists)model.likes.artist(values.get(key),false);else values.remove(key);model.saveLikes();dialog.dismiss();favourites(activity,model);});}
    }
    private static void artistPicker(Activity activity,CulturalEvents model,CulturalData.Event event,Runnable dismissed) {
        CulturalDialogs ui=new CulturalDialogs(activity,Ui.tr("Follow artist"));
        ui.root.addView(ui.text(Ui.tr("Choose names to follow. Suggestions may include artists only mentioned in the text."),22,false),new LinearLayout.LayoutParams(-1,ui.px(76)));
        ScrollView scroll=new ScrollView(activity);LinearLayout list=new LinearLayout(activity);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        ui.root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        java.util.concurrent.atomic.AtomicBoolean active=new java.util.concurrent.atomic.AtomicBoolean(true);
        Map<String,CulturalArtists.Artist> suggested=new LinkedHashMap<>();Map<String,CheckBox> artistBoxes=new HashMap<>();
        Set<String> artistDraft=new HashSet<>(),artistChanged=new HashSet<>();
        LinearLayout artistChoices=new LinearLayout(activity);artistChoices.setOrientation(LinearLayout.VERTICAL);
        TextView title=ui.text(Ui.tr("Suggested artists"),23,true);title.setPadding(0,ui.px(12),0,ui.px(6));list.addView(title);
        TextView artistStatus=ui.text(Ui.tr("Looking for artists…"),19,false);list.addView(artistStatus);list.addView(artistChoices);
        TextView hint=ui.text(Ui.tr("Artist name (optional)"),21,true);hint.setPadding(0,ui.px(8),0,ui.px(8));list.addView(hint);
        EditText artist=new EditText(activity);artist.setTextColor(Color.WHITE);artist.setHintTextColor(0xFFB5BDC8);artist.setHint(Ui.tr("Name to find in titles and descriptions"));
        artist.setTextSize(TypedValue.COMPLEX_UNIT_PX,ui.px(23));artist.setSingleLine(true);artist.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        artist.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});list.addView(artist,new LinearLayout.LayoutParams(-1,ui.px(62)));
        LinearLayout footer=new LinearLayout(activity);
        TextView save=ui.button(footer,Ui.tr("Save"),()->{
            for(String id:artistChanged){CulturalArtists.Artist suggestion=suggested.get(id);model.likes.resolvedArtist(suggestion.name,suggestion.id,suggestion.aliases,artistDraft.contains(id));}
            model.likes.artist(artist.getText().toString(),true);model.saveLikes();ui.dialog.dismiss();
        });
        ui.button(footer,Ui.tr("Cancel"),ui.dialog::dismiss);ui.root.addView(footer);
        ui.dialog.setOnDismissListener(d->{active.set(false);dismissed.run();});ui.show(1120,800);
        if(ui.dialog.getWindow()!=null)ui.dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);save.requestFocus();
        model.suggestArtists(event,active,result->{
            for(CulturalArtists.Artist suggestion:result.artists) {
                if(suggested.containsKey(suggestion.id))continue;suggested.put(suggestion.id,suggestion);
                String key=CulturalData.normalise(suggestion.name);
                boolean checked=model.likes.artists.containsKey(key) && suggestion.id.equals(model.likes.artistIds.get(key));
                if(checked)artistDraft.add(suggestion.id);
                CheckBox box=ui.categoryBox(Ui.tr("Artist")+" · "+suggestion.name+(suggestion.detail.isEmpty()?"":"\n"+suggestion.detail),23,()->{
                    boolean selected=artistDraft.add(suggestion.id);if(!selected)artistDraft.remove(suggestion.id);
                    artistChanged.add(suggestion.id);
                    if(selected)for(CulturalArtists.Artist other:suggested.values())if(!other.id.equals(suggestion.id) && key.equals(CulturalData.normalise(other.name))) {
                        artistDraft.remove(other.id);artistChanged.remove(other.id);CheckBox otherBox=artistBoxes.get(other.id);if(otherBox!=null)otherBox.setChecked(false);
                    }
                });
                box.setChecked(checked);artistBoxes.put(suggestion.id,box);
                LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,ui.px(suggestion.detail.isEmpty()?58:78));size.setMargins(0,ui.px(4),0,0);artistChoices.addView(box,size);
            }
            artistStatus.setText(Ui.tr(!result.done?"Looking for artists…":result.incomplete?"Artist search incomplete. You can still choose suggestions or enter a name.":result.artists.isEmpty()?"No matching artists found. You can enter a name below.":"Artist names checked with MusicBrainz. Select only those you want to follow."));
        });
    }
    static void details(Activity activity,CulturalEvents model,CulturalData.Hit hit) {
        CulturalData.Event e=hit.event;
        CulturalDialogs ui=new CulturalDialogs(activity,e.title);
        TextView heading=(TextView)ui.root.getChildAt(0);ui.root.removeView(heading);
        LinearLayout content=new LinearLayout(activity);
        LinearLayout left=new LinearLayout(activity);left.setOrientation(LinearLayout.VERTICAL);
        content.addView(left,new LinearLayout.LayoutParams(0,-1,1));
        heading.setSingleLine(false);heading.setMaxLines(2);left.addView(heading,new LinearLayout.LayoutParams(-1,ui.px(100)));
        String date=CulturalPresentation.capital(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy",Ui.locale()).format(hit.date),Ui.locale());
        ui.infoLine(left,"calendar",date+(e.ranges.size()>1?" · "+Ui.tr("Multiple dates"):""),0xFFA5D7FF);
        String address=CulturalPresentation.line(e.venueName+(!e.venueName.isEmpty() && !e.address.isEmpty()?" · ":"")+e.address);
        if(!address.isEmpty())ui.infoLine(left,"location",address,0xFFF5F6FA);
        TextView body=ui.text(e.description,22,false);body.setEllipsize(android.text.TextUtils.TruncateAt.END);
        body.setPadding(0,0,ui.px(12),0);
        LinearLayout.LayoutParams descriptionSize=new LinearLayout.LayoutParams(-1,0,1);descriptionSize.setMargins(0,ui.px(32),0,0);
        left.addView(body,descriptionSize);
        LinearLayout preferences=new LinearLayout(activity);ImageButton[] like={null};
        like[0]=ui.thumbButton(preferences,true,()->actions(activity,model,e,false,
                ()->like[0].setContentDescription(Ui.tr(model.likes.liked(e)?"Edit interests":"Like…")),ui.dialog::dismiss));
        ui.thumbButton(preferences,false,()->actions(activity,model,e,true,
                ()->like[0].setContentDescription(Ui.tr(model.likes.liked(e)?"Edit interests":"Like…")),ui.dialog::dismiss));
        LinearLayout mobile=new LinearLayout(activity);mobile.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mobileSize=new LinearLayout.LayoutParams(ui.px(300),-1);mobileSize.setMargins(ui.px(30),0,0,0);content.addView(mobile,mobileSize);
        ImageView artwork=new ImageView(activity);artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setBackground(ui.shape(0xFF242B35,0,18));artwork.setClipToOutline(true);artwork.setContentDescription(e.title);
        mobile.addView(artwork,new LinearLayout.LayoutParams(-1,ui.px(169)));
        TextView price=ui.text(e.free?Ui.tr("Free admission"):e.price.isEmpty()?"—":CulturalPresentation.line(e.price),21,true);
        price.setGravity(Gravity.CENTER);price.setMaxLines(4);price.setEllipsize(android.text.TextUtils.TruncateAt.END);
        price.setPadding(ui.px(14),ui.px(12),ui.px(14),ui.px(12));
        price.setBackground(ui.shape(CulturalPresentation.priceColor(e.price,e.free),0,18));
        LinearLayout.LayoutParams priceSize=new LinearLayout.LayoutParams(-1,-2);priceSize.setMargins(0,ui.px(16),0,ui.px(16));mobile.addView(price,priceSize);
        ImageView qr=new ImageView(activity);qr.setScaleType(ImageView.ScaleType.FIT_CENTER);qr.setPadding(ui.px(12),ui.px(12),ui.px(12),ui.px(12));
        GradientDrawable qrBackground=new GradientDrawable();qrBackground.setColor(Color.WHITE);qrBackground.setCornerRadius(ui.px(18));qr.setBackground(qrBackground);qr.setClipToOutline(true);
        qr.setContentDescription(Ui.tr("QR code to open this event on your phone"));
        int qrSize=ui.px(300);mobile.addView(qr,new LinearLayout.LayoutParams(-1,qrSize));
        for(int i=0;i<preferences.getChildCount();i++) {
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,ui.px(60),1);
            size.setMargins(i==0?0:ui.px(6),ui.px(16),i==0?ui.px(6):0,0);
            preferences.getChildAt(i).setLayoutParams(size);
        }
        mobile.addView(preferences,new LinearLayout.LayoutParams(-1,ui.px(76)));
        // The media stack defines the popup height; prose can only consume the space left beside it.
        mobile.measure(View.MeasureSpec.makeMeasureSpec(ui.px(300),View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int contentHeight=mobile.getMeasuredHeight();
        int descriptionHeight=contentHeight-ui.px(100)-ui.px(44)-(address.isEmpty()?0:ui.px(44))-ui.px(32);
        body.setMaxLines(Math.max(1,descriptionHeight/Math.max(1,body.getLineHeight())));
        ui.root.addView(content,new LinearLayout.LayoutParams(-1,contentHeight));
        android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
        QR_WORKER.execute(()->{
            android.graphics.Bitmap bitmap=null;
            try {int[] pixels=CulturalQr.pixels(e.url,qrSize-ui.px(24));bitmap=android.graphics.Bitmap.createBitmap(pixels,qrSize-ui.px(24),qrSize-ui.px(24),android.graphics.Bitmap.Config.ARGB_8888);}
            catch(Exception | OutOfMemoryError ignored) {}
            final android.graphics.Bitmap ready=bitmap;
            main.post(()->{
                if(!ui.dialog.isShowing()){if(ready!=null)ready.recycle();return;}
                if(ready==null){qr.setVisibility(View.GONE);qr.setContentDescription(Ui.tr("QR code could not be created."));return;}
                BitmapDrawable drawable=new BitmapDrawable(activity.getResources(),ready);drawable.setFilterBitmap(false);drawable.setDither(false);qr.setImageDrawable(drawable);
            });
        });
        final android.graphics.Bitmap[] shown={null};
        Runnable refreshArtwork=()->{android.graphics.Bitmap image=model.image(e.image);if(image!=shown[0]){shown[0]=image;artwork.setImageBitmap(image);}};
        ui.dialog.setOnDismissListener(d->model.removeListener(refreshArtwork));
        ui.show(1400,(int)Math.ceil((contentHeight+ui.root.getPaddingTop()+ui.root.getPaddingBottom())/ui.scale));
        like[0].requestFocus();model.addListener(refreshArtwork);model.prepare(Collections.singletonList(hit),0,1);
    }
    private void infoLine(LinearLayout parent,String icon,String value,int color) {
        TextView line=text(value,24,true);line.setTextColor(color);line.setSingleLine(true);line.setEllipsize(android.text.TextUtils.TruncateAt.END);
        line.setGravity(Gravity.CENTER_VERTICAL);line.setCompoundDrawablePadding(px(12));Drawable symbol=detailIcon(icon,color,27);
        symbol.setBounds(0,0,px(30),px(30));line.setCompoundDrawables(symbol,null,null,null);
        parent.addView(line,new LinearLayout.LayoutParams(-1,px(44)));
    }
    private Drawable detailIcon(String kind,int color,int size) {
        return new Drawable(){
            final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            final android.graphics.Path path=new android.graphics.Path();
            {
                paint.setColor(color);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(1.8f);
                paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
                if(kind.equals("calendar")) {
                    path.addRoundRect(3,5,21,22,2,2,android.graphics.Path.Direction.CW);
                    path.moveTo(3,10);path.lineTo(21,10);path.moveTo(7,2);path.lineTo(7,7);path.moveTo(17,2);path.lineTo(17,7);
                    path.addCircle(8,15,.5f,android.graphics.Path.Direction.CW);path.addCircle(15,15,.5f,android.graphics.Path.Direction.CW);
                } else if(kind.equals("location")) {
                    path.moveTo(12,22);path.cubicTo(9,18,4,13,4,9);path.cubicTo(4,-1,20,-1,20,9);path.cubicTo(20,13,15,18,12,22);path.close();
                    path.addCircle(12,9,3,android.graphics.Path.Direction.CW);
                } else if(kind.equals("music")) {
                    path.moveTo(9,18);path.lineTo(9,5);path.lineTo(21,2);path.lineTo(21,15);
                    path.moveTo(9,9);path.lineTo(21,6);path.addOval(3,16,9,21,android.graphics.Path.Direction.CW);path.addOval(15,13,21,18,android.graphics.Path.Direction.CW);
                } else if(kind.equals("hide")) {
                    path.moveTo(2,12);path.quadTo(12,-1,22,12);path.quadTo(12,25,2,12);path.close();
                    path.addCircle(12,12,3,android.graphics.Path.Direction.CW);path.moveTo(3,3);path.lineTo(21,21);
                } else if(kind.equals("reduce")) {
                    path.moveTo(3,5);path.lineTo(10,12);path.lineTo(14,8);path.lineTo(21,17);
                    path.moveTo(14,17);path.lineTo(21,17);path.lineTo(21,10);
                } else if(kind.equals("venue") || kind.equals("venue_block")) {
                    path.moveTo(2,8);path.lineTo(12,2);path.lineTo(22,8);path.close();path.moveTo(3,22);path.lineTo(21,22);
                    for(int x=5;x<=19;x+=7){path.moveTo(x,11);path.lineTo(x,19);}
                } else {
                    path.moveTo(3,3);path.lineTo(13,3);path.lineTo(23,13);path.lineTo(13,23);path.lineTo(3,13);path.close();
                    path.addCircle(8,8,1.5f,android.graphics.Path.Direction.CW);
                }
                if(kind.endsWith("_block")){path.moveTo(3,21);path.lineTo(21,3);}
            }
            @Override public int getIntrinsicWidth(){return px(size);}
            @Override public int getIntrinsicHeight(){return px(size);}
            @Override public void draw(android.graphics.Canvas canvas){android.graphics.Rect b=getBounds();canvas.save();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);canvas.drawPath(path,paint);canvas.restore();}
            @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
            @Override public void setColorFilter(android.graphics.ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
            @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
        };
    }
    static void calendarDay(Activity activity,HomeCalendars calendars,java.time.LocalDate day,java.util.function.Consumer<CalendarAgenda> loaded) {
        String date=CulturalPresentation.capital(day.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy",CalendarAgenda.SPANISH)),CalendarAgenda.SPANISH);
        CulturalDialogs ui=new CulturalDialogs(activity,date);
        ScrollView scroll=new ScrollView(activity);scroll.setVerticalScrollBarEnabled(false);scroll.setFocusable(true);
        LinearLayout list=new LinearLayout(activity);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        list.addView(ui.text("Cargando eventos…",23,false));ui.root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        ui.show(1080,700);scroll.requestFocus();
        calendars.month(java.time.YearMonth.from(day),agenda->{
            loaded.accept(agenda);if(!ui.dialog.isShowing())return;list.removeAllViews();
            boolean incomplete=!agenda.available || agenda.sources.stream().anyMatch(source->!source.available);
            if(incomplete)list.addView(ui.text("Algunos calendarios no están disponibles.",21,false));
            List<CalendarAgenda.Event> events=agenda.on(day);
            if(events.isEmpty())list.addView(ui.text(incomplete?"No se han podido consultar todos los eventos.":"No hay eventos para este día.",24,false));
            for(CalendarAgenda.Event event:events) {
                LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(ui.px(22),ui.px(18),ui.px(22),ui.px(18));
                card.setBackground(ui.shape(0xFF242B35,0,18));
                TextView calendar=ui.text(CalendarAgenda.panelName(event.calendar,event.name),20,true);calendar.setTextColor(event.color);
                card.addView(calendar);
                TextView title=ui.text(event.title,26,true);title.setPadding(0,ui.px(8),0,ui.px(8));card.addView(title);
                String time=event.allDay?"Todo el día":event.start.format(DateTimeFormatter.ofPattern("HH:mm"))+" – "+event.end.format(DateTimeFormatter.ofPattern("HH:mm"));
                card.addView(ui.text(time,22,false));LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.setMargins(0,ui.px(12),0,0);list.addView(card,size);
            }
        });
    }
    static void experiences(Activity activity,ExperienceBoard board,Runnable changed) {
        CulturalDialogs ui=new CulturalDialogs(activity,Ui.tr("The experience chalkboard"));
        LinearLayout toolbar=new LinearLayout(activity);
        TextView add=ui.button(toolbar,Ui.tr("Add experience"),()->{
            CulturalDialogs editor=new CulturalDialogs(activity,Ui.tr("Add experience"));
            EditText input=new EditText(activity);input.setTextColor(Color.WHITE);input.setHintTextColor(0x99FFFFFF);
            input.setHint(Ui.tr("Experience name"));input.setSingleLine(true);input.setTextSize(TypedValue.COMPLEX_UNIT_PX,editor.px(28));
            input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(ExperienceData.TEXT_LIMIT)});
            input.setPadding(editor.px(18),editor.px(12),editor.px(18),editor.px(12));input.setBackground(editor.shape(0xFF242B35,0,14));
            editor.root.addView(input,new LinearLayout.LayoutParams(-1,editor.px(66)));
            LinearLayout actions=new LinearLayout(activity);
            TextView save=editor.button(actions,Ui.tr("Save"),()->{
                if(!board.data.add(input.getText().toString())){input.setError(Ui.tr("Experience name"));return;}
                board.save();changed.run();editor.dialog.dismiss();ui.dialog.dismiss();experiences(activity,board,changed);
            });
            input.addTextChangedListener(new android.text.TextWatcher(){
                public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                public void onTextChanged(CharSequence s,int start,int before,int count){save.setEnabled(!ExperienceData.clean(s.toString()).isEmpty());}
                public void afterTextChanged(android.text.Editable s){}
            });save.setEnabled(false);
            editor.button(actions,Ui.tr("Cancel"),editor.dialog::dismiss);editor.root.addView(actions);
            editor.show(1100,300);input.requestFocus();
            editor.dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });add.setEnabled(board.data.items.size()<ExperienceData.MAX);
        ui.button(toolbar,Ui.tr("Photo album"),()->{
            CulturalDialogs editor=new CulturalDialogs(activity,Ui.tr("Photo album"));
            EditText input=new EditText(activity);input.setTextColor(Color.WHITE);input.setSingleLine(true);input.setText(board.album());
            input.setHint("https://photos.app.goo.gl/…");input.setTextSize(TypedValue.COMPLEX_UNIT_PX,editor.px(24));
            editor.root.addView(input,new LinearLayout.LayoutParams(-1,editor.px(70)));
            LinearLayout actions=new LinearLayout(activity);
            editor.button(actions,Ui.tr("Save"),()->{
                String value=input.getText().toString().trim();if(!ExperiencePhotos.valid(value)){input.setError(Ui.tr("Use a shared Google Photos album link"));return;}
                board.album(value);changed.run();editor.dialog.dismiss();
            });
            editor.button(actions,Ui.tr("Remove photos"),()->{board.album("");changed.run();editor.dialog.dismiss();});
            editor.button(actions,Ui.tr("Cancel"),editor.dialog::dismiss);editor.root.addView(actions);editor.show(1200,320);input.requestFocus();
        });
        ui.root.addView(toolbar,new LinearLayout.LayoutParams(-1,ui.px(80)));
        ScrollView scroll=new ScrollView(activity);LinearLayout list=new LinearLayout(activity);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        ui.root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        for(ExperienceData.Item item:board.data.items){
            LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name=ui.text(item.text,25,false);name.setGravity(Gravity.CENTER_VERTICAL);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name,new LinearLayout.LayoutParams(0,ui.px(66),4));
            TextView delete=ui.button(row,Ui.tr("Delete"),()->{
                CulturalDialogs confirm=new CulturalDialogs(activity,Ui.tr("Delete experience?"));
                TextView label=confirm.text(item.text,27,false);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);confirm.root.addView(label,new LinearLayout.LayoutParams(-1,confirm.px(86)));
                LinearLayout buttons=new LinearLayout(activity);
                confirm.button(buttons,Ui.tr("Delete"),()->{board.data.remove(item.id);board.save();changed.run();confirm.dialog.dismiss();ui.dialog.dismiss();experiences(activity,board,changed);});
                TextView cancel=confirm.button(buttons,Ui.tr("Cancel"),confirm.dialog::dismiss);confirm.root.addView(buttons);confirm.show(1000,310);cancel.requestFocus();
            });delete.setContentDescription(Ui.tr("Delete")+": "+item.text);
            list.addView(row,new LinearLayout.LayoutParams(-1,ui.px(78)));
        }
        ui.show(1200,760);add.requestFocus();
    }
    private void show() { show(1400,870); }
    private static void reason(Activity activity,CulturalEvents model,CulturalData.Hit hit,Runnable hidden) {
        CulturalDialogs ui=new CulturalDialogs(activity,Ui.tr("Why this event?"));
        ScrollView scroll=new ScrollView(activity);TextView text=ui.text(model.reasonDetails(hit),25,false);text.setPadding(0,ui.px(12),0,ui.px(18));scroll.addView(text);
        ui.root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=new LinearLayout(activity);
        ui.thumbButton(footer,true,()->{ui.dialog.dismiss();actions(activity,model,hit.event,false,()->{},hidden);});
        ui.thumbButton(footer,false,()->{ui.dialog.dismiss();actions(activity,model,hit.event,true,()->{},hidden);});
        ui.button(footer,Ui.tr("Your interests"),()->{ui.dialog.dismiss();favourites(activity,model);});
        ui.button(footer,Ui.tr("Close"),ui.dialog::dismiss);ui.root.addView(footer);ui.show(1120,700);
    }
    /** Vector thumbs, no emoji/font dependency; retain spoken labels for TalkBack. */
    private ImageButton thumbButton(LinearLayout row,boolean up,Runnable action) {
        ImageButton button=new ImageButton(activity);button.setFocusable(true);button.setFocusableInTouchMode(true);
        button.setId(View.generateViewId());button.setContentDescription(Ui.tr(up?"Like…":"Dislike…"));
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);button.setPadding(px(24),px(14),px(24),px(14));
        android.graphics.Path path=new android.graphics.Path();path.addRect(2,9,6,21,android.graphics.Path.Direction.CW);
        path.moveTo(8,21);path.lineTo(18,21);path.quadTo(19.2f,21,19.7f,19.7f);path.lineTo(22,12.5f);
        path.quadTo(23,9,20,9);path.lineTo(14,9);path.lineTo(15,4);path.quadTo(15,2,13,2);path.lineTo(8,9);path.close();
        button.setImageDrawable(new android.graphics.drawable.Drawable(){
            final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override public int getIntrinsicWidth(){return 32;}
            @Override public int getIntrinsicHeight(){return 32;}
            @Override public void draw(android.graphics.Canvas canvas){
                android.graphics.Rect b=getBounds();float sx=b.width()/24f,sy=b.height()/24f;canvas.save();canvas.translate(b.exactCenterX(),b.exactCenterY());
                canvas.scale(sx,up?sy:-sy);canvas.translate(-12,-12);paint.setColor(button.hasFocus()?0xFF102234:Color.WHITE);paint.setAlpha(button.isEnabled()?255:90);canvas.drawPath(path,paint);canvas.restore();
            }
            @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
            @Override public void setColorFilter(android.graphics.ColorFilter filter){paint.setColorFilter(filter);}
            @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
        });
        button.setBackground(shape(0xFF242B35,0,18));button.setOnFocusChangeListener((v,focused)->{button.setBackground(shape(focused?0xFFA5D7FF:0xFF242B35,focused?Color.WHITE:0,18));button.invalidate();});
        button.setOnClickListener(v->action.run());LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(px(80),px(60));size.setMargins(px(5),px(8),px(5),px(4));row.addView(button,size);return button;
    }
    private void show(int width,int height) {
        dialog.show(); Window w=dialog.getWindow();
        if(w!=null) { w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(.68f); w.setLayout(px(width),px(height)); w.setGravity(Gravity.CENTER);
            w.getDecorView().setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility()); }
    }
    private TextView button(LinearLayout row,String label,Runnable action) {
        return button(row,text(label,25,true),action);
    }
    private TextView button(LinearLayout row,TextView button,Runnable action) {
        button.setGravity(Gravity.CENTER); button.setFocusable(true); button.setFocusableInTouchMode(true);
        button.setSingleLine(true);button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setId(View.generateViewId()); button.setBackground(shape(0xFF242B35,0,18));
        button.setOnFocusChangeListener((v,focused)->{
            button.setTextColor(focused?0xFF102234:Color.WHITE); button.setBackground(shape(focused?0xFFA5D7FF:0xFF242B35,focused?Color.WHITE:0,18));
        });
        button.setOnClickListener(v->action.run()); LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,px(60),1); size.setMargins(px(5),px(8),px(5),px(4)); row.addView(button,size);
        return button;
    }
    private TextView text(String value,int size,boolean bold) {
        TextView text=new TextView(activity); text.setText(value); text.setTextColor(0xFFF5F6FA); text.setTypeface(bold?Ui.semibold:Ui.regular);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size)); text.setIncludeFontPadding(false); return text;
    }
    private GradientDrawable shape(int color,int stroke,int radius) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{color,blend(color,0xFF11151D)});
        d.setCornerRadius(px(radius)); if(stroke!=0)d.setStroke(px(1),stroke); return d;
    }
    private int blend(int color,int other) {
        return Color.argb(Color.alpha(color),(Color.red(color)*3+Color.red(other))/4,(Color.green(color)*3+Color.green(other))/4,(Color.blue(color)*3+Color.blue(other))/4);
    }
    private int px(float value) { return Math.max(1,Math.round(value*scale)); }
}
