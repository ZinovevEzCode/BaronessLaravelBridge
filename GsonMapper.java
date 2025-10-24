package ru.bont777.bridge;

import io.javalin.json.JsonMapper;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import java.io.UncheckedIOException;

public class GsonMapper implements JsonMapper {

    private final Gson gson = new Gson();

    @Override
    public <T> T fromJsonString(String json, Type targetType) {
        return gson.fromJson(json, targetType);
    }

    @Override
    public <T> T fromJsonStream(InputStream json, Type targetType) {
        try (Reader reader = new InputStreamReader(json, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, targetType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toJsonString(Object obj, Type type) {
        return gson.toJson(obj, type);
    }

    @Override
    public InputStream toJsonStream(Object obj, Type type) {
        String json = gson.toJson(obj, type);
        return new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeToOutputStream(Stream<?> stream, OutputStream outputStream) {
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            writer.beginArray();
            stream.forEach(item -> {
                try {
                    gson.toJson(item, Object.class, writer);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            writer.endArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
