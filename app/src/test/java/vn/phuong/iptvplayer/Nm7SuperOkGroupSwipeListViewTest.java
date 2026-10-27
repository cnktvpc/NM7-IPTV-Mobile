package vn.phuong.iptvplayer;

import android.app.Activity;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class Nm7SuperOkGroupSwipeListViewTest {
    private final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final FrameLayout root = new FrameLayout(activity);
        final Nm7SuperOkGroupSwipeListView list = new Nm7SuperOkGroupSwipeListView(activity);
        int changes, direction, clicks, cancels, longPresses;
        Fixture() {
            list.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return 20; }
                @Override public Object getItem(int position) { return position; }
                @Override public long getItemId(int position) { return position; }
                @Override public View getView(int position, View recycled, ViewGroup parent) {
                    TextView row = recycled instanceof TextView ? (TextView) recycled : new TextView(activity);
                    row.setText("Channel " + position);
                    row.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100));
                    row.setClickable(true);
                    row.setOnClickListener(v -> clicks++);
                    row.setOnLongClickListener(v -> { longPresses++; return true; });
                    row.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)cancels++;return false;});
                    return row;
                }
            });
            list.setOnGroupSwipeListener(value->{changes++;direction=value;return true;});
            list.setOnItemClickListener((parent, view, position, id) -> clicks++);
            list.setOnItemLongClickListener((parent, view, position, id) -> {
                longPresses++;
                return true;
            });
            list.setLongClickable(true);
            root.addView(list,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
            activity.setContentView(root);
            View decor=activity.getWindow().getDecorView();
            int w=View.MeasureSpec.makeMeasureSpec(420,View.MeasureSpec.EXACTLY);
            int h=View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY);
            decor.measure(w,h);decor.layout(0,0,420,900);
            root.measure(w,h);root.layout(0,0,420,900);
            list.measure(w,h);list.layout(0,0,420,900);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue("fixture should lay out child rows",list.getChildCount()>0);
        }
        float rowY() {
            View child=list.getChildAt(0);
            return list.getTop()+child.getTop()+Math.max(2,child.getHeight()/2f);
        }
        void send(long down,long time,int action,float x,float y) {
            MotionEvent e=MotionEvent.obtain(down,time,action,x,y,0);
            try { list.dispatchTouchEvent(e); } finally {e.recycle();}
        }
        String state(){return "changes="+changes+", direction="+direction+", clicks="+clicks+
                ", longPresses="+longPresses+", cancels="+cancels+", translationX="+list.getTranslationX()+
                ", listLeft="+list.getLeft()+", rowY="+rowY();}
    }

    @Test public void slowSwipeCancelsChildLongPressAndChangesOneGroup() {
        Fixture f=new Fixture();long d=android.os.SystemClock.uptimeMillis();float y=f.rowY();
        f.send(d,100,MotionEvent.ACTION_DOWN,330,y);
        f.send(d,160,MotionEvent.ACTION_MOVE,305,y+2);
        assertTrue("list should follow the finger; "+f.state(),f.list.getTranslationX()<0f);
        f.send(d,500,MotionEvent.ACTION_MOVE,245,y+3);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(650,TimeUnit.MILLISECONDS);
        f.send(d,1300,MotionEvent.ACTION_MOVE,180,y+3);
        f.send(d,1400,MotionEvent.ACTION_UP,175,y+3);
        assertEquals("wrong group callback: "+f.state(),1,f.changes);
        assertEquals("wrong direction: "+f.state(),1,f.direction);
        assertFalse("swiped row should not remain pressed: "+f.state(), f.list.getChildAt(0).isPressed());
        assertEquals("swipe became a click: "+f.state(),0,f.clicks);
        assertEquals("swipe became long-press: "+f.state(),0,f.longPresses);
    }

    @Test public void fastFlickSwitchesExactlyOnce() {
        Fixture f=new Fixture();long d=android.os.SystemClock.uptimeMillis();float y=f.rowY();
        f.send(d,100,MotionEvent.ACTION_DOWN,330,y);
        f.send(d,115,MotionEvent.ACTION_MOVE,305,y+1);
        f.send(d,130,MotionEvent.ACTION_UP,290,y+2);
        assertEquals("wrong group callback: "+f.state(),1,f.changes);
        assertEquals("wrong direction: "+f.state(),1,f.direction);
        assertEquals("flick became a click: "+f.state(),0,f.clicks);
    }

    @Test public void reverseSwipeMovesToPreviousGroup() {
        Fixture f=new Fixture();long d=android.os.SystemClock.uptimeMillis();float y=f.rowY();
        f.send(d,100,MotionEvent.ACTION_DOWN,80,y);
        f.send(d,150,MotionEvent.ACTION_MOVE,125,y+2);
        f.send(d,210,MotionEvent.ACTION_UP,225,y+3);
        assertEquals("wrong group callback: "+f.state(),1,f.changes);
        assertEquals("wrong direction: "+f.state(),-1,f.direction);
    }

    @Test public void diagonalStartNoiseDoesNotBlockHorizontalSwipe() {
        Fixture f=new Fixture();long d=android.os.SystemClock.uptimeMillis();float y=f.rowY();
        f.send(d,100,MotionEvent.ACTION_DOWN,330,y);
        f.send(d,150,MotionEvent.ACTION_MOVE,324,y+8);
        f.send(d,220,MotionEvent.ACTION_MOVE,260,y+11);
        f.send(d,290,MotionEvent.ACTION_UP,180,y+11);
        assertEquals("wrong group callback: "+f.state(),1,f.changes);
        assertEquals("wrong direction: "+f.state(),1,f.direction);
    }

    @Test public void verticalScrollDoesNotChangeCategoryOrTranslateList() {
        Fixture f=new Fixture();long d=android.os.SystemClock.uptimeMillis();float y=f.rowY();
        f.send(d,100,MotionEvent.ACTION_DOWN,220,y);
        f.send(d,150,MotionEvent.ACTION_MOVE,226,y+45);
        f.send(d,220,MotionEvent.ACTION_MOVE,228,y+135);
        f.send(d,280,MotionEvent.ACTION_UP,230,y+235);
        assertEquals("vertical move changed group: "+f.state(),0,f.changes);
        assertEquals("vertical move translated list: "+f.state(),0f,f.list.getTranslationX(),0.001f);
    }

    @Test public void tapStillUsesTheCardClickHandler() {
        Fixture f=new Fixture();
        // A normal tap must follow ListView's default item-click path. Remove the
        // synthetic child click/long-click callbacks used by swipe interception tests.
        View row=f.list.getChildAt(0);
        row.setOnClickListener(null);
        row.setClickable(false);
        row.setOnLongClickListener(null);
        row.setLongClickable(false);
        f.list.setOnItemLongClickListener(null);
        f.list.setLongClickable(false);

        long d=android.os.SystemClock.uptimeMillis();
        float y=f.rowY();
        f.send(d,d,MotionEvent.ACTION_DOWN,220,y);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(80,TimeUnit.MILLISECONDS);
        f.send(d,d+80,MotionEvent.ACTION_UP,221,y+1);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("tap changed group: "+f.state(),0,f.changes);
        assertEquals("tap did not reach ListView item-click handler: "+f.state(),1,f.clicks);
    }

    @Test public void multitouchCancelsSwipe() {
        Fixture f=new Fixture();long d=android.os.SystemClock.uptimeMillis();float y=f.rowY();
        f.send(d,100,MotionEvent.ACTION_DOWN,330,y);
        f.send(d,130,MotionEvent.ACTION_MOVE,290,y);
        MotionEvent p=MotionEvent.obtain(d,140,MotionEvent.ACTION_POINTER_DOWN,290,y,0);
        try { f.list.dispatchTouchEvent(p); } finally {p.recycle();}
        f.send(d,190,MotionEvent.ACTION_UP,180,y);
        assertEquals("multitouch changed group: "+f.state(),0,f.changes);
    }
}
