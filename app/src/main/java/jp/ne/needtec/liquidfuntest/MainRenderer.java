package jp.ne.needtec.liquidfuntest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

import com.google.fpl.liquidfun.BodyType;
import com.google.fpl.liquidfun.CircleShape;
import com.google.fpl.liquidfun.ParticleFlag;
import com.google.fpl.liquidfun.ParticleGroup;
import com.google.fpl.liquidfun.ParticleGroupDef;
import com.google.fpl.liquidfun.ParticleGroupFlag;
import com.google.fpl.liquidfun.ParticleSystem;
import com.google.fpl.liquidfun.ParticleSystemDef;
import com.google.fpl.liquidfun.PolygonShape;
import com.google.fpl.liquidfun.Vec2;
import com.google.fpl.liquidfun.World;
import com.google.fpl.liquidfun.Body;
import com.google.fpl.liquidfun.BodyDef;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Created by m.ita on 2015/03/23.
 */
public class MainRenderer implements GLSurfaceView.Renderer, View.OnTouchListener {
    private World world = null;
    private HashMap<Long, BodyData> mapBodyData = new HashMap<Long, BodyData>();
    private HashMap<Long, ParticleData> mapParticleData = new HashMap<Long, ParticleData>();
    private long nextBodyDataId = 1;
    private static final float TIME_STEP = 1 / 60f; // 60 fps
    private static final int VELOCITY_ITERATIONS = 6;
    private static final int POSITION_ITERATIONS = 2;
    private static final int PARTICLE_ITERATIONS = 5;
    private MainGlView view;
    private HashMap<Integer, Integer> mapResIdToTextureId = new HashMap<Integer, Integer>();

    private float mouseDownX;
    private float mouseDownY;
    private float mouseUpX;
    private float mouseUpY;

    enum MouseStatus {
        None,
        Up,
        Down
    }
    private MouseStatus mouseStatus = MouseStatus.None;
    private MouseStatus mousePrevState = MouseStatus.None;

    static {
        System.loadLibrary("liquidfun");
        System.loadLibrary("liquidfun_jni");
    }
    class ParticleData {
        long id;
        ParticleSystem particleSystem;
        float particleRadius;
        int textureId;
        ArrayList<ArrayList<Integer>> row;

        public ParticleData(long id, ParticleSystem ps, float particleRadius, ArrayList<ArrayList<Integer>> row, int textureId) {
            this.id = id;
            this.particleSystem = ps;
            this.textureId = textureId;
            this.particleRadius = particleRadius;
            this.row = row;
        }

        public long getId() {
            return this.id;
        }

        public ParticleSystem getParticleSystem() {
            return this.particleSystem;
        }

        public int getTextureId() { return this.textureId;}

        public float getParticleRadius() { return this.particleRadius;}

        public ArrayList<ArrayList<Integer>> getRow() { return this.row;}
    }

    class BodyData {
        long id;
        Body body;
        FloatBuffer vertexBuffer;
        FloatBuffer uvBuffer;
        int vertexLen;
        int drawMode;
        int textureId;

        public BodyData(long id, Body body, float[] buffer, float[] uv, int drawMode, int textureId) {
            this.id = id;
            this.body = body;
            this.vertexBuffer = makeFloatBuffer(buffer);
            this.uvBuffer = makeFloatBuffer(uv);
            this.vertexLen = buffer.length / 2;
            this.drawMode = drawMode;
            this.textureId = textureId;
        }

        public long getId() {
            return this.id;
        }

        public Body getBody() {
            return this.body;
        }

        public FloatBuffer getVertexBuffer() {
            return this.vertexBuffer;
        }

        public FloatBuffer getUvBuffer() { return this.uvBuffer;}

        public int getDrawMode() { return this.drawMode;}

        public int getVertexLen() { return this.vertexLen;}

        public int getTextureId() { return this.textureId;}
    }

    public MainRenderer(MainGlView view) {
        this.view = view;
        world = new World(0, -10);
        //this.addBox(1, 1, 0, 10, 0, BodyType.dynamicBody, 0);

    }

    private void addBodyData(Body body, float[] buffer, float[] uv, int drawMode, int textureId) {
        long id = nextBodyDataId++;
        BodyData data = new BodyData(id, body, buffer, uv, drawMode, textureId);
        this.mapBodyData.put(id, data);
    }

    private void addParticleData(ParticleSystem ps, float particleRadius, ArrayList<ArrayList<Integer>> row, int textureId) {
        long id = nextBodyDataId++;
        ParticleData data = new ParticleData(id, ps, particleRadius, row, textureId);
        this.mapParticleData.put(id, data);
    }

