package jp.ne.needtec.liquidfuntest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.opengl.GLUtils;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.google.fpl.liquidfun.BodyType;
import com.google.fpl.liquidfun.CircleShape;
import com.google.fpl.liquidfun.Fixture;
import com.google.fpl.liquidfun.PolygonShape;
import com.google.fpl.liquidfun.World;
import com.google.fpl.liquidfun.Body;
import com.google.fpl.liquidfun.Vec2;
import com.google.fpl.liquidfun.BodyDef;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;

/**
 * Created by mitagaki on 2015/03/23.
 */
public class MainRenderer implements GLSurfaceView.Renderer {
    private World world = null;
    private HashMap<Long, BodyData> mapBodyData = new HashMap<Long, BodyData>();
    private long nextBodyDataId = 1;
    private static final float TIME_STEP = 1 / 60f; // 60 fps
    private static final int VELOCITY_ITERATIONS = 6;
    private static final int POSITION_ITERATIONS = 2;
    private static final int PARTICLE_ITERATIONS = 5;
    private Context context;
    private HashMap<Integer, Integer> mapResIdToTextureId = new HashMap<Integer, Integer>();


    static {
        System.loadLibrary("liquidfun");
        System.loadLibrary("liquidfun_jni");
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

    public MainRenderer(Context context) {
        this.context = context;
        world = new World(0, -10);
        //this.addBox(1, 1, 0, 10, 0, BodyType.dynamicBody, 0);

    }

    private void addBodyData(Body body, float[] buffer, float[] uv, int drawMode, int textureId) {
        long id = nextBodyDataId++;
        BodyData data = new BodyData(id, body, buffer, uv, drawMode, textureId);
        this.mapBodyData.put(id, data);
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
                0, 0, 50,    // カメラの位置
                0, 0, 0,    // カメラの注視点
                0, 1, 0     // カメラの上方向
        );
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        this.addBox(gl, 50, 1, 0, 0, 0, BodyType.staticBody, 10, R.drawable.maricha);
        this.addBox(gl, 2, 2, 8.5f, 25, 0, BodyType.dynamicBody, 1, R.drawable.maricha);
        this.addBox(gl, 2, 2, 10, 30, 0, BodyType.dynamicBody, 1, R.drawable.maricha);
        this.addCircle(gl, 1, 11, 30, 0, BodyType.dynamicBody, 1, R.drawable.ball);

        gl.glEnable(GL10.GL_DEPTH_TEST);
        gl.glEnable(GL10.GL_LIGHTING);
        gl.glEnable(GL10.GL_LIGHT0);
        gl.glDepthFunc(GL10.GL_LEQUAL);
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);

        //テクスチャの有効化
        gl.glEnable(GL10.GL_TEXTURE_2D);
        //テクスチャの生成
        //Bitmap bmp= BitmapFactory.decodeResource(this.context.getResources(), R.drawable.maricha);
        //textureId=makeTexture(gl, bmp);


        //bmp= BitmapFactory.decodeResource(this.context.getResources(), R.drawable.ball);
        //int t2 = textureId=makeTexture(gl, bmp);
    }
    //テクスチャの生成
    private int makeTexture(GL10 gl10, int resId) {
        Integer texId = this.mapResIdToTextureId.get(resId);
        if (texId != null) {
            return  texId;
        }
        Bitmap bmp= BitmapFactory.decodeResource(this.context.getResources(), resId);

        //テクスチャのメモリ確保
        int[] textureIds=new int[1];
        gl10.glGenTextures(1,textureIds,0);

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
}
