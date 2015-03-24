package jp.ne.needtec.liquidfuntest;
import android.content.Context;
import android.opengl.GLSurfaceView;

/**
 * Created by mitagaki on 2015/03/23.
 */
public class MainGlView extends GLSurfaceView {

    MainRenderer renderer;

    public MainGlView(Context context) {
        super(context);
        renderer = new MainRenderer(context);
        setRenderer(renderer);
    }
}
