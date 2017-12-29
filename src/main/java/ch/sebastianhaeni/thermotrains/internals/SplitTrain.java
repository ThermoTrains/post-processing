package ch.sebastianhaeni.thermotrains.internals;

import org.apache.commons.lang3.math.NumberUtils;
import org.opencv.core.*;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.List;

import static ch.sebastianhaeni.thermotrains.util.FileUtil.*;
import static org.opencv.core.Core.inRange;
import static org.opencv.imgcodecs.Imgcodecs.imread;
import static org.opencv.imgproc.Imgproc.*;

public final class SplitTrain {

  private static final int MIN_CAR_LENGTH_IN_PX = 1500;
  private static final double PEAK_THRESHOLD = 0.7;
  private static final int DILATION_SIZE = 7;

  private SplitTrain() {
    // nop
  }

  public static void cut(@Nonnull String inputFolder, @Nonnull String outputFolder) {
    emptyFolder(outputFolder);

    List<Path> files = getFiles(inputFolder, "**result.jpg");
    Mat img = imread(files.get(0).toString());

    Mat hsv = new Mat();
    cvtColor(img, hsv, COLOR_BGR2HSV);

    int kernelSize = 3 * 2;
    blur(hsv, hsv, new Size(kernelSize, kernelSize));

    Scalar lower = new Scalar(0, 0, 0);
    Scalar upper = new Scalar(255, 255, 38);

    Mat dst = new Mat();
    inRange(hsv, lower, upper, dst);

    // dilate the threshold image to fill in holes
    Mat dilationElement = getStructuringElement(MORPH_ELLIPSE,
      new Size(2 * DILATION_SIZE + 1, 2 * DILATION_SIZE + 1),
      new Point(DILATION_SIZE, DILATION_SIZE));

    erode(dst, dst, dilationElement);

    Mat cropped = crop(dst);

    int[] hist = new int[cropped.cols()];

    for (int i = 0; i < cropped.cols(); i++) {

      int withinRange = 0;

      for (int j = 0; j < cropped.rows(); j++) {
        if (cropped.get(j, i)[0] > 0) {
          withinRange++;
        }
      }

      hist[i] = withinRange;
    }

    int max = NumberUtils.max(hist);

    // find peaks
    int lastPeak = -1;
    for (int i = 0; i < hist.length; i++) {
      if (hist[i] < max * PEAK_THRESHOLD) {
        hist[i] = 0;
      } else {
        hist[i] = 1;

        // Removes the plateau by flattening every element that is 1 before the current one in a fixed distance.
        if (lastPeak >= 0 && lastPeak + MIN_CAR_LENGTH_IN_PX > i) {
          hist[lastPeak] = 0;
        }
        lastPeak = i;
      }
    }

    // last pixel must be peak, to crop correctly
    hist[hist.length - 1] = 1;
    int prev = 0;
    int i = 0;
    for (int x = 0; x < hist.length; x++) {
      if (hist[x] == 0.0) {
        continue;
      }

      if (x - prev < MIN_CAR_LENGTH_IN_PX) {
        prev = x;
        continue;
      }

      Mat car = img.colRange(prev, x);
      prev = x;
      saveMat(outputFolder, car, ++i);
    }
  }

  /**
   * Crops the {@link Mat} to 2/3 of its height.
   */
  @Nonnull
  private static Mat crop(@Nonnull Mat mat) {
    Rect roi = new Rect(0, 0, mat.cols(), mat.rows() * 2/3);

    return new Mat(mat, roi);
  }
}
