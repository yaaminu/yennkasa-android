#include <string>
#include <memory>
#include <limits>
#include <stdexcept>
#include <openssl/evp.h>
#include <openssl/rand.h>

#include "pairapp.h"

using EVP_CIPHER_CTX_free_ptr = std::unique_ptr<EVP_CIPHER_CTX, decltype(&::EVP_CIPHER_CTX_free)>;

// g++ -Wall -std=c++11
int gen_params(unsigned char  key[KEY_SIZE], unsigned char iv[])
{
    EVP_add_cipher(EVP_aes_256_cbc());
    int rc = RAND_bytes(key, KEY_SIZE);
    if (rc != 1)
        //throw std::runtime_error("RAND_bytes key failed");
        return -1;

    rc = RAND_bytes(iv, BLOCK_SIZE);
    if (rc != 1)
        //throw std::runtime_error("RAND_bytes for iv failed");
        return -1;

    return 0;
}

//char* can be used in place of secure_string but secure_string is preferred
int crypto_aes_encrypt(unsigned char key[KEY_SIZE], const secure_string& input, secure_string& out)
{
    //convert key and initializatino vector into random bytes
    unsigned char iv[BLOCK_SIZE];
    gen_params(key, iv);
    EVP_CIPHER_CTX_free_ptr ctx(EVP_CIPHER_CTX_new(), ::EVP_CIPHER_CTX_free);
    int rc = EVP_EncryptInit_ex(ctx.get(), EVP_aes_256_cbc(), NULL, key, iv);
    if (rc != 1)
        //throw std::runtime_error("EVP_EncryptInit_ex failed");
        return -1;

    // Recovered text expands upto BLOCK_SIZE
    out.resize(input.size()+BLOCK_SIZE);
    int out_len1 = (int)out.size();

    rc = EVP_EncryptUpdate(ctx.get(), (unsigned char *)&out[0], &out_len1, (const unsigned char *)&input[0], (int)input.size());
    if (rc != 1)
        //throw std::runtime_error("EVP_EncryptUpdate failed");
        return -1;

    int out_len2 = (int)out.size() - out_len1;
    rc = EVP_EncryptFinal_ex(ctx.get(), (unsigned char *)&out[0]+out_len1, &out_len2);
    if (rc != 1)
        //throw std::runtime_error("EVP_EncryptFinal_ex failed");
        return -1;

    // Set cipher text size now that we know it
    out.resize(out_len1 + out_len2);

    return 0;
}

//char* can be used in place of secure_string even though secure_string is more preferred
int crypto_aes_decrypt(unsigned char key[KEY_SIZE], secure_string &input, secure_string &output)
{
    unsigned char iv[BLOCK_SIZE];
    EVP_CIPHER_CTX_free_ptr ctx(EVP_CIPHER_CTX_new(), ::EVP_CIPHER_CTX_free);
    int rc = EVP_DecryptInit_ex(ctx.get(), EVP_aes_256_cbc(), NULL, key, iv);
    if (rc != 1)
        //throw std::runtime_error("EVP_DecryptInit_ex failed");
        return -1;

    //Recovered text contracts upto BLOCK_SIZE
    output.resize(input.size());
    int out_len1 = (int)output.size();

    rc = EVP_DecryptUpdate(ctx.get(), (unsigned char*)&output[0], &out_len1, (const unsigned char *)&input[0], (int)input.size());
    if (rc != 1)
        //throw std::runtime_error("EVP_DecryptUpdate failed");
        return -1;

    int out_len2 = (int)output.size() - out_len1;
    rc = EVP_DecryptFinal_ex(ctx.get(), (unsigned char *)&output[0]+out_len1, &out_len2);
    if (rc != 1)
        //throw std::runtime_error("EVP_DecryptFinal_ex failed");
        return -1;

    // Set recovered text size now that we know it
    output.resize(out_len1 + out_len2);
    OPENSSL_cleanse(key, KEY_SIZE);
    OPENSSL_cleanse(iv, BLOCK_SIZE);

    return 0;
}

/*
    usage:
    secure_string plainText = "some random text"; or char const *plainText = "some random text";
    secure_string cryptedText, decryptedText;
    unsigned char key[KEY_SIZE];

    //encrypt plainText to cryptedText using the specified key
    crypto_aes_encrypt(key, plainText, decryptedText);

    //decrypt cryptedText to recoveredText using specified key
    crypto_aes_decrypt(key, cryptedText, decryptedText);
*/

