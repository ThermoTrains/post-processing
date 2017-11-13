using System;
using System.Collections.Generic;
using System.Configuration;
using System.Drawing;
using System.Reflection;
using System.Threading.Tasks;
using Basler.Pylon;
using Emgu.CV;
using Emgu.CV.Structure;
using log4net;
using SebastianHaeni.ThermoBox.Common.Component;
using SebastianHaeni.ThermoBox.Common.Motion;
using SebastianHaeni.ThermoBox.Common.Util;
using Configuration = Basler.Pylon.Configuration;
using System.IO;

namespace SebastianHaeni.ThermoBox.VisibleLightReader
{
    internal class VisibleLightReaderComponent : ThermoBoxComponent, IDisposable
    {
        private static readonly ILog Log = LogManager.GetLogger(MethodBase.GetCurrentMethod().DeclaringType);

        private static readonly string CameraName = ConfigurationManager.AppSettings["VISIBLE_LIGHT_CAMERA_NAME"];

        private static readonly Dictionary<string, string> CameraFilter =
            new Dictionary<string, string> {[CameraInfoKey.FriendlyName] = CameraName};

        private static readonly string CaptureFolder = ConfigurationManager.AppSettings["CAPTURE_FOLDER"];

        private readonly Camera _camera;
        private readonly Recorder _recorder;
        private readonly Size _size;

        private readonly PixelDataConverter _converter = new PixelDataConverter
        {
            OutputPixelFormat = PixelType.BGR8packed
        };

        private string _filename;
        private string _startRecording;
        private bool _stopRecording;
        private bool _abortRecording;
        private bool _pauseRecording;
        private bool _resumeRecording;

        private const int AnalyzeSequenceImages = 4;
        private const int ErrorThreshold = 5;

        public VisibleLightReaderComponent()
        {
            // Setup camera
            _camera = new Camera(CameraFilter, CameraSelectionStrategy.Unambiguous);

            // Set the acquisition mode to free running continuous acquisition when the camera is opened.
            _camera.CameraOpened += Configuration.AcquireContinuous;

            // Open the connection to the camera device.
            _camera.Open();

            // Read device parameters
            var fps = (int) _camera.Parameters[PLCamera.AcquisitionFrameRate].GetValue();
            var width = (int) _camera.Parameters[PLCamera.Width].GetValue();
            var height = (int) _camera.Parameters[PLCamera.Height].GetValue();
            _size = new Size(width, height);

            // Setup recorder
            _recorder = new Recorder(fps, _size, true);

            // Setup subscriptions
            Subscription(Commands.CaptureStart, (channel, filename) => _startRecording = filename);
            Subscription(Commands.CaptureStop, (channel, filename) => _stopRecording = true);
            Subscription(Commands.CaptureAbort, (channel, filename) => _abortRecording = true);
            Subscription(Commands.CapturePause, (channel, filename) => _pauseRecording = true);
            Subscription(Commands.CaptureResume, (channel, filename) => _resumeRecording = true);

            // Start detecting asynchronously
            new Task(DetectIncomingTrains).Start();
        }

        private void DetectIncomingTrains()
        {
            _camera.StreamGrabber.Start(GrabStrategy.LatestImages, GrabLoop.ProvidedByUser);

            // Detection class
            var detector = new EntryDetector();

            // Event handlers
            detector.Enter += (sender, args) => Publish(Commands.CaptureStart, FileUtil.GenerateTimestampFilename());
            detector.Exit += (sender, args) => Publish(Commands.CaptureStop);
            detector.Abort += (sender, args) => Publish(Commands.CaptureAbort);
            detector.Pause += (sender, args) => Publish(Commands.CapturePause);
            detector.Resume += (sender, args) => Publish(Commands.CaptureResume);

            // Array to contain images that will be collected until it's full and we analyze them.
            var images = new Image<Gray, byte>[AnalyzeSequenceImages];

            // Analyze image array counter
            var i = 0;

            // Buffer to put debayered RGB image into (3 channels)
            var convertedBuffer = new byte[_size.Width * _size.Height * 3];

            var errorCount = 0;

            // Grab images.
            while (true)
            {
                HandleStateChange();

                // Wait for an image and then retrieve it. A timeout of 5000 ms is used.
                var grabResult = _camera.StreamGrabber.RetrieveResult(5000, TimeoutHandling.ThrowException);

                using (grabResult)
                {
                    // Image grabbed successfully?
                    if (!grabResult.GrabSucceeded)
                    {
                        Log.Error($"Error: {grabResult.ErrorCode} {grabResult.ErrorDescription}");
                        errorCount++;

                        if (errorCount > ErrorThreshold)
                        {
                            Log.Error("Too many errors. Exiting detection. Not exiting recoding.");
                            break;
                        }

                        continue;
                    }

                    // Debayering RGB image
                    _converter.Convert(convertedBuffer, grabResult);

                    // Convert into EmguCV image type
                    var image = new Image<Rgb, byte>(grabResult.Width, grabResult.Height)
                    {
                        Bytes = convertedBuffer
                    };

                    // Write to recorder (if the recorder is not recording, it will discard it)
                    _recorder.Write(image);

                    // Convert to grayscale image for further analysis
                    var grayImage = image.Convert<Gray, byte>();

                    // Append to analyze array
                    images[i] = grayImage;
                    i++;

                    // Skip analysation step until we collected a full array of images
                    if (i != images.Length)
                    {
                        continue;
                    }

                    // Reset array counter
                    i = 0;

                    // Let the detector do it's thing (is a train entering? exiting?)
                    detector.Tick(images);

                    // dispose of references to lower memory consumption
                    for (var k = 0; k < images.Length; k++)
                    {
                        images[k] = null;
                    }
                }
            }
        }

        private void HandleStateChange()
        {
            if (_startRecording != null)
            {
                StartRecording(_startRecording);
                _startRecording = null;
                return;
            }

            if (_stopRecording)
            {
                StopRecording();
                _stopRecording = false;
                return;
            }

            if (_abortRecording)
            {
                AbortRecording();
                _abortRecording = false;
                return;
            }

            if (_pauseRecording)
            {
                PauseRecording();
                _pauseRecording = false;
                return;
            }

            if (_resumeRecording)
            {
                ResumeRecording();
                _resumeRecording = false;
            }
        }

        private void StartRecording(string filename)
        {
            Log.Info($"Starting capture {filename}");

            // ensuring the recordings directory exists
            var recordingDirectory = new DirectoryInfo(CaptureFolder);
            if (!recordingDirectory.Exists)
            {
                recordingDirectory.Create();
            }

            _filename = $@"{CaptureFolder}\{filename}-visible.mp4";
            _recorder.StartRecording(_filename);
        }

        private void StopRecording()
        {
            Log.Info("Stopping capture.");
            _recorder.StopRecording();
            Publish(Commands.Upload, _filename);
        }

        private void AbortRecording()
        {
            Log.Info("Aborting capture.");
            _recorder.StopRecording();

            // Deleting generated artifact
            File.Delete(_filename);
        }

        private void PauseRecording()
        {
            Log.Info("Pausing recording");
            _recorder.Pause();
        }

        private void ResumeRecording()
        {
            Log.Info("Resuming recording");
            _recorder.Resume();
        }

        public void Dispose()
        {
            _camera?.Dispose();
            _recorder?.Dispose();
        }
    }
}
