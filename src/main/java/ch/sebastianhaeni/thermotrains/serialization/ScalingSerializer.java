package ch.sebastianhaeni.thermotrains.serialization;

import com.google.gson.*;

import java.lang.reflect.Type;

public class ScalingSerializer implements JsonSerializer<Scaling> {
  @Override public JsonElement serialize(Scaling scaling, Type type, JsonSerializationContext jsonSerializationContext) {
    JsonObject result = new JsonObject();
    result.add("minValue", new JsonPrimitive(scaling.getMinValue()));
    result.add("inverseScale", new JsonPrimitive(scaling.getInverseScale()));
    return result;
  }
}
