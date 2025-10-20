package com.onefin.posapp.core.utils

import android.content.SharedPreferences
import android.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

object EncryptHelper {
    
    private const val SECRET_KEY = "EF8938A7C36596B31ED51B5767875"
    
    private const val PUBLIC_SERVER_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvigv7xw91VnfgzbswoloDjCS6BqaKySdCONr5f7JezmUa3z15JkBn0W3AyVfIKkSerOWmlaqwLZytXbARE+KcbkYUyFqG4pD7qlg7Lb1jcEz2zUNGucXvCPJNU6sLKWjQLzP14g8VPC0rUKEP4FFoSN8h1PTlWMFA4eTb+ZNpkAnq2zN/fB/Vr/3drC5/VJmUXpUiTPrFhnU3aKzN4y8+Xa5uo9Gb4T21WuGI9RaqP8ZaQm96ag+vzoYjRwIFrql4NZ5ZHyN5UeMWtQfL0Jkhm3cS0etWRIUinsuJjhlA8JP8qLlGcXuw9cKLX4P/LJiqGnyp9LySRUPCgdvYn6OGQIDAQAB"
    
    private const val PRIVATE_CLIENT_KEY = "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQC7p3iXqOFjoxXS3p0zgngf+7RrtZgN9C8aBqLtdB7l4zm++Y0Bk0NfhhePbywvKmRTCaxYxrnenKGPbQ/4pkTOsh7tk2d/pFFdxl1Klk9NPSAji5jYIzwWCJW8iSAJDJ7yiw3jAj/hyx/CZwGeG65lY1Rv6DudZaQjHit9y8hyrSfSpjqjEJKn6R02uCp73nxPQ9Hgfevvw1qcB++9BbO5+q0Q3dPZLJI0TNVNhptFIJWhhErygnjBnCAg3LotBbCgOtx+gCNUcFabvbnQft4e4Dq9PtqnjEa04KEEV7S0cDEPYY3zZ52hVIEn2HghO8lcXgRncZXTVumc+Y3Ce/mlAgMBAAECggEBAJajFFIiVnQGWaGB5I2R4V5DTwzbpGknKJxq0WVuPtNp+VQNvZyTG5VV7hnNM5nVHN3vuPM268QA1kxtT1HaHwgRwnQSTRYQ0ORHNWKHkLc/J0qBaDuw5S4GzDShmx7Ii9vFtmsRxjg6N4914r2KGQ/4kbKXqStriTxLnrwH1yv95M3J2V+oRotck1VbUiMRaKNiR1KkklREYNhTAdaM5VyjWMSt4+Zs1NJ4j6r/6BSpVlHDkeiGJHS5mHhUdQgkpmT+1cii8uuyljtpJAYpABENaqAWEIDBpWphfEVSv0Y7VEBxqu9KOFJi62VqHZ9lVA7yhPHw9fLTr4T6KB+HiC0CgYEA8AujnNKpQh9cjv1fdTYOSl+0dYHSwd6i3YMhr510gxdbMPLonyy9B+0G7Bb4qWn5mUjWe0i1LRyLLag1wle7XNK77hSUEZ34Ql1WKBp7G9SFrzIIHhdgCr9Sz3KeXsknKAqWo1yWJhyz3an39QmUlU8OjwKq57NOvGOb0aQGKicCgYEAyCBlpnkmxlGdfbQ3h1l79GyxHQm9xbqdObQR50LPA5jVdl18fCtiLTpE37O42AJvJl/SqeWqD2w4ZoNkW/0qI/OK6uSuJAGQnn1Einz+ZnjTzMG0rYNji9N/KKdWy2B2bbk3w5evAxHUmExO/Jdb5f5l25HMLyuAymo6ZZY9mVMCgYB02GWK56xGKHfojoMRzf9EyrNP46LQhevnQXZ7Qny9dvgHqqX7HU7idclB0Ki35oL9z0u/9RNj4xoIXnHUqNnmBBpAeLnenXOD8mUG3mUAlgGA1yzGYQB6GslXe2aFowqxXf4XhPD9mkkfZCXzm/c8eqAOkvDYGhsAbsghUuNBTwKBgQCI/IQYG73K6nrfXMjVwQ70FKJ3uf9IpaCRqwzGPBjv4WHcj8lyRVACnWwfpCnW0nO61MSivy5VOEKzCSVdQkHiMgbZGoeI5flUQ3LzSPPquLJh6gX+73zobXERJtpmhDUMjkf5fo6xjzbyuOkoRYMGP8kBLx+Q+jpCU8x9VQX9OQKBgQCHE7GOVMnSmZj9lfaQXbPU/9wS2vFYLyh4W4MQO7q8VfpT93QHz0i55jWQEvzBewaqXo8EAd6VLZ6KjqGWWZOrylJQDbI0KBBQEZLg4xSyvR+kF8gBwPyLSm+ppqpvUwH+7wt+Semo42oFN42QvfA6kVkdvz6RL0R23/ker9xTMg=="
    
    private const val DEVICE_SERIAL_KEY = "device_serial_key"
    
    /**
     * Sign data with RSA private key
     */
    fun sign(data: String, sharedPreferences: SharedPreferences): String {
        val privateKeyPEM = PRIVATE_CLIENT_KEY
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        
        val keyBytes = Base64.decode(privateKeyPEM, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKey = keyFactory.generatePrivate(keySpec)
        
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(data.toByteArray(Charsets.UTF_8))
        
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }
    
    /**
     * Encrypt data with 3DES
     */
    fun encrypt(data: String, sharedPreferences: SharedPreferences): String {
        val serial = sharedPreferences.getString(DEVICE_SERIAL_KEY, "") ?: ""
        val combined = (serial + SECRET_KEY).padEnd(24, '0').substring(0, 24)
        val keyBytes = combined.toByteArray(Charsets.UTF_8)
        
        val cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding")
        val secretKey = SecretKeySpec(keyBytes, "DESede")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }
    
    /**
     * Decrypt data with 3DES
     */
    fun decrypt(encryptedBase64: String, sharedPreferences: SharedPreferences): String {
        val serial = sharedPreferences.getString(DEVICE_SERIAL_KEY, "") ?: ""
        val combined = (serial + SECRET_KEY).padEnd(24, '0').substring(0, 24)
        val keyBytes = combined.toByteArray(Charsets.UTF_8)
        
        val cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding")
        val secretKey = SecretKeySpec(keyBytes, "DESede")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        
        val encryptedBytes = Base64.decode(encryptedBase64, Base64.DEFAULT)
        val decrypted = cipher.doFinal(encryptedBytes)
        
        return String(decrypted, Charsets.UTF_8)
    }
    
    /**
     * Verify signature with RSA public key
     */
    fun verifySign(message: String, signatureBase64: String): Boolean {
        return try {
            val publicKeyPEM = PUBLIC_SERVER_KEY
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")
            
            val keyBytes = Base64.decode(publicKeyPEM, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec)
            
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initVerify(publicKey)
            signature.update(message.toByteArray(Charsets.UTF_8))
            
            val signatureBytes = Base64.decode(signatureBase64, Base64.DEFAULT)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Generate random string
     */
    fun generateRandomString(length: Int = 10): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }
}