package org.reactnative.camera.tasks;

import android.graphics.Rect;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.google.android.cameraview.CameraView;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.text.Text.Line;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.Text.TextBlock;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.reactnative.camera.utils.ImageDimensions;
import org.reactnative.facedetector.FaceDetectorUtils;
import org.reactnative.frame.RNFrame;
import org.reactnative.frame.RNFrameFactory;


public class TextRecognizerAsyncTask extends android.os.AsyncTask<Void, Void, Task<Text>> {

  private TextRecognizerAsyncTaskDelegate mDelegate;
  private TextRecognizer mTextRecognizer;
  private byte[] mImageData;
  private int mWidth;
  private int mHeight;
  private int mRotation;
  private ImageDimensions mImageDimensions;
  private double mScaleX;
  private double mScaleY;
  private int mPaddingLeft;
  private int mPaddingTop;

  public TextRecognizerAsyncTask(
          TextRecognizerAsyncTaskDelegate delegate,
          byte[] imageData,
          int width,
          int height,
          int rotation,
          float density,
          int facing,
          int viewWidth,
          int viewHeight,
          int viewPaddingLeft,
          int viewPaddingTop
  ) {
    mDelegate = delegate;
    mImageData = imageData;
    mWidth = width;
    mHeight = height;
    mRotation = rotation;
    mImageDimensions = new ImageDimensions(width, height, rotation, facing);
    mScaleX = (double) (viewWidth) / (mImageDimensions.getWidth() * density);
    mScaleY = (double) (viewHeight) / (mImageDimensions.getHeight() * density);
    mPaddingLeft = viewPaddingLeft;
    mPaddingTop = viewPaddingTop;
  }

  @Override
  protected Task<Text> doInBackground(Void... ignored) {
    if (isCancelled() || mDelegate == null) {
      return null;
    }
    mTextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    RNFrame frame = RNFrameFactory.buildFrame(mImageData, mWidth, mHeight, mRotation);
    return mTextRecognizer.process(frame.getInputImage());
  }

  @Override
  protected void onPostExecute(Task<Text> task) {
    super.onPostExecute(task);
    if (mTextRecognizer != null) {
      mTextRecognizer.close();
    }
    if (task != null) {
      task.addOnSuccessListener(text -> {
        if (text != null) {
          WritableArray textBlocksList = Arguments.createArray();
          for (int i = 0; i < text.getTextBlocks().size(); ++i) {
            TextBlock textBlock = text.getTextBlocks().get(i);
            WritableMap serializedTextBlock = serializeText(textBlock);
            if (mImageDimensions.getFacing() == CameraView.FACING_FRONT) {
              serializedTextBlock = rotateTextX(serializedTextBlock);
            }
            textBlocksList.pushMap(serializedTextBlock);
          }
          mDelegate.onTextRecognized(textBlocksList);
        }
      }).addOnFailureListener(e -> {
        mDelegate.onTextRecognizerTaskCompleted();
      });

    }
    mDelegate.onTextRecognizerTaskCompleted();
  }

  private WritableMap serializeText(TextBlock text) {
    WritableMap encodedText = Arguments.createMap();

    WritableArray components = Arguments.createArray();
    for (Line component : text.getLines()) {
      components.pushMap(serializeText(component));
    }
    encodedText.putArray("components", components);

    encodedText.putString("value", text.getText());

    WritableMap bounds = serializeBounds(text.getBoundingBox());
    encodedText.putMap("bounds", bounds);

    encodedText.putString("type", "block");

    return encodedText;
  }

  private WritableMap serializeText(Line text) {
    WritableMap encodedText = Arguments.createMap();

    WritableArray components = Arguments.createArray();
    for (Text.Element component : text.getElements()) {
      components.pushMap(serializeText(component));
    }
    encodedText.putArray("components", components);

    encodedText.putString("value", text.getText());

    WritableMap bounds = serializeBounds(text.getBoundingBox());
    encodedText.putMap("bounds", bounds);

    encodedText.putString("type", "line");

    return encodedText;
  }

  private WritableMap serializeText(Text.Element text) {
    WritableMap encodedText = Arguments.createMap();

    WritableArray components = Arguments.createArray();
    encodedText.putArray("components", components);

    encodedText.putString("value", text.getText());

    WritableMap bounds = serializeBounds(text.getBoundingBox());
    encodedText.putMap("bounds", bounds);

    encodedText.putString("type", "element");

    return encodedText;
  }

  private WritableMap serializeBounds(Rect boundingBox) {
    int x = boundingBox.left;
    int y = boundingBox.top;
    int width = boundingBox.width();
    int height = boundingBox.height();
    if (x < mWidth / 2) {
      x = x + mPaddingLeft / 2;
    } else if (x > mWidth / 2) {
      x = x - mPaddingLeft / 2;
    }

    if (height < mHeight / 2) {
      y = y + mPaddingTop / 2;
    } else if (height > mHeight / 2) {
      y = y - mPaddingTop / 2;
    }

    WritableMap origin = Arguments.createMap();
    origin.putDouble("x", x * this.mScaleX);
    origin.putDouble("y", y * this.mScaleY);

    WritableMap size = Arguments.createMap();
    size.putDouble("width", width * this.mScaleX);
    size.putDouble("height", height * this.mScaleY);

    WritableMap bounds = Arguments.createMap();
    bounds.putMap("origin", origin);
    bounds.putMap("size", size);

    return bounds;
  }

  private WritableMap rotateTextX(WritableMap text) {
    ReadableMap faceBounds = text.getMap("bounds");

    ReadableMap oldOrigin = faceBounds.getMap("origin");
    WritableMap mirroredOrigin = FaceDetectorUtils.positionMirroredHorizontally(
            oldOrigin, mImageDimensions.getWidth(), mScaleX);

    double translateX = -faceBounds.getMap("size").getDouble("width");
    WritableMap translatedMirroredOrigin = FaceDetectorUtils.positionTranslatedHorizontally(mirroredOrigin, translateX);

    WritableMap newBounds = Arguments.createMap();
    newBounds.merge(faceBounds);
    newBounds.putMap("origin", translatedMirroredOrigin);

    text.putMap("bounds", newBounds);

    ReadableArray oldComponents = text.getArray("components");
    WritableArray newComponents = Arguments.createArray();
    for (int i = 0; i < oldComponents.size(); ++i) {
      WritableMap component = Arguments.createMap();
      component.merge(oldComponents.getMap(i));
      rotateTextX(component);
      newComponents.pushMap(component);
    }
    text.putArray("components", newComponents);

    return text;
  }

}
