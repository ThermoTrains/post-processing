package ch.sebastianhaeni.thermotrains.internals;

import ch.sebastianhaeni.thermotrains.serialization.Scaling;
import ch.sebastianhaeni.thermotrains.serialization.ScalingSerializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import static ch.sebastianhaeni.thermotrains.util.FileUtil.emptyFolder;
import static ch.sebastianhaeni.thermotrains.util.FileUtil.getFile;

public final class MetadataExtractor {

  private static final Logger LOG = LogManager.getLogger(MetadataExtractor.class);

  private MetadataExtractor() {
    //nop
  }

  public static void exportScaling(String inputFile, String outputFolder) {
    emptyFolder(outputFolder);

    //GetCompressionParameters
    int minValue = 0;
    double inverseScale = 1.0;

    try {
      StringBuffer sb = new StringBuffer();
      Process p = Runtime.getRuntime().exec("exiftool -s -s -s -Comment " + inputFile);
      p.waitFor();
      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

      String line = "";
      while ((line = reader.readLine())!= null) {
        sb.append(line + "\n");
      }
      String output = sb.toString().replaceAll("\\n", "");
      String[] values = output.split("/");
      minValue = Integer.valueOf(values[0]);
      inverseScale = Double.valueOf(values[1].replace(',', '.'));
      inverseScale = 1 / inverseScale;
      LOG.info("offset " + minValue);
      LOG.info("inverseScale " + inverseScale);

      Scaling scaling = new Scaling(minValue, inverseScale);
      Gson gson = new GsonBuilder()
        .registerTypeAdapter(Scaling.class, new ScalingSerializer())
        .create();
      String serializedJson = gson.toJson(scaling);

      File file = getFile(outputFolder, "scaling.json");
      PrintWriter out = new PrintWriter(file.getAbsoluteFile());
      out.print(serializedJson);
      out.close();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