    public void addCircle(GL10 gl,float r, float x, float y, float angle, BodyType type, float density, int resId) {
        // Box2d用
        BodyDef bodyDef = new BodyDef();
        bodyDef.setType(type);
        bodyDef.setPosition(x, y);
        bodyDef.setAngle(angle);
        Body body = world.createBody(bodyDef);
        CircleShape shape = new CircleShape();
        shape.setRadius(r);
        body.createFixture(shape, density);
        // OpenGL用
        float vertices[] = new float[32*2];
        float uv[] = new float[32*2];
        for(int i = 0; i < 32; ++i){
            float a = ((float)Math.PI * 2.0f * i)/32;
            vertices[i*2]   = r * (float)Math.sin(a);
            vertices[i*2+1] = r * (float)Math.cos(a);

            uv[i*2]   = ((float)Math.sin(a) + 1.0f)/2f;
            uv[i*2+1] = (-1 * (float)Math.cos(a) + 1.0f)/2f;
        }
        int textureId=makeTexture(gl, resId);
        this.addBodyData(body, vertices, uv, GL10.GL_TRIANGLE_FAN, textureId);
    }

    public void addBox(GL10 gl,float hx, float hy, float x, float y, float angle, BodyType type, float density, int resId) {
        // Box2d用
        BodyDef bodyDef = new BodyDef();
        bodyDef.setType(type);
        bodyDef.setPosition(x, y);
        Body body = world.createBody(bodyDef);
        PolygonShape shape = new PolygonShape();
        shape.setAsBox(hx, hy, 0, 0, angle);
        body.createFixture(shape, density);

        // OpenGL用
        float vertices[] = {
            - hx, + hy,
            - hx, - hy,
            + hx, + hy,
            + hx, - hy,
        };
        FloatBuffer buffer = this.makeFloatBuffer(vertices);

        float[] uv={
             0.0f,0.0f,//左上
             0.0f,1.0f,//左下
             1.0f,0.0f,//右上
             1.0f,1.0f,//右下
        };
        FloatBuffer uvBuffer = this.makeFloatBuffer(uv);
        int textureId=makeTexture(gl, resId);
        this.addBodyData(body, vertices, uv, GL10.GL_TRIANGLE_STRIP, textureId);
    }

    public void addSoftBody(GL10 gl,float hx, float hy, float cx, float cy, float particleRadius, int resId) {
        ParticleSystemDef psd = new ParticleSystemDef();
        psd.setRadius(particleRadius);
        ParticleSystem ps = world.createParticleSystem(psd);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(hx, hy, 0, 0, 0);

        ParticleGroupDef pgd = new ParticleGroupDef();
        pgd.setFlags(ParticleFlag.elasticParticle);
        pgd.setGroupFlags(ParticleGroupFlag.solidParticleGroup);
        pgd.setShape(shape);
        pgd.setPosition(cx, cy);
        ParticleGroup pg = ps.createParticleGroup(pgd);

        float py = 0;
        ArrayList<ArrayList<Integer>> row = new ArrayList<ArrayList<Integer>>();
        ArrayList<Integer> line = new ArrayList<Integer>();
        for (int i = pg.getBufferIndex(); i < pg.getParticleCount() - pg.getBufferIndex(); ++i) {
            float y = ps.getParticlePositionY(i);
            if (i==0) {
                py = y;
            }
            // 行変更
            if ((float)Math.abs(py - y) > 0.01f) {
                row.add(line);
                line = new ArrayList<Integer>();
            }
            line.add(i);
            py = y;
        }
        row.add(line);

        int textureId=makeTexture(gl, resId);
        this.addParticleData(ps, particleRadius, row, textureId);
    }

    /**
     * 描画のため繰り返し呼ばれる
     * @param gl
     */
    @Override
    public void onDrawFrame(GL10 gl) {
        world.step(TIME_STEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS, PARTICLE_ITERATIONS);
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT
                | GL10.GL_DEPTH_BUFFER_BIT);

        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glLoadIdentity();


