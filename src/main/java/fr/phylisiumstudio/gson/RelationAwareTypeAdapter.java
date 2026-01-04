package fr.phylisiumstudio.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Field;

public class RelationAwareTypeAdapter<T> extends TypeAdapter<T> {
    private final Gson gson;
    private final Class<T> type;

    public RelationAwareTypeAdapter(Gson gson, Class<T> type) {
        this.gson = gson;
        this.type = type;
    }

    @Override
    public void write(JsonWriter out, T value) throws IOException {
        out.beginObject();

        for (Field field : type.getDeclaredFields()) {
            field.setAccessible(true);

            GsonRelation relation = field.getAnnotation(GsonRelation.class);
            Object fieldValue;

            try {
                fieldValue = field.get(value);
            } catch (IllegalAccessException e) {
                continue;
            }

            if (relation != null) {
                switch (relation.value()) {
                    case IGNORE -> {
                        continue;
                    }
                    case REFERENCE -> {
                        out.name(field.getName());
                        writeReference(out, fieldValue);
                        continue;
                    }
                }
            }

            out.name(field.getName());
            gson.toJson(fieldValue, field.getGenericType(), out);
        }

        out.endObject();
    }

    @Override
    public T read(JsonReader jsonReader) throws IOException {
        return gson.fromJson(jsonReader, type);
    }

    private void writeReference(JsonWriter out, Object obj) throws IOException {
        try {
            Field idField = obj.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            out.value(idField.get(obj).toString());
        } catch (Exception e) {
            out.nullValue();
        }
    }
}
