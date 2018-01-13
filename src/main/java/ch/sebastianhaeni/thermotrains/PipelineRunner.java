package ch.sebastianhaeni.thermotrains;

import javax.annotation.Nonnull;

import ch.sebastianhaeni.thermotrains.internals.CalibrateCamera;
import ch.sebastianhaeni.thermotrains.internals.ExtractFrames;
import ch.sebastianhaeni.thermotrains.internals.MotionCrop;
import ch.sebastianhaeni.thermotrains.internals.PrepareTrainFrames;
import ch.sebastianhaeni.thermotrains.internals.Rectify;
import ch.sebastianhaeni.thermotrains.internals.SplitTrain;
import ch.sebastianhaeni.thermotrains.internals.Straighten;
import ch.sebastianhaeni.thermotrains.internals.TrainStitcher;
import ch.sebastianhaeni.thermotrains.internals.Undistort;
import ch.sebastianhaeni.thermotrains.util.Procedure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opencv.core.Core;

/**
 * Runs through the pipeline from {@code START_STEP} to {@code STOP_STEP}.
 * If {@code START_STEP} is > 1, then the input artifacts have to be present already.
 * Right now the pipeline pipes results with files.
 *
 * TODO piping without resorting to the file system should be supported
 */
public final class PipelineRunner {
  static {
    // load OpenCV native library
    System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
  }

  private static final Logger LOG = LogManager.getLogger(PipelineRunner.class);

  private static final int START_STEP = 7;
  private static final int STOP_STEP = 7;

  private PipelineRunner() {
    // nop
  }

  public static void main(@Nonnull String[] args) {
    runStep(1, () -> ExtractFrames.extractFrames(
      "samples/calibration/flir-checkerboard.mp4",
      "target/1-calibration"
    ));
    runStep(2, () -> CalibrateCamera.performCheckerboardCalibration(
      "target/1-calibration",
      "target/2-calibration-found"
    ));
    runStep(3, () -> PrepareTrainFrames.prepare(
      "samples/distorted/new/2018-01-02@16-46-32-IR.seq.mp4",
//      "/Users/rlaubscher/Desktop/review/2017-11-29@15-02-13-visible.mp4",
      "target/3-distorted"
    ));
    runStep(4, () -> Undistort.undistortImages(
      "samples/calibration/flir-calibration.json",
      "target/3-distorted",
      "target/4-undistorted"
    ));
    runStep(5, () -> Straighten.straighten(
      "target/4-undistorted",
      "target/5-straightened"
    ));
//    runStep(5, () -> MotionCrop.cropToMotion(
//      "target/5-straightened",
//      "target/6-cropped"
//    ));
    runStep(6, () -> Rectify.transform(
      "target/4-undistorted",
//      "target/5-straightened",
      "target/7-rectified"
    ));
    runStep(7, () -> TrainStitcher.stitchTrain(
      "target/7-rectified",
      "target/8-stitched"
    ));
    runStep(8, () -> SplitTrain.cut(
      "target/8-stitched",
      "target/9-final"
    ));
  }

  private static void runStep(int step, @Nonnull Procedure<?> procedure) {
    if (START_STEP > step || STOP_STEP < step) {
      return;
    }

    LOG.info("Running step {}", step);

    try {
      procedure.run();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
