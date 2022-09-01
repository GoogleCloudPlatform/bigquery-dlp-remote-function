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

package com.google.cloud.solutions.bqremoteencryptionfn.fns;


import com.google.cloud.solutions.bqremoteencryptionfn.TransformFnFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * Implementation of AES Encryption. The actual Key type is passed as a parameter and is dependent
 * on the KeyString length.
 */
public final class AesFn extends UnaryStringArgFn {

  public static final String FN_NAME = "aes";
  public static final String AES_CIPHER_TYPE_KEY = "aes-cipher-type";
  public static final String AES_IV_PARAMETER_KEY = "aes-iv-parameter-base64";

  public enum AesKeyType {
    UTF8_KEY,
    BASE64_KEY
  }

  @Component
  @PropertySource("classpath:aes.properties")
  public static class AesTransformFnFactory implements TransformFnFactory<AesFn> {

    @Value("${aesKey}")
    private String aesKeyString;

    @Value("${aesKeyType}")
    private AesKeyType aesKeyType;

    @Value("${aesCipherType}")
    private String cipherType;

    @Value("${aesIvParameterBase64}")
    private String ivParameterBase64;

    @Override
    public String getFnName() {
      return FN_NAME;
    }

    @Override
    public AesFn createFn(@Nonnull Map<String, String> options) {
      return new AesFn(
          aesKeyString,
          aesKeyType,
          options.getOrDefault(AES_CIPHER_TYPE_KEY, cipherType),
          options.getOrDefault(AES_IV_PARAMETER_KEY, ivParameterBase64));
    }
  }

  private final String keyString;
  private final AesKeyType keyType;
  private final String cipherTransformType;
  private final String ivParameterBase64;

  public AesFn(
      String keyString, AesKeyType keyType, String cipherTransformType, String ivParameterBase64) {
    this.keyString = keyString;
    this.keyType = keyType;
    this.cipherTransformType = cipherTransformType;
    this.ivParameterBase64 = ivParameterBase64;
  }

  @Override
  public List<String> deidentifyUnaryRow(List<String> rows) throws Exception {
    var encryptCipher = makeCipher(Cipher.ENCRYPT_MODE);

    var encoder = Base64.getEncoder();

    ImmutableList.Builder<String> replies = ImmutableList.builder();

    for (String element : rows) {
      var bytes = element.getBytes(StandardCharsets.UTF_8);
      replies.add(encoder.encodeToString(encryptCipher.doFinal(bytes)));
    }
    return replies.build();
  }

  @Override
  public List<String> reidentifyUnaryRow(List<String> rows) throws Exception {

    var decryptCipher = makeCipher(Cipher.DECRYPT_MODE);

    var decoder = Base64.getDecoder();

    ImmutableList.Builder<String> replies = ImmutableList.builder();

    for (String base64String : rows) {
      var bytes = decoder.decode(base64String);
      replies.add(new String(decryptCipher.doFinal(bytes), StandardCharsets.UTF_8));
    }
    return replies.build();
  }

  @Override
  public String getName() {
    return FN_NAME;
  }

  private Cipher makeCipher(int opMode) throws GeneralSecurityException {

    var keyBytes =
        switch (keyType) {
          case UTF8_KEY -> keyString.getBytes(StandardCharsets.UTF_8);
          case BASE64_KEY -> BaseEncoding.base64().decode(keyString);
        };

    var secretKey = new SecretKeySpec(keyBytes, "AES");
    var cipher = Cipher.getInstance(cipherTransformType);

    if (!cipherTransformType.toUpperCase().contains("ECB")) {
      var ivBytes = Base64.getDecoder().decode(ivParameterBase64);
      cipher.init(opMode, secretKey, new IvParameterSpec(ivBytes));
    } else {
      cipher.init(opMode, secretKey);
    }

    return cipher;
  }
}
