package no.paneon.api.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import no.paneon.api.utils.Out;

import java.util.Iterator;

public class InlineSchemaRefactorer {

	static final Logger LOG = LogManager.getLogger(InlineSchemaRefactorer.class);

    public JSONObject refactorInlineSchemas(JSONObject openApi) {
        // Ensure components.schemas exists
        JSONObject components = openApi.optJSONObject("components");
        if (components == null) {
            components = new JSONObject();
            openApi.put("components", components);
        }

        JSONObject schemas = components.optJSONObject("schemas");
        if (schemas == null) {
            schemas = new JSONObject();
            components.put("schemas", schemas);
        }

        JSONObject paths = openApi.optJSONObject("paths");
        if (paths == null) return openApi;

        for (String pathKey : paths.keySet()) {
            JSONObject pathItem = paths.getJSONObject(pathKey);
            String schemaBaseName = getLastPathSegment(pathKey);

            for (String methodKey : pathItem.keySet()) {
                JSONObject operation = pathItem.optJSONObject(methodKey);
                if (operation == null) continue;

                // Process responses
                JSONObject responses = operation.optJSONObject("responses");
                if (responses != null) {
                    for (String status : responses.keySet()) {
                        JSONObject response = responses.optJSONObject(status);
                        if (response == null) continue;

                        JSONObject content = response.optJSONObject("content");
                        if (content != null) {
                            replaceInlineSchemasInContent(content, schemas, schemaBaseName + "Response");
                        }
                    }
                }

                // Process requestBody
                JSONObject requestBody = operation.optJSONObject("requestBody");
                if (requestBody != null) {
                    JSONObject content = requestBody.optJSONObject("content");
                    if (content != null) {
                        replaceInlineSchemasInContent(content, schemas, schemaBaseName + "Request");
                    }
                }
            }
        }

        return openApi;
    }

    private void replaceInlineSchemasInContent(JSONObject content, JSONObject schemas, String baseSchemaName) {
        int index = 1;
        for (String mediaTypeKey : content.keySet()) {
            JSONObject mediaType = content.optJSONObject(mediaTypeKey);
            if (mediaType == null) continue;

            JSONObject schema = mediaType.optJSONObject("schema");
            if (schema != null && !schema.has("$ref")) {
                // Avoid naming collisions
                String uniqueName = baseSchemaName;
                while (schemas.has(uniqueName)) {
                    uniqueName = baseSchemaName + index++;
                }

				LOG.debug("replaceInlineSchemasInContent:: name={} value={}", uniqueName, schema.toString() );

                schemas.put(uniqueName, new JSONObject(schema.toString())); // deep copy
                JSONObject ref = new JSONObject();
                ref.put("$ref", "#/components/schemas/" + uniqueName);
                mediaType.put("schema", ref);
            }
        }
    }

    private String getLastPathSegment(String path) {
        String[] parts = path.replaceAll("[{}]", "").split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isEmpty()) {
                return capitalize(parts[i]);
            }
        }
        return "Unnamed";
    }

    private String capitalize(String word) {
        if (word == null || word.isEmpty()) return word;
        return word.substring(0, 1).toUpperCase() + word.substring(1);
    }
}
