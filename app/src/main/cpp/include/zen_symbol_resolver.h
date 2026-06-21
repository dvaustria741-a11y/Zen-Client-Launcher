// zen_symbol_resolver.h
#pragma once
#include <string>

/**
 * ZenSymbolResolver
 *
 * Thin wrapper around dlopen/dlsym that provides a stable interface for
 * looking up Bedrock exported symbols by mangled name at runtime.
 *
 * Initialized once from the JNI bridge after MainActivity passes the
 * Bedrock native library directory path.
 */
class ZenSymbolResolver {
public:
    explicit ZenSymbolResolver(const std::string& bedrockLibPath);
    ~ZenSymbolResolver();

    // Returns the virtual address of `mangledName` in the Bedrock DSO,
    // or nullptr if not found.
    void* resolve(const char* mangledName) const;

    // Same as resolve() but fatally logs and aborts if not found.
    void* resolveOrAbort(const char* mangledName) const;

private:
    std::string m_libPath;
    void*       m_handle = nullptr;
};

extern ZenSymbolResolver* g_resolver;
