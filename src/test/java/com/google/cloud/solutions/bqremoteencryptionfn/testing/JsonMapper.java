/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.solutions.bqremoteencryptionfn.testing;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

public final class JsonMapper {

  private static final Gson jsonMapper = new Gson();

  public static <T> String toJson(T obj) {
    try {
      if (obj instanceof Message proto) {
        return JsonFormat.printer().print(proto);
      }

      return jsonMapper.toJson(obj);
    } catch (InvalidProtocolBufferException e) {
      return "";
    }
  }

  public static <T> T fromJson(String json, Class<T> clazz) {
    return jsonMapper.fromJson(json, TypeToken.get(clazz));
  }

  @SuppressWarnings("unchecked") // Use of generics for creation of Proto message from JSON.
  public static <T extends Message> T jsonToProto(String json, Class<T> protoClazz) {
    try {
      var builder = (Message.Builder) protoClazz.getMethod("newBuilder").invoke(null);
      JsonFormat.parser().merge(json, builder);
      return (T) builder.build();
    } catch (Exception exception) {
      throw new RuntimeException("error converting\n" + json, exception);
    }
  }

  private JsonMapper() {}
}
