package tw.ai_lab.screenshotbatch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

final class MetadataOptions {
    final Set<String> selectedFields = new LinkedHashSet<>();
    final ArrayList<String> labels = new ArrayList<>();
    String batchName = "";

    JSONObject toJson() throws Exception {
        JSONObject result = new JSONObject();
        JSONArray fields = new JSONArray();
        for (String field : selectedFields) fields.put(field);
        JSONArray labelArray = new JSONArray();
        for (String label : labels) labelArray.put(label);
        result.put("fields", fields);
        result.put("labels", labelArray);
        result.put("batch_name", batchName);
        return result;
    }

    static MetadataOptions fromJson(String source) {
        MetadataOptions options = new MetadataOptions();
        try {
            JSONObject value = new JSONObject(source);
            JSONArray fields = value.optJSONArray("fields");
            if (fields != null) for (int i = 0; i < fields.length(); i++) options.selectedFields.add(fields.getString(i));
            JSONArray labels = value.optJSONArray("labels");
            if (labels != null) for (int i = 0; i < labels.length(); i++) options.labels.add(labels.getString(i));
            options.batchName = value.optString("batch_name", "");
        } catch (Exception ignored) { }
        return options;
    }
}
