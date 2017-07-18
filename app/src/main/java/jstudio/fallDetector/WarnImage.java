package jstudio.fallDetector;

import android.graphics.Camera;
import android.graphics.Matrix;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;
import android.widget.TextView;

class WarnImage {

    private final int depthZ = 0;
    private final int duration = 450;
    private WarningAnimation animation;

    private View layout;
    private TextView textView;
    private long time;

    WarnImage(View layout, TextView textView, final long time){
        this.layout = layout;
        this.textView = textView;
        this.time = time;
        textView.setText(String.valueOf(time));
    }

    void countDown(final long time){
        if(this.time != time){
            this.time = time;
            startAnimation();
        }
    }

    private void initialize(){
        final int width = layout.getWidth() / 2;
        final int height = layout.getHeight() / 2;
        animation = new  WarningAnimation(0, 90, width, height, depthZ, true);
        //从0到90度，顺时针旋转视图，此时reverse参数为true，达到90度时动画结束时视图变得不可见，
        animation.setDuration(duration);
        animation.setFillAfter(true);
        animation.setInterpolator(new AccelerateInterpolator());
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }
            @Override
            public void onAnimationRepeat(Animation animation) {

            }
            @Override
            public void onAnimationEnd(Animation animation) {
                textView.setText(String.valueOf(time));
                //从270到360度，顺时针旋转视图，此时reverse参数为false，达到360度动画结束时视图变得可见
                WarningAnimation rotateAnimation = new WarningAnimation(270, 360, width, height, depthZ, false);
                rotateAnimation.setDuration(duration);
                rotateAnimation.setFillAfter(true);
                rotateAnimation.setInterpolator(new DecelerateInterpolator());
                layout.startAnimation(rotateAnimation);
            }
        });
    }

    private void startAnimation(){
        if(animation == null)
            initialize();
        layout.startAnimation(animation);
    }

    /*動畫*/
    private class WarningAnimation extends Animation {
        private final float mFromDegrees;
        private final float mToDegrees;
        private final float mCenterX;
        private final float mCenterY;
        private final float mDepthZ;
        private final boolean mReverse;
        private Camera mCamera;

        /** http://www.jianshu.com/p/153d9f31288d
         * Creates a new 3D rotation on the Y axis. The rotation is defined by its
         * start angle and its end angle. Both angles are in degrees. The rotation
         * is performed around a center point on the 2D space, definied by a pair
         * of X and Y coordinates, called centerX and centerY. When the animation
         * starts, a translation on the Z axis (depth) is performed. The length
         * of the translation can be specified, as well as whether the translation
         * should be reversed in time.
         *
         * @param fromDegrees the start angle of the 3D rotation
         * @param toDegrees   the end angle of the 3D rotation
         * @param centerX     the X center of the 3D rotation
         * @param centerY     the Y center of the 3D rotation
         * @param reverse     true if the translation should be reversed, false otherwise
         */
        WarningAnimation(float fromDegrees, float toDegrees,
                         float centerX, float centerY, float depthZ, boolean reverse) {
            mFromDegrees = fromDegrees;
            mToDegrees = toDegrees;
            mCenterX = centerX;
            mCenterY = centerY;
            mDepthZ = depthZ;
            mReverse = reverse;
        }

        @Override
        public void initialize(int width, int height, int parentWidth, int parentHeight) {
            super.initialize(width, height, parentWidth, parentHeight);
            mCamera = new Camera();
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            final float fromDegrees = mFromDegrees;
            float degrees = fromDegrees + ((mToDegrees - fromDegrees) * interpolatedTime);

            final float centerX = mCenterX;
            final float centerY = mCenterY;
            final Camera camera = mCamera;

            final Matrix matrix = t.getMatrix();

            camera.save();
            if (mReverse) {
                camera.translate(0.0f, 0.0f, mDepthZ * interpolatedTime);
            } else {
                camera.translate(0.0f, 0.0f, mDepthZ * (1.0f - interpolatedTime));
            }
            camera.rotateY(degrees);
            camera.getMatrix(matrix);
            camera.restore();

            matrix.preTranslate(-centerX, -centerY);
            matrix.postTranslate(centerX, centerY);
        }
    }

}