        for(Long key: this.mapParticleData.keySet()) {
            gl.glPushMatrix();
            {
                ParticleData pd = this.mapParticleData.get(key);
                ParticleSystem ps = pd.getParticleSystem();
                ParticleGroup pg = ps.getParticleGroupList();
                ArrayList<ArrayList<Integer>> row = pd.getRow();
                for (int i = 0; i < row.size() -1; ++i) {
                    ArrayList<Integer> col = row.get(i);
                    float dy = 1.0f/(row.size()-1);
                    float dx = 1.0f/(col.size()-1);
                    float paddingX = 2 * pd.getParticleRadius()/(col.size());
                    float paddingY = 2 * pd.getParticleRadius()/(row.size());
                    for (int j = 0; j < col.size() - 1; ++j) {
                        float px1 = paddingX * j - pd.getParticleRadius();
                        float px2 = paddingX * (j+1) - pd.getParticleRadius();
                        float py1 = paddingY * i - pd.getParticleRadius();
                        float py2 = paddingY * (i+1) - pd.getParticleRadius();

                        float vertices[] = {
                            ps.getParticlePositionX(row.get(i).get(j)) + px1, ps.getParticlePositionY(row.get(i).get(j)) + py1,
                            ps.getParticlePositionX(row.get(i + 1).get(j)) + px1, ps.getParticlePositionY(row.get(i + 1).get(j)) + py2,
                            ps.getParticlePositionX(row.get(i).get(j + 1)) + px2, ps.getParticlePositionY(row.get(i).get(j + 1)) + py1,
                            ps.getParticlePositionX(row.get(i + 1).get(j + 1)) + px2, ps.getParticlePositionY(row.get(i + 1).get(j + 1)) + py2,
                        };
                        float[] uv={
                            j * dx, 1 - i * dy,         //左上
                            j * dx, 1 - (i+1) * dy,     //左下
                            (j+1) * dx, 1 - i * dy,     //右上
                            (j+1) * dx, 1 - (i+1) * dy, //右下
                        };
                        FloatBuffer vertexBuffer = makeFloatBuffer(vertices);
                        FloatBuffer uvBuffer = makeFloatBuffer(uv);

                        //テクスチャの指定
                        gl.glActiveTexture(GL10.GL_TEXTURE0);
                        gl.glBindTexture(GL10.GL_TEXTURE_2D, pd.getTextureId());

                        //UVバッファの指定
                        gl.glTexCoordPointer(2,GL10.GL_FLOAT,0, uvBuffer);

                        gl.glVertexPointer(2, GL10.GL_FLOAT, 0, vertexBuffer);
                        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, vertices.length / 2);
                    }
                }
            }
            gl.glPopMatrix();
        }
        for(Long key: this.mapBodyData.keySet()) {
            gl.glPushMatrix();
            {
                BodyData bd = this.mapBodyData.get(key);
                gl.glTranslatef(bd.getBody().getPositionX(), bd.getBody().getPositionY(), 0);
                float angle = (float)Math.toDegrees(bd.getBody().getAngle());
                gl.glRotatef(angle , 0, 0, 1);

                //テクスチャの指定
                gl.glActiveTexture(GL10.GL_TEXTURE0);
                gl.glBindTexture(GL10.GL_TEXTURE_2D, bd.getTextureId());

                //UVバッファの指定
                gl.glTexCoordPointer(2,GL10.GL_FLOAT,0, bd.getUvBuffer());

                FloatBuffer buff = bd.getVertexBuffer();
                gl.glVertexPointer(2, GL10.GL_FLOAT, 0, buff);
                gl.glDrawArrays(bd.getDrawMode(), 0, bd.getVertexLen());

            }
            gl.glPopMatrix();
        }
        if (this.mouseStatus != MouseStatus.Up) {
            return;
        }
        this.mouseStatus = MouseStatus.None;
        float[] positionDn = getWorldPoint(this.mouseDownX, this.mouseDownY, gl);
        float[] positionUp = getWorldPoint(this.mouseUpX, this.mouseUpY, gl);
        float x = positionDn[0];
        float y = positionDn[1];
        Log.i("mouseDn", Float.toString(x));
        Log.i("mouseDn", Float.toString(y));
        Log.i("mouseUp", Float.toString(positionUp[0]));
        Log.i("mouseUp", Float.toString(positionUp[1]));
        for(Long key: this.mapParticleData.keySet()) {
            ParticleData pd = this.mapParticleData.get(key);
            ParticleSystem ps = pd.getParticleSystem();
            ParticleGroup pg = ps.getParticleGroupList();
            for (int i = pg.getBufferIndex(); i < pg.getParticleCount() - pg.getBufferIndex(); ++i) {
                float py = ps.getParticlePositionY(i);
                float px = ps.getParticlePositionX(i);
                if (Math.abs(px-x) <= pd.getParticleRadius() * 2 && Math.abs(py-y) <= pd.getParticleRadius() * 2 ) {
                    float vx = (positionUp[0] - x)*10000;
                    float vy = (positionUp[1] - y)*10000;
                    Log.i("Vx", Float.toString(vx));
                    Log.i("Vy", Float.toString(vy));
                    ps.setParticleVelocity(i, vx, vy);
                    //ps.setPaused(true);
                    return;
                }
            }
        }
    }
    private float[] getWorldPoint(float mx, float my, GL10 gl) {
        GL11 gl11 = (GL11)gl;
        int[] bits = new int[16];
        float[] model = new float[16];
        float[] proj = new float[16];
        gl11.glGetIntegerv(gl11.GL_MODELVIEW_MATRIX_FLOAT_AS_INT_BITS_OES, bits, 0);
        for(int i = 0; i < bits.length; i++){
            model[i] = Float.intBitsToFloat(bits[i]);
        }
        gl11.glGetIntegerv(gl11.GL_PROJECTION_MATRIX_FLOAT_AS_INT_BITS_OES, bits, 0);
        for(int i = 0; i < bits.length; i++){
            proj[i] = Float.intBitsToFloat(bits[i]);
        }

        float[] ret = new float[4];
        GLU.gluUnProject(
                mx, (float)this.view.getHeight()-my, 1f,
                model, 0, proj, 0,
                new int[]{0, 0, this.view.getWidth(), this.view.getHeight()}, 0,
                ret, 0);
        float x = (float)(ret[0] / ret[3]);
        float y = (float)(ret[1] / ret[3]);
        float z = (float)(ret[2] / ret[3]);
        float position[] = new float[2];
        position[0] = x;
        position[1] = y;
        return position;
    }


    /**
     * 主に landscape と portraid の切り替え (縦向き、横向き切り替え) のときに呼ばれる
     * @param gl
     * @param width
     * @param height
     */
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        gl.glViewport(0, 0, width, height);

        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
        GLU.gluPerspective(gl, 45f, (float) width / height, 1f, 50f);
        GLU.gluLookAt(gl,
                0, 15, 50,    // カメラの位置
                0, 15, 0,    // カメラの注視点
                0, 1, 0     // カメラの上方向
        );
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        this.addBox(gl, 1, 20, -20, 10, 0, BodyType.staticBody, 10, R.drawable.wall);
        this.addBox(gl, 1, 20, 20, 10, 0, BodyType.staticBody, 10, R.drawable.wall);
        this.addBox(gl, 20, 1, 0, 0, 0, BodyType.staticBody, 10, R.drawable.wall);
        this.addBox(gl, 20, 1, 0, 30, 0, BodyType.staticBody, 10, R.drawable.wall);
        this.addSoftBody(gl, 2, 2, 8.5f, 5, 0.2f, R.drawable.maricha);
        this.addBox(gl, 2, 2, 10, 15, 0, BodyType.dynamicBody, 1, R.drawable.wall);
        this.addCircle(gl, 1, 11, 15, 0, BodyType.dynamicBody, 1, R.drawable.ball);

        //gl.glEnable(GL10.GL_DEPTH_TEST);
        gl.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
        gl.glEnable(GL10.GL_LIGHTING);
        gl.glEnable(GL10.GL_LIGHT0);
        gl.glDepthFunc(GL10.GL_LEQUAL);
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);

        //テクスチャの有効化
        gl.glEnable(GL10.GL_TEXTURE_2D);
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
        gl.glEnable(GL10.GL_BLEND);
    }
    //テクスチャの生成
    private int makeTexture(GL10 gl10, int resId) {
        Integer texId = this.mapResIdToTextureId.get(resId);
        if (texId != null) {
            return  texId;
        }
        Bitmap bmp= BitmapFactory.decodeResource(this.view.getContext().getResources(), resId);

        //テクスチャのメモリ確保
        int[] textureIds=new int[1];
        gl10.glGenTextures(1,textureIds, 0);

        //テクスチャへのビットマップ指定
        gl10.glActiveTexture(GL10.GL_TEXTURE0);
        gl10.glBindTexture(GL10.GL_TEXTURE_2D,textureIds[0]);
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bmp, 0);

        //テクスチャのフィルタ指定
        gl10.glTexParameterf(GL10.GL_TEXTURE_2D,
                GL10.GL_TEXTURE_MIN_FILTER,GL10.GL_NEAREST);
        gl10.glTexParameterf(GL10.GL_TEXTURE_2D,
                GL10.GL_TEXTURE_MAG_FILTER,GL10.GL_NEAREST);
        this.mapResIdToTextureId.put(resId, textureIds[0]);
        return textureIds[0];
    }

    //float配列をFloatBufferに変換
    private static FloatBuffer makeFloatBuffer(float[] array) {
        FloatBuffer fb=ByteBuffer.allocateDirect(array.length*4).order(
                ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(array).position(0);
        return fb;
    }

    // ////////////////////////////////////////////////////////////
    // タッチされたときに呼び出される
    public synchronized boolean onTouch(View v, MotionEvent event) {
        switch( event.getAction() ) {
            case MotionEvent.ACTION_DOWN:
                this.mouseStatus = MouseStatus.Down;
                this.mouseDownX =  event.getX();
                this.mouseDownY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
                this.mouseStatus = MouseStatus.Up;
                this.mouseUpX =  event.getX();
                this.mouseUpY = event.getY();
                break;
        }
        return true;
    }
}
