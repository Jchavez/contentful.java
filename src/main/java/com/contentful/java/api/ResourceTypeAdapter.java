/*
 * Copyright (C) 2014 Contentful GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.contentful.java.api;

import com.contentful.java.lib.Constants;
import com.contentful.java.model.CDAAsset;
import com.contentful.java.model.CDAContentType;
import com.contentful.java.model.CDAEntry;
import com.contentful.java.model.CDAResource;
import com.contentful.java.model.CDASpace;
import com.contentful.java.model.Locale;
import com.contentful.java.model.ResourceWithList;
import com.contentful.java.model.ResourceWithMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom type adapter for de-serializing resources.
 */
class ResourceTypeAdapter implements JsonDeserializer<CDAResource> {
  // Client reference
  private final CDAClient client;

  public ResourceTypeAdapter(CDAClient client) {
    this.client = client;
  }

  @Override public CDAResource deserialize(JsonElement jsonElement, Type type,
      JsonDeserializationContext context) throws JsonParseException {

    CDAResource result = null;

    JsonObject sys = jsonElement.getAsJsonObject().getAsJsonObject("sys");

    if (sys != null) {
      Constants.CDAResourceType resourceType =
          Constants.CDAResourceType.valueOf(sys.get("type").getAsString());

      if (Constants.CDAResourceType.Asset.equals(resourceType)) {
        result = deserializeAsset(jsonElement, context, sys);
      } else if (Constants.CDAResourceType.Entry.equals(resourceType)) {
        result = deserializeEntry(jsonElement, context, sys);
      } else if (Constants.CDAResourceType.ContentType.equals(resourceType)) {
        result = deserializeContentType(jsonElement, context, sys);
      } else if (Constants.CDAResourceType.Space.equals(resourceType)) {
        result = deserializeSpace(jsonElement, context, sys);
      } else {
        result = deserializeResource(jsonElement, context, sys);
      }
    }

    return result;
  }

  /**
   * De-serialize a resource of unknown type.
   *
   * @param jsonElement the resource in JSON form
   * @param context gson context
   * @param sys system attributes
   * @return {@code CDAResource} result object
   */
  private CDAResource deserializeResource(JsonElement jsonElement,
      JsonDeserializationContext context, JsonObject sys) {

    CDAResource result = new CDAResource();
    setBaseFields(result, sys, jsonElement, context);
    return result;
  }

  /**
   * De-serialize a resource of type Asset.
   *
   * @param jsonElement the resource in JSON form
   * @param context gson context
   * @param sys system attributes
   * @return {@code CDAAsset} result object
   */
  private CDAAsset deserializeAsset(JsonElement jsonElement, JsonDeserializationContext context,
      JsonObject sys) {

    CDAAsset result = new CDAAsset();
    setBaseFields(result, sys, jsonElement, context);

    Map fileMap = (Map) result.getFields().get("file");
    result.setUrl(String.format("%s:%s", client.getHttpScheme(), fileMap.get("url")));
    result.setMimeType((String) fileMap.get("contentType"));

    return result;
  }

  /**
   * De-serialize a resource of type Content Type.
   *
   * @param jsonElement the resource in JSON form
   * @param context gson context
   * @param sys map of system attributes
   * @return {@code CDAContentType} result object
   */
  private CDAContentType deserializeContentType(JsonElement jsonElement,
      JsonDeserializationContext context, JsonObject sys) {

    // Display field
    JsonObject attrs = jsonElement.getAsJsonObject();
    String displayField = getFieldAsString(attrs, "displayField");

    // Name
    String name = getFieldAsString(attrs, "name");

    // Description
    String userDescription = getFieldAsString(attrs, "description");

    CDAContentType result = new CDAContentType(displayField, name, userDescription);
    setBaseFields(result, sys, jsonElement, context);
    return result;
  }

