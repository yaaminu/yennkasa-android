/**
 this header file will contain all native implementations of the pariapp instant
 messenger. The first implementation of course is our crypto api.
*/


//TODO yaaminu: declare error codes for use in entire app


/*****************begin crypto**********************/

/**
 serves as tuple for holding pair of items.
*/
struct tuple
{
  char* first,
  char* second
};

static const unsigned int KEY_SIZE = 32;
static const unsigned int BLOCK_SIZE = 16;

template <typename T>
struct zallocator
{
public:
    typedef T value_type;
    typedef value_type* pointer;
    typedef const value_type* const_pointer;
    typedef value_type& reference;
    typedef const value_type& const_reference;
    typedef std::size_t size_type;
    typedef std::ptrdiff_t difference_type;

    pointer address (reference v) const
    {
        return &v;
    }
    const_pointer address (const_reference v) const
    {
        return &v;
    }

    pointer allocate (size_type n, const void* hint = 0)
    {
        if (n > std::numeric_limits<size_type>::max() / sizeof(T))
            throw std::bad_alloc();
        return static_cast<pointer> (::operator new (n * sizeof (value_type)));
    }

    void deallocate(pointer p, size_type n)
    {
        OPENSSL_cleanse(p, n*sizeof(T));
        ::operator delete(p);
    }

    size_type max_size() const
    {
        return std::numeric_limits<size_type>::max() / sizeof (T);
    }

    template<typename U>
    struct rebind
    {
        typedef zallocator<U> other;
    };

    void construct (pointer ptr, const T& val)
    {
        new (static_cast<T*>(ptr) ) T (val);
    }

    void destroy(pointer ptr)
    {
        static_cast<T*>(ptr)->~T();
    }

#if __cpluplus >= 201103L
    template<typename U, typename... Args>
    void construct (U* ptr, Args&&  ... args)
    {
        ::new (static_cast<void*> (ptr) ) U (std::forward<Args> (args)...);
    }

    template<typename U>
    void destroy(U* ptr)
    {
        ptr->~U();
    }
#endif
};

typedef unsigned char byte;
typedef std::basic_string<char, std::char_traits<char>, zallocator<char> > secure_string;
//TODO coded_raf:
//I dont personally know what it looks like in the openssl api so you
//may modify they actual type accordingly
typedef char* rsa_key_t;

/**
 generates a public/private key pair. the passed tupple is allocated
  and ready to be used.
 @param container- servers as container for storing the generated key pairs
 @returns 0 for success any othe integer denotes failure.
*/
//TODO coded_raf: implement these
int crypto_gen_rsa_public_private_key(tuple* container);

/**
  encrypts input into out with the key using AES
 @returns 0 for success any othe integer denotes failure.
*/
int crypto_aes_encrypt(unsigned char key[KEY_SIZE],char* input,char* out);

/**
  decrypts input into out with the key using AES
 @returns 0 for success any othe integer denotes failure.
*/
int crypto_aes_decrypt(unsigned char key[KEY_SIZE],char* input,char* out);

/**
  encrypts input into out with the key using RSA
 @returns 0 for success any othe integer denotes failure.
*/
//TODO:
int crytpo_rsa_encrypt(rsa_key_t public_key,char* input,char*out);

/**
  decrypts input into out with the key using RSA
 @returns 0 for success any othe integer denotes failure.
*/
//TODO:
int crytpo_rsa_decrypt(rsa_key_t private_key,char* input,char*out);

//I don't know if openssl provides crc32 or not.
//we can easily do this in java land but it can be done here. it's
//preferred
/**
 checks the integrity of input using the crc32 algorithm
*/

//TODO coded_raf: verify if uint32 is avaialble by default
//in the android gcc compiler
int crypto_crc32_validate(uint32 checksum,char* input);

/**************end crypto*********************************************/
