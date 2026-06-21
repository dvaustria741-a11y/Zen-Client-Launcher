/**
 * zen_symbol_resolver.cpp
 *
 * Wraps dlopen / dlsym to let the rest of our codebase find Bedrock's
 * exported symbols by name without caring about which exact .so file they
 * came from or what the load address is.
 *
 * CONCEPTUAL OVERVIEW — HOW RUNTIME SYMBOL RESOLUTION WORKS IN THIS PATTERN
 * ───────────────────────────────────────────────────────────────────────────
 *
 * After MainActivity.kt executes:
 *   System.load("/path/to/libminecraftpe.so")   // loads Bedrock into process
 *   System.loadLibrary("zenclient")              // loads our hook lib
 *
 * Both DSOs (dynamically shared objects / .so files) are now fully mapped
 * into this process's virtual address space by the Android dynamic linker.
 *
 * WHAT THAT MEANS FOR SYMBOL RESOLUTION:
 *
 * Every exported function in libminecraftpe.so has been assigned a virtual
 * address within our process.  The linker built a symbol table (a sorted list
 * of {name → address} pairs) for each DSO.  Our code can query those tables
 * at runtime using the POSIX dynamic linking API:
 *
 *   void* handle = dlopen("libminecraftpe.so", RTLD_NOLOAD | RTLD_GLOBAL);
 *   void* sym    = dlsym(handle, "_ZN12SomeBedrockClass4someMethodEv");
 *
 * RTLD_NOLOAD: don't load the library again — just get a handle to the
 * already-mapped copy. This is safe and has no side effects.
 *
 * dlsym returns the virtual address of the symbol in the current process.
 * We cast that address to the correct function pointer type and call it
 * directly — no JNI, no IPC, just a normal C function call through a pointer.
 *
 * MANGLED NAMES:
 * C++ compilers encode type information into symbol names ("name mangling").
 * A Bedrock method like `void Player::attack(Entity*)` becomes something like
 * `_ZN6Player6attackEP6Entity` in the symbol table.  We look up by the
 * mangled name because that's what's actually in the .so's export table.
 * Tools like `nm -D libminecraftpe.so | c++filt` let you browse and demangle.
 *
 * INLINE HOOKS (optional, see zen_hook_installer.cpp):
 * For functions that aren't exported (i.e., not in the symbol table), we
 * locate them by scanning the .text segment for known byte patterns — a
 * technique sometimes called "signature scanning".  Once we have the address,
 * we overwrite the first few bytes with a jump to our stub (after mprotect).
 * This is how game clients typically hook internal non-exported functions.
 */

#include "zen_symbol_resolver.h"
#include <dlfcn.h>
#include <android/log.h>
#include <string>

#define LOG_TAG "ZenSymbolResolver"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global resolver instance — initialized from ZenNativeActivity.kt via JNI
ZenSymbolResolver* g_resolver = nullptr;

ZenSymbolResolver::ZenSymbolResolver(const std::string& bedrockLibPath)
    : m_libPath(bedrockLibPath) {
    // RTLD_NOLOAD: we don't want to load the library — it's already mapped.
    // RTLD_GLOBAL: not strictly needed since it's already global, but keeps
    //              the handle valid even if the original load used local scope.
    m_handle = dlopen(bedrockLibPath.c_str(), RTLD_NOLOAD | RTLD_GLOBAL);
    if (!m_handle) {
        // Fallback: try RTLD_DEFAULT which searches all already-loaded DSOs
        m_handle = RTLD_DEFAULT;
        LOGI("dlopen RTLD_NOLOAD failed (%s); falling back to RTLD_DEFAULT", dlerror());
    } else {
        LOGI("Got dlopen handle for %s", bedrockLibPath.c_str());
    }
}

ZenSymbolResolver::~ZenSymbolResolver() {
    if (m_handle && m_handle != RTLD_DEFAULT) {
        dlclose(m_handle);
    }
}

/**
 * resolve(mangledName)
 *
 * Returns the virtual address of an exported Bedrock symbol, or nullptr.
 * The caller must cast the returned pointer to the appropriate function type.
 *
 * Example usage:
 *   auto* fn = reinterpret_cast<void(*)(void*, int)>(
 *       g_resolver->resolve("_ZN10SomeClass3doEi")
 *   );
 *   if (fn) fn(instance, 42);
 */
void* ZenSymbolResolver::resolve(const char* mangledName) const {
    if (!m_handle) return nullptr;

    void* sym = dlsym(m_handle, mangledName);
    if (!sym) {
        // dlsym returns null both for "not found" and for actual null values.
        // dlerror() distinguishes them.
        const char* err = dlerror();
        if (err) {
            LOGE("Symbol not found: %s  (%s)", mangledName, err);
        }
    } else {
        LOGI("Resolved: %s → %p", mangledName, sym);
    }
    return sym;
}

/**
 * resolveOrAbort(mangledName)
 *
 * Like resolve() but fatally logs if the symbol isn't found.
 * Use only for symbols that are truly required for the hook to be safe.
 */
void* ZenSymbolResolver::resolveOrAbort(const char* mangledName) const {
    void* sym = resolve(mangledName);
    if (!sym) {
        __android_log_assert("symbol_not_found", LOG_TAG,
            "Required symbol missing: %s — cannot proceed safely.", mangledName);
    }
    return sym;
}