  /**
   * De-serialize a resource of type Entry. This method should return a {@code CDAEntry} object or
   * in case the resource matches a previously registered custom class via {@link
   * CDAClient#registerCustomClass}, an object of that custom class type will be created.
   *
   * @param jsonElement the resource in JSON form
   * @param context gson context
   * @param sys map of system attributes
   * @return {@code CDAEntry} or a subclass of it
   */
  private CDAEntry deserializeEntry(JsonElement jsonElement, JsonDeserializationContext context,
      JsonObject sys) {

    CDAEntry result;

    String contentTypeId = sys.get("contentType")
        .getAsJsonObject()
        .get("sys")
        .getAsJsonObject()
        .get("id")
        .getAsString();

    Class<?> clazz = client.getCustomTypesMap().get(contentTypeId);

    if (clazz == null) {
      // Create a regular Entry, no custom class was registered.
      result = new CDAEntry();
    } else {
      // Use custom class registered for this Content Type.
      try {
        result = (CDAEntry) clazz.newInstance();
      } catch (InstantiationException e) {
        throw new JsonParseException(e);
      } catch (IllegalAccessException e) {
        throw new JsonParseException(e);
      }
    }

    setBaseFields(result, sys, jsonElement, context);
    return result;
  }

  /**
   * De-serialize a resource of type Space.
   *
   * @param jsonElement the resource in JSON form
   * @param context gson context
   * @param sys map of system attributes
   * @return {@code CDASpace} result object
   */
  private CDASpace deserializeSpace(JsonElement jsonElement, JsonDeserializationContext context,
      JsonObject sys) {
    // Name
    String name = jsonElement.getAsJsonObject().get("name").getAsString();

    // Locales
    JsonArray localesArray = jsonElement.getAsJsonObject().get("locales").getAsJsonArray();
    Type t = new TypeToken<ArrayList<Locale>>() { } .getType();
    ArrayList<Locale> locales = context.deserialize(localesArray, t);

    // Default locale
    String defaultLocale = Constants.DEFAULT_LOCALE;
    for (Locale l : locales) {
      if (l.isDefault) {
        defaultLocale = l.code;
        break;
      }
    }

    CDASpace result = new CDASpace(defaultLocale, locales, name);
    setBaseFields(result, sys, jsonElement, context);
    return result;
  }

  /**
   * Sets the base fields for a resource.
   * This will set the list fields based on the target's type, depending on whether
   * it is an instance of the {@code ResourceWithMap} class or the {@code ResourceWithList},
   * different results will be provided.
   *
   * This method will also set the map of system attributes for the resource.
   *
   * @param target Target {@link CDAResource} object to set fields for.
   * @param sys JsonObject representing the system attributes of the resource in JSON form.
   * @param jsonElement JsonElement representing the resource in JSON form.
   * @param context De-serialization context.
   * @throws JsonParseException on failure.
   */
  @SuppressWarnings("unchecked")
  private void setBaseFields(CDAResource target, JsonObject sys, JsonElement jsonElement,
      JsonDeserializationContext context) {
    // System attributes
    Map<String, Object> sysMap = context.deserialize(sys, Map.class);
    sysMap.put("space", client.getSpace());
    target.setSys(sysMap);

    // Fields
    JsonElement fields = jsonElement.getAsJsonObject().get("fields");
    if (target instanceof ResourceWithMap) {
      ResourceWithMap res = (ResourceWithMap) target;
      target.setLocale(client.getSpace().getDefaultLocale());
      res.setRawFields(
          context.<Map<String, Object>>deserialize(fields.getAsJsonObject(), Map.class));
      res.getLocalizedFieldsMap().put(client.getSpace().getDefaultLocale(), res.getRawFields());
    } else if (target instanceof ResourceWithList) {
      ResourceWithList<Object> res = (ResourceWithList<Object>) target;
      res.setFields(context.<List<Object>>deserialize(fields.getAsJsonArray(), List.class));
    }
  }

  private String getFieldAsString(JsonObject jsonObject, String name) {
    JsonElement value = jsonObject.get(name);
    if (value != null) {
      return value.getAsString();
    }
    return null;
  }
}
